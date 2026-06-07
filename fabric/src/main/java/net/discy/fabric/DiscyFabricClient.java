package net.discy.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.discy.DiscyClient;

public class DiscyFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DiscyClient.init();
    }
}
