package net.discy.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.jetbrains.annotations.Nullable;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenInvokerMixin {

    @Invoker("renderSlot")
    void discy$invokeRenderSlot(GuiGraphics graphics, Slot slot);

    @Invoker("renderFloatingItem")
    void discy$invokeRenderFloatingItem(GuiGraphics graphics, ItemStack stack, int x, int y, @Nullable String text);
}
