package net.discy.core.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.discy.Discy;
import net.discy.core.block.DiscDesignStudioBlock;
import net.discy.core.block.DjDeckBlock;
import net.discy.core.item.BlankMusicDiscItem;
import net.discy.core.item.CustomDiscItem;
import net.discy.core.util.DiscyIdentifier;

import java.util.function.Supplier;

public class ObjectRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Discy.MOD_ID, Registries.ITEM);
    public static final Registrar<Item> ITEM_REGISTRAR = ITEMS.getRegistrar();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Discy.MOD_ID, Registries.BLOCK);
    public static final Registrar<Block> BLOCK_REGISTRAR = BLOCKS.getRegistrar();

    public static final RegistrySupplier<Item> CUSTOM_DISC = registerItem("custom_disc",
            () -> new CustomDiscItem(getSettings().stacksTo(1), 1, () -> SoundEventRegistry.CUSTOM_DISC_SOUND.get(), 180));

    public static final RegistrySupplier<Block> DJ_DECK_BLOCK = registerBlock("dj_deck",
            () -> new DjDeckBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0f)
                    .sound(net.minecraft.world.level.block.SoundType.METAL)));
    public static final RegistrySupplier<Item> DJ_DECK_ITEM = registerItem("dj_deck",
            () -> new BlockItem(DJ_DECK_BLOCK.get(), new Item.Properties()));

    public static final RegistrySupplier<Block> DISC_DESIGN_STUDIO_BLOCK = registerBlock("disc_design_studio",
            () -> new DiscDesignStudioBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.5f)
                    .sound(net.minecraft.world.level.block.SoundType.WOOD)));
    public static final RegistrySupplier<Item> DISC_DESIGN_STUDIO_ITEM = registerItem("disc_design_studio",
            () -> new BlockItem(DISC_DESIGN_STUDIO_BLOCK.get(), new Item.Properties()));

    public static final RegistrySupplier<Item> BLANK_MUSIC_DISC = registerItem("blank_music_disc",
            () -> new BlankMusicDiscItem(new Item.Properties().stacksTo(16)));

    public static final RegistrySupplier<Item> TREE_SAP = registerItem("tree_sap",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistrySupplier<Item> MOLDABLE_SAP = registerItem("moldable_sap",
            () -> new Item(new Item.Properties().stacksTo(64)));

    static Item.Properties getSettings() {
        return new Item.Properties();
    }

    public static void init() {
        PermanentDiscRegistry.loadAndRegister();
        ITEMS.register();
        BLOCKS.register();
    }

    public static <T extends Block> RegistrySupplier<T> registerBlock(String name, Supplier<T> block) {
        return net.discy.core.util.DiscyUtil.registerWithoutItem(BLOCKS, BLOCK_REGISTRAR, new DiscyIdentifier(name), block);
    }

    public static <T extends Item> RegistrySupplier<T> registerItem(String path, Supplier<T> itemSupplier) {
        ResourceLocation id = new DiscyIdentifier(path);
        return ITEM_REGISTRAR.register(id, itemSupplier);
    }
}
