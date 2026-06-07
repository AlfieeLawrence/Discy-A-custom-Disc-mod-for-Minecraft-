package net.discy.core.screen.forge;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.discy.core.block.DjDeckBlockEntity;
import net.discy.core.screen.DjDeckMenuData;
import org.jetbrains.annotations.Nullable;

public class DjDeckMenusImpl {
    public static void open(ServerPlayer player, DjDeckBlockEntity deck) {
        MenuRegistry.openExtendedMenu(player, new ExtendedMenuProvider() {
            @Override
            public void saveExtraData(FriendlyByteBuf buf) {
                DjDeckMenuData.writeOpeningData(deck, buf);
            }

            @Override
            public Component getDisplayName() {
                return DjDeckMenuData.title();
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player p) {
                return DjDeckMenuData.createServerMenu(syncId, inv, p, deck);
            }
        });
    }
}
