package net.discy.core.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.discy.core.registry.ObjectRegistry;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Shared leaf-loot config for tree sap drops (same chance as vanilla apples). */
public final class LeafSapLoot {

    /** Vanilla apple drop chance from oak / dark oak leaves (1.20.1). */
    public static final float APPLE_CHANCE = 0.005F;

    private static final ResourceLocation[] LEAF_TABLES = {
            new ResourceLocation("minecraft", "blocks/oak_leaves"),
            new ResourceLocation("minecraft", "blocks/spruce_leaves"),
            new ResourceLocation("minecraft", "blocks/birch_leaves"),
            new ResourceLocation("minecraft", "blocks/jungle_leaves"),
            new ResourceLocation("minecraft", "blocks/acacia_leaves"),
            new ResourceLocation("minecraft", "blocks/dark_oak_leaves"),
            new ResourceLocation("minecraft", "blocks/mangrove_leaves"),
            new ResourceLocation("minecraft", "blocks/cherry_leaves"),
            new ResourceLocation("minecraft", "blocks/azalea_leaves"),
            new ResourceLocation("minecraft", "blocks/flowering_azalea_leaves"),
    };

    private static final Set<ResourceLocation> LEAF_TABLE_IDS = Stream.of(LEAF_TABLES)
            .collect(Collectors.toUnmodifiableSet());

    private LeafSapLoot() {}

    public static boolean isLeafTable(ResourceLocation id) {
        return LEAF_TABLE_IDS.contains(id);
    }

    public static LootPool.Builder sapPool() {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(ObjectRegistry.TREE_SAP.get()))
                .when(LootItemRandomChanceCondition.randomChance(APPLE_CHANCE));
    }
}
