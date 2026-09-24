package dev.skirmish.module.zoom;

/** Pure zoom math (unit tested): factor limits, scroll steps, smoothing and the mouse sensitivity scale. */
public final class ZoomMath {
    /** Scroll limits; the menu's starting factor is 2..10. */
    public static final double MIN_FACTOR = 1.5;
    public static final double MAX_FACTOR = 10.0;
    /** One scroll notch multiplies or divides the factor by this. */
    public static final double SCROLL_STEP = 1.25;
    /** Share of the remaining distance covered per tick while smoothing (vanilla's FOV easing uses 0.5 too). */
    public static final double SMOOTHING = 0.5;

    private ZoomMath() {
    }

    public static double clampFactor(double factor) {
        if (Double.isNaN(factor)) {
            return MIN_FACTOR;
        }
        return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, factor));
    }

    /** New factor after {@code notches} wheel notches (positive = scroll up = zoom in). */
    public static double scroll(double factor, double notches) {
        if (notches == 0 || Double.isNaN(notches)) {
            return clampFactor(factor);
        }
        return clampFactor(factor * Math.pow(SCROLL_STEP, Math.signum(notches)));
    }

    /** One tick of easing of the FOV multiplier towards {@code target}; snaps when close. */
    public static double approach(double current, double target) {
        double next = current + (target - current) * SMOOTHING;
        return Math.abs(target - next) < 1e-3 ? target : next;
    }

    public static double lerp(double t, double from, double to) {
        return from + (to - from) * t;
    }

    /**
     * Vanilla turns the camera by {@code (s * 0.6 + 0.2)^3} per mouse count, {@code s} being the sensitivity option.
     * Returns the sensitivity value that makes that product {@code scale} times as large (scale in (0, 1]), so the
     * view turns as far on screen per mouse movement as it does unzoomed.
     */
    public static double scaledSensitivity(double sensitivity, double scale) {
        if (!(scale > 0) || scale >= 1) {
            return sensitivity;
        }
        double base = sensitivity * 0.6 + 0.2;
        return (base * Math.cbrt(scale) - 0.2) / 0.6;
    }
}
