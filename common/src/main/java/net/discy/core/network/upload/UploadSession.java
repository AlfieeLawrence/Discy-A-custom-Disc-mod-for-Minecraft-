package net.discy.core.network.upload;

import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

final class UploadSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadSession.class);

    final UUID uploadId;
    final UUID playerUuid;
    final String displayName;
    final long expectedBytes;
    final BlockPos deckPos;
    final Path tempFile;
    final String fileExtension;
    final int chosenSlot;
    final String textureLabel;

    private OutputStream out;
    long bytesReceived;
    boolean finished;
    long lastActivityMs;

    UploadSession(UUID uploadId, UUID playerUuid, String displayName, long expectedBytes,
                  BlockPos deckPos, Path tempFile, String fileExtension, int chosenSlot, String textureLabel) {
        this.uploadId = uploadId;
        this.playerUuid = playerUuid;
        this.displayName = displayName;
        this.expectedBytes = expectedBytes;
        this.deckPos = deckPos;
        this.tempFile = tempFile;
        this.fileExtension = fileExtension == null ? ".ogg" : fileExtension;
        this.chosenSlot = chosenSlot;
        this.textureLabel = textureLabel == null ? "" : textureLabel;
        this.lastActivityMs = System.currentTimeMillis();
    }

    void open() throws IOException {
        Files.createDirectories(tempFile.getParent());
        this.out = Files.newOutputStream(tempFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    void appendChunk(byte[] chunk) throws IOException {
        if (out == null) throw new IOException("UploadSession used before open()");
        out.write(chunk);
        bytesReceived += chunk.length;
        lastActivityMs = System.currentTimeMillis();
    }

    void close() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                LOGGER.warn("Closing temp upload file {} failed: {}", tempFile, e.getMessage());
            }
            out = null;
        }
    }

    void cleanup() {
        close();
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            LOGGER.warn("Could not delete temp upload file {}: {}", tempFile, e.getMessage());
        }
    }
}
