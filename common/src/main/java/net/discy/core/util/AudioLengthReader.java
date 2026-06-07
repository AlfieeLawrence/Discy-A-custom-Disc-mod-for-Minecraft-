package net.discy.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads accurate playback length for uploaded / scanned audio files. */
public final class AudioLengthReader {

    private AudioLengthReader() {}

    public static int readSeconds(Path file) {
        return readSeconds(file, 180);
    }

    public static int readSeconds(Path file, int fallbackSeconds) {
        String lower = file.getFileName().toString().toLowerCase();
        try {
            if (lower.endsWith(".mp3")) {
                return Mp3LengthReader.readLengthSeconds(file, fallbackSeconds);
            }
            if (lower.endsWith(".ogg")) {
                return OggLengthReader.readLengthSeconds(file, fallbackSeconds);
            }
            String kind = probeKind(file);
            if ("mp3".equals(kind)) {
                return Mp3LengthReader.readLengthSeconds(file, fallbackSeconds);
            }
            if ("ogg".equals(kind)) {
                return OggLengthReader.readLengthSeconds(file, fallbackSeconds);
            }
        } catch (IOException ignored) {
        }
        return fallbackSeconds;
    }

    private static String probeKind(Path file) throws IOException {
        byte[] magic = new byte[4];
        try (InputStream in = Files.newInputStream(file)) {
            if (in.read(magic) < 3) return "unknown";
        }
        if (magic[0] == 'I' && magic[1] == 'D' && magic[2] == '3') return "mp3";
        if (magic[0] == 'O' && magic[1] == 'g' && magic[2] == 'g' && magic[3] == 'S') return "ogg";
        if ((magic[0] & 0xFF) == 0xFF && (magic[1] & 0xE0) == 0xE0) return "mp3";
        return "unknown";
    }
}
