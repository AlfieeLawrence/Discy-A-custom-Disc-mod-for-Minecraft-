package net.discy.integration.sophisticatedcore;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.discy.api.DiscyApi;
import net.discy.core.network.DiscyNetworking;
import net.discy.integration.BlockDiscPlayback;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared server playback for Sophisticated Backpacks / Storage jukebox upgrades. */
public final class PortableDiscPlayback {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Storage UUIDs currently streaming a custom disc — used for position updates while moving. */
    private static final Map<UUID, ServerLevel> ACTIVE = new ConcurrentHashMap<>();

    private PortableDiscPlayback() {}

    public static boolean play(ServerLevel level, BlockPos pos, UUID storageUuid, ItemStack disc) {
        return play(level, Vec3.atCenterOf(pos), storageUuid, disc);
    }

    public static boolean play(ServerLevel level, Vec3 pos, UUID storageUuid, ItemStack disc) {
        if (!DiscyApi.isBoundCustomDisc(disc)) {
            return false;
        }
        String hash = DiscyApi.getSongHash(disc);
        if (hash == null) {
            return false;
        }
        String name = DiscyApi.getDisplayName(disc);
        ACTIVE.put(storageUuid, level);
        DiscyNetworking.sendPlayAt(level, pos.x, pos.y, pos.z, hash, name != null ? name : "", storageUuid);
        return true;
    }

    /** Clears server-side tracking when SC stops playback; clients are notified by SC stop packets. */
    public static void release(UUID storageUuid) {
        ACTIVE.remove(storageUuid);
    }

    /** Fabric SoundHandler API — must notify clients because SC stop packets are not used there. */
    public static void stop(ServerLevel level, Vec3 pos, UUID storageUuid) {
        if (ACTIVE.remove(storageUuid) == null) {
            return;
        }
        DiscyNetworking.sendStopPortable(level, pos.x, pos.y, pos.z, storageUuid);
    }

    public static void updatePosition(UUID storageUuid, Vec3 pos) {
        ServerLevel level = ACTIVE.get(storageUuid);
        if (level == null) {
            return;
        }
        DiscyNetworking.sendUpdatePortable(level, storageUuid, pos.x, pos.y, pos.z);
    }

    public static int lengthTicks(ItemStack disc) {
        return BlockDiscPlayback.lengthTicks(disc);
    }

    /** Forge 1.3+ timing callback so SC can auto-advance playlists. */
    public static void trackFinish(ServerLevel level, UUID storageUuid, Vec3 pos, ItemStack disc,
                                   Runnable onFinished) {
        try {
            Class<?> soundHandler = Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.ServerStorageSoundHandler");
            soundHandler.getMethod("putSoundInfo", ServerLevel.class, UUID.class, Runnable.class,
                            Vec3.class, long.class)
                    .invoke(null, level, storageUuid, onFinished, pos,
                            level.getGameTime() + lengthTicks(disc));
        } catch (Throwable t) {
            LOGGER.warn("Discy: could not register Sophisticated Core finish timer: {}", t.toString());
        }
    }
}
