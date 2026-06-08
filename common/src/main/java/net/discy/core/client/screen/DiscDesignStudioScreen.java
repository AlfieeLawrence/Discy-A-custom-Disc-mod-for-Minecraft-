package net.discy.core.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.discy.core.client.DiscPixelCache;
import net.discy.core.client.texture.DiscTexturePixelIO;
import net.discy.core.client.texture.DiskTextureManager;
import net.discy.core.client.upload.TextureUploadManager;
import net.discy.core.library.SongLibrary;
import net.discy.core.network.DiscyNetworking;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public class DiscDesignStudioScreen extends Screen {

    private enum Tool { PAINT, ERASE, FILL, PICK }

    private enum BrushMode { DRAG, PRECISE }

    private static final int MAX_NAME_LEN = 48;
    private static final int MAX_TEXTURE_DIM = 64;
    private static final int MAX_UNDO = 48;
    private static final int MAX_BRUSH_SIZE = 4;
    private static final int LEFT_TOOL_W = 108;
    private static final int RIGHT_PANEL_W = 112;
    private static final int SIDE_MARGIN = 8;
    private static final int COLUMN_GAP = 12;
    private static final int CONTENT_TOP = 22;
    private static final int BOTTOM_BAR_H = 30;
    private static final int BOTTOM_RESERVE = BOTTOM_BAR_H + 10;
    private static final int WHEEL_RADIUS = 34;
    private static final int BRIGHTNESS_H = 10;
    private static final int TOOL_BTN_H = 18;
    private static final int TOOL_ROW_COUNT = 8;
    private static final int LEFT_TOOLS_TOTAL_H = TOOL_ROW_COUNT * TOOL_BTN_H + (TOOL_ROW_COUNT - 1) * 2;
    private static final int CANVAS_LABEL_H = 12;

    private static final int[] QUICK_PALETTE = {
            0xFF000000, 0xFFFFFFFF, 0xFF9E9E9E, 0xFFD32F2F,
            0xFFFF9800, 0xFF388E3C, 0xFF1976D2, 0x00000000
    };

    private int[][] pixels = new int[16][16];
    private int canvasW = 16;
    private int canvasH = 16;
    private int pixelScale = 12;
    private int selectedColor = 0xFF000000;
    private int selectedQuick = 0;
    private float colorHue;
    private float colorSat;
    private float colorVal = 1f;

    private Tool activeTool = Tool.PAINT;
    private BrushMode brushMode = BrushMode.DRAG;
    private int brushSize = 1;

    private boolean painting;
    private boolean erasing;
    private boolean pickingWheel;
    private boolean pickingBrightness;
    private boolean strokeSnapshotTaken;

    private final Deque<int[][]> undoStack = new ArrayDeque<>();
    private final Deque<int[][]> redoStack = new ArrayDeque<>();

    private Button undoButton;
    private Button redoButton;
    private Button brushSizeButton;

    private EditBox nameField;
    private EditBox hexField;
    private int canvasX;
    private int canvasY;
    private int wheelCx;
    private int wheelCy;
    private int brightnessY;
    private int colorPanelX;
    private int colorTitleY;
    private int swatchY;
    private int leftColX;
    private int rightColX;
    private int panelTop;
    private int panelBottom;
    private int leftToolsY;
    private int layoutWheelRadius = WHEEL_RADIUS;
    private boolean syncingHex;
    private boolean sessionStarted;

    private record EditorState(int[][] pixels, int canvasW, int canvasH, String name, String hex,
                               int selectedColor, float colorHue, float colorSat, float colorVal,
                               int selectedQuick, Tool activeTool, BrushMode brushMode, int brushSize) {}

    public DiscDesignStudioScreen() {
        super(Component.translatable("screen.discy.disc_design_studio"));
    }

    @Override
    protected void init() {
        EditorState saved = sessionStarted ? captureState() : null;
        applyLayout();

        int bottomY = height - BOTTOM_BAR_H + 6;
        int nameX = 58;
        nameField = new EditBox(font, nameX, bottomY, Math.min(150, width / 3), 18,
                Component.literal("Texture name"));
        nameField.setMaxLength(MAX_NAME_LEN);
        nameField.setHint(Component.literal("my_disc_art"));
        addRenderableWidget(nameField);

        int saveX = nameX + nameField.getWidth() + 6;
        addRenderableWidget(Button.builder(Component.translatable("gui.discy.design_studio.save"), b -> saveTexture())
                .bounds(saveX, bottomY - 1, 56, 20).build());

        int btnW = LEFT_TOOL_W - 4;
        int halfW = (LEFT_TOOL_W - 6) / 2;
        int ly = leftToolsY;
        addRenderableWidget(Button.builder(Component.translatable("gui.discy.design_studio.new_blank"), b -> loadBlank(true))
                .bounds(leftColX, ly, btnW, TOOL_BTN_H).build());
        ly += TOOL_BTN_H + 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.discy.design_studio.load_file"), b -> loadFromComputer())
                .bounds(leftColX, ly, btnW, TOOL_BTN_H).build());
        ly += TOOL_BTN_H + 2;

        undoButton = addRenderableWidget(Button.builder(Component.translatable("gui.discy.design_studio.undo"), b -> undo())
                .bounds(leftColX, ly, halfW, TOOL_BTN_H).build());
        redoButton = addRenderableWidget(Button.builder(Component.translatable("gui.discy.design_studio.redo"), b -> redo())
                .bounds(leftColX + halfW + 2, ly, halfW, TOOL_BTN_H).build());
        ly += TOOL_BTN_H + 2;

        addRenderableWidget(Button.builder(Component.translatable("gui.discy.design_studio.tool_paint"), b -> setTool(Tool.PAINT))
                .bounds(leftColX, ly, halfW, TOOL_BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.discy.design_studio.tool_erase"), b -> setTool(Tool.ERASE))
                .bounds(leftColX + halfW + 2, ly, halfW, TOOL_BTN_H).build());
        ly += TOOL_BTN_H + 2;

        addRenderableWidget(Button.builder(Component.translatable("gui.discy.design_studio.tool_fill"), b -> setTool(Tool.FILL))
                .bounds(leftColX, ly, halfW, TOOL_BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.discy.design_studio.tool_pick"), b -> setTool(Tool.PICK))
                .bounds(leftColX + halfW + 2, ly, halfW, TOOL_BTN_H).build());
        ly += TOOL_BTN_H + 2;

        addRenderableWidget(Button.builder(Component.literal("-"), b -> changeBrushSize(-1))
                .bounds(leftColX, ly, 18, TOOL_BTN_H).build());
        brushSizeButton = addRenderableWidget(Button.builder(brushSizeLabel(), b -> { })
                .bounds(leftColX + 20, ly, 36, TOOL_BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changeBrushSize(1))
                .bounds(leftColX + 58, ly, 18, TOOL_BTN_H).build());
        ly += TOOL_BTN_H + 2;

        addRenderableWidget(Button.builder(Component.translatable("gui.discy.design_studio.mode_drag"), b -> setBrushMode(BrushMode.DRAG))
                .bounds(leftColX, ly, halfW, TOOL_BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.discy.design_studio.mode_precise"), b -> setBrushMode(BrushMode.PRECISE))
                .bounds(leftColX + halfW + 2, ly, halfW, TOOL_BTN_H).build());

        hexField = new EditBox(font, 0, 0, RIGHT_PANEL_W - 8, 18, Component.literal("Hex"));
        hexField.setMaxLength(7);
        hexField.setHint(Component.literal("#RRGGBB"));
        addRenderableWidget(hexField);

        if (!sessionStarted) {
            loadBlank(false);
            sessionStarted = true;
        } else if (saved != null) {
            restoreState(saved);
        }
        updateHistoryButtons();
        applyLayout();
    }

    private Component brushSizeLabel() {
        return Component.translatable("gui.discy.design_studio.brush_size", brushSize);
    }

    private void setTool(Tool tool) {
        activeTool = tool;
    }

    private void setBrushMode(BrushMode mode) {
        brushMode = mode;
    }

    private void changeBrushSize(int delta) {
        brushSize = Mth.clamp(brushSize + delta, 1, MAX_BRUSH_SIZE);
        if (brushSizeButton != null) {
            brushSizeButton.setMessage(brushSizeLabel());
        }
    }

    private EditorState captureState() {
        String name = nameField != null ? nameField.getValue() : "";
        String hex = hexField != null ? hexField.getValue() : "";
        return new EditorState(copyPixels(pixels), canvasW, canvasH, name, hex,
                selectedColor, colorHue, colorSat, colorVal, selectedQuick,
                activeTool, brushMode, brushSize);
    }

    private void restoreState(EditorState state) {
        pixels = state.pixels();
        canvasW = state.canvasW();
        canvasH = state.canvasH();
        selectedColor = state.selectedColor();
        colorHue = state.colorHue();
        colorSat = state.colorSat();
        colorVal = state.colorVal();
        selectedQuick = state.selectedQuick();
        activeTool = state.activeTool();
        brushMode = state.brushMode();
        brushSize = state.brushSize();
        nameField.setValue(state.name());
        syncingHex = true;
        hexField.setValue(state.hex());
        syncingHex = false;
        if (brushSizeButton != null) {
            brushSizeButton.setMessage(brushSizeLabel());
        }
    }

    private static int[][] copyPixels(int[][] src) {
        int[][] out = new int[src.length][];
        for (int y = 0; y < src.length; y++) {
            out[y] = src[y].clone();
        }
        return out;
    }

    private void pushUndo() {
        undoStack.push(copyPixels(pixels));
        while (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
        redoStack.clear();
        updateHistoryButtons();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(copyPixels(pixels));
        pixels = undoStack.pop();
        updateHistoryButtons();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(copyPixels(pixels));
        pixels = redoStack.pop();
        updateHistoryButtons();
    }

    private void updateHistoryButtons() {
        if (undoButton != null) undoButton.active = !undoStack.isEmpty();
        if (redoButton != null) redoButton.active = !redoStack.isEmpty();
    }

    private void beginStrokeIfNeeded() {
        if (!strokeSnapshotTaken) {
            pushUndo();
            strokeSnapshotTaken = true;
        }
    }

    private void applyLayout() {
        int contentBottom = height - BOTTOM_RESERVE;

        leftColX = SIDE_MARGIN;
        rightColX = width - SIDE_MARGIN - RIGHT_PANEL_W;
        int centerLeft = leftColX + LEFT_TOOL_W + COLUMN_GAP;
        int centerRight = rightColX - COLUMN_GAP;
        int centerW = Math.max(1, centerRight - centerLeft);

        int maxCanvasH = Math.max(1, contentBottom - CONTENT_TOP - CANVAS_LABEL_H);
        int maxCanvasW = centerW;

        pixelScale = Math.min(maxCanvasW / canvasW, maxCanvasH / canvasH);
        pixelScale = Mth.clamp(pixelScale, 4, 16);
        int drawW = canvasW * pixelScale;
        int drawH = canvasH * pixelScale;
        while ((drawW > maxCanvasW || drawH > maxCanvasH) && pixelScale > 4) {
            pixelScale--;
            drawW = canvasW * pixelScale;
            drawH = canvasH * pixelScale;
        }

        canvasX = centerLeft + (centerW - drawW) / 2;
        canvasY = CONTENT_TOP + (maxCanvasH - drawH) / 2;

        panelTop = canvasY;
        panelBottom = canvasY + drawH;
        leftToolsY = panelTop + Math.max(0, (drawH - LEFT_TOOLS_TOTAL_H) / 2);

        layoutColorPanel();
    }

    private void layoutColorPanel() {
        colorPanelX = rightColX;
        wheelCx = rightColX + RIGHT_PANEL_W / 2;
        int panelH = panelBottom - panelTop;

        int fixedH = 14 + 6 + BRIGHTNESS_H + 8 + 11 + 4 + 10 + 18;
        layoutWheelRadius = Math.min(WHEEL_RADIUS, Math.max(18, (panelH - fixedH) / 2));

        int hexY = panelBottom - 18;
        if (hexField != null) {
            hexField.setX(colorPanelX + 4);
            hexField.setY(hexY);
            hexField.setWidth(RIGHT_PANEL_W - 8);
        }

        int hexLabelY = hexY - 10;
        swatchY = hexLabelY - 11 - 4;
        brightnessY = swatchY - BRIGHTNESS_H - 8;
        wheelCy = brightnessY - layoutWheelRadius - 6;
        colorTitleY = wheelCy - layoutWheelRadius - 14;

        if (colorTitleY < panelTop + 2) {
            colorTitleY = panelTop + 2;
            wheelCy = colorTitleY + 14 + layoutWheelRadius;
            brightnessY = wheelCy + layoutWheelRadius + 6;
            swatchY = brightnessY + BRIGHTNESS_H + 8;
            if (swatchY + 11 + 4 > hexLabelY) {
                swatchY = hexLabelY - 11 - 4;
                brightnessY = swatchY - BRIGHTNESS_H - 4;
                wheelCy = brightnessY - layoutWheelRadius - 4;
            }
        }
    }

    private void loadBlank(boolean pushHistory) {
        if (pushHistory) pushUndo();
        try {
            setPixels(DiscTexturePixelIO.loadBlankTemplate());
            if (nameField != null) nameField.setValue("");
        } catch (IOException e) {
            notify(Component.literal("Could not load blank template.").withStyle(ChatFormatting.RED));
        }
    }

    private void loadFromComputer() {
        TextureUploadManager.pickSinglePngAsync(path -> {
            if (path == null) return;
            try {
                int[][] loaded = DiscTexturePixelIO.loadFromPath(path);
                if (loaded.length > MAX_TEXTURE_DIM || loaded[0].length > MAX_TEXTURE_DIM) {
                    notify(Component.literal("Texture is too large (max " + MAX_TEXTURE_DIM + "px).")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                pushUndo();
                setPixels(loaded);
                String stem = path.getFileName().toString().replaceAll("(?i)\\.png$", "");
                nameField.setValue(stem + "_copy");
                applyLayout();
            } catch (IOException e) {
                notify(Component.literal("Could not load PNG.").withStyle(ChatFormatting.RED));
            }
        });
    }

    private void setPixels(int[][] data) {
        pixels = data;
        canvasH = data.length;
        canvasW = data[0].length;
    }

    private void saveTexture() {
        String stem = sanitizeStem(nameField.getValue());
        if (stem.isBlank()) {
            notify(Component.literal("Enter a name for your texture.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        try {
            byte[] png = DiscTexturePixelIO.encodePng(pixels);
            if (png.length > 1024 * 1024) {
                notify(Component.literal("Texture file is too large.").withStyle(ChatFormatting.RED));
                return;
            }
            DiscyNetworking.sendUploadTexture(stem, png);
            DiskTextureManager.saveAndRegister(stem, png);
            DiscPixelCache.evict(stem);
            SongLibrary.get().addTexture(stem);
            notify(Component.literal("Saved texture: " + stem).withStyle(ChatFormatting.GREEN));
        } catch (IOException e) {
            notify(Component.literal("Could not save texture.").withStyle(ChatFormatting.RED));
        }
    }

    private static String sanitizeStem(String raw) {
        String stem = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (stem.length() > MAX_NAME_LEN) stem = stem.substring(0, MAX_NAME_LEN);
        return stem;
    }

    private void notify(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }

    private void setColorFromArgb(int argb) {
        selectedColor = argb;
        int a = (argb >> 24) & 0xFF;
        if (a == 0) {
            selectedQuick = QUICK_PALETTE.length - 1;
            syncHexField();
            return;
        }
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        colorVal = max;
        if (max <= 0.001f) {
            colorHue = 0f;
            colorSat = 0f;
        } else {
            colorSat = (max - min) / max;
            if (colorSat <= 0.001f) {
                colorHue = 0f;
            } else if (max == r) {
                colorHue = ((g - b) / (max - min)) / 6f;
            } else if (max == g) {
                colorHue = (2f + (b - r) / (max - min)) / 6f;
            } else {
                colorHue = (4f + (r - g) / (max - min)) / 6f;
            }
            if (colorHue < 0f) colorHue += 1f;
        }
        selectedQuick = -1;
        syncHexField();
    }

    private void updateSelectedFromHsv() {
        selectedColor = hsvToArgb(colorHue, colorSat, colorVal, 1f);
        selectedQuick = -1;
        syncHexField();
    }

    private void syncHexField() {
        if (hexField == null || syncingHex) return;
        syncingHex = true;
        if (((selectedColor >> 24) & 0xFF) == 0) {
            hexField.setValue("");
        } else {
            hexField.setValue(String.format("#%06X", selectedColor & 0xFFFFFF));
        }
        syncingHex = false;
    }

    private void applyHexField() {
        if (syncingHex || hexField == null) return;
        String raw = hexField.getValue().trim().replace("#", "");
        if (raw.length() != 6) return;
        try {
            int rgb = Integer.parseInt(raw, 16);
            setColorFromArgb(0xFF000000 | rgb);
        } catch (NumberFormatException ignored) {
        }
    }

    private static int hsvToArgb(float h, float s, float v, float a) {
        int alpha = Mth.clamp((int) (a * 255f), 0, 255);
        if (s <= 0f) {
            int gray = Mth.clamp((int) (v * 255f), 0, 255);
            return (alpha << 24) | (gray << 16) | (gray << 8) | gray;
        }
        h = h - (float) Math.floor(h);
        float sector = h * 6f;
        int i = (int) sector;
        float f = sector - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        int ri = Mth.clamp((int) (r * 255f), 0, 255);
        int gi = Mth.clamp((int) (g * 255f), 0, 255);
        int bi = Mth.clamp((int) (b * 255f), 0, 255);
        return (alpha << 24) | (ri << 16) | (gi << 8) | bi;
    }

    private void applyBrush(int cx, int cy, int color) {
        int r = brushSize - 1;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if (x >= 0 && x < canvasW && y >= 0 && y < canvasH) {
                    pixels[y][x] = color;
                }
            }
        }
    }

    private void floodFill(int sx, int sy, int fillColor) {
        int target = pixels[sy][sx];
        if (target == fillColor) return;
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(packCoord(sx, sy));
        while (!queue.isEmpty()) {
            long packed = queue.removeFirst();
            int x = unpackX(packed);
            int y = unpackY(packed);
            if (x < 0 || x >= canvasW || y < 0 || y >= canvasH) continue;
            if (pixels[y][x] != target) continue;
            pixels[y][x] = fillColor;
            queue.add(packCoord(x + 1, y));
            queue.add(packCoord(x - 1, y));
            queue.add(packCoord(x, y + 1));
            queue.add(packCoord(x, y - 1));
        }
    }

    private static long packCoord(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackY(long packed) {
        return (int) packed;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        renderSidePanels(g);
        renderToolHighlights(g);
        renderCanvas(g);
        renderColorPanel(g);
        renderBrushPreview(g, mouseX, mouseY);

        int bottomY = height - BOTTOM_BAR_H + 6;
        g.drawString(font, Component.translatable("gui.discy.design_studio.name_label"),
                8, bottomY + 5, 0xFFE0E0E0, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderSidePanels(GuiGraphics g) {
        g.fill(leftColX - 2, panelTop - 2, leftColX + LEFT_TOOL_W + 2, panelBottom + 2, 0xCC1A1A1A);
        g.fill(colorPanelX - 2, panelTop - 2, colorPanelX + RIGHT_PANEL_W + 2, panelBottom + 2, 0xCC1A1A1A);
    }

    private void renderToolHighlights(GuiGraphics g) {
        int halfW = (LEFT_TOOL_W - 6) / 2;
        int ly = leftToolsY + (TOOL_BTN_H + 2) * 3;
        highlightIf(g, leftColX, ly, halfW, activeTool == Tool.PAINT);
        highlightIf(g, leftColX + halfW + 2, ly, halfW, activeTool == Tool.ERASE);
        ly += TOOL_BTN_H + 2;
        highlightIf(g, leftColX, ly, halfW, activeTool == Tool.FILL);
        highlightIf(g, leftColX + halfW + 2, ly, halfW, activeTool == Tool.PICK);
        ly += (TOOL_BTN_H + 2) * 2;
        highlightIf(g, leftColX, ly, halfW, brushMode == BrushMode.DRAG);
        highlightIf(g, leftColX + halfW + 2, ly, halfW, brushMode == BrushMode.PRECISE);
    }

    private void highlightIf(GuiGraphics g, int x, int y, int w, boolean on) {
        if (on) {
            g.fill(x - 1, y - 1, x + w + 1, y + TOOL_BTN_H + 1, 0xFF6688FF);
        }
    }

    private void renderBrushPreview(GuiGraphics g, int mouseX, int mouseY) {
        if (activeTool != Tool.PAINT && activeTool != Tool.ERASE) return;
        int[] coords = canvasPixelAt(mouseX, mouseY);
        if (coords == null) return;
        int r = brushSize - 1;
        int px = coords[0];
        int py = coords[1];
        int x0 = canvasX + (px - r) * pixelScale;
        int y0 = canvasY + (py - r) * pixelScale;
        int size = (r * 2 + 1) * pixelScale;
        g.fill(x0 - 1, y0 - 1, x0 + size + 1, y0 + size + 1, 0x88FFFFFF);
    }

    private void renderCanvas(GuiGraphics g) {
        int drawW = canvasW * pixelScale;
        int drawH = canvasH * pixelScale;
        g.fill(canvasX - 2, canvasY - 2, canvasX + drawW + 2, canvasY + drawH + 2, 0xFF404040);
        g.fill(canvasX - 1, canvasY - 1, canvasX + drawW + 1, canvasY + drawH + 1, 0xFF202020);

        RenderSystem.enableBlend();
        for (int py = 0; py < canvasH; py++) {
            for (int px = 0; px < canvasW; px++) {
                int argb = pixels[py][px];
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int gch = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int sx = canvasX + px * pixelScale;
                int sy = canvasY + py * pixelScale;
                if (a < 255) {
                    int checker = ((px + py) & 1) == 0 ? 0xFF606060 : 0xFF484848;
                    g.fill(sx, sy, sx + pixelScale, sy + pixelScale, checker);
                }
                if (a > 0) {
                    int color = (0xFF << 24) | (r << 16) | (gch << 8) | b;
                    g.fill(sx, sy, sx + pixelScale, sy + pixelScale, color);
                }
            }
        }
        RenderSystem.disableBlend();

        if (canvasY + drawH + CANVAS_LABEL_H <= height - BOTTOM_RESERVE) {
            g.drawString(font, canvasW + " x " + canvasH, canvasX, canvasY + drawH + 4, 0xFF888888, false);
        }
    }

    private void renderColorPanel(GuiGraphics g) {
        g.drawString(font, Component.translatable("gui.discy.design_studio.colors"),
                colorPanelX + 4, colorTitleY, 0xFFCCCCCC, false);

        renderColorWheel(g);
        renderBrightnessStrip(g);

        int swatch = 11;
        for (int i = 0; i < QUICK_PALETTE.length; i++) {
            int sx = colorPanelX + 4 + i * (swatch + 2);
            boolean sel = i == selectedQuick;
            g.fill(sx - 1, swatchY - 1, sx + swatch + 1, swatchY + swatch + 1, sel ? 0xFFFFFFFF : 0xFF555555);
            int color = QUICK_PALETTE[i];
            if (((color >> 24) & 0xFF) == 0) {
                g.fill(sx, swatchY, sx + swatch, swatchY + swatch, 0xFF505050);
                g.drawString(font, "X", sx + 2, swatchY + 1, 0xFFFF6666, false);
            } else {
                g.fill(sx, swatchY, sx + swatch, swatchY + swatch, color | 0xFF000000);
            }
        }

        int hexLabelY = hexField != null ? hexField.getY() - 10 : swatchY + swatch + 4;
        g.drawString(font, Component.translatable("gui.discy.design_studio.hex_label"),
                colorPanelX + 4, hexLabelY, 0xFFAAAAAA, false);
    }

    private void renderColorWheel(GuiGraphics g) {
        int r = layoutWheelRadius;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r * r) continue;
                float hue = (float) ((Math.atan2(dy, dx) / (Math.PI * 2) + 1.0) % 1.0);
                float sat = (float) Math.sqrt((double) (dx * dx + dy * dy)) / r;
                int color = hsvToArgb(hue, sat, 1f, 1f);
                g.fill(wheelCx + dx, wheelCy + dy, wheelCx + dx + 1, wheelCy + dy + 1, color);
            }
        }
        g.fill(wheelCx - r - 1, wheelCy - r - 1, wheelCx + r + 1, wheelCy - r, 0xFF555555);
        g.fill(wheelCx - r - 1, wheelCy + r, wheelCx + r + 1, wheelCy + r + 1, 0xFF555555);
        g.fill(wheelCx - r - 1, wheelCy - r, wheelCx - r, wheelCy + r, 0xFF555555);
        g.fill(wheelCx + r, wheelCy - r, wheelCx + r + 1, wheelCy + r, 0xFF555555);

        if (colorSat > 0.01f) {
            int mx = wheelCx + (int) (Math.cos(colorHue * Math.PI * 2) * colorSat * r);
            int my = wheelCy + (int) (Math.sin(colorHue * Math.PI * 2) * colorSat * r);
            g.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFFFFFFFF);
            g.fill(mx - 1, my - 1, mx + 2, my + 2, 0xFF000000);
        }
    }

    private void renderBrightnessStrip(GuiGraphics g) {
        int x0 = wheelCx - layoutWheelRadius;
        int x1 = wheelCx + layoutWheelRadius;
        int steps = x1 - x0;
        for (int i = 0; i < steps; i++) {
            float v = i / (float) Math.max(1, steps - 1);
            int color = hsvToArgb(colorHue, colorSat, v, 1f);
            g.fill(x0 + i, brightnessY, x0 + i + 1, brightnessY + BRIGHTNESS_H, color);
        }
        g.fill(x0 - 1, brightnessY - 1, x1 + 1, brightnessY, 0xFF555555);
        g.fill(x0 - 1, brightnessY + BRIGHTNESS_H, x1 + 1, brightnessY + BRIGHTNESS_H + 1, 0xFF555555);
        g.fill(x0 - 1, brightnessY, x0, brightnessY + BRIGHTNESS_H, 0xFF555555);
        g.fill(x1, brightnessY, x1 + 1, brightnessY + BRIGHTNESS_H, 0xFF555555);

        int markerX = x0 + (int) (colorVal * (steps - 1));
        g.fill(markerX - 1, brightnessY - 2, markerX + 2, brightnessY + BRIGHTNESS_H + 2, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (handleColorWheelClick(mouseX, mouseY, button)) return true;
        if (handleBrightnessClick(mouseX, mouseY, button)) return true;
        if (handleQuickPaletteClick(mouseX, mouseY, button)) return true;
        if (handleCanvasClick(mouseX, mouseY, button)) return true;
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        painting = false;
        erasing = false;
        pickingWheel = false;
        pickingBrightness = false;
        strokeSnapshotTaken = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pickingWheel) {
            pickColorFromWheel(mouseX, mouseY);
            return true;
        }
        if (pickingBrightness) {
            pickBrightness(mouseX, mouseY);
            return true;
        }
        if (brushMode == BrushMode.DRAG && (painting || erasing)) {
            handleCanvasDrag(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean handleColorWheelClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        double dx = mouseX - wheelCx;
        double dy = mouseY - wheelCy;
        if (dx * dx + dy * dy > (double) layoutWheelRadius * layoutWheelRadius) return false;
        pickingWheel = true;
        pickColorFromWheel(mouseX, mouseY);
        return true;
    }

    private void pickColorFromWheel(double mouseX, double mouseY) {
        double dx = mouseX - wheelCx;
        double dy = mouseY - wheelCy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > layoutWheelRadius) dist = layoutWheelRadius;
        colorHue = (float) ((Math.atan2(dy, dx) / (Math.PI * 2) + 1.0) % 1.0);
        colorSat = (float) (dist / layoutWheelRadius);
        updateSelectedFromHsv();
    }

    private boolean handleBrightnessClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int x0 = wheelCx - layoutWheelRadius;
        int x1 = wheelCx + layoutWheelRadius;
        if (mouseX < x0 || mouseX > x1 || mouseY < brightnessY || mouseY > brightnessY + BRIGHTNESS_H) {
            return false;
        }
        pickingBrightness = true;
        pickBrightness(mouseX, mouseY);
        return true;
    }

    private void pickBrightness(double mouseX, double mouseY) {
        int x0 = wheelCx - layoutWheelRadius;
        int x1 = wheelCx + layoutWheelRadius;
        colorVal = Mth.clamp((float) ((mouseX - x0) / (x1 - x0)), 0f, 1f);
        updateSelectedFromHsv();
    }

    private boolean handleQuickPaletteClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int swatch = 11;
        for (int i = 0; i < QUICK_PALETTE.length; i++) {
            int sx = colorPanelX + 4 + i * (swatch + 2);
            if (mouseX >= sx && mouseX < sx + swatch && mouseY >= swatchY && mouseY < swatchY + swatch) {
                selectedQuick = i;
                selectedColor = QUICK_PALETTE[i];
                if (((selectedColor >> 24) & 0xFF) != 0) {
                    setColorFromArgb(selectedColor);
                } else {
                    syncHexField();
                }
                return true;
            }
        }
        return false;
    }

    private int[] canvasPixelAt(double mouseX, double mouseY) {
        int drawW = canvasW * pixelScale;
        int drawH = canvasH * pixelScale;
        if (mouseX < canvasX || mouseX >= canvasX + drawW
                || mouseY < canvasY || mouseY >= canvasY + drawH) {
            return null;
        }
        int px = Mth.clamp((int) ((mouseX - canvasX) / pixelScale), 0, canvasW - 1);
        int py = Mth.clamp((int) ((mouseY - canvasY) / pixelScale), 0, canvasH - 1);
        return new int[]{px, py};
    }

    private boolean handleCanvasClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int[] coords = canvasPixelAt(mouseX, mouseY);
        if (coords == null) return false;
        int px = coords[0];
        int py = coords[1];

        return switch (activeTool) {
            case PICK -> {
                setColorFromArgb(pixels[py][px]);
                activeTool = Tool.PAINT;
                yield true;
            }
            case FILL -> {
                pushUndo();
                floodFill(px, py, selectedColor);
                yield true;
            }
            case PAINT -> {
                beginStrokeIfNeeded();
                applyBrush(px, py, selectedColor);
                if (brushMode == BrushMode.DRAG) painting = true;
                yield true;
            }
            case ERASE -> {
                beginStrokeIfNeeded();
                applyBrush(px, py, 0x00000000);
                if (brushMode == BrushMode.DRAG) erasing = true;
                yield true;
            }
        };
    }

    private void handleCanvasDrag(double mouseX, double mouseY) {
        int[] coords = canvasPixelAt(mouseX, mouseY);
        if (coords == null) return;
        int px = coords[0];
        int py = coords[1];
        if (erasing) {
            applyBrush(px, py, 0x00000000);
        } else if (painting) {
            applyBrush(px, py, selectedColor);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField.isFocused() && nameField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (hexField.isFocused() && hexField.keyPressed(keyCode, scanCode, modifiers)) return true;

        boolean ctrl = hasControlDown();
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            if (hasShiftDown()) redo();
            else undo();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nameField.isFocused() && nameField.charTyped(codePoint, modifiers)) return true;
        if (hexField.charTyped(codePoint, modifiers)) {
            applyHexField();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
