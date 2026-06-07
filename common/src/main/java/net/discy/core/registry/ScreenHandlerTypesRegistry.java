package net.discy.core.registry;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.discy.Discy;
import net.discy.core.screen.DjDeckMenu;
import net.discy.core.util.DiscyIdentifier;

public class ScreenHandlerTypesRegistry {
    public static final DeferredRegister<MenuType<?>> SCREEN_HANDLERS = DeferredRegister.create(Discy.MOD_ID, Registries.MENU);
    public static final Registrar<MenuType<?>> SCREEN_HANDLER_REGISTRAR = SCREEN_HANDLERS.getRegistrar();

    public static final RegistrySupplier<MenuType<DjDeckMenu>> DJ_DECK_MENU =
            SCREEN_HANDLER_REGISTRAR.register(
                    new DiscyIdentifier("dj_deck"),
                    () -> MenuRegistry.ofExtended(DjDeckMenu::new)
            );

    public static void init() {
        SCREEN_HANDLERS.register();
    }
}
