package net.discy.core.util;

import dev.architectury.platform.Platform;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public final class DiscyUtil {
    private DiscyUtil() {}

    public static <T extends Block> RegistrySupplier<T> registerWithoutItem(
            DeferredRegister<Block> register,
            Registrar<Block> registrar,
            ResourceLocation path,
            Supplier<T> block) {
        return Platform.isForge() ? register.register(path.getPath(), block) : registrar.register(path, block);
    }
}
