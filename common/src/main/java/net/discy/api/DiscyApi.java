package net.discy.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.discy.core.item.CustomDiscItem;
import net.discy.core.network.DiscyNetworking;
import org.jetbrains.annotations.Nullable;

/**
 * Integration API for mods with jukebox-like blocks (e.g. Sophisticated Storage backpacks,
 * Let's Do Furniture gramophones).
 *
 * <p>Block-based players that fire vanilla record level events ({@code 1010}/{@code 1011}) are
 * handled automatically. Portable players should call {@link #playDiscAt} / {@link #stopDiscAt}
 * or register a handler like Sophisticated Core's {@code SoundHandler}.
 *
 * <pre>{@code
 * if (DiscyApi.isBoundCustomDisc(disc)) {
 *     DiscyApi.playDiscAt(level, x, y, z, disc);
 *     // later:
 *     DiscyApi.stopDiscAt(level, x, y, z);
 * } else {
 *     // vanilla RecordItem path
 * }
 * }</pre>
 */
public final class DiscyApi {

    private DiscyApi() {}

    public static boolean isCustomDisc(ItemStack stack) {
        return CustomDiscItem.isCustomDisc(stack);
    }

    public static boolean isBoundCustomDisc(ItemStack stack) {
        return isCustomDisc(stack) && CustomDiscItem.readHash(stack) != null;
    }

    @Nullable
    public static String getSongHash(ItemStack stack) {
        return CustomDiscItem.readHash(stack);
    }

    @Nullable
    public static String getDisplayName(ItemStack stack) {
        var name = CustomDiscItem.readDisplayName(stack);
        return name != null ? name.getString() : null;
    }

    public static void playDiscAt(ServerLevel level, BlockPos pos, ItemStack disc) {
        if (!isBoundCustomDisc(disc)) {
            throw new IllegalArgumentException("Not a bound custom disc: " + disc);
        }
        String hash = getSongHash(disc);
        String name = getDisplayName(disc);
        DiscyNetworking.sendPlay(level, pos, hash, name != null ? name : "");
    }

    public static void playDiscAt(ServerLevel level, double x, double y, double z, ItemStack disc) {
        if (!isBoundCustomDisc(disc)) {
            throw new IllegalArgumentException("Not a bound custom disc: " + disc);
        }
        String hash = getSongHash(disc);
        String name = getDisplayName(disc);
        DiscyNetworking.sendPlayAt(level, x, y, z, hash, name != null ? name : "");
    }

    public static void stopDiscAt(ServerLevel level, BlockPos pos) {
        DiscyNetworking.sendStop(level, pos);
    }

    public static void stopDiscAt(ServerLevel level, double x, double y, double z) {
        DiscyNetworking.sendStopAt(level, x, y, z);
    }
}
