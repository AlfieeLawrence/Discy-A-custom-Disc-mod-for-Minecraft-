package net.discy.mixin;

import dev.architectury.platform.Platform;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class DiscyMixinPlugin implements IMixinConfigPlugin {

    private static final String SOPHISTICATED = "sophisticatedcore";
    private static final String FURNITURE = "furniture";
    private static final String FORGE_LOADING_MOD_LIST = "net.minecraftforge.fml.loading.LoadingModList";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".sophisticatedcore.")) {
            return isModLoadedSafe(SOPHISTICATED);
        }
        if (mixinClassName.contains(".integration.furniture.")
                || mixinClassName.contains(".mixin.furniture.")) {
            return isModLoadedSafe(FURNITURE);
        }
        return true;
    }

    /** Forge mixin bootstrap runs before {@code ModList} exists; use {@code LoadingModList} there. */
    private static boolean isModLoadedSafe(String modId) {
        Boolean forgeResult = queryForgeLoadingModList(modId);
        if (forgeResult != null) {
            return forgeResult;
        }
        try {
            return Platform.isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Boolean queryForgeLoadingModList(String modId) {
        try {
            Class<?> loadingModListClass = Class.forName(FORGE_LOADING_MOD_LIST);
            Object instance = loadingModListClass.getMethod("get").invoke(null);
            if (instance == null) {
                return null;
            }
            @SuppressWarnings("unchecked")
            List<Object> mods = (List<Object>) loadingModListClass.getMethod("getMods").invoke(instance);
            for (Object modInfo : mods) {
                String id = (String) modInfo.getClass().getMethod("getModId").invoke(modInfo);
                if (modId.equals(id)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
