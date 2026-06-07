package net.discy;

import net.discy.core.network.DiscyNetworking;
import net.discy.integration.sophisticatedcore.SophisticatedCoreIntegration;
import net.discy.core.registry.EntityTypeRegistry;
import net.discy.core.registry.ObjectRegistry;
import net.discy.core.registry.RecipeRegistry;
import net.discy.core.registry.ScreenHandlerTypesRegistry;
import net.discy.core.registry.SoundEventRegistry;
import net.discy.core.registry.TabRegistry;

public class Discy {
    public static final String MOD_ID = "discy";

    public static void init() {
        SoundEventRegistry.init();
        ObjectRegistry.init();
        RecipeRegistry.init();
        TabRegistry.init();
        EntityTypeRegistry.init();
        ScreenHandlerTypesRegistry.init();
        DiscyNetworking.init();
        SophisticatedCoreIntegration.init();
    }

    /** Call after {@link DiscyNetworking#setGameDir} so song scan uses the real instance folder. */
    public static void initLibrary() {
        SongLibraryHolder.scan();
    }

    /** Avoids loading SongLibrary from platform entrypoints before common init. */
    public static final class SongLibraryHolder {
        public static void scan() {
            net.discy.core.library.SongLibrary.get().scanSongsFolder();
        }
    }
}
