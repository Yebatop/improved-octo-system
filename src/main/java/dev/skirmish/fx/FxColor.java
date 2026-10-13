package dev.skirmish.fx;

/** ARGB colour helpers for effects. */
public final class FxColor {
    private FxColor() {
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = b >>> 24;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    public static int alpha(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Hue 0..1 around the wheel at the given saturation and value. */
    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = v - c;
        float r;
        float g;
        float b;
        int sector = (int) (h * 6);
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    /** Mixes a colour towards white by {@code t}. */
    public static int lighten(int argb, float t) {
        return (argb & 0xFF000000) | (lerp(argb, 0xFFFFFFFF, t) & 0xFFFFFF);
    }
}
