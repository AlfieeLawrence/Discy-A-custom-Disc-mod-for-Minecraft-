package net.discy.core.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.discy.Discy;
import net.discy.core.util.DiscyIdentifier;

public class TabRegistry {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Discy.MOD_ID, Registries.CREATIVE_MODE_TAB);
    public static final Registrar<CreativeModeTab> TAB_REGISTRAR = TABS.getRegistrar();

    public static final RegistrySupplier<CreativeModeTab> DISCY_TAB = TAB_REGISTRAR.register(new DiscyIdentifier("tab"), () ->
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("creativetab.discy.tab"))
                    .icon(() -> new ItemStack(ObjectRegistry.BLANK_MUSIC_DISC.get()))
                    .displayItems((parameters, output) -> {
                        for (var disc : PermanentDiscRegistry.getDiscs()) {
                            output.accept(disc.get());
                        }
                        output.accept(ObjectRegistry.CUSTOM_DISC.get());
                        output.accept(ObjectRegistry.DJ_DECK_ITEM.get());
                        output.accept(ObjectRegistry.BLANK_MUSIC_DISC.get());
                        output.accept(ObjectRegistry.TREE_SAP.get());
                        output.accept(ObjectRegistry.MOLDABLE_SAP.get());
                    })
                    .build()
    );

    public static void init() {
        TABS.register();
    }
}
