package net.discy.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.discy.Discy;
import net.discy.DiscyClient;
import net.discy.core.client.render.CustomDiscItemRenderer;
import net.discy.core.registry.ObjectRegistry;

@Mod.EventBusSubscriber(modid = Discy.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DiscyForgeClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(DiscyClient::init);
    }

    @SubscribeEvent
    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(ObjectRegistry.CUSTOM_DISC.get(),
                (graphics, font, stack, x, y) -> CustomDiscItemRenderer.renderGui(graphics, stack, x, y));
    }
}
