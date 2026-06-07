package net.discy.forge.mixin.sophisticatedcore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.VanillaDiscHandler;
import net.discy.api.DiscyApi;
import net.discy.integration.sophisticatedcore.PortableDiscPlayback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = VanillaDiscHandler.class, remap = false)
public class VanillaDiscHandlerMixin {

    @Inject(method = "supports", at = @At("HEAD"), cancellable = true, remap = false)
    private void discy$deferBoundCustomDiscs(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (DiscyApi.isBoundCustomDisc(itemStack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "playDisc(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/UUID;Lnet/minecraft/world/item/ItemStack;Ljava/lang/Runnable;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void discy$playCustomBlock(ServerLevel level, BlockPos position, UUID storageUuid,
                                       ItemStack disc, Runnable onFinished, CallbackInfo ci) {
        if (!DiscyApi.isBoundCustomDisc(disc)) return;
        Vec3 pos = Vec3.atCenterOf(position);
        if (PortableDiscPlayback.play(level, pos, storageUuid, disc)) {
            PortableDiscPlayback.trackFinish(level, storageUuid, pos, disc, onFinished);
        }
        ci.cancel();
    }

    @Inject(
            method = "playDisc(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Ljava/util/UUID;Lnet/minecraft/world/item/ItemStack;ILjava/lang/Runnable;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void discy$playCustomEntity(ServerLevel level, Vec3 position, UUID storageUuid, ItemStack disc,
                                        int entityId, Runnable onFinished, CallbackInfo ci) {
        if (!DiscyApi.isBoundCustomDisc(disc)) return;
        if (PortableDiscPlayback.play(level, position, storageUuid, disc)) {
            PortableDiscPlayback.trackFinish(level, storageUuid, position, disc, onFinished);
        }
        ci.cancel();
    }
}
