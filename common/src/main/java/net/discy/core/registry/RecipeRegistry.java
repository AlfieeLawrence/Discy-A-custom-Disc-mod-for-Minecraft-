package net.discy.core.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.discy.Discy;
import net.discy.core.recipe.DiscCopyRecipe;
import net.discy.core.util.DiscyIdentifier;

public final class RecipeRegistry {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Discy.MOD_ID, Registries.RECIPE_SERIALIZER);

    public static final RegistrySupplier<RecipeSerializer<DiscCopyRecipe>> DISC_COPY =
            SERIALIZERS.register(new DiscyIdentifier("disc_copy"), () -> DiscCopyRecipe.SERIALIZER);

    private RecipeRegistry() {}

    public static void init() {
        SERIALIZERS.register();
    }
}
