package net.discy.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.discy.Discy;
import net.discy.core.network.DiscyNetworking;
import net.discy.fabric.loot.DiscyFabricLoot;
import net.discy.integration.sophisticatedcore.SophisticatedCoreIntegration;

public class DiscyFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        DiscyNetworking.setGameDir(FabricLoader.getInstance().getGameDir());
        Discy.init();
        Discy.initLibrary();
        DiscyFabricLoot.register();
        SophisticatedCoreIntegration.init();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SophisticatedCoreIntegration.init();
            DiscyNetworking.setGameDir(server.getServerDirectory().toPath());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            DiscyNetworking.distributeAllSongsToClient(player);
            DiscyNetworking.distributeAllTexturesToClient(player);
        });
    }
}
