package net.discy;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.discy.core.client.CustomDiscPlayer;
import net.discy.core.client.DiscPixelCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.discy.core.client.download.SongDownloadManager;
import net.discy.core.client.screen.DjDeckScreen;
import net.discy.core.client.screen.SongRenameScreen;
import net.discy.core.client.texture.DiskTextureManager;
import net.discy.core.client.upload.UploadManager;
import net.discy.core.library.SongInfo;
import net.discy.core.library.SongLibrary;
import net.discy.core.network.DiscyNetworking;
import net.discy.core.registry.ScreenHandlerTypesRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DiscyClient {
    public static void init() {
        ClientLifecycleEvent.CLIENT_SETUP.register(minecraft -> {
            Path gameDir = minecraft.gameDirectory.toPath();
            DiscyNetworking.setGameDir(gameDir);
            SongLibrary.get().scanSongsFolder();
            DiskTextureManager.refreshAsync();
            DiskTextureManager.scanUserTextureStemsAsync(stems -> {
                for (String stem : stems) {
                    DiskTextureManager.rlForStem(stem);
                }
            });
        });

        ClientTickEvent.CLIENT_POST.register(client -> CustomDiscPlayer.tick());

        MenuRegistry.registerScreenFactory(ScreenHandlerTypesRegistry.DJ_DECK_MENU.get(), DjDeckScreen::new);

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.PLAY_DISC, (buf, context) -> {
            BlockPos pos = buf.readBlockPos();
            String hash = buf.readUtf();
            String displayName = buf.readUtf();
            context.queue(() -> {
                if (context.getPlayer() == null || context.getPlayer().level() == null) return;
                CustomDiscPlayer.play(pos, hash, displayName);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.STOP_DISC, (buf, context) -> {
            BlockPos pos = buf.readBlockPos();
            context.queue(() -> CustomDiscPlayer.stop(pos));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.PLAY_DISC_AT, (buf, context) -> {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            String hash = buf.readUtf();
            String displayName = buf.readUtf();
            java.util.UUID storageUuid = buf.readBoolean() ? buf.readUUID() : null;
            context.queue(() -> CustomDiscPlayer.playAt(x, y, z, hash, displayName, storageUuid));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.STOP_DISC_AT, (buf, context) -> {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            context.queue(() -> CustomDiscPlayer.stopAt(x, y, z));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.STOP_DISC_PORTABLE, (buf, context) -> {
            java.util.UUID storageUuid = buf.readUUID();
            context.queue(() -> CustomDiscPlayer.stopPortable(storageUuid));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.UPDATE_DISC_PORTABLE, (buf, context) -> {
            java.util.UUID storageUuid = buf.readUUID();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            context.queue(() -> CustomDiscPlayer.updatePortable(storageUuid, x, y, z));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.UPLOAD_DONE, (buf, context) -> {
            buf.readUUID();
            buf.readUtf();
            context.queue(UploadManager::onUploadDone);
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.UPLOAD_ERROR, (buf, context) -> {
            buf.readUUID();
            String message = buf.readUtf();
            context.queue(() -> UploadManager.onUploadError(message));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.SONG_LIST, (buf, context) -> {
            int count = buf.readVarInt();
            List<SongInfo> songs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String hash = buf.readUtf();
                String displayName = buf.readUtf();
                int length = buf.readVarInt();
                String source = buf.readUtf();
                songs.add(new SongInfo(hash, displayName, length, null, source));
            }
            context.queue(() -> {
                for (SongInfo song : songs) {
                    SongLibrary.get().addSong(song.hash(), song.displayName(), song.lengthSeconds(), song.source());
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.SONG_ADDED, (buf, context) -> {
            String hash = buf.readUtf();
            String displayName = buf.readUtf();
            int length = buf.readVarInt();
            String source = buf.readUtf();
            context.queue(() -> SongLibrary.get().addSong(hash, displayName, length, source));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.TEXTURE_ADDED, (buf, context) -> {
            String label = buf.readUtf();
            context.queue(() -> SongLibrary.get().addTexture(label));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.SONG_REMOVED, (buf, context) -> {
            String hash = buf.readUtf();
            context.queue(() -> {
                SongDownloadManager.deleteLocalSong(hash);
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof DjDeckScreen deckScreen) {
                    deckScreen.onSongRemoved(hash);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.SONG_RENAMED, (buf, context) -> {
            String hash = buf.readUtf();
            String displayName = buf.readUtf();
            context.queue(() -> {
                SongDownloadManager.renameLocalSong(hash, displayName);
                notifyDeckSongRenamed(hash);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.TEXTURE_REMOVED, (buf, context) -> {
            String label = buf.readUtf();
            context.queue(() -> {
                SongLibrary.deleteTextureFile(label);
                SongLibrary.get().removeTexture(label);
                DiskTextureManager.release(label);
                DiscPixelCache.evict(label);
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof DjDeckScreen deckScreen) {
                    deckScreen.onTextureRemoved(label);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.DISTRIBUTE_TEXTURE, (buf, context) -> {
            String label = buf.readUtf(128);
            byte[] pngBytes = buf.readByteArray();
            context.queue(() -> {
                try {
                    String stem = label.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                    DiskTextureManager.saveAndRegister(stem, pngBytes);
                    SongLibrary.get().addTexture(stem);
                    DiscPixelCache.evict(stem);
                } catch (Exception ignored) {
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.DISTRIBUTE_SONG, (buf, context) -> {
            String hash = buf.readUtf();
            String displayName = buf.readUtf();
            int lengthSeconds = buf.readInt();
            int totalBytes = buf.readInt();
            String fileExtension = buf.readUtf();
            context.queue(() -> SongDownloadManager.startDownload(hash, displayName, lengthSeconds, totalBytes, fileExtension));
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DiscyNetworking.DISTRIBUTE_SONG_CHUNK, (buf, context) -> {
            String hash = buf.readUtf();
            int offset = buf.readInt();
            byte[] chunk = buf.readByteArray();
            context.queue(() -> SongDownloadManager.receiveChunk(hash, offset, chunk));
        });

        Runnable stopAllAudio = () -> {
            CustomDiscPlayer.clearAll();
            DiscPixelCache.clear();
        };

        // Leaving a world / disconnecting must silence OpenAL sources — CLIENT_STOPPING alone
        // does not run when switching single-player worlds.
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> stopAllAudio.run());

        ClientLifecycleEvent.CLIENT_STOPPING.register(client -> stopAllAudio.run());
    }

    private static void notifyDeckSongRenamed(String hash) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen instanceof DjDeckScreen deckScreen) {
            deckScreen.onSongRenamed(hash);
        } else if (screen instanceof SongRenameScreen renameScreen
                && renameScreen.getParentScreen() instanceof DjDeckScreen deckScreen) {
            deckScreen.onSongRenamed(hash);
        }
    }
}
