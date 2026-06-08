package net.discy.core.client.upload;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.discy.core.client.DiscPixelCache;
import net.discy.core.client.texture.DiskTextureManager;
import net.discy.core.library.SongLibrary;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Background texture upload — file dialog and disk/network work never block the render thread.
 */
public final class TextureUploadManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextureUploadManager.class);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "discy-texture-upload");
        t.setDaemon(true);
        return t;
    });
    private static final int MAX_PNG_BYTES = 1024 * 1024;

    private static volatile boolean uploading;

    private TextureUploadManager() {}

    public static boolean isUploading() {
        return uploading;
    }

    public static void pickAndUpload(Consumer<String> onStemReady, Consumer<String> onError) {
        if (uploading) {
            notifyError(onError, "Already uploading a texture.");
            return;
        }
        uploading = true;
        EXECUTOR.submit(() -> uploadPaths(pickPngFiles(false), onStemReady, onError));
    }

    public static void uploadPngBytes(String stem, byte[] bytes, Runnable onComplete, Consumer<String> onError) {
        if (uploading) {
            notifyError(onError, "Already uploading a texture.");
            return;
        }
        if (bytes == null || bytes.length == 0) {
            notifyError(onError, "Texture is empty.");
            return;
        }
        if (bytes.length > MAX_PNG_BYTES) {
            notifyError(onError, "Texture is too large (max 1 MB).");
            return;
        }
        uploading = true;
        String safeStem = stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (safeStem.isBlank()) {
            notifyError(onError, "Invalid texture name.");
            return;
        }
        DiscyNetworking.sendUploadTexture(safeStem, bytes);
        Minecraft.getInstance().execute(() -> {
            try {
                DiskTextureManager.saveAndRegister(safeStem, bytes);
                DiscPixelCache.evict(safeStem);
                SongLibrary.get().addTexture(safeStem);
            } catch (IOException e) {
                notifyError(onError, "Could not register " + safeStem + ": " + e.getMessage());
                return;
            }
            if (onComplete != null) {
                onComplete.run();
            }
            uploading = false;
        });
    }

    /** Opens a file dialog on a background thread; callback runs on the client thread. */
    public static void pickSinglePngAsync(Consumer<Path> onPicked) {
        EXECUTOR.submit(() -> {
            List<Path> paths = pickPngFiles(false);
            Path picked = paths.isEmpty() ? null : paths.get(0);
            Minecraft.getInstance().execute(() -> onPicked.accept(picked));
        });
    }

    public static void pickAndUploadMultiple(Runnable onComplete, Consumer<String> onError) {
        if (uploading) {
            notifyError(onError, "Already uploading textures.");
            return;
        }
        uploading = true;
        EXECUTOR.submit(() -> uploadPaths(pickPngFiles(true), stem -> { }, onError, onComplete));
    }

    private static void uploadPaths(List<Path> paths, Consumer<String> onStemReady, Consumer<String> onError) {
        uploadPaths(paths, onStemReady, onError, null);
    }

    private static void uploadPaths(List<Path> paths, Consumer<String> onStemReady,
                                    Consumer<String> onError, Runnable onComplete) {
        try {
            if (paths.isEmpty()) {
                uploading = false;
                return;
            }
            String lastStem = null;
            int uploaded = 0;
            for (Path picked : paths) {
                String stem = sanitizeStem(picked);
                byte[] bytes = Files.readAllBytes(picked);
                if (bytes.length == 0) {
                    LOGGER.warn("Skipped empty texture: {}", picked.getFileName());
                    continue;
                }
                if (bytes.length > MAX_PNG_BYTES) {
                    LOGGER.warn("Skipped oversized texture: {}", picked.getFileName());
                    continue;
                }
                DiscyNetworking.sendUploadTexture(stem, bytes);
                String finalStem = stem;
                byte[] finalBytes = bytes;
                Minecraft.getInstance().execute(() -> {
                    try {
                        DiskTextureManager.saveAndRegister(finalStem, finalBytes);
                        DiscPixelCache.evict(finalStem);
                        SongLibrary.get().addTexture(finalStem);
                    } catch (IOException e) {
                        notifyError(onError, "Could not register " + finalStem + ": " + e.getMessage());
                    }
                });
                lastStem = stem;
                uploaded++;
            }
            if (uploaded == 0) {
                notifyError(onError, "No textures were uploaded.");
                return;
            }
            String resultStem = lastStem;
            Minecraft.getInstance().execute(() -> {
                if (onStemReady != null && resultStem != null) {
                    onStemReady.accept(resultStem);
                }
                if (onComplete != null) {
                    onComplete.run();
                }
                uploading = false;
            });
        } catch (IOException e) {
            notifyError(onError, "Could not read texture: " + e.getMessage());
        } catch (Throwable t) {
            LOGGER.error("Texture upload failed", t);
            notifyError(onError, "Texture upload failed.");
        }
    }

    private static List<Path> pickPngFiles(boolean multiple) {
        List<Path> out = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer patterns = stack.mallocPointer(1);
            patterns.put(stack.UTF8("*.png"));
            patterns.flip();

            String defaultPath = DiskTextureManager.diskTexturesDir().toAbsolutePath().toString();
            Files.createDirectories(Paths.get(defaultPath));

            String selected = TinyFileDialogs.tinyfd_openFileDialog(
                    multiple ? "Select disc textures (16x16 PNG)" : "Select disc texture (16x16 PNG)",
                    defaultPath + File.separator,
                    patterns,
                    "PNG images (*.png)",
                    multiple);
            if (selected == null || selected.isBlank()) {
                return out;
            }
            if (multiple && selected.contains("|")) {
                for (String part : selected.split("\\|")) {
                    if (!part.isBlank()) out.add(Paths.get(part.trim()));
                }
            } else {
                out.add(Paths.get(selected));
            }
        } catch (IOException e) {
            LOGGER.warn("Could not open texture file dialog: {}", e.getMessage());
        }
        return out;
    }

    private static String sanitizeStem(Path file) {
        String stem = file.getFileName().toString();
        if (stem.toLowerCase(Locale.ROOT).endsWith(".png")) {
            stem = stem.substring(0, stem.length() - 4);
        }
        stem = stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return stem.isBlank() ? "custom" : stem;
    }

    private static void notifyError(Consumer<String> onError, String message) {
        uploading = false;
        Minecraft.getInstance().execute(() -> {
            if (onError != null) {
                onError.accept(message);
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), false);
            }
        });
    }
}
