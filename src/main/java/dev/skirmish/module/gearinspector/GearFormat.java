package dev.skirmish.module.gearinspector;

import java.util.Locale;

/** Number and color formatting for the panel. No Minecraft classes, covered by tests. */
public final class GearFormat {
    public static final int GREEN = 0xFF55FF55;
    public static final int YELLOW = 0xFFFFFF55;
    public static final int RED = 0xFFFF5555;
    /** "100%?" when the damage value was not sent and the user chose to assume an undamaged item. */
    public static final int ASSUMED = 0xFF8FBF8F;
    public static final int NO_DATA = 0xFF9A9A9A;
    public static final int NOT_DAMAGEABLE = 0xFFAAAAAA;
    public static final int UNBREAKABLE = 0xFF55FFFF;

    private GearFormat() {
    }

    /**
     * Remaining durability in whole percent, rounded down so a damaged item never reads 100%.
     * Returns 0 for {@code max <= 0}.
     */
    public static int percent(int remaining, int max) {
        if (max <= 0) {
            return 0;
        }
        int clamped = Math.max(0, Math.min(max, remaining));
        return (int) ((long) clamped * 100 / max);
    }

    /**
     * Enchantments counted in the compact card's «+N чар.» badge: the received vanilla ones (only when listed) plus
     * HolyWorld lore enchantments. 0 hides the badge.
     */
    public static int enchantCount(EnchantmentStatus status, int listed, int custom) {
        return (status == EnchantmentStatus.LISTED ? Math.max(0, listed) : 0) + Math.max(0, custom);
    }

    /** "87%", "0%" for a broken item and "<1%" when something is left but less than one percent. */
    public static String percentText(int remaining, int max) {
        int percent = percent(remaining, max);
        if (percent == 0 && remaining > 0 && max > 0) {
            return "<1%";
        }
        return percent + "%";
    }

    /** Red (0) → yellow (0.5) → green (1), ARGB with full alpha. */
    public static int gradient(double fraction) {
        double f = Double.isNaN(fraction) ? 0 : Math.max(0, Math.min(1, fraction));
        if (f >= 0.5) {
            return lerp(YELLOW, GREEN, (f - 0.5) * 2);
        }
        return lerp(RED, YELLOW, f * 2);
    }

    static int lerp(int from, int to, double t) {
        int a = channel(from, to, t, 24);
        int r = channel(from, to, t, 16);
        int g = channel(from, to, t, 8);
        int b = channel(from, to, t, 0);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int channel(int from, int to, double t, int shift) {
        int x = from >>> shift & 0xFF;
        int y = to >>> shift & 0xFF;
        return (int) Math.round(x + (y - x) * t) & 0xFF;
    }

    /** One decimal, dot separator: {@code 7.4}. */
    public static String distance(double meters) {
        return String.format(Locale.ROOT, "%.1f", meters);
    }

    /** Black with the given opacity (0–100 %). */
    public static int background(int opacityPercent) {
        int percent = Math.max(0, Math.min(100, opacityPercent));
        return Math.round(percent * 255 / 100f) << 24;
    }
}
