package net.discy.core.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.discy.core.client.texture.DiskTextureManager;
import net.discy.core.client.texture.PermanentDiscTextures;
import net.discy.core.client.upload.TextureUploadManager;
import net.discy.core.client.upload.UploadManager;
import net.discy.core.screen.DjDeckLayout;
import net.discy.core.screen.DjDeckMenu;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DjDeckScreen extends AbstractContainerScreen<DjDeckMenu> {
    private static final ResourceLocation CONTAINER_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/dispenser.png");
    private static final int NAME_MAX_W = DjDeckLayout.CONTENT_W - 38;
    private static final int TEX_CELL = 18;
    private static final int TEX_PAD = 3;

    private int texGridCols() {
        int innerW = DjDeckLayout.CONTENT_W - TEX_PAD * 2;
        return Math.max(1, (innerW + 2) / (TEX_CELL + 2));
    }

    private int texGridVisibleRows(int panelH) {
        int innerH = panelH - TEX_PAD * 2;
        return Math.max(1, (innerH + 2) / (TEX_CELL + 2));
    }

    private int texGridStepX(int cols) {
        int innerW = DjDeckLayout.CONTENT_W - TEX_PAD * 2;
        return cols <= 1 ? 0 : (innerW - TEX_CELL) / (cols - 1);
    }

    private int texGridStepY(int panelH, int visibleRows) {
        int innerH = panelH - TEX_PAD * 2;
        return visibleRows <= 1 ? 0 : (innerH - TEX_CELL) / (visibleRows - 1);
    }

    private int activeTab;
    private int scrollOffset;
    private int texScrollRow;
    private int hoveredSongIndex = -1;
    private int marqueeTick;
    private Button actionButton;
    private boolean searchFocused;
    private String searchQuery = "";
    private List<DjDeckMenu.SongRow> filteredRows = new ArrayList<>();
    private final List<TexEntry> texEntries = new ArrayList<>();
    private boolean loadingTextures;

    private record TexEntry(String label, net.minecraft.resources.ResourceLocation loc) {}

    public DjDeckScreen(DjDeckMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = DjDeckLayout.PANEL_WIDTH;
        this.imageHeight = DjDeckLayout.PANEL_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = DjDeckLayout.INV_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        rebuildFiltered();
        if (activeTab == 1) loadTexEntriesAsync();

        searchFocused = false;

        actionButton = addRenderableWidget(Button.builder(Component.literal("Upload & burn..."), b -> onActionButton())
                .bounds(leftPos + DjDeckLayout.CONTENT_X, topPos + DjDeckLayout.BUTTON_Y,
                        DjDeckLayout.CONTENT_W, DjDeckLayout.BUTTON_H).build());
        actionButton.active = !UploadManager.isUploading();
        updateActionButton();
    }

    private void updateActionButton() {
        if (actionButton == null) return;
        if (activeTab == 0) {
            actionButton.setMessage(Component.literal("Upload & burn..."));
            actionButton.active = !UploadManager.isUploading();
        } else {
            actionButton.setMessage(Component.literal("Upload many PNGs"));
            actionButton.active = !TextureUploadManager.isUploading();
        }
        actionButton.visible = true;
    }

    private void onActionButton() {
        if (activeTab == 0) {
            startUploadBurnFlow();
        } else {
            TextureUploadManager.pickAndUploadMultiple(this::loadTexEntriesAsync, msg -> { });
        }
    }

    private void setTab(int tab) {
        activeTab = tab;
        if (tab != 0) {
            searchFocused = false;
            searchQuery = "";
        }
        scrollOffset = 0;
        texScrollRow = 0;
        hoveredSongIndex = -1;
        marqueeTick = 0;
        if (tab == 1) loadTexEntriesAsync();
        rebuildFiltered();
        updateActionButton();
    }

    private void rebuildFiltered() {
        List<DjDeckMenu.SongRow> all = menu.getSongRows();
        if (searchQuery.isBlank()) {
            filteredRows = new ArrayList<>(all);
            return;
        }
        String q = searchQuery.toLowerCase(Locale.ROOT);
        filteredRows = new ArrayList<>();
        for (DjDeckMenu.SongRow r : all) {
            if (r.displayName().toLowerCase(Locale.ROOT).contains(q)) {
                filteredRows.add(r);
            }
        }
    }

    private void loadTexEntriesAsync() {
        loadingTextures = true;
        texEntries.clear();
        for (PermanentDiscTextures.Entry entry : PermanentDiscTextures.presetEntries()) {
            texEntries.add(new TexEntry(entry.label(), entry.texture()));
        }
        DiskTextureManager.scanUserTextureStemsAsync(stems -> {
            for (String stem : stems) {
                texEntries.add(new TexEntry(stem, DiskTextureManager.rlForStem(stem)));
            }
            loadingTextures = false;
        });
    }

    private void startUploadBurnFlow() {
        if (menu.getDiscStack().isEmpty()) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("Insert a blank disc in the slot first.")
                                .withStyle(ChatFormatting.YELLOW), false);
            }
            return;
        }
        UploadManager.openFileChooserForBurn(menu.getDeckPos(), this);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateActionButton();
        if (hoveredSongIndex >= 0 && hoveredSongIndex < filteredRows.size()) {
            String name = filteredRows.get(hoveredSongIndex).displayName();
            if (font.width(name) > NAME_MAX_W) {
                marqueeTick++;
            } else {
                marqueeTick = 0;
            }
        } else {
            marqueeTick = 0;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        RenderSystem.disableDepthTest();
        this.renderBg(g, partialTick, mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate((float) this.leftPos, (float) this.topPos, 0.0F);
        this.renderLabels(g, mouseX, mouseY);
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) {
                this.renderSlot(g, slot);
            }
        }
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
        RenderSystem.enableDepthTest();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        renderFullPanel(g);
        renderDiscSlotBackground(g);
        renderTabs(g);
        if (activeTab == 0) {
            renderSongPanelBg(g);
        } else {
            renderTexturePanel(g);
        }
        renderUploadProgress(g);
    }

    /**
     * Vanilla-style frame for the full panel, a clean deck interior (no dispenser 3x3 art),
     * and only the player-inventory slot strip from the container texture.
     */
    private void renderFullPanel(GuiGraphics g) {
        int x = leftPos;
        int y = topPos;
        int w = imageWidth;
        int invTop = y + DjDeckLayout.INV_SECTION_Y;
        int deckInnerBottom = invTop;

        g.blit(CONTAINER_TEXTURE, x, y, 0, 0, w, 4, 256, 256);
        if (deckInnerBottom > y + 4) {
            g.blit(CONTAINER_TEXTURE, x, y + 4, 0, 4, 4, deckInnerBottom - y - 4, 256, 256);
            g.blit(CONTAINER_TEXTURE, x + w - 4, y + 4, 172, 4, 4, deckInnerBottom - y - 4, 256, 256);
            g.fill(x + 4, y + 4, x + w - 4, deckInnerBottom, 0xFFC6C6C6);
        }

        g.blit(CONTAINER_TEXTURE, x, invTop,
                DjDeckLayout.INV_TEXTURE_U, DjDeckLayout.INV_TEXTURE_V,
                w, DjDeckLayout.INV_TEXTURE_H, 256, 256);
    }

    private void renderDiscSlotBackground(GuiGraphics g) {
        blitSlot(g, DjDeckLayout.DISC_SLOT_X, DjDeckLayout.DISC_SLOT_Y);
    }

    private void blitSlot(GuiGraphics g, int slotX, int slotY) {
        blitSlotAt(g, leftPos + slotX - 1, topPos + slotY - 1);
    }

    private void blitSlotAt(GuiGraphics g, int x, int y) {
        g.blit(CONTAINER_TEXTURE, x, y, 7, 83, 18, 18, 256, 256);
    }

    private void renderTabs(GuiGraphics g) {
        int tabX = leftPos + DjDeckLayout.CONTENT_X;
        int tabY = topPos + DjDeckLayout.TAB_Y;
        String[] labels = { "Songs", "Textures" };
        for (int t = 0; t < 2; t++) {
            int tx = tabX + t * DjDeckLayout.TAB_W;
            int tw = DjDeckLayout.TAB_W - (t == 1 ? 0 : 1);
            int bg = t == activeTab ? 0xFFC6C6C6 : 0xFF8B8B8B;
            g.fill(tx, tabY, tx + tw, tabY + DjDeckLayout.TAB_H, bg);
            g.fill(tx, tabY, tx + tw, tabY + 1, 0xFFFFFFFF);
            g.fill(tx, tabY + DjDeckLayout.TAB_H - 1, tx + tw, tabY + DjDeckLayout.TAB_H, 0xFF555555);
            int color = t == activeTab ? 0x404040 : 0x666666;
            String label = labels[t];
            g.drawString(font, label, tx + (tw - font.width(label)) / 2, tabY + 2, color, false);
        }
    }

    private void renderInsetPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF8B8B8B);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFC6C6C6);
    }

    private void renderSongPanelBg(GuiGraphics g) {
        int sx = leftPos + DjDeckLayout.CONTENT_X;
        renderInsetPanel(g, sx, topPos + DjDeckLayout.SEARCH_Y,
                DjDeckLayout.CONTENT_W, DjDeckLayout.SEARCH_H);
        renderInsetPanel(g, sx, topPos + DjDeckLayout.LIST_Y,
                DjDeckLayout.CONTENT_W, DjDeckLayout.LIST_H);
    }

    private void renderSearchField(GuiGraphics g) {
        if (activeTab != 0) return;

        int sx = DjDeckLayout.CONTENT_X + 3;
        int sy = DjDeckLayout.SEARCH_Y + 3;
        boolean empty = searchQuery.isEmpty();
        String shown = empty && !searchFocused ? "Search songs..." : searchQuery;
        int color = empty && !searchFocused ? 0xFF707070 : 0xFF404040;
        g.drawString(font, shown, sx, sy, color, false);
        if (searchFocused && (System.currentTimeMillis() / 300L) % 2L == 0L) {
            g.drawString(font, "_", sx + font.width(searchQuery), sy, 0xFF404040, false);
        }
    }

    private void renderSongListText(GuiGraphics g, int mouseX, int mouseY) {
        if (activeTab != 0) return;

        int gx = DjDeckLayout.CONTENT_X;
        int gy = DjDeckLayout.LIST_Y;
        int gh = DjDeckLayout.LIST_H;
        int absGx = leftPos + gx;
        int absGy = topPos + gy;
        hoveredSongIndex = -1;

        if (filteredRows.isEmpty()) {
            String msg = searchQuery.isBlank() ? "No songs yet" : "No matches";
            g.drawCenteredString(font, msg, gx + DjDeckLayout.CONTENT_W / 2, gy + gh / 2 - 8, 0xFF666666);
            g.drawCenteredString(font, "Upload & burn to add", gx + DjDeckLayout.CONTENT_W / 2, gy + gh / 2 + 4, 0xFF888888);
            return;
        }

        int visibleRows = gh / DjDeckLayout.ROW_HEIGHT;
        int maxScroll = Math.max(0, filteredRows.size() - visibleRows);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredRows.size()) break;
            DjDeckMenu.SongRow row = filteredRows.get(idx);
            int ry = gy + i * DjDeckLayout.ROW_HEIGHT;
            int absRy = topPos + ry;
            boolean hovered = mouseX >= absGx && mouseX < absGx + DjDeckLayout.CONTENT_W
                    && mouseY >= absRy && mouseY < absRy + DjDeckLayout.ROW_HEIGHT;
            if (hovered) {
                hoveredSongIndex = idx;
                g.fill(gx + 2, ry + 1, gx + DjDeckLayout.CONTENT_W - 2, ry + DjDeckLayout.ROW_HEIGHT - 1, 0xFFAAAAAA);
            }
            String name = row.displayName();
            int textX = gx + 4;
            int textY = ry + 3;
            if (hovered && font.width(name) > NAME_MAX_W) {
                int scroll = (marqueeTick / 2) % (font.width(name) + 24);
                g.enableScissor(absGx + 4, absRy + 1, absGx + 4 + NAME_MAX_W, absRy + DjDeckLayout.ROW_HEIGHT - 1);
                g.drawString(font, name, textX - scroll, textY, 0xFF404040, false);
                g.disableScissor();
            } else if (font.width(name) > NAME_MAX_W) {
                g.drawString(font, trimToWidth(name, NAME_MAX_W), textX, textY, 0xFF404040, false);
            } else {
                g.drawString(font, name, textX, textY, 0xFF404040, false);
            }
            String dur = formatDuration(row.lengthSeconds());
            g.drawString(font, dur, gx + DjDeckLayout.CONTENT_W - font.width(dur) - 4, textY, 0xFF555555, false);
        }
    }

    private void renderTexturePanel(GuiGraphics g) {
        int gx = leftPos + DjDeckLayout.CONTENT_X;
        int gy = topPos + DjDeckLayout.SEARCH_Y;
        int gh = DjDeckLayout.BUTTON_Y - DjDeckLayout.SEARCH_Y - 2;
        renderInsetPanel(g, gx, gy, DjDeckLayout.CONTENT_W, gh);

        if (loadingTextures && texEntries.size() <= PermanentDiscTextures.presetEntries().size()) {
            g.drawCenteredString(font, "Loading...", gx + DjDeckLayout.CONTENT_W / 2, gy + gh / 2 - 4, 0xFF666666);
            return;
        }

        int cols = texGridCols();
        int visibleRows = texGridVisibleRows(gh);
        int stepX = texGridStepX(cols);
        int stepY = texGridStepY(gh, visibleRows);
        int totalRows = (texEntries.size() + cols - 1) / cols;
        int maxScroll = Math.max(0, totalRows - visibleRows);
        texScrollRow = Math.min(texScrollRow, maxScroll);
        int start = texScrollRow * cols;
        int end = Math.min(texEntries.size(), start + visibleRows * cols);
        for (int i = start; i < end; i++) {
            int rel = i - start;
            int row = rel / cols;
            int col = rel % cols;
            int cx = gx + TEX_PAD + col * stepX;
            int cy = gy + TEX_PAD + row * stepY;
            blitSlotAt(g, cx, cy);
            try {
                g.blit(texEntries.get(i).loc(), cx + 1, cy + 1, 0, 0, 16, 16, 16, 16);
            } catch (Throwable ignored) {
            }
        }
    }

    private void renderUploadProgress(GuiGraphics g) {
        if (!UploadManager.isUploading()) return;
        int gx = leftPos + DjDeckLayout.CONTENT_X;
        int gy = topPos + DjDeckLayout.LIST_Y;
        g.fill(gx, gy, gx + DjDeckLayout.CONTENT_W, gy + DjDeckLayout.LIST_H, 0xCC000000);
        g.drawCenteredString(font, UploadManager.getStatus(),
                gx + DjDeckLayout.CONTENT_W / 2, gy + DjDeckLayout.LIST_H / 2 - 4, 0xFFFFFF);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String s = text;
        while (s.length() > 3 && font.width(s + "...") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }

    private static String formatDuration(int totalSeconds) {
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        g.drawString(font, "Disc", DjDeckLayout.DISC_SLOT_X, 18, 0x404040, false);
        renderSearchField(g);
        renderSongListText(g, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty()) {
            Slot slot = this.hoveredSlot;
            if (slot == null) {
                for (Slot candidate : this.menu.slots) {
                    if (candidate.isActive() && candidate.hasItem()
                            && this.isHovering(candidate.x, candidate.y, 16, 16, mouseX, mouseY)) {
                        slot = candidate;
                        break;
                    }
                }
            }
            if (slot != null && slot.hasItem()) {
                ItemStack stack = slot.getItem();
                g.renderTooltip(this.font, this.getTooltipFromContainerItem(stack), stack.getTooltipImage(), mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeTab == 0 && searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                scrollOffset = 0;
                rebuildFiltered();
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activeTab == 0 && searchFocused) {
            if (Character.isISOControl(codePoint)) return true;
            if (searchQuery.length() >= 64) return true;
            searchQuery += codePoint;
            scrollOffset = 0;
            rebuildFiltered();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleTabClick(mouseX, mouseY)) return true;

        if (activeTab == 0) {
            int sx = leftPos + DjDeckLayout.CONTENT_X;
            int sy = topPos + DjDeckLayout.SEARCH_Y;
            if (button == 0 && mouseX >= sx && mouseX < sx + DjDeckLayout.CONTENT_W
                    && mouseY >= sy && mouseY < sy + DjDeckLayout.SEARCH_H) {
                searchFocused = true;
                return true;
            }
            searchFocused = false;

            int gx = sx;
            int gy = topPos + DjDeckLayout.LIST_Y;
            if (button == 0 && mouseX >= gx && mouseX < gx + DjDeckLayout.CONTENT_W
                    && mouseY >= gy && mouseY < gy + DjDeckLayout.LIST_H) {
                int row = (int) ((mouseY - gy) / DjDeckLayout.ROW_HEIGHT);
                int idx = scrollOffset + row;
                if (idx >= 0 && idx < filteredRows.size()) {
                    if (menu.getDiscStack().isEmpty()) {
                        if (minecraft.player != null) {
                            minecraft.player.displayClientMessage(
                                    Component.literal("Insert a blank disc in the slot first.")
                                            .withStyle(ChatFormatting.YELLOW), false);
                        }
                        return true;
                    }
                    DjDeckMenu.SongRow song = filteredRows.get(idx);
                    minecraft.setScreen(new TexturePickerScreen(this, song.hash(), song.displayName(), menu.getDeckPos()));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        int tabX = leftPos + DjDeckLayout.CONTENT_X;
        int tabY = topPos + DjDeckLayout.TAB_Y;
        for (int t = 0; t < 2; t++) {
            int tx = tabX + t * DjDeckLayout.TAB_W;
            if (mouseX >= tx && mouseX < tx + DjDeckLayout.TAB_W
                    && mouseY >= tabY && mouseY < tabY + DjDeckLayout.TAB_H) {
                setTab(t);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int gx = leftPos + DjDeckLayout.CONTENT_X;
        if (activeTab == 0) {
            int gy = topPos + DjDeckLayout.LIST_Y;
            if (mouseX >= gx && mouseX < gx + DjDeckLayout.CONTENT_W
                    && mouseY >= gy && mouseY < gy + DjDeckLayout.LIST_H) {
                int maxScroll = Math.max(0, filteredRows.size() - DjDeckLayout.LIST_H / DjDeckLayout.ROW_HEIGHT);
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) delta));
                return true;
            }
        } else {
            int gy = topPos + DjDeckLayout.SEARCH_Y;
            int gh = DjDeckLayout.BUTTON_Y - DjDeckLayout.SEARCH_Y - 2;
            if (mouseX >= gx && mouseX < gx + DjDeckLayout.CONTENT_W
                    && mouseY >= gy && mouseY < gy + gh) {
                int cols = texGridCols();
                int visibleRows = texGridVisibleRows(gh);
                int totalRows = (texEntries.size() + cols - 1) / cols;
                int maxScroll = Math.max(0, totalRows - visibleRows);
                texScrollRow = Math.max(0, Math.min(maxScroll, texScrollRow - (int) delta));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
