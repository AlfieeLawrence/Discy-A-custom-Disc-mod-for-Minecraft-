package net.discy.core.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.discy.core.library.SongLibrary;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Load/save disc texture pixel buffers for the in-game design studio. */
public final class DiscTexturePixelIO {

    private static final String BLANK_CLASSPATH = "/assets/discy/textures/item/blank_music_disc.png";
    public static final String BLANK_SOURCE_ID = "__blank__";

    private DiscTexturePixelIO() {}

    public static int[][] loadBlankTemplate() throws IOException {
        try (InputStream in = DiskTextureManager.class.getResourceAsStream(BLANK_CLASSPATH)) {
            if (in == null) throw new IOException("Blank disc texture missing from jar");
            try (NativeImage img = NativeImage.read(in)) {
                return fromNativeImage(img);
            }
        }
    }

    public static int[][] loadFromPath(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png);
             NativeImage img = NativeImage.read(in)) {
            return fromNativeImage(img);
        }
    }

    @Nullable
    public static int[][] loadFromStem(String stem) throws IOException {
        if (BLANK_SOURCE_ID.equals(stem)) {
            return loadBlankTemplate();
        }
        DiskTextureManager.ensurePresetsExported();
        Path png = SongLibrary.diskTexturesDir().resolve(stem + ".png");
        if (!Files.isRegularFile(png)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(png);
             NativeImage img = NativeImage.read(in)) {
            return fromNativeImage(img);
        }
    }

    public static byte[] encodePng(int[][] pixels) throws IOException {
        try (NativeImage img = toNativeImage(pixels)) {
            Path temp = Files.createTempFile("discy-design-", ".png");
            try {
                img.writeToFile(temp.toFile());
                return Files.readAllBytes(temp);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    public static int[][] fromNativeImage(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y][x] = abgrToArgb(img.getPixelRGBA(x, y));
            }
        }
        return out;
    }

    public static NativeImage toNativeImage(int[][] pixels) {
        int h = pixels.length;
        int w = pixels[0].length;
        NativeImage img = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setPixelRGBA(x, y, argbToAbgr(pixels[y][x]));
            }
        }
        return img;
    }

    private static int abgrToArgb(int abgr) {
        int a = (abgr >> 24) & 0xFF;
        int b = (abgr >> 16) & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int r = abgr & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
