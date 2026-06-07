package net.discy.core.screen;

/**
 * Single source of truth for DJ deck screen + menu slot positions.
 * Player inventory rows use the same Y offsets as a vanilla 166px container,
 * shifted down by {@link #TOP_EXTENSION}.
 */
public final class DjDeckLayout {
    public static final int PANEL_WIDTH = 176;
    public static final int VANILLA_PANEL_H = 166;
    public static final int PANEL_HEIGHT = 240;
    public static final int TOP_EXTENSION = PANEL_HEIGHT - VANILLA_PANEL_H;

    public static final int DISC_SLOT_X = 26;
    public static final int DISC_SLOT_Y = 28;

    public static final int CONTENT_X = 50;
    public static final int CONTENT_W = 118;

    public static final int TAB_Y = 16;
    public static final int TAB_H = 12;
    public static final int TAB_W = 59;

    public static final int SEARCH_Y = 30;
    public static final int SEARCH_H = 14;

    public static final int LIST_Y = 46;
    public static final int LIST_H = 48;
    public static final int ROW_HEIGHT = 14;

    public static final int BUTTON_Y = 98;
    public static final int BUTTON_H = 18;

    /** Where the vanilla inventory header strip starts on screen. */
    public static final int INV_SECTION_Y = TOP_EXTENSION + 71;

    /** Vanilla player-inventory label row inside a 166px container. */
    public static final int INV_LABEL_Y = TOP_EXTENSION + 72;
    /** Vanilla first inventory row inside a 166px container. */
    public static final int INV_Y = TOP_EXTENSION + 84;
    /** Vanilla hotbar row inside a 166px container. */
    public static final int HOTBAR_Y = TOP_EXTENSION + 142;

    /** Where to blit the inventory section from {@code container/dispenser.png}. */
    public static final int INV_TEXTURE_U = 0;
    public static final int INV_TEXTURE_V = 71;
    public static final int INV_TEXTURE_H = VANILLA_PANEL_H - INV_TEXTURE_V;
    public static final int INV_BG_Y = INV_SECTION_Y;

    private DjDeckLayout() {}
}
