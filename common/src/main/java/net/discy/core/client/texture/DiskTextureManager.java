package net.discy.core.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.discy.Discy;
import net.discy.core.library.SongLibrary;
import net.discy.core.registry.PermanentDiscRegistry;
import net.discy.core.util.DiscyIdentifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class DiskTextureManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiskTextureManager.class);
    private static final String RL_PATH_PREFIX = "disk_texture/";
    private static final Map<String, ResourceLocation> REGISTERED = new HashMap<>();
    private static final ExecutorService BG = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "discy-texture-scan");
        t.setDaemon(true);
        return t;
    });

    private DiskTextureManager() {}

    public static Path diskTexturesDir() {
        return SongLibrary.diskTexturesDir();
    }

    public static ResourceLocation locForStem(String stem) {
        return REGISTERED.get(stem);
    }

    /** Ensures a stem is registered and attempts an immediate GPU upload when possible. */
    @Nullable
    public static ResourceLocation ensureStem(String stem) {
        if (stem == null || stem.isBlank()) return null;

        ResourceLocation loc = rlForStem(stem);
        if (isDynamicReady(loc)) {
            return loc;
        }

        try {
            exportPresets();
            Path png = diskTexturesDir().resolve(stem + ".png");
            if (Files.isRegularFile(png)) {
                loadOneSync(png);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not prepare texture '{}': {}", stem, e.getMessage());
        }
        return REGISTERED.get(stem);
    }

    public static boolean isDynamicReady(ResourceLocation loc) {
        if (loc == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        return mc.getTextureManager().getTexture(loc) instanceof DynamicTexture;
    }

    /** Resource location for a stem, queueing a GPU load if needed (never blocks the render thread). */
    public static ResourceLocation rlForStem(String stem) {
        ResourceLocation existing = REGISTERED.get(stem);
        if (existing != null) return existing;
        String rlStem = stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        ResourceLocation loc = new DiscyIdentifier(RL_PATH_PREFIX + rlStem);
        REGISTERED.put(stem, loc);
        Path png = diskTexturesDir().resolve(stem + ".png");
        if (Files.isRegularFile(png)) {
            loadOne(png);
        }
        return loc;
    }

    /** Lists user-uploaded texture stems on a background thread (export presets first). */
    public static void scanUserTextureStemsAsync(Consumer<List<String>> callback) {
        BG.submit(() -> {
            List<String> stems = new ArrayList<>();
            try {
                exportPresets();
                Path dir = diskTexturesDir();
                if (Files.isDirectory(dir)) {
                    try (var stream = Files.list(dir)) {
                        stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                                .sorted()
                                .forEach(p -> {
                                    String stem = p.getFileName().toString();
                                    stem = stem.substring(0, stem.length() - 4);
                                    if (!PermanentDiscTextures.isExportedPresetStem(stem)) {
                                        stems.add(stem);
                                    }
                                });
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Could not scan disk_textures: {}", e.getMessage());
            }
            List<String> result = stems;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> callback.accept(result));
            } else {
                callback.accept(result);
            }
        });
    }

    public static void refresh() {
        exportPresets();
        loadAll();
    }

    /** Exports preset PNGs and loads every texture on a background thread (safe during client setup). */
    public static void refreshAsync() {
        BG.submit(() -> {
            try {
                exportPresets();
                loadAll();
            } catch (Exception e) {
                LOGGER.warn("Could not refresh disk textures: {}", e.getMessage());
            }
        });
    }

    public static void saveAndRegister(String stem, byte[] pngBytes) throws IOException {
        Path dir = diskTexturesDir();
        SongLibrary.ensureDirectory(dir);
        Path dest = dir.resolve(stem + ".png");
        Files.write(dest, pngBytes);
        registerFromFile(dest);
    }

    /** Load a single PNG without rescanning the whole disk_textures folder. */
    public static void registerFromFile(Path pngFile) {
        loadOne(pngFile);
    }

    public static void release(String stem) {
        ResourceLocation loc = REGISTERED.remove(stem);
        if (loc == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(() -> mc.getTextureManager().release(loc));
    }

    private static void exportPresets() {
        Path dir = diskTexturesDir();
        exportClasspathPng("/assets/discy/textures/item/blank_music_disc.png", dir.resolve("music_disc.png"));
        for (PermanentDiscRegistry.PermanentDiscDefinition defn : PermanentDiscRegistry.getDefinitions()) {
            exportClasspathPng("/assets/discy/textures/item/" + defn.texture() + ".png", dir.resolve(defn.id() + ".png"));
        }
    }

    private static void exportClasspathPng(String resourcePath, Path dest) {
        try {
            if (Files.exists(dest) && Files.size(dest) > 0) return;
            try (InputStream is = DiskTextureManager.class.getResourceAsStream(resourcePath)) {
                if (is == null) return;
                Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not export '{}': {}", dest.getFileName(), e.getMessage());
        }
    }

    private static void loadAll() {
        Path dir = diskTexturesDir();
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .forEach(DiskTextureManager::loadOne);
        } catch (IOException e) {
            LOGGER.warn("Could not list disk_textures: {}", e.getMessage());
        }
    }

    private static void loadOneSync(Path pngFile) {
        String filename = pngFile.getFileName().toString();
        String stem = filename.substring(0, filename.length() - 4);
        String rlStem = stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        ResourceLocation loc = new DiscyIdentifier(RL_PATH_PREFIX + rlStem);

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            REGISTERED.put(stem, loc);
            return;
        }

        try (InputStream is = Files.newInputStream(pngFile)) {
            NativeImage img = NativeImage.read(is);
            DynamicTexture dt = new DynamicTexture(img);
            mc.getTextureManager().register(loc, dt);
            REGISTERED.put(stem, loc);
        } catch (Exception e) {
            LOGGER.warn("Could not load texture '{}': {}", filename, e.getMessage());
        }
    }

    private static void loadOne(Path pngFile) {
        String filename = pngFile.getFileName().toString();
        String stem = filename.substring(0, filename.length() - 4);
        String rlStem = stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        ResourceLocation loc = new DiscyIdentifier(RL_PATH_PREFIX + rlStem);

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            REGISTERED.put(stem, loc);
            return;
        }
        mc.execute(() -> {
            try (InputStream is = Files.newInputStream(pngFile)) {
                NativeImage img = NativeImage.read(is);
                DynamicTexture dt = new DynamicTexture(img);
                mc.getTextureManager().register(loc, dt);
                REGISTERED.put(stem, loc);
            } catch (Exception e) {
                LOGGER.warn("Could not load texture '{}': {}", filename, e.getMessage());
            }
        });
        REGISTERED.put(stem, loc);
    }
}
