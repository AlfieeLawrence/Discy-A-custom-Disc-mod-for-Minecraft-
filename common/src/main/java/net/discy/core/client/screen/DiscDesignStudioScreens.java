package net.discy.core.client.screen;

import net.minecraft.client.Minecraft;

public final class DiscDesignStudioScreens {

    private DiscDesignStudioScreens() {}

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new DiscDesignStudioScreen());
        }
    }
}
