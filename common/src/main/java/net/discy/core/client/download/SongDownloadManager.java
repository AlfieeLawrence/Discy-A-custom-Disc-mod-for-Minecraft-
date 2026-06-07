package net.discy.core.client.download;

import net.discy.core.library.SongLibrary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SongDownloadManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SongDownloadManager.class);
    private static final Map<String, DownloadState> activeDownloads = new ConcurrentHashMap<>();

    record DownloadState(String hash, String displayName, int lengthSeconds, String fileExtension,
                         byte[] data, int totalBytes, int receivedEnd) {}

    public static void startDownload(String hash, String displayName, int lengthSeconds, int totalBytes, String fileExtension) {
        byte[] buffer = new byte[totalBytes];
        activeDownloads.put(hash, new DownloadState(hash, displayName, lengthSeconds, fileExtension, buffer, totalBytes, 0));
        LOGGER.info("Started song download: {} ({} bytes)", hash.substring(0, 8), totalBytes);
    }

    public static void receiveChunk(String hash, int offset, byte[] chunk) {
        DownloadState state = activeDownloads.get(hash);
        if (state == null) return;

        if (offset < 0 || offset + chunk.length > state.totalBytes) return;
        System.arraycopy(chunk, 0, state.data, offset, chunk.length);
        int newEnd = Math.max(state.receivedEnd, offset + chunk.length);
        activeDownloads.put(hash, new DownloadState(state.hash, state.displayName, state.lengthSeconds,
                state.fileExtension, state.data, state.totalBytes, newEnd));

        if (newEnd >= state.totalBytes) {
            completeDownload(hash);
        }
    }

    private static void completeDownload(String hash) {
        DownloadState state = activeDownloads.remove(hash);
        if (state == null) return;

        Path clientDir = SongLibrary.songsDir();
        try {
            SongLibrary.ensureDirectory(clientDir);
            String ext = state.fileExtension.startsWith(".") ? state.fileExtension : "." + state.fileExtension;
            String safeName = SongLibrary.sanitizeForFilename(state.displayName);
            String fileBase = hash.substring(0, 12) + "_" + safeName;
            Path file = clientDir.resolve(fileBase + ext);
            Path meta = clientDir.resolve(fileBase + ".json");
            Files.write(file, state.data);
            SongLibrary.writeSidecar(meta, state.displayName, state.lengthSeconds);

            SongLibrary.get().addSong(hash, state.displayName, state.lengthSeconds, "server");

            LOGGER.info("Song download complete: {} -> {}", state.displayName, file);
        } catch (IOException e) {
            LOGGER.error("Failed to save downloaded song", e);
        }
    }
}
