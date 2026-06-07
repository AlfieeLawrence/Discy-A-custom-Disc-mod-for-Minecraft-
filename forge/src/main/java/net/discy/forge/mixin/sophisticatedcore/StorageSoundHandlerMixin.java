package net.discy.forge.mixin.sophisticatedcore;

import net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.StorageSoundHandler;
import net.discy.core.client.CustomDiscPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Stops Discy OpenAL streams when Sophisticated Core ends jukebox playback on the client. */
@Mixin(value = StorageSoundHandler.class, remap = false)
public class StorageSoundHandlerMixin {

    @Inject(method = "stopStorageSound", at = @At("HEAD"), remap = false)
    private static void discy$stopPortable(UUID storageUuid, CallbackInfo ci) {
        CustomDiscPlayer.stopPortable(storageUuid);
    }
}
