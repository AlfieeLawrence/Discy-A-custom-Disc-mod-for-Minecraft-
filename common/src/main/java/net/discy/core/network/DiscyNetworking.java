package net.discy.core.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.discy.core.block.DjDeckBlockEntity;
import net.discy.core.item.BlankMusicDiscItem;
import net.discy.core.item.CustomDiscItem;
import net.discy.core.library.SongInfo;
import net.discy.core.library.SongLibrary;
import net.discy.core.network.upload.UploadHandler;
import net.discy.core.registry.ObjectRegistry;
import net.discy.core.client.texture.PermanentDiscTextures;
import net.discy.core.screen.DjDeckMenu;
import net.discy.core.screen.DjDeckMenus;
import net.discy.core.util.DiscyIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

public class DiscyNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscyNetworking.class);
    public static final long MAX_UPLOAD_BYTES = 50L * 1024L * 1024L;
    public static final int CHUNK_SIZE = 16384;

    public static final ResourceLocation BURN_DISC = new DiscyIdentifier("burn_disc");
    public static final ResourceLocation BURN_WITH_LABEL = new DiscyIdentifier("burn_with_label");
    public static final ResourceLocation UPLOAD_BEGIN = new DiscyIdentifier("upload_begin");
    public static final ResourceLocation UPLOAD_CHUNK = new DiscyIdentifier("upload_chunk");
    public static final ResourceLocation UPLOAD_END = new DiscyIdentifier("upload_end");
    public static final ResourceLocation UPLOAD_DONE = new DiscyIdentifier("upload_done");
    public static final ResourceLocation UPLOAD_ERROR = new DiscyIdentifier("upload_error");
    public static final ResourceLocation SONG_ADDED = new DiscyIdentifier("song_added");
    public static final ResourceLocation REQUEST_SONGS = new DiscyIdentifier("request_songs");
    public static final ResourceLocation REQUEST_REFRESH = new DiscyIdentifier("request_refresh");
    public static final ResourceLocation SONG_LIST = new DiscyIdentifier("song_list");
    public static final ResourceLocation UPLOAD_TEXTURE = new DiscyIdentifier("upload_texture");
    public static final ResourceLocation TEXTURE_ADDED = new DiscyIdentifier("texture_added");
    public static final ResourceLocation REMOVE_SONG = new DiscyIdentifier("remove_song");
    public static final ResourceLocation REMOVE_TEXTURE = new DiscyIdentifier("remove_texture");
    public static final ResourceLocation SONG_REMOVED = new DiscyIdentifier("song_removed");
    public static final ResourceLocation RENAME_SONG = new DiscyIdentifier("rename_song");
    public static final ResourceLocation SONG_RENAMED = new DiscyIdentifier("song_renamed");
    public static final ResourceLocation TEXTURE_REMOVED = new DiscyIdentifier("texture_removed");
    public static final ResourceLocation DISTRIBUTE_TEXTURE = new DiscyIdentifier("distribute_texture");
    public static final ResourceLocation DISTRIBUTE_SONG = new DiscyIdentifier("distribute_song");
    public static final ResourceLocation DISTRIBUTE_SONG_CHUNK = new DiscyIdentifier("distribute_song_chunk");
    public static final ResourceLocation PLAY_DISC = new DiscyIdentifier("play_disc");
    public static final ResourceLocation STOP_DISC = new DiscyIdentifier("stop_disc");
    public static final ResourceLocation PLAY_DISC_AT = new DiscyIdentifier("play_disc_at");
    public static final ResourceLocation STOP_DISC_AT = new DiscyIdentifier("stop_disc_at");
    /** Sophisticated Backpacks / Storage — stop by storage UUID (position may have moved). */
    public static final ResourceLocation STOP_DISC_PORTABLE = new DiscyIdentifier("stop_disc_portable");
    /** Sophisticated Backpacks / Storage — move an active custom stream to a new position. */
    public static final ResourceLocation UPDATE_DISC_PORTABLE = new DiscyIdentifier("update_disc_portable");

    private static final double JUKEBOX_AUDIENCE_RADIUS = 64.0D;
    private static final double PORTABLE_AUDIENCE_RADIUS = 128.0D;

    private static Path gameDir = Path.of(".");

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, BURN_DISC, (buf, context) -> {
            BlockPos deckPos = buf.readBlockPos();
            String songHash = buf.readUtf();
            int textureSlot = buf.readVarInt();
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                context.queue(() -> handleBurn(serverPlayer, deckPos, songHash, textureSlot, null));
            }
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, BURN_WITH_LABEL, (buf, context) -> {
            BlockPos deckPos = buf.readBlockPos();
            String songHash = buf.readUtf();
            String textureLabel = buf.readUtf();
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                context.queue(() -> handleBurn(serverPlayer, deckPos, songHash, 0, textureLabel));
            }
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, UPLOAD_BEGIN, (buf, context) -> {
            UUID uploadId = buf.readUUID();
            String displayName = buf.readUtf();
            buf.readVarInt(); // legacy length field from client — server computes
            long totalBytes = buf.readLong();
            String fileExtension = buf.readUtf();
            BlockPos deckPos = buf.readBlockPos();
            int chosenSlot = buf.readVarInt();
            String textureLabel = buf.readUtf(64);
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                context.queue(() -> UploadHandler.onBegin(serverPlayer, uploadId, displayName,
                        totalBytes, deckPos, fileExtension, chosenSlot, textureLabel));
            }
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, UPLOAD_CHUNK, (buf, context) -> {
            UUID uploadId = buf.readUUID();
            int length = buf.readVarInt();
            byte[] chunk = buf.readByteArray(length);
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                context.queue(() -> UploadHandler.onChunk(serverPlayer, uploadId, chunk));
            }
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, UPLOAD_END, (buf, context) -> {
            UUID uploadId = buf.readUUID();
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                context.queue(() -> UploadHandler.onEnd(serverPlayer, uploadId));
            }
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, REQUEST_SONGS, (buf, context) -> {
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                context.queue(() -> sendSongList(serverPlayer));
            }
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, REQUEST_REFRESH, (buf, context) -> {
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                context.queue(() -> refreshDjDeck(serverPlayer));
            }
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, UPLOAD_TEXTURE, (buf, context) -> {
            String label = buf.readUtf(128);
            byte[] pngBytes = buf.readByteArray(4 * 1024 * 1024);
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                context.queue(() -> handleUploadTexture(serverPlayer, label, pngBytes));
            }
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, REMOVE_SONG, (buf, context) -> {
            String songHash = buf.readUtf();
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                context.queue(() -> handleRemoveSong(serverPlayer, songHash));
            }
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, REMOVE_TEXTURE, (buf, context) -> {
            String label = buf.readUtf(128);
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                context.queue(() -> handleRemoveTexture(serverPlayer, label));
            }
        });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, RENAME_SONG, (buf, context) -> {
            String songHash = buf.readUtf();
            String newName = buf.readUtf(64);
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                context.queue(() -> handleRenameSong(serverPlayer, songHash, newName));
            }
        });
    }

    private static void handleBurn(ServerPlayer player, BlockPos deckPos, String songHash,
                                   int textureSlot, String textureLabel) {
        if (player.level().isClientSide) return;
        if (!(player.level() instanceof ServerLevel sl)) return;

        BlockEntity be = sl.getBlockEntity(deckPos);
        if (!(be instanceof DjDeckBlockEntity deck)) {
            player.sendSystemMessage(Component.literal("DJ Deck not found at that position.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        double distSq = player.distanceToSqr(deckPos.getX() + 0.5, deckPos.getY() + 0.5, deckPos.getZ() + 0.5);
        if (distSq > 64.0 * 64.0) {
            player.sendSystemMessage(Component.literal("You are too far from the DJ Deck.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        ItemStack current = deck.getDisc();
        if (current.isEmpty()) {
            player.sendSystemMessage(Component.literal("No disc in the deck!")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        if (!(current.getItem() instanceof BlankMusicDiscItem)
                && !(current.getItem() instanceof CustomDiscItem)
                && !(current.getItem() instanceof RecordItem)) {
            player.sendSystemMessage(Component.literal("This item cannot be bound to a song!")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        SongInfo song = SongLibrary.get().getSong(songHash);
        if (song == null) {
            player.sendSystemMessage(Component.literal("Song not found: " + songHash.substring(0, 8)));
            return;
        }

        ItemStack result = createBoundDisc(song, textureSlot, textureLabel);
        deck.setDisc(result);
        if (player.containerMenu instanceof DjDeckMenu menu && menu.getDeckPos().equals(deckPos)) {
            menu.getSlot(0).set(result);
            menu.broadcastChanges();
        }

        player.sendSystemMessage(Component.literal("Created disc: " + song.displayName())
                .withStyle(net.minecraft.ChatFormatting.GREEN));
    }

    /** Always produces a {@link CustomDiscItem} stack (vanilla discs are converted, not re-tagged in place). */
    public static ItemStack createBoundDisc(SongInfo song, int textureSlot, String textureLabel) {
        ItemStack result = new ItemStack(ObjectRegistry.CUSTOM_DISC.get(), 1);
        if (textureLabel != null && !textureLabel.isBlank()) {
            CustomDiscItem.bindWithLabel(result, song, textureLabel);
        } else {
            CustomDiscItem.bind(result, song, textureSlot);
        }
        return result;
    }

    private static void handleRemoveSong(ServerPlayer player, String songHash) {
        if (songHash == null || songHash.isBlank()) return;

        SongInfo song = SongLibrary.get().getSong(songHash);
        if (song == null) {
            player.sendSystemMessage(Component.literal("Song not found.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        SongLibrary.deleteSongFiles(songHash);
        SongLibrary.get().removeSong(songHash);
        broadcastSongRemoved(songHash, player);
        player.sendSystemMessage(Component.literal("Removed song: " + song.displayName())
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
        LOGGER.info("Song removed: {} by {}", song.displayName(), player.getName().getString());
    }

    private static void handleRenameSong(ServerPlayer player, String songHash, String newName) {
        if (songHash == null || songHash.isBlank() || newName == null) return;

        String trimmed = newName.trim();
        if (trimmed.isBlank() || trimmed.length() > 64) {
            player.sendSystemMessage(Component.literal("Invalid song name.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        SongInfo song = SongLibrary.get().getSong(songHash);
        if (song == null) {
            player.sendSystemMessage(Component.literal("Song not found.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        if (trimmed.equals(song.displayName())) return;

        SongLibrary.updateSongDisplayNameOnDisk(songHash, trimmed);
        SongLibrary.get().renameSong(songHash, trimmed);
        broadcastSongRenamed(songHash, trimmed, player);
        player.sendSystemMessage(Component.literal("Renamed song to: " + trimmed)
                .withStyle(net.minecraft.ChatFormatting.GREEN));
        LOGGER.info("Song renamed: {} -> '{}' by {}", song.displayName(), trimmed, player.getName().getString());
    }

    private static void handleRemoveTexture(ServerPlayer player, String label) {
        if (label == null || label.isBlank()) return;
        String safe = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (safe.isBlank()) return;

        if (PermanentDiscTextures.isExportedPresetStem(safe)) {
            player.sendSystemMessage(Component.literal("Preset textures cannot be removed.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        Path file = SongLibrary.diskTexturesDir().resolve(safe + ".png");
        if (!Files.isRegularFile(file)) {
            player.sendSystemMessage(Component.literal("Texture not found.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        SongLibrary.deleteTextureFile(safe);
        SongLibrary.get().removeTexture(safe);
        broadcastTextureRemoved(safe, player);
        player.sendSystemMessage(Component.literal("Removed texture: " + safe)
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
        LOGGER.info("Texture removed: {} by {}", safe, player.getName().getString());
    }

    private static void handleUploadTexture(ServerPlayer player, String label, byte[] pngBytes) {
        if (label == null || label.isBlank() || pngBytes == null || pngBytes.length == 0) return;
        if (pngBytes.length > 1024 * 1024) return;
        String safe = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (safe.isBlank()) return;

        Path dir = SongLibrary.diskTexturesDir();
        try {
            SongLibrary.ensureDirectory(dir);
            Path file = dir.resolve(safe + ".png");
            Files.write(file, pngBytes);
            SongLibrary.get().addTexture(safe);
            broadcastTextureAdded(safe, player);
            LOGGER.info("Texture uploaded: {} ({} bytes) by {}", safe, pngBytes.length, player.getName().getString());
        } catch (IOException e) {
            LOGGER.error("Failed to save texture", e);
        }
    }

    public static void setGameDir(Path dir) {
        gameDir = dir;
    }

    public static Path getGameDir() {
        return gameDir;
    }

    public static void distributeSongToClient(ServerPlayer player, String hash) {
        SongInfo song = SongLibrary.get().getSong(hash);
        if (song == null) return;

        Path file = SongLibrary.findAudioPath(hash);
        if (file == null) return;

        try {
            byte[] audioData = Files.readAllBytes(file);
            String fileExtension = file.getFileName().toString().substring(file.getFileName().toString().lastIndexOf('.'));
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUtf(hash);
            buf.writeUtf(song.displayName());
            buf.writeInt(song.lengthSeconds());
            buf.writeInt(audioData.length);
            buf.writeUtf(fileExtension);
            NetworkManager.sendToPlayer(player, DISTRIBUTE_SONG, buf);

            int chunkSize = 32768;
            for (int i = 0; i < audioData.length; i += chunkSize) {
                int chunkEnd = Math.min(i + chunkSize, audioData.length);
                byte[] chunk = new byte[chunkEnd - i];
                System.arraycopy(audioData, i, chunk, 0, chunk.length);

                FriendlyByteBuf chunkBuf = new FriendlyByteBuf(Unpooled.buffer());
                chunkBuf.writeUtf(hash);
                chunkBuf.writeInt(i);
                chunkBuf.writeByteArray(chunk);
                NetworkManager.sendToPlayer(player, DISTRIBUTE_SONG_CHUNK, chunkBuf);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to distribute song to client", e);
        }
    }

    public static void distributeAllSongsToClient(ServerPlayer player) {
        for (SongInfo song : SongLibrary.get().getAllSongs()) {
            distributeSongToClient(player, song.hash());
        }
    }

    public static void distributeSongToOthers(ServerPlayer from, String hash) {
        for (ServerPlayer other : from.server.getPlayerList().getPlayers()) {
            if (!other.getUUID().equals(from.getUUID())) {
                distributeSongToClient(other, hash);
            }
        }
    }

    public static void distributeAllTexturesToClient(ServerPlayer player) {
        Path dir = SongLibrary.diskTexturesDir();
        if (!Files.isDirectory(dir)) return;
        try {
            Files.list(dir)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .forEach(p -> {
                        try {
                            String label = p.getFileName().toString();
                            label = label.substring(0, label.length() - 4);
                            byte[] bytes = Files.readAllBytes(p);
                            sendDistributeTexture(player, label, bytes);
                        } catch (IOException e) {
                            LOGGER.warn("Could not distribute texture {}", p);
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("Could not list textures for distribution");
        }
    }

    public static void sendDistributeTexture(ServerPlayer player, String label, byte[] pngBytes) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(label, 128);
        buf.writeByteArray(pngBytes);
        NetworkManager.sendToPlayer(player, DISTRIBUTE_TEXTURE, buf);
    }

    private static void sendSongList(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        Collection<SongInfo> songs = SongLibrary.get().getAllSongs();
        buf.writeVarInt(songs.size());
        for (SongInfo song : songs) {
            buf.writeUtf(song.hash());
            buf.writeUtf(song.displayName());
            buf.writeVarInt(song.lengthSeconds());
            buf.writeUtf(song.source());
        }
        NetworkManager.sendToPlayer(player, SONG_LIST, buf);
    }

    public static void broadcastSongAdded(SongInfo song, ServerPlayer fromPlayer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(song.hash());
        buf.writeUtf(song.displayName());
        buf.writeVarInt(song.lengthSeconds());
        buf.writeUtf(song.source());
        for (ServerPlayer player : fromPlayer.server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(player, SONG_ADDED, buf);
        }
    }

    public static void broadcastSongRemoved(String hash, ServerPlayer fromPlayer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(hash);
        for (ServerPlayer player : fromPlayer.server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(player, SONG_REMOVED, buf);
        }
    }

    private static void broadcastSongRenamed(String hash, String displayName, ServerPlayer fromPlayer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(hash);
        buf.writeUtf(displayName);
        for (ServerPlayer player : fromPlayer.server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(player, SONG_RENAMED, buf);
        }
    }

    private static void broadcastTextureRemoved(String label, ServerPlayer fromPlayer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(label);
        for (ServerPlayer player : fromPlayer.server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(player, TEXTURE_REMOVED, buf);
        }
    }

    private static void broadcastTextureAdded(String label, ServerPlayer fromPlayer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(label);
        for (ServerPlayer player : fromPlayer.server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(player, TEXTURE_ADDED, buf);
        }
        Path file = SongLibrary.diskTexturesDir().resolve(label + ".png");
        if (Files.isRegularFile(file)) {
            try {
                byte[] bytes = Files.readAllBytes(file);
                for (ServerPlayer player : fromPlayer.server.getPlayerList().getPlayers()) {
                    if (!player.getUUID().equals(fromPlayer.getUUID())) {
                        sendDistributeTexture(player, label, bytes);
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    public static void sendUploadDone(ServerPlayer player, UUID uploadId, String hash) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(uploadId);
        buf.writeUtf(hash);
        NetworkManager.sendToPlayer(player, UPLOAD_DONE, buf);
    }

    public static void sendUploadError(ServerPlayer player, UUID uploadId, String message) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(uploadId);
        buf.writeUtf(message);
        NetworkManager.sendToPlayer(player, UPLOAD_ERROR, buf);
    }

    public static void sendBurn(BlockPos deckPos, String songHash, int textureSlot) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(deckPos);
        buf.writeUtf(songHash);
        buf.writeVarInt(textureSlot);
        NetworkManager.sendToServer(BURN_DISC, buf);
    }

    public static void sendBurnWithLabel(BlockPos deckPos, String songHash, String textureLabel) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(deckPos);
        buf.writeUtf(songHash);
        buf.writeUtf(textureLabel);
        NetworkManager.sendToServer(BURN_WITH_LABEL, buf);
    }

    public static void sendUploadTexture(String label, byte[] pngBytes) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(label, 128);
        buf.writeByteArray(pngBytes);
        NetworkManager.sendToServer(UPLOAD_TEXTURE, buf);
    }

    public static void sendRemoveSong(String songHash) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(songHash);
        NetworkManager.sendToServer(REMOVE_SONG, buf);
    }

    public static void sendRemoveTexture(String label) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(label, 128);
        NetworkManager.sendToServer(REMOVE_TEXTURE, buf);
    }

    public static void sendRenameSong(String songHash, String newDisplayName) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(songHash);
        buf.writeUtf(newDisplayName, 64);
        NetworkManager.sendToServer(RENAME_SONG, buf);
    }

    public static void requestSongList() {
        NetworkManager.sendToServer(REQUEST_SONGS, new FriendlyByteBuf(Unpooled.buffer()));
    }

    public static void requestRefresh() {
        NetworkManager.sendToServer(REQUEST_REFRESH, new FriendlyByteBuf(Unpooled.buffer()));
    }

    /** Rescan library, sync assets, then reopen the DJ deck so the song list reflects disk changes. */
    private static void refreshDjDeck(ServerPlayer player) {
        SongLibrary.get().scanSongsFolder();
        distributeAllSongsToClient(player);
        distributeAllTexturesToClient(player);

        if (player.containerMenu instanceof DjDeckMenu menu) {
            BlockPos deckPos = menu.getDeckPos();
            BlockEntity be = player.level().getBlockEntity(deckPos);
            if (be instanceof DjDeckBlockEntity deck) {
                player.closeContainer();
                DjDeckMenus.open(player, deck);
                return;
            }
        }
        sendSongList(player);
    }

    /** Tell nearby clients to stream the bound song at a jukebox (vanilla particles via levelEvent 1010 on server). */
    public static void sendPlay(ServerLevel level, BlockPos jukeboxPos, String songHash, String displayName) {
        double x = jukeboxPos.getX() + 0.5D;
        double y = jukeboxPos.getY() + 0.5D;
        double z = jukeboxPos.getZ() + 0.5D;
        double radiusSq = JUKEBOX_AUDIENCE_RADIUS * JUKEBOX_AUDIENCE_RADIUS;
        String name = displayName == null ? "" : displayName;
        for (ServerPlayer player : level.players()) {
            if (player.level().dimension() != level.dimension()) continue;
            if (player.distanceToSqr(x, y, z) > radiusSq) continue;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBlockPos(jukeboxPos);
            buf.writeUtf(songHash);
            buf.writeUtf(name);
            NetworkManager.sendToPlayer(player, PLAY_DISC, buf);
        }
    }

    public static void sendStop(ServerLevel level, BlockPos jukeboxPos) {
        double x = jukeboxPos.getX() + 0.5D;
        double y = jukeboxPos.getY() + 0.5D;
        double z = jukeboxPos.getZ() + 0.5D;
        double radiusSq = JUKEBOX_AUDIENCE_RADIUS * JUKEBOX_AUDIENCE_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.level().dimension() != level.dimension()) continue;
            if (player.distanceToSqr(x, y, z) > radiusSq) continue;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBlockPos(jukeboxPos);
            NetworkManager.sendToPlayer(player, STOP_DISC, buf);
        }
    }

    /** Portable jukeboxes (backpacks, etc.) — arbitrary world coordinates. */
    public static void sendPlayAt(ServerLevel level, double x, double y, double z,
                                  String songHash, String displayName) {
        sendPlayAt(level, x, y, z, songHash, displayName, null);
    }

    /** @param storageUuid Sophisticated Storage/Backpacks contents UUID, for reliable stop while moving */
    public static void sendPlayAt(ServerLevel level, double x, double y, double z,
                                  String songHash, String displayName, @org.jetbrains.annotations.Nullable java.util.UUID storageUuid) {
        double radiusSq = storageUuid != null
                ? PORTABLE_AUDIENCE_RADIUS * PORTABLE_AUDIENCE_RADIUS
                : JUKEBOX_AUDIENCE_RADIUS * JUKEBOX_AUDIENCE_RADIUS;
        String name = displayName == null ? "" : displayName;
        for (ServerPlayer player : level.players()) {
            if (player.level().dimension() != level.dimension()) continue;
            if (player.distanceToSqr(x, y, z) > radiusSq) continue;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeUtf(songHash);
            buf.writeUtf(name);
            buf.writeBoolean(storageUuid != null);
            if (storageUuid != null) {
                buf.writeUUID(storageUuid);
            }
            NetworkManager.sendToPlayer(player, PLAY_DISC_AT, buf);
        }
    }

    public static void sendStopAt(ServerLevel level, double x, double y, double z) {
        double radiusSq = JUKEBOX_AUDIENCE_RADIUS * JUKEBOX_AUDIENCE_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.level().dimension() != level.dimension()) continue;
            if (player.distanceToSqr(x, y, z) > radiusSq) continue;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            NetworkManager.sendToPlayer(player, STOP_DISC_AT, buf);
        }
    }

    /** Stop portable playback by Sophisticated Core storage UUID. */
    public static void sendStopPortable(ServerLevel level, double nearX, double nearY, double nearZ,
                                        java.util.UUID storageUuid) {
        for (ServerPlayer player : level.players()) {
            if (player.level().dimension() != level.dimension()) continue;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUUID(storageUuid);
            NetworkManager.sendToPlayer(player, STOP_DISC_PORTABLE, buf);
        }
    }

    /** Reposition portable custom disc audio while a backpack / cart moves. */
    public static void sendUpdatePortable(ServerLevel level, UUID storageUuid, double x, double y, double z) {
        double radiusSq = PORTABLE_AUDIENCE_RADIUS * PORTABLE_AUDIENCE_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.level().dimension() != level.dimension()) continue;
            if (player.distanceToSqr(x, y, z) > radiusSq) continue;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUUID(storageUuid);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            NetworkManager.sendToPlayer(player, UPDATE_DISC_PORTABLE, buf);
        }
    }
}
