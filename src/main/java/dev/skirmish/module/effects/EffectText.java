package dev.skirmish.module.effects;

import java.util.Locale;

/** Pure formatting for the effect and armor HUDs (unit tested). */
public final class EffectText {
    /** Shown instead of a time for effects without an end (beacons on some servers, /effect infinite). */
    public static final String INFINITE = "∞";

    private EffectText() {
    }

    /** Effect level from the amplifier: 0 → "" (level I is not written, like vanilla), 1 → "II", ... */
    public static String level(int amplifier) {
        if (amplifier <= 0) {
            return "";
        }
        return roman(amplifier + 1);
    }

    /** Roman numeral for 1..3999; plain digits beyond (servers sometimes use amplifier 255). */
    public static String roman(int n) {
        if (n <= 0 || n >= 4000) {
            return Integer.toString(n);
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                n -= values[i];
                out.append(symbols[i]);
            }
        }
        return out.toString();
    }

    /** Remaining time from ticks (20 per second): "m:ss", or "h:mm:ss" from an hour on; negative = infinite. */
    public static String duration(int ticks, boolean infinite) {
        if (infinite || ticks < 0) {
            return INFINITE;
        }
        long seconds = (ticks + 19) / 20;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    /** Durability state for a remaining fraction and the warning threshold (fraction too). */
    public enum Wear {
        GOOD, WORN, CRITICAL
    }

    /** Remaining durability as a fraction of the maximum, 0..1; 1 for items without durability. */
    public static double remaining(int maxDamage, int damage) {
        if (maxDamage <= 0) {
            return 1.0;
        }
        return Math.max(0, Math.min(maxDamage, maxDamage - damage)) / (double) maxDamage;
    }

    /** Below the threshold: critical; below twice the threshold: worn. */
    public static Wear wear(double remaining, double threshold) {
        if (remaining < threshold) {
            return Wear.CRITICAL;
        }
        return remaining < threshold * 2 ? Wear.WORN : Wear.GOOD;
    }

    /**
     * Whole percent, rounded down so an item is never shown as 100 % once it took damage, and never 0 % while it
     * still has a use left.
     */
    public static int percent(int maxDamage, int damage) {
        if (maxDamage <= 0) {
            return 100;
        }
        int left = Math.max(0, Math.min(maxDamage, maxDamage - damage));
        int percent = (int) Math.floor(left * 100.0 / maxDamage);
        return left > 0 ? Math.max(1, percent) : 0;
    }
}
