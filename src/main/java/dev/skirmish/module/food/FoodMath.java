package dev.skirmish.module.food;

/**
 * Hunger bar maths for the Food HUD: how much of each of the ten icons (icon 0 is the rightmost, points 1–2) a value
 * covers, and what a food would bring. Pure Java.
 */
final class FoodMath {
    static final int ICONS = 10;
    static final int MAX = 20;

    private FoodMath() {
    }

    /** Points (0, 1 or 2) of {@code value} that fall on icon {@code icon}. */
    static int onIcon(float value, int icon) {
        float v = value - icon * 2;
        if (v >= 2f) {
            return 2;
        }
        return v >= 1f ? 1 : v > 0f ? 1 : 0;
    }

    /** Hunger after eating: capped at 20. */
    static int foodAfter(int food, int nutrition) {
        return Math.min(MAX, food + Math.max(0, nutrition));
    }

    /** Saturation after eating: capped by the new hunger level, like vanilla {@code FoodData.add}. */
    static float saturationAfter(int food, float saturation, int nutrition, float gain) {
        return Math.min(foodAfter(food, nutrition), saturation + Math.max(0f, gain));
    }

    /** Icon {@code icon} gains points when going from {@code before} to {@code after}. */
    static boolean gains(float before, float after, int icon) {
        return onIcon(after, icon) > onIcon(before, icon);
    }

    /** Pulse 0..1 over {@code periodMs} for the preview icons. */
    static float pulse(long now, float periodMs) {
        double phase = (now % (long) periodMs) / periodMs * Math.PI * 2;
        return (float) (0.5 + 0.5 * Math.sin(phase));
    }
}
