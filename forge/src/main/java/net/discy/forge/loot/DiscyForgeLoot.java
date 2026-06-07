package net.discy.forge.loot;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.discy.Discy;

public final class DiscyForgeLoot {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Discy.MOD_ID);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> LEAF_SAP =
            LOOT_MODIFIERS.register("leaf_sap", () -> LeafSapLootModifier.CODEC);

    private DiscyForgeLoot() {}

    public static void register() {
        LOOT_MODIFIERS.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
    }
}
