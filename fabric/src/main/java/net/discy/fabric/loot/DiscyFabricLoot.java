package net.discy.fabric.loot;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.discy.core.loot.LeafSapLoot;

public final class DiscyFabricLoot {

    private DiscyFabricLoot() {}

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (!source.isBuiltin()) return;
            if (!LeafSapLoot.isLeafTable(id)) return;
            tableBuilder.withPool(LeafSapLoot.sapPool());
        });
    }
}
