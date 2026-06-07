package net.discy.fabric.mixin.sophisticatedcore;

import net.discy.core.client.CustomDiscPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Stop Discy OpenAL streams before Sophisticated Core starts a vanilla disc. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.PlayDiscMessage", remap = false)
public class PlayDiscMessageMixin {

    @Shadow(remap = false)
    private UUID storageUuid;

    @Inject(method = "handle", at = @At("HEAD"), remap = false)
    private void discy$stopCustomBeforeVanilla(CallbackInfoReturnable<Boolean> cir) {
        CustomDiscPlayer.stopPortable(storageUuid);
    }
}
