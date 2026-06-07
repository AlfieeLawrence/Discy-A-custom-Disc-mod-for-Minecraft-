package net.discy.core.screen;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.level.ServerPlayer;
import net.discy.core.block.DjDeckBlockEntity;

public final class DjDeckMenus {
    private DjDeckMenus() {}

    @ExpectPlatform
    public static void open(ServerPlayer player, DjDeckBlockEntity deck) {
        throw new AssertionError();
    }
}
