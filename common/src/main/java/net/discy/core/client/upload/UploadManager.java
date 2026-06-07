package net.discy.core.client.upload;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.discy.core.client.screen.TexturePickerScreen;
import net.discy.core.network.DiscyNetworking;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UploadManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadManager.class);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static volatile boolean isUploading;
    private static volatile boolean justCompleted;
    private static volatile String uploadStatus = "";

    public static boolean isUploading() {
        return isUploading;
    }

    public static boolean wasJustCompleted() {
        return justCompleted;
    }

    public static void clearCompletedFlag() {
        justCompleted = false;
    }

    public static String getStatus() {
        return uploadStatus;
    }

    /** Pick an audio file, then open the texture picker before uploading. */
    public static void openFileChooserForBurn(BlockPos deckPos, Screen parentScreen) {
        if (isUploading) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("Already uploading!"));
            return;
        }
        EXECUTOR.submit(() -> {
            try {
                Path selected = pickAudioFile();
                if (selected != null) {
                    Minecraft.getInstance().execute(() ->
                            Minecraft.getInstance().setScreen(
                                    new TexturePickerScreen(parentScreen, deckPos, selected)));
                }
            } catch (Throwable t) {
                LOGGER.error("File chooser error", t);
            }
        });
    }

    public static void startUpload(File file, BlockPos deckPos, int chosenSlot, String textureLabel) {
        beginUpload(file, deckPos, chosenSlot, textureLabel == null ? "" : textureLabel);
    }

    private static Path pickAudioFile() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filterPatterns = stack.mallocPointer(2);
            filterPatterns.put(stack.UTF8("*.ogg"));
            filterPatterns.put(stack.UTF8("*.mp3"));
            filterPatterns.flip();

            String defaultPath = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("discy").resolve("songs").toAbsolutePath().toString();
            Files.createDirectories(Paths.get(defaultPath));

            String selected = TinyFileDialogs.tinyfd_openFileDialog(
                    "Select audio file to burn",
                    defaultPath + File.separator,
                    filterPatterns,
                    "Audio files (*.ogg, *.mp3)",
                    false);
            if (selected == null || selected.isBlank()) return null;
            return Paths.get(selected);
        } catch (Throwable t) {
            LOGGER.error("Could not open file dialog", t);
            return null;
        }
    }

    private static void beginUpload(File file, BlockPos deckPos, int chosenSlot, String textureLabel) {
        isUploading = true;
        justCompleted = false;
        uploadStatus = "Reading file...";

        EXECUTOR.submit(() -> {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                long size = data.length;
                if (size > DiscyNetworking.MAX_UPLOAD_BYTES) {
                    fail("File too large (max 50 MB)");
                    return;
                }

                UUID uploadId = UUID.randomUUID();
                String displayName = file.getName().replaceAll("(?i)\\.(ogg|mp3)$", "");
                String fileExtension = file.getName().substring(file.getName().lastIndexOf('.'));

                FriendlyByteBuf beginBuf = new FriendlyByteBuf(Unpooled.buffer());
                beginBuf.writeUUID(uploadId);
                beginBuf.writeUtf(displayName);
                beginBuf.writeVarInt(0);
                beginBuf.writeLong(size);
                beginBuf.writeUtf(fileExtension);
                beginBuf.writeBlockPos(deckPos);
                beginBuf.writeVarInt(chosenSlot);
                beginBuf.writeUtf(textureLabel, 64);
                NetworkManager.sendToServer(DiscyNetworking.UPLOAD_BEGIN, beginBuf);

                int chunkSize = DiscyNetworking.CHUNK_SIZE;
                int totalChunks = (int) Math.ceil((double) size / chunkSize);
                for (int i = 0; i < totalChunks; i++) {
                    int offset = i * chunkSize;
                    int len = (int) Math.min(chunkSize, size - offset);
                    byte[] chunk = new byte[len];
                    System.arraycopy(data, offset, chunk, 0, len);

                    FriendlyByteBuf chunkBuf = new FriendlyByteBuf(Unpooled.buffer());
                    chunkBuf.writeUUID(uploadId);
                    chunkBuf.writeVarInt(len);
                    chunkBuf.writeByteArray(chunk);
                    NetworkManager.sendToServer(DiscyNetworking.UPLOAD_CHUNK, chunkBuf);
                    uploadStatus = String.format("Uploading... %d%%", (i * 100) / Math.max(1, totalChunks));
                }

                FriendlyByteBuf endBuf = new FriendlyByteBuf(Unpooled.buffer());
                endBuf.writeUUID(uploadId);
                NetworkManager.sendToServer(DiscyNetworking.UPLOAD_END, endBuf);
                uploadStatus = "Waiting for server...";
            } catch (IOException e) {
                fail("Upload failed: " + e.getMessage());
            }
        });
    }

    private static void fail(String msg) {
        isUploading = false;
        uploadStatus = "";
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal(msg));
            }
        });
    }

    public static void onUploadDone() {
        isUploading = false;
        justCompleted = true;
        uploadStatus = "";
    }

    public static void onUploadError(String message) {
        isUploading = false;
        uploadStatus = "";
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Upload error: " + message));
            }
        });
    }
}
