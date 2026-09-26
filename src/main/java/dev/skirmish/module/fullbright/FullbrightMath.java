package dev.skirmish.module.fullbright;

/** Pure math of the fullbright lightmap boost (unit tested). */
public final class FullbrightMath {
    /** Lightmap brightness factor at 100 % strength; vanilla's "Bright" is 1.0. */
    public static final float MAX_GAMMA = 12f;

    private FullbrightMath() {
    }

    /** Brightness factor for a strength in percent: 0 % = vanilla "Bright" (1.0), 100 % = {@link #MAX_GAMMA}. */
    public static float targetGamma(double strengthPercent) {
        double s = Math.max(0, Math.min(100, strengthPercent)) / 100.0;
        return (float) (1.0 + (MAX_GAMMA - 1.0) * s);
    }

    /**
     * The gamma the lightmap should use. {@code darknessBlend} is the Darkness effect's fade-in (0..1, not scaled by
     * the "Darkness Pulsing" option): the boost fades back to the user's own gamma while Darkness is on, so under
     * Darkness the world looks exactly as in vanilla. Never darker than the user's own setting.
     */
    public static float gamma(float vanillaGamma, float target, float darknessBlend) {
        float d = Math.max(0f, Math.min(1f, darknessBlend));
        float boosted = Math.max(vanillaGamma, target);
        return boosted + (vanillaGamma - boosted) * d;
    }
}
