package net.discy.fabric.client;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.discy.core.block.DjDeckBlockEntity;
import net.discy.core.screen.DjDeckMenuData;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric requires {@link ExtendedScreenHandlerFactory} to open an
 * {@link net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType}.
 */
public record DjDeckMenuProvider(DjDeckBlockEntity deck) implements ExtendedScreenHandlerFactory {

    @Override
    public Component getDisplayName() {
        return DjDeckMenuData.title();
    }

    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        DjDeckMenuData.writeOpeningData(deck, buf);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return DjDeckMenuData.createServerMenu(syncId, inv, player, deck);
    }
}
