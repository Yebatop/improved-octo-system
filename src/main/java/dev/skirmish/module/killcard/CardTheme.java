package dev.skirmish.module.killcard;

import java.awt.Color;

/** Visual themes of the card. Colors are ARGB. */
public enum CardTheme {
    MIDNIGHT(0xFF0E1322, 0xFF1D2640, 0xFFFF4D5E, 0xFFFFB547, 0xFFF2F4F8, 0xFF8C96AD,
            0x14FFFFFF, 0x22FFFFFF, 0x59000000, Pattern.LINES),
    ARCTIC(0xFFF6F9FC, 0xFFD9E4F1, 0xFF2F6FEB, 0xFFE5484D, 0xFF14213D, 0xFF5B6B82,
            0xB3FFFFFF, 0x1F14213D, 0xFFE8EEF6, Pattern.DOTS),
    NEON(0xFF12062B, 0xFF4A0E7A, 0xFF00F5D4, 0xFFF15BB5, 0xFFFFFFFF, 0xFFC9B6E4,
            0x1FFFFFFF, 0x4000F5D4, 0x66000000, Pattern.GRID);

    enum Pattern {
        LINES, DOTS, GRID
    }

    final Color backgroundTop;
    final Color backgroundBottom;
    /** Main accent: victim name, title, bars. */
    final Color accent;
    /** Second accent: warnings (damage unknown) and highlights. */
    final Color accent2;
    final Color text;
    final Color muted;
    final Color panel;
    final Color panelBorder;
    final Color slot;
    final Pattern pattern;

    CardTheme(int backgroundTop, int backgroundBottom, int accent, int accent2, int text, int muted,
              int panel, int panelBorder, int slot, Pattern pattern) {
        this.backgroundTop = argb(backgroundTop);
        this.backgroundBottom = argb(backgroundBottom);
        this.accent = argb(accent);
        this.accent2 = argb(accent2);
        this.text = argb(text);
        this.muted = argb(muted);
        this.panel = argb(panel);
        this.panelBorder = argb(panelBorder);
        this.slot = argb(slot);
        this.pattern = pattern;
    }

    boolean isLight() {
        return this == ARCTIC;
    }

    private static Color argb(int value) {
        return new Color(value, true);
    }
}
