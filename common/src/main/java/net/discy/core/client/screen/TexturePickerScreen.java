package net.discy.core.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.discy.core.client.texture.DiskTextureManager;
import net.discy.core.client.texture.PermanentDiscTextures;
import net.discy.core.client.upload.UploadManager;
import net.discy.core.network.DiscyNetworking;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TexturePickerScreen extends Screen {
    private static final int CELL = 28;
    private static final int CELL_GAP = 4;
    private static final int CELLS_PER_ROW = 8;
    private static final int MAX_VISIBLE_ROWS = 5;

    private record TextureEntry(int slot, String label, ResourceLocation textureLoc, boolean userTexture) {}

    private final Screen parent;
    private final BlockPos deckPos;
    private final String songHash;
    private final String displayName;
    private final Path pendingUploadFile;

    private final List<TextureEntry> entries = new ArrayList<>();
    private int scrollRow;
    private int selectedSlot;
    private String selectedLabel = "";
    private boolean loadingUserTextures;

    public TexturePickerScreen(Screen parent, String songHash, String songName, BlockPos deckPos) {
        super(Component.literal("Choose texture"));
        this.parent = parent;
        this.deckPos = deckPos;
        this.songHash = songHash;
        this.displayName = songName;
        this.pendingUploadFile = null;
    }

    public TexturePickerScreen(Screen parent, BlockPos deckPos, Path pendingUploadFile) {
        super(Component.literal("Choose texture"));
        this.parent = parent;
        this.deckPos = deckPos;
        this.songHash = null;
        this.displayName = pendingUploadFile.getFileName().toString().replaceAll("(?i)\\.(ogg|mp3)$", "");
        this.pendingUploadFile = pendingUploadFile;
    }

    private boolean isUploadBurn() {
        return pendingUploadFile != null;
    }

    @Override
    protected void init() {
        rebuildPresetEntries();
        loadUserTexturesAsync();

        int cx = width / 2;
        int gridH = MAX_VISIBLE_ROWS * (CELL + CELL_GAP);
        int panelTop = height / 2 - gridH / 2 - 16;
        int btnY = panelTop + gridH + 12;

        addRenderableWidget(Button.builder(Component.literal("Burn"), b -> doConfirm())
                .bounds(cx - 62, btnY, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx + 4, btnY, 60, 20).build());
    }

    private void rebuildPresetEntries() {
        entries.clear();
        for (PermanentDiscTextures.Entry entry : PermanentDiscTextures.presetEntries()) {
            entries.add(new TextureEntry(entry.slot(), entry.label(), entry.texture(), entry.userTexture()));
        }
        if (selectedLabel.isBlank() && !entries.isEmpty()) {
            selectedSlot = entries.get(0).slot();
        }
    }

    private void loadUserTexturesAsync() {
        loadingUserTextures = true;
        DiskTextureManager.scanUserTextureStemsAsync(stems -> {
            for (String stem : stems) {
                entries.add(new TextureEntry(0, stem, DiskTextureManager.rlForStem(stem), true));
            }
            loadingUserTextures = false;
        });
    }

    private void doConfirm() {
        if (isUploadBurn()) {
            if (!selectedLabel.isBlank()) {
                UploadManager.startUpload(pendingUploadFile.toFile(), deckPos, 0, selectedLabel);
            } else {
                UploadManager.startUpload(pendingUploadFile.toFile(), deckPos, selectedSlot, "");
            }
        } else if (!selectedLabel.isBlank()) {
            DiscyNetworking.sendBurnWithLabel(deckPos, songHash, selectedLabel);
        } else {
            DiscyNetworking.sendBurn(deckPos, songHash, selectedSlot);
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        g.drawCenteredString(font, isUploadBurn() ? "Pick texture for new disc" : "Pick texture", width / 2, 16, 0xFFFFFF);
        g.drawCenteredString(font, displayName, width / 2, 28, 0xFFAAAAAA);

        int cellStep = CELL + CELL_GAP;
        int gridW = CELLS_PER_ROW * cellStep - CELL_GAP;
        int gx = width / 2 - gridW / 2;
        int gy = height / 2 - (MAX_VISIBLE_ROWS * cellStep) / 2 + 8;

        g.fill(gx - 6, gy - 6, gx + gridW + 6, gy + MAX_VISIBLE_ROWS * cellStep + 6, 0xCC222222);

        if (loadingUserTextures && entries.size() <= PermanentDiscTextures.presetEntries().size()) {
            g.drawCenteredString(font, "Loading textures...", width / 2, gy + 40, 0xFFAAAAAA);
        }

        int startIdx = scrollRow * CELLS_PER_ROW;
        int endIdx = Math.min(entries.size(), startIdx + MAX_VISIBLE_ROWS * CELLS_PER_ROW);
        for (int i = startIdx; i < endIdx; i++) {
            int rel = i - startIdx;
            int row = rel / CELLS_PER_ROW;
            int col = rel % CELLS_PER_ROW;
            int cx = gx + col * cellStep;
            int cy = gy + row * cellStep;
            TextureEntry e = entries.get(i);
            boolean sel = (!selectedLabel.isBlank() && selectedLabel.equals(e.label()))
                    || (selectedLabel.isBlank() && e.slot() == selectedSlot && !e.userTexture());
            g.fill(cx - 1, cy - 1, cx + CELL + 1, cy + CELL + 1, sel ? 0xFF6688FF : 0xFF444444);
            g.fill(cx, cy, cx + CELL, cy + CELL, 0xFF1A1A1A);
            try {
                g.blit(e.textureLoc(), cx + 6, cy + 6, 0, 0, 16, 16, 16, 16);
            } catch (Throwable ignored) {
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cellStep = CELL + CELL_GAP;
        int gridW = CELLS_PER_ROW * cellStep - CELL_GAP;
        int gx = width / 2 - gridW / 2;
        int gy = height / 2 - (MAX_VISIBLE_ROWS * cellStep) / 2 + 8;
        int startIdx = scrollRow * CELLS_PER_ROW;
        int endIdx = Math.min(entries.size(), startIdx + MAX_VISIBLE_ROWS * CELLS_PER_ROW);
        for (int i = startIdx; i < endIdx; i++) {
            int rel = i - startIdx;
            int row = rel / CELLS_PER_ROW;
            int col = rel % CELLS_PER_ROW;
            int cx = gx + col * cellStep;
            int cy = gy + row * cellStep;
            if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                TextureEntry e = entries.get(i);
                if (e.userTexture()) {
                    selectedLabel = e.label();
                    selectedSlot = 0;
                } else {
                    selectedLabel = "";
                    selectedSlot = e.slot();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxRows = (entries.size() + CELLS_PER_ROW - 1) / CELLS_PER_ROW;
        int maxScroll = Math.max(0, maxRows - MAX_VISIBLE_ROWS);
        scrollRow = Math.max(0, Math.min(maxScroll, scrollRow - (int) delta));
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
