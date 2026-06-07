package net.discy.forge.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.discy.core.loot.LeafSapLoot;
import net.discy.core.registry.ObjectRegistry;
import org.jetbrains.annotations.NotNull;

/** Adds tree sap to leaf block loot tables at the same rate as vanilla apples. */
public class LeafSapLootModifier extends LootModifier {

    public static final Codec<LeafSapLootModifier> CODEC = RecordCodecBuilder.create(inst ->
            codecStart(inst).apply(inst, LeafSapLootModifier::new));

    public LeafSapLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                          LootContext context) {
        if (!LeafSapLoot.isLeafTable(context.getQueriedLootTableId())) {
            return generatedLoot;
        }
        if (context.getRandom().nextFloat() < LeafSapLoot.APPLE_CHANCE) {
            generatedLoot.add(new ItemStack(ObjectRegistry.TREE_SAP.get()));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
