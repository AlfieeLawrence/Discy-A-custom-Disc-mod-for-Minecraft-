package net.discy.core.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Minimal OGG/Vorbis length parser for writing accurate sidecar metadata.
 */
public final class OggLengthReader {

    private static final int OGG_PAGE_HEADER_BYTES = 27;

    private OggLengthReader() {}

    public static int readLengthSeconds(Path file) throws IOException {
        return readLengthSeconds(file, -1);
    }

    public static int readLengthSeconds(Path file, int fallbackSeconds) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long sampleRate = readSampleRate(raf);
            if (sampleRate <= 0) return fallbackSeconds;
            long totalSamples = readLastGranulePos(raf);
            if (totalSamples <= 0) return fallbackSeconds;
            double seconds = (double) totalSamples / (double) sampleRate;
            if (seconds > 3600 || seconds <= 0) return fallbackSeconds;
            return (int) Math.round(seconds);
        }
    }

    public static int readLengthSecondsFromBytes(byte[] oggBytes, int fallbackSeconds) {
        try {
            long sampleRate = readSampleRateFromStream(new ByteArrayInputStream(oggBytes));
            if (sampleRate <= 0) return fallbackSeconds;
            long totalSamples = readLastGranulePosFromBytes(oggBytes);
            if (totalSamples <= 0) return fallbackSeconds;
            double seconds = (double) totalSamples / (double) sampleRate;
            if (seconds > 3600 || seconds <= 0) return fallbackSeconds;
            return (int) Math.round(seconds);
        } catch (IOException e) {
            return fallbackSeconds;
        }
    }

    private static long readSampleRate(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        byte[] firstChunk = new byte[Math.min(256, (int) Math.min(raf.length(), Integer.MAX_VALUE))];
        raf.readFully(firstChunk);
        return readSampleRateFromStream(new ByteArrayInputStream(firstChunk));
    }

    private static long readSampleRateFromStream(InputStream in) throws IOException {
        byte[] hdr = readN(in, OGG_PAGE_HEADER_BYTES);
        if (hdr == null) return -1;
        if (hdr[0] != 'O' || hdr[1] != 'g' || hdr[2] != 'g' || hdr[3] != 'S') return -1;
        int segCount = hdr[26] & 0xFF;
        byte[] segTable = readN(in, segCount);
        if (segTable == null) return -1;
        byte[] idPacket = readN(in, 30);
        if (idPacket == null) return -1;
        if (idPacket[0] != 0x01) return -1;
        if (!new String(idPacket, 1, 6, StandardCharsets.US_ASCII).equals("vorbis")) return -1;
        return readUInt32LE(idPacket, 12);
    }

    private static long readLastGranulePos(RandomAccessFile raf) throws IOException {
        long fileLen = raf.length();
        long pos = 0;
        long lastGranule = -1;
        byte[] header = new byte[OGG_PAGE_HEADER_BYTES];
        while (pos + OGG_PAGE_HEADER_BYTES <= fileLen) {
            raf.seek(pos);
            raf.readFully(header);
            if (header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S') break;
            long granule = readInt64LE(header, 6);
            int segCount = header[26] & 0xFF;
            byte[] segTable = new byte[segCount];
            raf.readFully(segTable);
            int totalSegBytes = 0;
            for (byte b : segTable) totalSegBytes += (b & 0xFF);
            pos = raf.getFilePointer() + totalSegBytes;
            if (granule >= 0) lastGranule = granule;
        }
        return lastGranule;
    }

    private static long readLastGranulePosFromBytes(byte[] oggBytes) {
        long lastGranule = -1;
        int pos = 0;
        while (pos + OGG_PAGE_HEADER_BYTES <= oggBytes.length) {
            if (oggBytes[pos] != 'O' || oggBytes[pos + 1] != 'g'
                    || oggBytes[pos + 2] != 'g' || oggBytes[pos + 3] != 'S') break;
            long granule = readInt64LE(oggBytes, pos + 6);
            int segCount = oggBytes[pos + 26] & 0xFF;
            int segTableEnd = pos + 27 + segCount;
            if (segTableEnd > oggBytes.length) break;
            int totalSegBytes = 0;
            for (int i = 0; i < segCount; i++) totalSegBytes += (oggBytes[pos + 27 + i] & 0xFF);
            pos = segTableEnd + totalSegBytes;
            if (granule >= 0) lastGranule = granule;
        }
        return lastGranule;
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        if (n <= 0) return new byte[0];
        byte[] out = new byte[n];
        int read = 0;
        while (read < n) {
            int k = in.read(out, read, n - read);
            if (k < 0) return null;
            read += k;
        }
        return out;
    }

    private static long readUInt32LE(byte[] arr, int off) {
        return (arr[off] & 0xFFL)
                | ((arr[off + 1] & 0xFFL) << 8)
                | ((arr[off + 2] & 0xFFL) << 16)
                | ((arr[off + 3] & 0xFFL) << 24);
    }

    private static long readInt64LE(byte[] arr, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) v |= (arr[off + i] & 0xFFL) << (i * 8);
        return v;
    }
}
