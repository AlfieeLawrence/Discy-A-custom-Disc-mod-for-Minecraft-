package net.discy.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.discy.core.item.CustomDiscItem;
import net.discy.core.library.SongLibrary;
import net.discy.core.network.DiscyNetworking;

/**
 * Shared server-side playback helpers for block-based jukeboxes (vanilla or modded).
 */
public final class BlockDiscPlayback {

    private BlockDiscPlayback() {}

    public static void playAt(ServerLevel level, BlockPos pos, ItemStack disc) {
        String hash = CustomDiscItem.readHash(disc);
        if (hash == null) {
            return;
        }
        Component nameComp = CustomDiscItem.readDisplayName(disc);
        String name = nameComp != null ? nameComp.getString() : "";
        DiscyNetworking.sendPlay(level, pos, hash, name);
    }

    public static void stopAt(ServerLevel level, BlockPos pos) {
        DiscyNetworking.sendStop(level, pos);
    }

    public static int lengthTicks(ItemStack disc) {
        if (!(disc.getItem() instanceof CustomDiscItem)) {
            return disc.getItem() instanceof net.minecraft.world.item.RecordItem record
                    ? record.getLengthInTicks()
                    : 20 * 180;
        }

        int secs = 0;
        String hash = CustomDiscItem.readHash(disc);
        if (hash != null) {
            var found = SongLibrary.get().byHash(hash);
            if (found.isPresent()) {
                secs = found.get().lengthSeconds();
            }
        }
        if (secs <= 0) {
            Integer nbSecs = CustomDiscItem.readLengthSeconds(disc);
            if (nbSecs != null) {
                secs = nbSecs;
            }
        }
        if (secs <= 0) {
            return 20 * 180;
        }
        return secs * 20;
    }

    public static boolean shouldStopPlaying(long tickCount, long recordStartedTick, ItemStack disc) {
        return tickCount >= recordStartedTick + (long) lengthTicks(disc) + 20L;
    }

    /** Prevent stale {@code isPlaying} NBT from resuming placeholder vanilla audio on chunk load. */
    public static void clearStalePlayingOnLoad(Level level, ItemStack disc, Runnable clearPlaying) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (CustomDiscItem.isCustomDisc(disc) && CustomDiscItem.readHash(disc) != null) {
            clearPlaying.run();
        }
    }
}
