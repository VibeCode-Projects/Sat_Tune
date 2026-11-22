package com.sattune.util;

import java.awt.*;

/**
 * Central design token registry.  All UI code references constants from here.
 */
public final class Theme {

    private Theme() {}

    // ── Colors ────────────────────────────────────────────────────────────────

    public static final Color BG_MAIN      = hex("#090909");
    public static final Color BG_SIDEBAR   = hex("#0D0D0D");
    public static final Color BG_SECONDARY = hex("#141414");
    public static final Color BG_HOVER     = hex("#1B1B1B");
    public static final Color BG_SELECTED  = hex("#202020");
    public static final Color BORDER       = hex("#252525");
    public static final Color TEXT_PRIMARY = hex("#F5F5F5");
    public static final Color TEXT_SECONDARY= hex("#888888");
    public static final Color TEXT_MUTED   = hex("#555555");
    public static final Color ACCENT       = hex("#A78BFA");
    public static final Color ACCENT_DIM   = hex("#7C5FCC");
    public static final Color DANGER       = hex("#EF4444");
    public static final Color SUCCESS      = hex("#22C55E");

    // Playback bar
    public static final Color BAR_BG       = hex("#0A0A0A");

    // ── Dimensions ────────────────────────────────────────────────────────────

    public static final int SIDEBAR_WIDTH   = 200;
    public static final int PLAYBAR_HEIGHT  = 76;
    public static final int ARTWORK_SIZE_SM = 40;
    public static final int ARTWORK_SIZE_MD = 48;
    public static final int ARC             = 6;   // border radius

    // ── Fonts ─────────────────────────────────────────────────────────────────

    public static final Font FONT_TITLE   = new Font("Inter", Font.PLAIN, 13);
    public static final Font FONT_LABEL   = new Font("Inter", Font.PLAIN, 12);
    public static final Font FONT_SMALL   = new Font("Inter", Font.PLAIN, 11);
    public static final Font FONT_HEADING = new Font("Inter", Font.PLAIN, 14);
    public static final Font FONT_LOGO    = new Font("Inter", Font.BOLD,  15);

    public static Font resolveFont(String name, int style, int size) {
        Font f = new Font(name, style, size);
        if (f.getFamily().equals(name)) return f;
        // Fallback to system sans-serif
        return new Font(Font.SANS_SERIF, style, size);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Color hex(String h) {
        return Color.decode(h);
    }

    /** Lighten/darken a color by a factor 0–1 (positive = lighter). */
    public static Color adjust(Color c, float factor) {
        int r = Math.min(255, Math.max(0, (int)(c.getRed()   + 255 * factor)));
        int g = Math.min(255, Math.max(0, (int)(c.getGreen() + 255 * factor)));
        int b = Math.min(255, Math.max(0, (int)(c.getBlue()  + 255 * factor)));
        return new Color(r, g, b);
    }

    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
