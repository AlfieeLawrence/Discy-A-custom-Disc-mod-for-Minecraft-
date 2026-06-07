package net.discy.core.client;

import java.util.HashMap;
import java.util.Map;

/** Pixel cache shared with {@link net.discy.mixin.ItemRendererMixin}. */
public final class DiscPixelCache {

    private static final Map<String, int[][]> CACHE = new HashMap<>();

    private DiscPixelCache() {}

    public static int[][] get(String label) {
        return CACHE.get(label);
    }

    public static void put(String label, int[][] pixels) {
        CACHE.put(label, pixels);
    }

    public static void evict(String label) {
        CACHE.remove(label);
    }

    public static void clear() {
        CACHE.clear();
    }
}
