package dev.skirmish.ui;

/**
 * The mockup's line icons (24×24 SVG view boxes, round caps and joins) drawn with SDF capsules. {@code size} is
 * the rendered size in design px; stroke widths are in view box units like the SVG sources.
 */
public final class Icons {
    private Icons() {
    }

    private static void path(Ui ui, float x, float y, float size, float stroke, int color, float... points) {
        float s = size / 24f;
        float[] scaled = new float[points.length];
        for (int i = 0; i < points.length; i += 2) {
            scaled[i] = x + points[i] * s;
            scaled[i + 1] = y + points[i + 1] * s;
        }
        ui.polyline(stroke * s, color, scaled);
    }

    /** Sword (logo): {@code M14.5 17.5L3 6V3h3l11.5 11.5 M13 19l6-6}; {@code hilt} adds the HUD variant's grip. */
    public static void sword(Ui ui, float x, float y, float size, float stroke, int color, boolean hilt) {
        path(ui, x, y, size, stroke, color, 14.5f, 17.5f, 3f, 6f, 3f, 3f, 6f, 3f, 17.5f, 14.5f);
        path(ui, x, y, size, stroke, color, 13f, 19f, 19f, 13f);
        if (hilt) {
            path(ui, x, y, size, stroke, color, 16f, 16f, 20f, 20f);
            path(ui, x, y, size, stroke, color, 19f, 21f, 21f, 19f);
        }
    }

    /** Clock: circle r9 and hands {@code M12 7v5l3 2}. */
    public static void clock(Ui ui, float x, float y, float size, float stroke, int color) {
        float s = size / 24f;
        ui.ring(x + 12f * s, y + 12f * s, (18f + stroke) * s, stroke * s, color);
        path(ui, x, y, size, stroke, color, 12f, 7f, 12f, 12f, 15f, 14f);
    }

    /** Navigation arrow {@code M12 3l6 16-6-4-6 4z} rotated by {@code degrees} around the icon center. */
    public static void navArrow(Ui ui, float x, float y, float size, float stroke, int color, float degrees) {
        var pose = ui.graphics().pose();
        pose.pushMatrix();
        pose.translate(x + size / 2f, y + size / 2f);
        pose.rotate((float) Math.toRadians(degrees));
        path(ui, -size / 2f, -size / 2f, size, stroke, color, 12f, 3f, 18f, 19f, 12f, 15f, 6f, 19f, 12f, 3f);
        pose.popMatrix();
    }

    /** Pause: two 4×14 bars with 1 px radius (filled). */
    public static void pause(Ui ui, float x, float y, float size, int color) {
        float s = size / 24f;
        ui.rect(x + 6f * s, y + 5f * s, 4f * s, 14f * s, s, color);
        ui.rect(x + 14f * s, y + 5f * s, 4f * s, 14f * s, s, color);
    }

    /** Play: filled triangle matching the pause glyph's box (not in the mockup, which shows the playing state). */
    public static void play(Ui ui, float x, float y, float size, int color) {
        float s = size / 24f;
        ui.triangle(x + 7f * s, y + 5f * s, x + 7f * s, y + 19f * s, x + 19f * s, y + 12f * s, s, color);
    }

    /** Step back «: {@code M11 17l-5-5 5-5 M18 17l-5-5 5-5}. */
    public static void back(Ui ui, float x, float y, float size, float stroke, int color) {
        path(ui, x, y, size, stroke, color, 11f, 17f, 6f, 12f, 11f, 7f);
        path(ui, x, y, size, stroke, color, 18f, 17f, 13f, 12f, 18f, 7f);
    }

    /** Step forward »: {@code M13 17l5-5-5-5 M6 17l5-5-5-5}. */
    public static void forward(Ui ui, float x, float y, float size, float stroke, int color) {
        path(ui, x, y, size, stroke, color, 13f, 17f, 18f, 12f, 13f, 7f);
        path(ui, x, y, size, stroke, color, 6f, 17f, 11f, 12f, 6f, 7f);
    }

    /** Close ×: {@code M6 6l12 12 M18 6L6 18}. */
    public static void close(Ui ui, float x, float y, float size, float stroke, int color) {
        path(ui, x, y, size, stroke, color, 6f, 6f, 18f, 18f);
        path(ui, x, y, size, stroke, color, 18f, 6f, 6f, 18f);
    }

    /** Eye (password shown). Not in the mockup; drawn in the same line style. */
    public static void eye(Ui ui, float x, float y, float size, int color) {
        float s = size / 24f;
        eyeOutline(ui, x, y, size, color);
        ui.ring(x + 12f * s, y + 12f * s, 8f * s, 2f * s, color);
    }

    /** Eye with a slash (password hidden). */
    public static void eyeOff(Ui ui, float x, float y, float size, int color) {
        eye(ui, x, y, size, color);
        path(ui, x, y, size, 2f, color, 4f, 4f, 20f, 20f);
    }

    private static void eyeOutline(Ui ui, float x, float y, float size, int color) {
        int n = 10;
        float[] top = new float[(n + 1) * 2];
        float[] bottom = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            float px = 2f + 20f * t;
            float dy = 7f * (float) Math.sin(Math.PI * t);
            top[i * 2] = px;
            top[i * 2 + 1] = 12f - dy;
            bottom[i * 2] = px;
            bottom[i * 2 + 1] = 12f + dy;
        }
        path(ui, x, y, size, 2f, color, top);
        path(ui, x, y, size, 2f, color, bottom);
    }
}
