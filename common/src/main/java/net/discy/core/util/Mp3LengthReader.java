package net.discy.core.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * MPEG-1 Layer III duration by scanning frame headers (works for CBR and VBR).
 */
public final class Mp3LengthReader {

    private static final int[] BITRATES_KBPS = {
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0
    };
    private static final int[] SAMPLE_RATES = {44100, 48000, 32000, 0};

    private Mp3LengthReader() {}

    public static int readLengthSeconds(Path file, int fallbackSeconds) {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long pos = skipId3v2(raf);
            long end = raf.length();
            if (hasId3v1(raf)) end -= 128;
            if (end <= pos + 4) return fallbackSeconds;

            double totalSeconds = 0;
            int frames = 0;
            int stale = 0;
            while (pos + 4 < end && frames < 500_000) {
                raf.seek(pos);
                int b1 = raf.readUnsignedByte();
                if (b1 != 0xFF) {
                    pos++;
                    stale++;
                    if (stale > 4096) break;
                    continue;
                }
                int b2 = raf.readUnsignedByte();
                if ((b2 & 0xE0) != 0xE0) {
                    pos++;
                    stale++;
                    if (stale > 4096) break;
                    continue;
                }
                stale = 0;
                int b3 = raf.readUnsignedByte();
                int b4 = raf.readUnsignedByte();
                int header = (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;

                int version = (b2 >> 3) & 3;
                int layer = (b2 >> 1) & 3;
                if (layer != 1) { // Layer III only
                    pos++;
                    continue;
                }
                int bitrateIdx = (b3 >> 4) & 0x0F;
                int sampleIdx = (b3 >> 2) & 0x03;
                int padding = (b3 >> 1) & 1;
                if (bitrateIdx == 0 || bitrateIdx == 15 || sampleIdx == 3) {
                    pos++;
                    continue;
                }

                int bitrateKbps = BITRATES_KBPS[bitrateIdx];
                int sampleRate = SAMPLE_RATES[sampleIdx];
                if (version == 2) { // MPEG-2 / 2.5 — not common for music uploads
                    sampleRate = switch (sampleIdx) {
                        case 0 -> 22050;
                        case 1 -> 24000;
                        case 2 -> 16000;
                        default -> 0;
                    };
                }
                if (sampleRate <= 0 || bitrateKbps <= 0) {
                    pos++;
                    continue;
                }

                int frameSize = frameSizeBytes(version, bitrateKbps, sampleRate, padding);
                if (frameSize < 4) {
                    pos++;
                    continue;
                }

                totalSeconds += 1152.0 / sampleRate;
                pos += frameSize;
                frames++;
            }

            if (totalSeconds > 0 && totalSeconds <= 3600) {
                return Math.max(1, (int) Math.round(totalSeconds));
            }
            return fallbackSeconds;
        } catch (IOException e) {
            return fallbackSeconds;
        }
    }

    private static int frameSizeBytes(int version, int bitrateKbps, int sampleRate, int padding) {
        if (version == 3) { // MPEG-1
            return (144_000 * bitrateKbps / sampleRate) + padding;
        }
        return (72_000 * bitrateKbps / sampleRate) + padding;
    }

    private static long skipId3v2(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        if (raf.read() != 'I' || raf.read() != 'D' || raf.read() != '3') {
            raf.seek(0);
            return 0;
        }
        raf.seek(6);
        int size = 0;
        for (int i = 0; i < 4; i++) {
            size = (size << 7) | (raf.read() & 0x7F);
        }
        return 10L + size;
    }

    private static boolean hasId3v1(RandomAccessFile raf) throws IOException {
        if (raf.length() < 128) return false;
        raf.seek(raf.length() - 128);
        return raf.read() == 'T' && raf.read() == 'A' && raf.read() == 'G';
    }
}
