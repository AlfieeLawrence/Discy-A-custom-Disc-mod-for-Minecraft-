package net.discy.core.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.discy.core.block.DjDeckBlockEntity;
import net.discy.core.item.BlankMusicDiscItem;
import net.discy.core.item.CustomDiscItem;
import net.discy.core.registry.ObjectRegistry;
import net.discy.core.registry.ScreenHandlerTypesRegistry;

import java.util.ArrayList;
import java.util.List;

public class DjDeckMenu extends AbstractContainerMenu {

    public record SongRow(String hash, String displayName, int lengthSeconds) {}

    private final Container deckContainer;
    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final List<SongRow> songRows;

    public DjDeckMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, buf.readBlockPos(), DjDeckMenuData.readSongRows(buf));
    }

    public DjDeckMenu(int containerId, Inventory playerInv, DjDeckBlockEntity deck, List<SongRow> rows) {
        this(containerId, playerInv, deck, deck.getBlockPos(),
                ContainerLevelAccess.create(deck.getLevel(), deck.getBlockPos()), rows);
    }

    private DjDeckMenu(int containerId, Inventory playerInv, BlockPos pos, List<SongRow> rows) {
        this(containerId, playerInv, new SimpleContainer(1), pos, ContainerLevelAccess.NULL, rows);
    }

    private DjDeckMenu(int containerId, Inventory playerInv, Container container,
                       BlockPos pos, ContainerLevelAccess access, List<SongRow> rows) {
        super(ScreenHandlerTypesRegistry.DJ_DECK_MENU.get(), containerId);
        this.deckContainer = container;
        this.access = access;
        this.pos = pos;
        this.songRows = rows == null ? new ArrayList<>() : rows;

        addSlot(new Slot(deckContainer, 0, DjDeckLayout.DISC_SLOT_X, DjDeckLayout.DISC_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BlankMusicDiscItem
                        || stack.getItem() instanceof CustomDiscItem
                        || stack.getItem() instanceof RecordItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18,
                        DjDeckLayout.INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, DjDeckLayout.HOTBAR_Y));
        }
    }

    public BlockPos getDeckPos() {
        return pos;
    }

    public List<SongRow> getSongRows() {
        return songRows;
    }

    public void removeSongRow(String hash) {
        songRows.removeIf(row -> row.hash().equals(hash));
    }

    public void updateSongRow(String hash, String displayName, int lengthSeconds) {
        for (int i = 0; i < songRows.size(); i++) {
            if (songRows.get(i).hash().equals(hash)) {
                songRows.set(i, new SongRow(hash, displayName, lengthSeconds));
                return;
            }
        }
    }

    public ItemStack getDiscStack() {
        return deckContainer.getItem(0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return original;

        ItemStack inSlot = slot.getItem();
        original = inSlot.copy();
        boolean moved;

        if (index == 0) {
            moved = moveItemStackTo(inSlot, 1, 37, true);
        } else if (inSlot.getItem() instanceof BlankMusicDiscItem
                || inSlot.getItem() instanceof CustomDiscItem
                || inSlot.getItem() instanceof RecordItem) {
            moved = moveItemStackTo(inSlot, 0, 1, false);
        } else if (index < 28) {
            moved = moveItemStackTo(inSlot, 28, 37, false);
        } else {
            moved = moveItemStackTo(inSlot, 1, 28, false);
        }

        if (!moved) return ItemStack.EMPTY;
        if (inSlot.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access == ContainerLevelAccess.NULL
                || stillValid(access, player, ObjectRegistry.DJ_DECK_BLOCK.get());
    }
}
