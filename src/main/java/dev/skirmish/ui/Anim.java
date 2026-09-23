package dev.skirmish.ui;

import net.minecraft.util.Util;

/**
 * A value that eases towards its target over a duration from theme.json {@code motion.*}. Time is wall-clock, so
 * animations run at the same speed regardless of frame rate. Easing is CSS {@code ease-out}.
 */
public final class Anim {
    private final String durationKey;
    private float from;
    private float to;
    private long start;
    private boolean initialized;

    public Anim(String durationKey) {
        this.durationKey = durationKey;
    }

    /** Sets a new target; the first call jumps straight to it. */
    public Anim target(float value) {
        if (!initialized) {
            from = to = value;
            initialized = true;
            return this;
        }
        if (value != to) {
            from = value();
            to = value;
            start = Util.getMillis();
        }
        return this;
    }

    public Anim target(boolean on) {
        return target(on ? 1f : 0f);
    }

    public void snap(float value) {
        from = to = value;
        initialized = true;
    }

    public float target() {
        return to;
    }

    public float value() {
        if (!initialized) {
            return 0f;
        }
        float duration = Theme.get().num("motion." + durationKey);
        if (duration <= 0f) {
            return to;
        }
        float t = (Util.getMillis() - start) / duration;
        if (t >= 1f) {
            return to;
        }
        return from + (to - from) * easeOut(Math.max(0f, t));
    }

    public boolean running() {
        return value() != to;
    }

    /** CSS ease-out: cubic-bezier(0, 0, 0.58, 1). */
    public static float easeOut(float t) {
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        // Solve x(s) = t for the bezier parameter s (Newton), then return y(s).
        float s = t;
        for (int i = 0; i < 8; i++) {
            float x = bezier(s, 0f, 0.58f) - t;
            float dx = bezierDerivative(s, 0f, 0.58f);
            if (Math.abs(x) < 1e-5f || dx == 0f) {
                break;
            }
            s = Math.max(0f, Math.min(1f, s - x / dx));
        }
        return bezier(s, 0f, 1f);
    }

    private static float bezier(float s, float p1, float p2) {
        float u = 1f - s;
        return 3f * u * u * s * p1 + 3f * u * s * s * p2 + s * s * s;
    }

    private static float bezierDerivative(float s, float p1, float p2) {
        float u = 1f - s;
        return 3f * u * u * p1 + 6f * u * s * (p2 - p1) + 3f * s * s * (1f - p2);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            r |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return r;
    }
}
