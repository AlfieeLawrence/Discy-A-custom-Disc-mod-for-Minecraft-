package net.discy.core.screen.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.discy.core.block.DjDeckBlockEntity;
import net.discy.fabric.client.DjDeckMenuProvider;

public class DjDeckMenusImpl {
    public static void open(ServerPlayer player, DjDeckBlockEntity deck) {
        player.openMenu(new DjDeckMenuProvider(deck));
    }
}
