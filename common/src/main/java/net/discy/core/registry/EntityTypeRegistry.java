package net.discy.core.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.discy.Discy;
import net.discy.core.block.DjDeckBlockEntity;
import net.discy.core.util.DiscyIdentifier;

import java.util.function.Supplier;

public class EntityTypeRegistry {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Discy.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    private static final Registrar<BlockEntityType<?>> BLOCK_ENTITY_REGISTRAR = BLOCK_ENTITY_TYPES.getRegistrar();

    public static final RegistrySupplier<BlockEntityType<DjDeckBlockEntity>> DJ_DECK_BLOCK_ENTITY =
            registerBlockEntity("dj_deck", () -> BlockEntityType.Builder
                    .of(DjDeckBlockEntity::new, ObjectRegistry.DJ_DECK_BLOCK.get())
                    .build(null));

    private static <T extends BlockEntityType<?>> RegistrySupplier<T> registerBlockEntity(
            String path, Supplier<T> type) {
        return BLOCK_ENTITY_REGISTRAR.register(new DiscyIdentifier(path), type);
    }

    public static void init() {
        BLOCK_ENTITY_TYPES.register();
    }
}
