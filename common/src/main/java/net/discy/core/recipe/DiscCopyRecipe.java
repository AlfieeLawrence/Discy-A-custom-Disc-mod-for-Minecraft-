package net.discy.core.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.discy.core.item.BlankMusicDiscItem;
import net.discy.core.item.CustomDiscItem;

/**
 * Bound custom disc + blank music disc -> copy of the bound disc (original kept).
 */
public class DiscCopyRecipe extends CustomRecipe {

    public DiscCopyRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        return findInputs(inv) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess registries) {
        Inputs inputs = findInputs(inv);
        if (inputs == null) return ItemStack.EMPTY;
        ItemStack copy = inputs.boundDisc.copy();
        copy.setCount(1);
        return copy;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer inv) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof CustomDiscItem
                    && CustomDiscItem.readHash(stack) != null) {
                remaining.set(i, stack.copy());
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    public static final RecipeSerializer<DiscCopyRecipe> SERIALIZER =
            new SimpleCraftingRecipeSerializer<>(DiscCopyRecipe::new);

    private record Inputs(ItemStack boundDisc, int blankCount) {}

    private Inputs findInputs(CraftingContainer inv) {
        ItemStack bound = ItemStack.EMPTY;
        int blanks = 0;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof CustomDiscItem) {
                if (CustomDiscItem.readHash(stack) == null) return null;
                if (!bound.isEmpty()) return null;
                bound = stack;
            } else if (stack.getItem() instanceof BlankMusicDiscItem) {
                blanks++;
            } else {
                return null;
            }
        }

        if (bound.isEmpty() || blanks != 1) return null;
        return new Inputs(bound, blanks);
    }
}
