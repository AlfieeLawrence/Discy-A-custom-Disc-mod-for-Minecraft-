package net.discy.core.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.discy.core.network.DiscyNetworking;
import net.discy.core.util.DiscyIdentifier;
import net.discy.core.util.AudioLengthReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class SongLibrary {
    private static final Logger LOGGER = LoggerFactory.getLogger(SongLibrary.class);
    private static final SongLibrary INSTANCE = new SongLibrary();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, SongInfo> songs = new ConcurrentHashMap<>();
    private final Set<String> textures = ConcurrentHashMap.newKeySet();

    public static SongLibrary get() {
        return INSTANCE;
    }

    public static Path songsDir() {
        return DiscyNetworking.getGameDir().resolve("discy").resolve("songs");
    }

    public static Path diskTexturesDir() {
        return DiscyNetworking.getGameDir().resolve("discy").resolve("disk_textures");
    }

    public static Path uploadsDir() {
        return DiscyNetworking.getGameDir().resolve("discy").resolve(".uploads");
    }

    /** Register in memory; first registration wins for display metadata. */
    public SongInfo addSong(String hash, String displayName, int lengthSeconds, String source) {
        SongInfo existing = songs.get(hash);
        if (existing != null) {
            return existing;
        }
        ResourceLocation soundId = new DiscyIdentifier("custom." + hash);
        SongInfo info = new SongInfo(hash, displayName, lengthSeconds, soundId, source);
        songs.put(hash, info);
        LOGGER.info("Added song: {} '{}' ({}s)", hash.substring(0, Math.min(8, hash.length())), displayName, lengthSeconds);
        return info;
    }

    public void removeSong(String hash) {
        songs.remove(hash);
    }

    public boolean renameSong(String hash, String newDisplayName) {
        SongInfo existing = songs.get(hash);
        if (existing == null) return false;
        String trimmed = newDisplayName.trim();
        if (trimmed.isBlank()) return false;
        songs.put(hash, new SongInfo(hash, trimmed, existing.lengthSeconds(), existing.soundId(), existing.source()));
        return true;
    }

    /** Updates the per-song sidecar {@code display_name} on disk. */
    public static boolean updateSongDisplayNameOnDisk(String hash, String displayName) {
        Path audio = findAudioPath(hash);
        if (audio == null) return false;
        SongInfo song = get().getSong(hash);
        int lengthSeconds = song != null ? song.lengthSeconds() : 180;
        String stem = audio.getFileName().toString();
        int dot = stem.lastIndexOf('.');
        if (dot > 0) stem = stem.substring(0, dot);
        Path metaPath = audio.getParent().resolve(stem + ".json");
        try {
            writeSidecar(metaPath, displayName, lengthSeconds);
            return true;
        } catch (IOException e) {
            LOGGER.warn("Could not update sidecar for {}: {}", hash.substring(0, Math.min(8, hash.length())), e.getMessage());
            return false;
        }
    }

    public void removeTexture(String textureId) {
        textures.remove(textureId);
    }

    /** Delete on-disk audio and sidecar for a library hash. Returns true if the audio file was removed. */
    public static boolean deleteSongFiles(String hash) {
        Path audio = findAudioPath(hash);
        if (audio == null) return false;
        try {
            String stem = audio.getFileName().toString();
            int dot = stem.lastIndexOf('.');
            if (dot > 0) stem = stem.substring(0, dot);
            Path meta = audio.getParent().resolve(stem + ".json");
            Files.deleteIfExists(audio);
            Files.deleteIfExists(meta);
            return true;
        } catch (IOException e) {
            LOGGER.warn("Could not delete song files for {}: {}", hash.substring(0, Math.min(8, hash.length())), e.getMessage());
            return false;
        }
    }

    /** Delete a user-uploaded texture PNG. Preset stems are not deleted. */
    public static boolean deleteTextureFile(String stem) {
        if (stem == null || stem.isBlank()) return false;
        Path file = diskTexturesDir().resolve(stem + ".png");
        if (!Files.isRegularFile(file)) return false;
        try {
            Files.delete(file);
            return true;
        } catch (IOException e) {
            LOGGER.warn("Could not delete texture {}: {}", stem, e.getMessage());
            return false;
        }
    }

    public SongInfo getSong(String hash) {
        return songs.get(hash);
    }

    public Optional<SongInfo> byHash(String hash) {
        return Optional.ofNullable(songs.get(hash));
    }

    public Collection<SongInfo> getAllSongs() {
        return songs.values();
    }

    public List<SongInfo> snapshot() {
        return new ArrayList<>(songs.values());
    }

    public void clear() {
        songs.clear();
    }

    public void addTexture(String textureId) {
        textures.add(textureId);
    }

    public Set<String> getTextures() {
        return new HashSet<>(textures);
    }

    public static String hashBytes(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static String hashFile(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    digest.update(buf, 0, n);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    public static void ensureDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    public static void writeSidecar(Path metaPath, String displayName, int lengthSeconds) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("display_name", displayName);
        json.addProperty("length_seconds", lengthSeconds);
        ensureDirectory(metaPath.getParent());
        Files.writeString(metaPath, GSON.toJson(json), StandardCharsets.UTF_8);
    }

    /**
     * Rebuild library from {@code discy/songs/}. Names come from per-file {@code .json} sidecars,
     * not from the audio filename (which may be hash-prefixed).
     */
    public int scanSongsFolder() {
        Map<String, String> manifestNames = loadManifestNames();
        songs.clear();

        Path songsDir = songsDir();
        if (!Files.exists(songsDir)) {
            LOGGER.info("Songs folder does not exist: {}", songsDir);
            scanTexturesFolder();
            return 0;
        }

        int added = 0;
        try (Stream<Path> files = Files.list(songsDir)) {
            for (Path audio : files.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".ogg") || n.endsWith(".mp3");
            }).sorted().toList()) {
                try {
                    String hash = hashFile(audio);
                    if (songs.containsKey(hash)) continue;

                    String stem = audio.getFileName().toString();
                    int dot = stem.lastIndexOf('.');
                    if (dot > 0) stem = stem.substring(0, dot);

                    Path metaPath = songsDir.resolve(stem + ".json");
                    String displayName = null;
                    int lengthSeconds = 180;

                    if (Files.isRegularFile(metaPath)) {
                        SidecarData sidecar = readSidecarData(metaPath);
                        if (sidecar != null) {
                            displayName = sidecar.displayName;
                            lengthSeconds = sidecar.lengthSeconds;
                        }
                    }

                    if (displayName == null || displayName.isBlank()) {
                        displayName = manifestNames.get(hash);
                    }
                    if (displayName == null || displayName.isBlank()) {
                        displayName = prettifyStem(stem, hash);
                    }

                    int measured = AudioLengthReader.readSeconds(audio, -1);
                    if (measured > 0) {
                        lengthSeconds = measured;
                    } else if (lengthSeconds <= 1) {
                        lengthSeconds = 180;
                    }

                    addSong(hash, displayName, lengthSeconds, "folder");
                    added++;
                } catch (IOException e) {
                    LOGGER.error("Failed to read song file: {}", audio, e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan songs folder", e);
        }

        scanTexturesFolder();
        LOGGER.info("Scanned songs folder: {} song(s) in library", songs.size());
        return added;
    }

    private record SidecarData(String displayName, int lengthSeconds) {}

    private static SidecarData readSidecarData(Path metaPath) {
        try {
            String content = Files.readString(metaPath, StandardCharsets.UTF_8);
            JsonObject json = GSON.fromJson(content, JsonObject.class);
            if (json == null) return null;
            String name = json.has("display_name") ? json.get("display_name").getAsString() : null;
            int len = json.has("length_seconds") ? Math.max(1, json.get("length_seconds").getAsInt()) : 180;
            return new SidecarData(name, len);
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warn("Could not read sidecar {}: {}", metaPath, e.getMessage());
            return null;
        }
    }

    private static String prettifyStem(String stem, String hash) {
        if (stem.length() > 13 && stem.charAt(12) == '_' && stem.regionMatches(0, hash, 0, 12)) {
            String rest = stem.substring(13).replace('_', ' ');
            if (!rest.isBlank()) return rest;
        }
        if (stem.equals(hash) || stem.length() >= 32) {
            return "Song " + hash.substring(0, 8);
        }
        return stem.replace('_', ' ');
    }

    private static Map<String, String> loadManifestNames() {
        Map<String, String> out = new HashMap<>();
        Path manifestPath = DiscyNetworking.getGameDir().resolve("discy").resolve("songs_manifest.json");
        if (!Files.exists(manifestPath)) return out;
        try {
            String content = Files.readString(manifestPath, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(content, JsonObject.class);
            if (root == null || !root.has("songs")) return out;
            JsonObject songsJson = root.getAsJsonObject("songs");
            for (String hash : songsJson.keySet()) {
                JsonObject entry = songsJson.getAsJsonObject(hash);
                if (entry != null && entry.has("display_name")) {
                    out.put(hash, entry.get("display_name").getAsString());
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warn("Could not load legacy manifest: {}", e.getMessage());
        }
        return out;
    }

    private void scanTexturesFolder() {
        textures.clear();
        Path dir = diskTexturesDir();
        if (!Files.exists(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        textures.add(name.substring(0, name.length() - 4));
                    });
        } catch (IOException e) {
            LOGGER.warn("Could not scan disk_textures: {}", e.getMessage());
        }
    }

    /** Locate on-disk audio for a library hash (supports hash-prefixed and legacy filenames). */
    @Nullable
    public static Path findAudioPath(String hash) {
        Path songsDir = songsDir();
        if (!Files.isDirectory(songsDir)) return null;
        try {
            return Files.list(songsDir)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".ogg") || n.endsWith(".mp3");
                    })
                    .filter(p -> {
                        try {
                            return hashFile(p).equals(hash);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    public static String sanitizeForFilename(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                out.append(c);
            } else if (c == ' ' || c == '-') {
                out.append('_');
            }
        }
        if (out.length() == 0) return "song";
        if (out.length() > 48) return out.substring(0, 48);
        return out.toString();
    }
}
