package net.discy.fabric.mixin.sophisticatedcore;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.ServerStorageSoundHandler;
import net.discy.core.network.DiscyNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.function.Function;

@Mixin(value = ServerStorageSoundHandler.class, remap = false)
public class ServerStorageSoundHandlerMixin {

    /** Stop the previous track (custom or vanilla) before switching discs — prevents dual playback on skip. */
    @Inject(method = "runSoundHandler", at = @At("HEAD"), remap = false)
    private static void discy$stopBeforeSwitch(ServerLevel level, Vec3 pos, UUID storageUuid, Runnable onFinished,
                                               Function<?, Boolean> playFunc, CallbackInfo ci) {
        ServerStorageSoundHandler.stopPlayingDisc(level, pos, storageUuid);
    }

    @Inject(method = "sendStopMessage", at = @At("HEAD"), remap = false)
    private static void discy$stopCustomStream(ServerLevel serverWorld, Vec3 position, UUID storageUuid,
                                               CallbackInfo ci) {
        DiscyNetworking.sendStopPortable(serverWorld, position.x, position.y, position.z, storageUuid);
    }
}
