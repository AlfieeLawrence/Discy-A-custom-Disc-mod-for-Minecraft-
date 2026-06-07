package net.discy.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.discy.core.item.CustomDiscItem;
import net.discy.core.registry.ObjectRegistry;
import net.discy.integration.BlockDiscPlayback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxBlockEntityMixin extends BlockEntity {
    @Shadow private long recordStartedTick;
    @Shadow private long tickCount;
    @Shadow private boolean isPlaying;
    @Shadow public abstract ItemStack getItem(int slot);

    private JukeboxBlockEntityMixin() {
        super(null, null, null);
    }

    /**
     * Saved jukeboxes can have {@code isPlaying=true} from a previous session. When the chunk
     * reloads the client would treat that as "now playing" and fire vanilla record audio
     * (our placeholder {@link net.discy.core.registry.SoundEventRegistry#CUSTOM_DISC_SOUND}).
     * Custom streaming is started only via {@code startPlaying} + PLAY_DISC, not from NBT resume.
     */
    @Inject(method = "load", at = @At("TAIL"))
    private void discy$clearStalePlayingFlag(CompoundTag tag, CallbackInfo ci) {
        BlockDiscPlayback.clearStalePlayingOnLoad(this.getLevel(), getItem(0), () -> isPlaying = false);
    }

    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void discy$startPlayingCustom(CallbackInfo ci) {
        ItemStack stack = getItem(0);
        if (!(stack.getItem() instanceof CustomDiscItem)) return;

        Level level = this.getLevel();
        if (!(level instanceof ServerLevel)) return;
        if (CustomDiscItem.readHash(stack) == null) return;

        BlockPos pos = this.getBlockPos();
        this.recordStartedTick = this.tickCount;
        this.isPlaying = true;
        level.updateNeighborsAt(pos, this.getBlockState().getBlock());

        int itemId = BuiltInRegistries.ITEM.getId(ObjectRegistry.CUSTOM_DISC.get());
        level.levelEvent(null, 1010, pos, itemId);

        this.setChanged();
        ci.cancel();
    }

    @Inject(method = "shouldRecordStopPlaying", at = @At("HEAD"), cancellable = true)
    private void discy$shouldStopByNbtLength(RecordItem recordItem, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = getItem(0);
        if (!(stack.getItem() instanceof CustomDiscItem)) return;
        if (CustomDiscItem.readHash(stack) == null) return;

        cir.setReturnValue(BlockDiscPlayback.shouldStopPlaying(tickCount, recordStartedTick, stack));
    }
}
