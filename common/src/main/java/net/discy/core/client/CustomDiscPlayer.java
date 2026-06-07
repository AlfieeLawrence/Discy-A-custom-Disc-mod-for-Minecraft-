package net.discy.core.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.discy.core.client.audio.AudioDecoder;
import net.discy.core.client.audio.Mp3Decoder;
import net.discy.core.client.audio.StreamingAudioSource;
import net.discy.core.client.audio.VorbisDecoder;
import net.discy.core.library.SongInfo;
import net.discy.core.library.SongLibrary;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Client-side OpenAL streaming for bound custom discs (jukeboxes + portable players via
 * {@code play_disc_at}). Distance falloff is applied per tick so songs fade out when you
 * walk away instead of playing at full volume globally.
 */
public final class CustomDiscPlayer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Full volume while the listener is within this many blocks of the source. */
    private static final double FULL_VOL_RADIUS = 20.0;
    /** Beyond full-volume radius, gain reaches zero at this distance (gradual linear fade). */
    private static final double SILENT_RADIUS = 72.0;
    private static final double FADE_SPAN = SILENT_RADIUS - FULL_VOL_RADIUS;

    private static final Map<BlockPos, StreamingAudioSource> ACTIVE = new HashMap<>();
    private static final Map<PositionKey, StreamingAudioSource> ACTIVE_AT = new HashMap<>();
    /** Sophisticated Backpacks / Storage — keyed by contents UUID so stop works while moving. */
    private static final Map<UUID, PositionKey> PORTABLE_BY_UUID = new HashMap<>();

    private record PositionKey(double x, double y, double z) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PositionKey that = (PositionKey) o;
            return Double.compare(that.x, x) == 0 &&
                   Double.compare(that.y, y) == 0 &&
                   Double.compare(that.z, z) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }

    private CustomDiscPlayer() {}

    public static void play(BlockPos pos, String songHash, String displayName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        stop(pos);

        Optional<SongInfo> opt = SongLibrary.get().byHash(songHash);
        if (opt.isEmpty()) {
            LOGGER.warn("CustomDiscPlayer: no library entry for hash {} - dropping play request", songHash);
            return;
        }
        SongInfo song = opt.get();
        byte[] bytes = readSongBytes(songHash);
        if (bytes == null) {
            LOGGER.error("CustomDiscPlayer: could not read audio for {}", song.shortHash());
            return;
        }

        AudioDecoder decoder;
        try {
            decoder = openDecoder(bytes, song.shortHash());
        } catch (IOException e) {
            LOGGER.error("CustomDiscPlayer: could not open decoder for {}: {}", song.shortHash(), e.getMessage());
            return;
        }

        StreamingAudioSource src;
        try {
            src = new StreamingAudioSource(decoder,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            src.start();
        } catch (Throwable t) {
            decoder.close();
            LOGGER.error("CustomDiscPlayer: could not open AL source for {}: {}", song.shortHash(), t.getMessage());
            return;
        }

        ACTIVE.put(pos.immutable(), src);
        setNowPlaying(mc, displayName);
    }

    public static void playAt(double x, double y, double z, String songHash, String displayName) {
        playAt(x, y, z, songHash, displayName, null);
    }

    public static void playAt(double x, double y, double z, String songHash, String displayName,
                              @Nullable UUID storageUuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PositionKey key = new PositionKey(x, y, z);
        if (storageUuid != null) {
            stopPortable(storageUuid);
            stopSophisticatedVanillaSound(storageUuid);
        } else {
            stopAt(x, y, z);
        }

        Optional<SongInfo> opt = SongLibrary.get().byHash(songHash);
        if (opt.isEmpty()) {
            LOGGER.warn("CustomDiscPlayer: no library entry for hash {} - dropping play request", songHash);
            return;
        }
        SongInfo song = opt.get();
        byte[] bytes = readSongBytes(songHash);
        if (bytes == null) {
            LOGGER.error("CustomDiscPlayer: could not read audio for {}", song.shortHash());
            return;
        }

        AudioDecoder decoder;
        try {
            decoder = openDecoder(bytes, song.shortHash());
        } catch (IOException e) {
            LOGGER.error("CustomDiscPlayer: could not open decoder for {}: {}", song.shortHash(), e.getMessage());
            return;
        }

        StreamingAudioSource src;
        try {
            src = new StreamingAudioSource(decoder, x, y, z);
            src.start();
        } catch (Throwable t) {
            decoder.close();
            LOGGER.error("CustomDiscPlayer: could not open AL source for {}: {}", song.shortHash(), t.getMessage());
            return;
        }

        ACTIVE_AT.put(key, src);
        if (storageUuid != null) {
            PORTABLE_BY_UUID.put(storageUuid, key);
        }
        setNowPlaying(mc, displayName);
    }

    public static void stopPortable(UUID storageUuid) {
        PositionKey key = PORTABLE_BY_UUID.remove(storageUuid);
        if (key == null) return;
        StreamingAudioSource src = ACTIVE_AT.remove(key);
        if (src != null) src.close();
    }

    /** Move an active portable stream (Sophisticated Backpacks / Storage in Motion). */
    public static void updatePortable(UUID storageUuid, double x, double y, double z) {
        PositionKey oldKey = PORTABLE_BY_UUID.get(storageUuid);
        if (oldKey == null) return;

        PositionKey newKey = new PositionKey(x, y, z);
        if (oldKey.equals(newKey)) return;

        StreamingAudioSource src = ACTIVE_AT.remove(oldKey);
        if (src == null) return;

        src.setPosition(x, y, z);
        ACTIVE_AT.put(newKey, src);
        PORTABLE_BY_UUID.put(storageUuid, newKey);
    }

    public static void stop(BlockPos pos) {
        StreamingAudioSource src = ACTIVE.remove(pos);
        if (src != null) src.close();
    }

    public static void stopAt(double x, double y, double z) {
        PositionKey key = new PositionKey(x, y, z);
        StreamingAudioSource src = ACTIVE_AT.remove(key);
        if (src != null) src.close();
    }

    public static void clearAll() {
        for (StreamingAudioSource src : ACTIVE.values()) src.close();
        ACTIVE.clear();
        for (StreamingAudioSource src : ACTIVE_AT.values()) src.close();
        ACTIVE_AT.clear();
        PORTABLE_BY_UUID.clear();
    }

    public static void tick() {
        if (ACTIVE.isEmpty() && ACTIVE_AT.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clearAll();
            return;
        }

        Player player = mc.player;
        float master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
        float records = mc.options.getSoundSourceVolume(SoundSource.RECORDS);
        float volumeScale = master * records;

        tickSources(ACTIVE.entrySet().iterator(), player, volumeScale);
        tickSourcesAt(ACTIVE_AT.entrySet().iterator(), player, volumeScale);
    }

    private static void tickSources(Iterator<Map.Entry<BlockPos, StreamingAudioSource>> it,
                                    Player player, float volumeScale) {
        while (it.hasNext()) {
            Map.Entry<BlockPos, StreamingAudioSource> e = it.next();
            BlockPos jukeboxPos = e.getKey();
            StreamingAudioSource src = e.getValue();

            float distGain = computeDistanceGain(player,
                    jukeboxPos.getX() + 0.5, jukeboxPos.getY() + 0.5, jukeboxPos.getZ() + 0.5);
            if (!tickOne(src, volumeScale, distGain)) {
                it.remove();
            }
        }
    }

    private static void tickSourcesAt(Iterator<Map.Entry<PositionKey, StreamingAudioSource>> it,
                                      Player player, float volumeScale) {
        while (it.hasNext()) {
            Map.Entry<PositionKey, StreamingAudioSource> e = it.next();
            PositionKey key = e.getKey();
            StreamingAudioSource src = e.getValue();

            float distGain = computeDistanceGain(player, key.x, key.y, key.z);
            if (!tickOne(src, volumeScale, distGain)) {
                it.remove();
            }
        }
    }

    /** @return false if the source was removed (finished or error). Out-of-range sources stay alive but muted. */
    private static boolean tickOne(StreamingAudioSource src, float volumeScale, float distGain) {
        float gain = distGain <= 0.001f ? 0f : volumeScale * distGain;
        try {
            src.tick(gain);
        } catch (Throwable t) {
            LOGGER.error("CustomDiscPlayer tick: {}", t.getMessage());
            src.close();
            return false;
        }
        if (src.isFinished()) {
            src.close();
            return false;
        }
        return true;
    }

    /**
     * Smooth falloff: full volume inside {@link #FULL_VOL_RADIUS}, then a gentle linear
     * fade to silence at {@link #SILENT_RADIUS} (less abrupt than the old 0.1/block curve).
     */
    private static float computeDistanceGain(Player player, double sx, double sy, double sz) {
        if (player == null) return 0f;
        double dx = player.getX() - sx;
        double dy = player.getEyeY() - sy;
        double dz = player.getZ() - sz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist <= FULL_VOL_RADIUS) {
            return 1.0f;
        }
        if (dist >= SILENT_RADIUS) {
            return 0f;
        }
        double t = (dist - FULL_VOL_RADIUS) / FADE_SPAN;
        return (float) (1.0 - t);
    }

    private static void setNowPlaying(Minecraft mc, String displayName) {
        if (mc.gui == null) return;
        Component name = (displayName == null || displayName.isEmpty())
                ? Component.translatable("item.discy.now_playing.generic")
                : Component.literal(displayName);
        mc.gui.setNowPlaying(name);
    }

    private static AudioDecoder openDecoder(byte[] bytes, String shortHash) throws IOException {
        if (isMp3(bytes)) {
            return new Mp3Decoder(bytes);
        }
        return new VorbisDecoder(bytes);
    }

    private static boolean isMp3(byte[] b) {
        if (b.length < 3) return false;
        if (b[0] == 'I' && b[1] == 'D' && b[2] == '3') return true;
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0;
    }

    private static void stopSophisticatedVanillaSound(UUID storageUuid) {
        try {
            Class<?> handler = Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.StorageSoundHandler");
            handler.getMethod("stopStorageSound", UUID.class).invoke(null, storageUuid);
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private static byte[] readSongBytes(String hash) {
        Path file = SongLibrary.findAudioPath(hash);
        if (file == null) return null;
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            return null;
        }
    }
}
