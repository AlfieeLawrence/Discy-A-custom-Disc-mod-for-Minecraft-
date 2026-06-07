package net.discy.core.util;

import net.minecraft.resources.ResourceLocation;
import net.discy.Discy;

public class DiscyIdentifier extends ResourceLocation {
    public DiscyIdentifier(String path) {
        super(Discy.MOD_ID, path);
    }
}
