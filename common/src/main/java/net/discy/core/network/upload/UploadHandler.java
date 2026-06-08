package net.discy.core.network.upload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.discy.core.block.DjDeckBlockEntity;
import net.discy.core.item.BlankMusicDiscItem;
import net.discy.core.item.CustomDiscItem;
import net.discy.core.library.SongInfo;
import net.discy.core.library.SongLibrary;
import net.discy.core.network.DiscyNetworking;
import net.discy.core.screen.DjDeckMenu;
import net.discy.core.util.AudioLengthReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class UploadHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadHandler.class);
    private static final long SESSION_IDLE_TIMEOUT_MS = 60_000L;
    private static final Map<UUID, UploadSession> SESSIONS = new HashMap<>();

    private UploadHandler() {}

    public static void onBegin(ServerPlayer player, UUID uploadId, String displayName,
                               long totalBytes, BlockPos deckPos, String fileExtension,
                               int chosenSlot, String textureLabel) {
        evictStaleSessions();

        if (SESSIONS.containsKey(uploadId)) {
            SESSIONS.remove(uploadId).cleanup();
        }
        if (totalBytes <= 0 || totalBytes > DiscyNetworking.MAX_UPLOAD_BYTES) {
            DiscyNetworking.sendUploadError(player, uploadId,
                    "File too large (max " + (DiscyNetworking.MAX_UPLOAD_BYTES / 1024 / 1024) + " MB)");
            return;
        }
        if (displayName == null || displayName.isBlank()) {
            DiscyNetworking.sendUploadError(player, uploadId, "Empty display name");
            return;
        }
        if (!validateDeck(player, deckPos)) {
            DiscyNetworking.sendUploadError(player, uploadId,
                    "DJ deck no longer reachable — re-open the menu and try again.");
            return;
        }
        if (player.level() instanceof ServerLevel sl
                && sl.getBlockEntity(deckPos) instanceof DjDeckBlockEntity deck) {
            if (deck.getDisc().isEmpty()) {
                DiscyNetworking.sendUploadError(player, uploadId,
                        "Insert a disc into the deck before burning.");
                return;
            }
        }

        try {
            Path temp = SongLibrary.uploadsDir().resolve(uploadId + ".tmp");
            UploadSession session = new UploadSession(uploadId, player.getUUID(), displayName.trim(),
                    totalBytes, deckPos, temp, fileExtension, chosenSlot, textureLabel);
            session.open();
            SESSIONS.put(uploadId, session);
            LOGGER.info("Upload {} begun: \"{}\" {} bytes from {}", uploadId, session.displayName,
                    totalBytes, player.getName().getString());
        } catch (IOException e) {
            DiscyNetworking.sendUploadError(player, uploadId,
                    "Server could not open temp file: " + e.getMessage());
        }
    }

    public static void onChunk(ServerPlayer player, UUID uploadId, byte[] chunk) {
        UploadSession session = SESSIONS.get(uploadId);
        if (session == null || !session.playerUuid.equals(player.getUUID())) return;
        if (session.finished) return;
        long projected = session.bytesReceived + (long) chunk.length;
        if (projected > session.expectedBytes || projected > DiscyNetworking.MAX_UPLOAD_BYTES) {
            failAndCleanup(player, session, "Upload exceeded declared size");
            return;
        }
        try {
            session.appendChunk(chunk);
        } catch (IOException e) {
            failAndCleanup(player, session, "I/O error during upload: " + e.getMessage());
        }
    }

    public static void onEnd(ServerPlayer player, UUID uploadId) {
        UploadSession session = SESSIONS.remove(uploadId);
        if (session == null) {
            DiscyNetworking.sendUploadError(player, uploadId,
                    "Unknown upload id — connection may have dropped.");
            return;
        }
        if (!session.playerUuid.equals(player.getUUID())) {
            session.cleanup();
            return;
        }
        session.finished = true;
        session.close();

        if (session.bytesReceived != session.expectedBytes) {
            session.cleanup();
            DiscyNetworking.sendUploadError(player, uploadId,
                    "Upload truncated (" + session.bytesReceived + "/" + session.expectedBytes + " bytes)");
            return;
        }

        String hash;
        try {
            hash = SongLibrary.hashFile(session.tempFile);
        } catch (IOException e) {
            session.cleanup();
            DiscyNetworking.sendUploadError(player, uploadId, "Could not hash uploaded file");
            return;
        }

        int lengthSeconds = AudioLengthReader.readSeconds(session.tempFile);

        SongLibrary library = SongLibrary.get();
        Optional<SongInfo> existing = library.byHash(hash);
        SongInfo song;
        boolean isNewSong;
        if (existing.isPresent()) {
            session.cleanup();
            song = existing.get();
            isNewSong = false;
        } else {
            String safeName = SongLibrary.sanitizeForFilename(session.displayName);
            String ext = session.fileExtension.startsWith(".") ? session.fileExtension : "." + session.fileExtension;
            String fileBase = hash.substring(0, 12) + "_" + safeName;
            Path songsDir = SongLibrary.songsDir();
            Path dst = songsDir.resolve(fileBase + ext);
            Path metaPath = songsDir.resolve(fileBase + ".json");
            try {
                SongLibrary.ensureDirectory(songsDir);
                Files.move(session.tempFile, dst, StandardCopyOption.REPLACE_EXISTING);
                SongLibrary.writeSidecar(metaPath, session.displayName, lengthSeconds);
            } catch (IOException e) {
                session.cleanup();
                DiscyNetworking.sendUploadError(player, uploadId, "Could not save uploaded file");
                return;
            }
            library.scanSongsFolder();
            song = library.byHash(hash).orElse(null);
            if (song == null) {
                DiscyNetworking.sendUploadError(player, uploadId,
                        "Internal error: uploaded file not found in library after rescan");
                return;
            }
            isNewSong = true;
        }

        autoBindDeck(player, session.deckPos, song, session.chosenSlot, session.textureLabel);

        DiscyNetworking.sendUploadDone(player, uploadId, hash);
        player.sendSystemMessage(Component.literal("Burn complete: " + song.displayName())
                .withStyle(ChatFormatting.GREEN));

        if (isNewSong) {
            DiscyNetworking.broadcastSongAdded(song, player);
            DiscyNetworking.distributeSongToOthers(player, song.hash());
        }
        // Uploader is excluded from distributeSongToOthers; sync audio to their client too.
        DiscyNetworking.distributeSongToClient(player, song.hash());
    }

    public static void dropSessionsFor(UUID playerUuid) {
        SESSIONS.values().removeIf(s -> {
            if (s.playerUuid.equals(playerUuid)) {
                s.cleanup();
                return true;
            }
            return false;
        });
    }

    private static void evictStaleSessions() {
        long cutoff = System.currentTimeMillis() - SESSION_IDLE_TIMEOUT_MS;
        SESSIONS.values().removeIf(s -> {
            if (s.lastActivityMs < cutoff) {
                s.cleanup();
                return true;
            }
            return false;
        });
    }

    private static void failAndCleanup(ServerPlayer player, UploadSession session, String message) {
        SESSIONS.remove(session.uploadId);
        session.cleanup();
        DiscyNetworking.sendUploadError(player, session.uploadId, message);
    }

    private static boolean validateDeck(ServerPlayer player, BlockPos deckPos) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof DjDeckMenu deckMenu)) return false;
        if (!deckMenu.getDeckPos().equals(deckPos)) return false;
        if (!(player.level() instanceof ServerLevel sl)) return false;
        BlockEntity be = sl.getBlockEntity(deckPos);
        return be instanceof DjDeckBlockEntity;
    }

    private static boolean autoBindDeck(ServerPlayer player, BlockPos deckPos, SongInfo song,
                                        int chosenSlot, String textureLabel) {
        if (!(player.level() instanceof ServerLevel sl)) return false;
        BlockEntity be = sl.getBlockEntity(deckPos);
        if (!(be instanceof DjDeckBlockEntity deck)) return false;
        ItemStack current = deck.getDisc();
        if (current.isEmpty()) return false;

        if (!(current.getItem() instanceof BlankMusicDiscItem)
                && !(current.getItem() instanceof CustomDiscItem)
                && !(current.getItem() instanceof RecordItem)) {
            return false;
        }

        ItemStack bound = DiscyNetworking.createBoundDisc(song, chosenSlot, textureLabel);
        deck.setDisc(bound);
        if (player.containerMenu instanceof DjDeckMenu menu && menu.getDeckPos().equals(deckPos)) {
            menu.getSlot(0).set(bound);
            menu.broadcastChanges();
        }
        return true;
    }
}
