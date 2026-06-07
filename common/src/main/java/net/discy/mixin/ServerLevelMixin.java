package net.discy.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.discy.api.DiscyApi;
import net.discy.core.registry.ObjectRegistry;
import net.discy.integration.BlockDiscPlayback;
import net.discy.integration.DiscStackResolver;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modded jukeboxes (e.g. Let's Do Furniture gramophone) fire the same level events as vanilla
 * ({@code 1010} play / {@code 1011} stop). Hook them so custom Discy discs stream client-side.
 *
 * <p>The four-argument {@code levelEvent} exists on {@link ServerLevel}, not {@link net.minecraft.world.level.Level}.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    private static final int RECORD_PLAY_EVENT = 1010;
    private static final int RECORD_STOP_EVENT = 1011;

    @Inject(
            method = "levelEvent(Lnet/minecraft/world/entity/player/Player;ILnet/minecraft/core/BlockPos;I)V",
            at = @At("HEAD")
    )
    private void discy$handleRecordEvents(@Nullable Player player, int type, BlockPos pos, int data,
                                          CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;

        if (type == RECORD_STOP_EVENT) {
            BlockDiscPlayback.stopAt(level, pos);
            return;
        }

        if (type != RECORD_PLAY_EVENT) {
            return;
        }

        int customDiscId = BuiltInRegistries.ITEM.getId(ObjectRegistry.CUSTOM_DISC.get());
        if (data != customDiscId) {
            return;
        }

        ItemStack disc = DiscStackResolver.resolve(level, pos);
        if (DiscyApi.isBoundCustomDisc(disc)) {
            BlockDiscPlayback.playAt(level, pos, disc);
        }
    }
}
