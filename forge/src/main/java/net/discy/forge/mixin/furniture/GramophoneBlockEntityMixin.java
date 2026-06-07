package net.discy.forge.mixin.furniture;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.discy.core.item.CustomDiscItem;
import net.discy.integration.BlockDiscPlayback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.berksire.furniture.block.entity.GramophoneBlockEntity", remap = false)
public abstract class GramophoneBlockEntityMixin {

    @Shadow(remap = false)
    private ItemStack recordItem;

    @Shadow(remap = false)
    private boolean isPlaying;

    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"), remap = true)
    private void discy$clearStalePlayingFlag(CompoundTag tag, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        BlockDiscPlayback.clearStalePlayingOnLoad(self.getLevel(), recordItem, () -> isPlaying = false);
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/RecordItem;getLengthInTicks()I",
                    remap = true
            ),
            remap = false
    )
    private int discy$lengthTicks(RecordItem recordItem) {
        if (CustomDiscItem.isCustomDisc(this.recordItem) && CustomDiscItem.readHash(this.recordItem) != null) {
            return BlockDiscPlayback.lengthTicks(this.recordItem);
        }
        return recordItem.getLengthInTicks();
    }
}
