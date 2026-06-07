package net.discy.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.common.Mod;
import net.discy.Discy;
import net.discy.core.network.DiscyNetworking;
import net.discy.core.network.upload.UploadHandler;
import net.discy.forge.loot.DiscyForgeLoot;

@Mod(Discy.MOD_ID)
public class DiscyForge {
    public DiscyForge() {
        EventBuses.registerModEventBus(Discy.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        DiscyNetworking.setGameDir(FMLPaths.GAMEDIR.get());
        Discy.init();
        Discy.initLibrary();
        DiscyForgeLoot.register();
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerJoin);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogout);
    }

    private void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        DiscyNetworking.setGameDir(server.getServerDirectory().toPath());
        Discy.initLibrary();
    }

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DiscyNetworking.distributeAllSongsToClient(player);
            DiscyNetworking.distributeAllTexturesToClient(player);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UploadHandler.dropSessionsFor(event.getEntity().getUUID());
    }
}
