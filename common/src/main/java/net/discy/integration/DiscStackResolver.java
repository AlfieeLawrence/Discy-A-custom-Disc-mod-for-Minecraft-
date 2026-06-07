package net.discy.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.discy.core.item.CustomDiscItem;

import java.lang.reflect.Method;

/**
 * Finds the inserted record stack on jukebox-like block entities from other mods.
 */
public final class DiscStackResolver {

    private static final String[] RECORD_GETTERS = {"getFirstItem", "getRecord", "getDisc"};

    private DiscStackResolver() {}

    public static ItemStack resolve(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return ItemStack.EMPTY;
        }

        if (blockEntity instanceof JukeboxBlockEntity jukebox) {
            return jukebox.getItem(0);
        }

        if (blockEntity instanceof Container container && container.getContainerSize() > 0) {
            ItemStack stack = container.getItem(0);
            if (isRecordStack(stack)) {
                return stack;
            }
        }

        for (String getter : RECORD_GETTERS) {
            ItemStack stack = invokeRecordGetter(blockEntity, getter);
            if (isRecordStack(stack)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static boolean isRecordStack(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof RecordItem || CustomDiscItem.isCustomDisc(stack));
    }

    private static ItemStack invokeRecordGetter(BlockEntity blockEntity, String methodName) {
        try {
            Method method = blockEntity.getClass().getMethod(methodName);
            if (!ItemStack.class.isAssignableFrom(method.getReturnType())) {
                return ItemStack.EMPTY;
            }
            Object result = method.invoke(blockEntity);
            return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }
}
