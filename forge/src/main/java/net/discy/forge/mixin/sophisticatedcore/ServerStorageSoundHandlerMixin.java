package net.discy.forge.mixin.sophisticatedcore;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.ServerStorageSoundHandler;
import net.discy.core.network.DiscyNetworking;
import net.discy.integration.sophisticatedcore.PortableDiscPlayback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = ServerStorageSoundHandler.class, remap = false)
public class ServerStorageSoundHandlerMixin {

    /** Stop the previous track (custom or vanilla) before switching discs — prevents dual playback on skip. */
    @Inject(
            method = "startPlayingDisc(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/UUID;Lnet/minecraft/world/item/Item;Ljava/lang/Runnable;)V",
            at = @At("HEAD"),
            remap = false
    )
    private static void discy$stopBeforeBlockDisc(ServerLevel level, net.minecraft.core.BlockPos pos, UUID storageUuid,
                                                  net.minecraft.world.item.Item disc, Runnable onFinished,
                                                  CallbackInfo ci) {
        ServerStorageSoundHandler.stopPlayingDisc(level, Vec3.atCenterOf(pos), storageUuid);
    }

    @Inject(
            method = "startPlayingDisc(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Ljava/util/UUID;ILnet/minecraft/world/item/Item;Ljava/lang/Runnable;)V",
            at = @At("HEAD"),
            remap = false
    )
    private static void discy$stopBeforeEntityDisc(ServerLevel level, Vec3 pos, UUID storageUuid, int entityId,
                                                   net.minecraft.world.item.Item disc, Runnable onFinished,
                                                   CallbackInfo ci) {
        ServerStorageSoundHandler.stopPlayingDisc(level, pos, storageUuid);
    }

    @Inject(
            method = "updateKeepAlive(Ljava/util/UUID;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Ljava/lang/Runnable;)V",
            at = @At("TAIL"),
            remap = false
    )
    private static void discy$updateCustomPosition(UUID storageUuid, Level level, Vec3 pos, Runnable onFinished,
                                                   CallbackInfo ci) {
        if (level instanceof ServerLevel) {
            PortableDiscPlayback.updatePosition(storageUuid, pos);
        }
    }

    @Inject(method = "sendStopMessage", at = @At("HEAD"), remap = false)
    private static void discy$stopCustomStream(ServerLevel serverWorld, Vec3 position, UUID storageUuid,
                                               CallbackInfo ci) {
        PortableDiscPlayback.release(storageUuid);
        DiscyNetworking.sendStopPortable(serverWorld, position.x, position.y, position.z, storageUuid);
    }
}
