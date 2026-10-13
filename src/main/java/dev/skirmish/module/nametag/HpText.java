package dev.skirmish.module.nametag;

import java.util.Locale;

/** Pure formatting and coloring of the nametag HP line (unit tested). */
public final class HpText {
    public static final String HEART = "❤";

    /** Health points (20 = full) or hearts (10 = full, like the vanilla health bar). */
    public enum Units {
        HP, HEARTS
    }

    private HpText() {
    }

    /**
     * Health with at most one decimal and no trailing ",0": 20 → "20", 17.25 → "17.3", hearts 17.3 → "8.7". A living
     * player never reads 0.
     */
    public static String value(float health, Units units, char decimalSeparator) {
        double v = units == Units.HEARTS ? health / 2.0 : health;
        if (!Double.isFinite(v) || v <= 0) {
            return "0";
        }
        double rounded = Math.round(v * 10.0) / 10.0;
        if (rounded <= 0) {
            rounded = 0.1;
        }
        if (rounded == Math.rint(rounded)) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", rounded).replace('.', decimalSeparator);
    }

    /** Absorption as "+4" (same units and rounding), or "" when there is none. */
    public static String absorption(float absorption, Units units, char decimalSeparator) {
        if (!(absorption > 0.05f)) {
            return "";
        }
        return "+" + value(absorption, units, decimalSeparator);
    }

    /** health / max in [0, 1]; 0 for a broken max. */
    public static float fraction(float health, float maxHealth) {
        if (!(maxHealth > 0f) || !Float.isFinite(health)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, health / maxHealth));
    }

    /** Red at 0, amber at half, green at full, blended in between (ARGB in, ARGB out). */
    public static int color(float fraction, int bad, int warn, int good) {
        float f = Math.max(0f, Math.min(1f, fraction));
        return f < 0.5f ? lerp(bad, warn, f / 0.5f) : lerp(warn, good, (f - 0.5f) / 0.5f);
    }

    static int lerp(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }
}
