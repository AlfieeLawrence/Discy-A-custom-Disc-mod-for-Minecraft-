package net.discy.core.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.discy.core.network.DiscyNetworking;

public class SongRenameScreen extends Screen {
    private static final int FIELD_W = 200;
    private static final int MAX_NAME_LEN = 64;

    private final Screen parent;
    private final String songHash;
    private final String currentName;
    private EditBox nameField;

    public SongRenameScreen(Screen parent, String songHash, String currentName) {
        super(Component.literal("Rename song"));
        this.parent = parent;
        this.songHash = songHash;
        this.currentName = currentName;
    }

    public Screen getParentScreen() {
        return parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        nameField = new EditBox(font, cx - FIELD_W / 2, cy - 10, FIELD_W, 20, Component.literal("Song name"));
        nameField.setValue(currentName);
        nameField.setMaxLength(MAX_NAME_LEN);
        nameField.setFocused(true);
        addRenderableWidget(nameField);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(cx - 62, cy + 20, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cx + 4, cy + 20, 60, 20).build());

        setInitialFocus(nameField);
    }

    private void save() {
        String name = nameField.getValue().trim();
        if (!name.isBlank() && !name.equals(currentName)) {
            DiscyNetworking.sendRenameSong(songHash, name);
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nameField.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, "Rename song", width / 2, height / 2 - 32, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
