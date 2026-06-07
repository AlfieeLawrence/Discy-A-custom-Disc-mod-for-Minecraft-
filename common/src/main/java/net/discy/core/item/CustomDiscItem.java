package net.discy.core.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.discy.core.library.SongInfo;
import net.discy.core.registry.ObjectRegistry;
import net.discy.core.registry.PermanentDiscRegistry;
import net.discy.core.util.DiscyIdentifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Single registered {@link RecordItem} whose song identity lives in stack NBT.
 */
public class CustomDiscItem extends RecordItem {

    public static final String NBT_ROOT = "CustomDisc";
    public static final String NBT_HASH = "Hash";
    public static final String NBT_NAME = "DisplayName";
    public static final String NBT_LENGTH = "LengthSeconds";
    public static final String NBT_TEXTURE_LABEL = "TextureLabel";

    public CustomDiscItem(Properties properties, int comparatorOutput,
                          Supplier<SoundEvent> sound, int lengthInTicks) {
        super(comparatorOutput, sound.get(), properties, lengthInTicks);
    }

    public static boolean isCustomDisc(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ObjectRegistry.CUSTOM_DISC.get();
    }

    @Nullable
    public static String readHash(ItemStack stack) {
        CompoundTag root = readRoot(stack);
        if (root == null || !root.contains(NBT_HASH)) return null;
        String h = root.getString(NBT_HASH);
        return h.isEmpty() ? null : h;
    }

    @Nullable
    public static Component readDisplayName(ItemStack stack) {
        CompoundTag root = readRoot(stack);
        if (root == null || !root.contains(NBT_NAME)) return null;
        String name = root.getString(NBT_NAME);
        return name.isEmpty() ? null : Component.literal(name);
    }

    @Nullable
    public static Integer readLengthSeconds(ItemStack stack) {
        CompoundTag root = readRoot(stack);
        if (root == null || !root.contains(NBT_LENGTH)) return null;
        return root.getInt(NBT_LENGTH);
    }

    @Nullable
    public static String readTextureLabel(ItemStack stack) {
        CompoundTag root = readRoot(stack);
        if (root != null && root.contains(NBT_TEXTURE_LABEL)) {
            String label = root.getString(NBT_TEXTURE_LABEL);
            if (!label.isEmpty()) return label;
        }
        return readLegacyTextureStem(stack);
    }

    /** Maps preset picker slots to PNG stems under {@code discy/disk_textures/}. */
    @Nullable
    public static String textureStemForSlot(int slot) {
        if (slot <= 0) {
            return "music_disc";
        }
        List<PermanentDiscRegistry.PermanentDiscDefinition> defs = PermanentDiscRegistry.getDefinitions();
        if (slot <= defs.size()) {
            return defs.get(slot - 1).id();
        }
        return null;
    }

    @Nullable
    public static ResourceLocation presetSpriteId(String stem) {
        if (stem == null || stem.isBlank()) return null;
        if ("music_disc".equals(stem)) {
            return new DiscyIdentifier("item/blank_music_disc");
        }
        for (PermanentDiscRegistry.PermanentDiscDefinition def : PermanentDiscRegistry.getDefinitions()) {
            if (def.id().equals(stem)) {
                return new DiscyIdentifier("item/" + def.texture());
            }
        }
        return null;
    }

    @Nullable
    private static String readLegacyTextureStem(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("CustomModelData")) return null;
        return textureStemForSlot(tag.getInt("CustomModelData"));
    }

    public static void bind(ItemStack stack, SongInfo song, int slotOverride) {
        bindWithLabel(stack, song, textureStemForSlot(slotOverride));
    }

    public static void bindWithLabel(ItemStack stack, SongInfo song, String textureLabel) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag root = new CompoundTag();
        root.putString(NBT_HASH, song.hash());
        root.putString(NBT_NAME, song.displayName());
        root.putInt(NBT_LENGTH, song.lengthSeconds());
        if (textureLabel != null && !textureLabel.isBlank()) {
            root.putString(NBT_TEXTURE_LABEL, textureLabel);
        }
        tag.put(NBT_ROOT, root);
        tag.remove("CustomModelData");
    }

    @Nullable
    private static CompoundTag readRoot(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_ROOT)) return null;
        return tag.getCompound(NBT_ROOT);
    }

    @Override
    public Component getName(ItemStack stack) {
        Component nbtName = readDisplayName(stack);
        return nbtName != null ? nbtName : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Component nbtName = readDisplayName(stack);
        if (nbtName == null) {
            tooltip.add(Component.translatable("item.discy.custom_disc.unbound")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return;
        }
        MutableComponent line = nbtName.copy().withStyle(ChatFormatting.GRAY);
        tooltip.add(line);

        Integer secs = readLengthSeconds(stack);
        if (secs != null && secs > 0) {
            tooltip.add(Component.literal(formatDuration(secs)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String formatDuration(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
