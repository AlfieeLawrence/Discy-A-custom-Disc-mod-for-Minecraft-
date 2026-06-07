package net.discy.core.screen;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.discy.core.block.DjDeckBlockEntity;
import net.discy.core.library.SongInfo;
import net.discy.core.library.SongLibrary;

import java.util.ArrayList;
import java.util.List;

/** Shared menu open payload and server menu construction for the DJ deck. */
public final class DjDeckMenuData {
    private DjDeckMenuData() {}

    public static Component title() {
        return Component.translatable("block.discy.dj_deck");
    }

    public static void writeOpeningData(DjDeckBlockEntity deck, FriendlyByteBuf buf) {
        buf.writeBlockPos(deck.getBlockPos());
        List<SongInfo> snapshot = SongLibrary.get().snapshot();
        buf.writeVarInt(snapshot.size());
        for (SongInfo song : snapshot) {
            buf.writeUtf(song.hash());
            buf.writeUtf(song.displayName());
            buf.writeVarInt(song.lengthSeconds());
        }
    }

    public static List<DjDeckMenu.SongRow> readSongRows(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<DjDeckMenu.SongRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new DjDeckMenu.SongRow(buf.readUtf(), buf.readUtf(), buf.readVarInt()));
        }
        return rows;
    }

    public static AbstractContainerMenu createServerMenu(int syncId, Inventory inv, Player player, DjDeckBlockEntity deck) {
        List<DjDeckMenu.SongRow> rows = new ArrayList<>();
        for (SongInfo song : SongLibrary.get().snapshot()) {
            rows.add(new DjDeckMenu.SongRow(song.hash(), song.displayName(), song.lengthSeconds()));
        }
        return new DjDeckMenu(syncId, inv, deck, rows);
    }
}
