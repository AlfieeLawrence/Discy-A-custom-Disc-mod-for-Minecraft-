package net.discy.core.client.texture;

import net.minecraft.resources.ResourceLocation;
import net.discy.core.registry.PermanentDiscRegistry;
import net.discy.core.util.DiscyIdentifier;

import java.util.ArrayList;
import java.util.List;

/** Preset disc textures from {@link PermanentDiscRegistry} plus the blank default. */
public final class PermanentDiscTextures {

    public record Entry(String label, ResourceLocation texture, boolean userTexture, int slot) {}

    private PermanentDiscTextures() {}

    public static List<Entry> presetEntries() {
        List<Entry> entries = new ArrayList<>();
        ResourceLocation def = DiskTextureManager.locForStem("music_disc");
        if (def == null) def = new DiscyIdentifier("textures/item/blank_music_disc.png");
        entries.add(new Entry("Default", def, false, 0));

        int slot = 1;
        for (PermanentDiscRegistry.PermanentDiscDefinition defn : PermanentDiscRegistry.getDefinitions()) {
            entries.add(new Entry(defn.id(), defn.texturePath(), false, slot++));
        }
        return entries;
    }

    public static boolean isExportedPresetStem(String stem) {
        if (stem.equals("music_disc")) return true;
        for (PermanentDiscRegistry.PermanentDiscDefinition defn : PermanentDiscRegistry.getDefinitions()) {
            if (stem.equals(defn.id()) || stem.equals(defn.texture())) return true;
        }
        return false;
    }
}
