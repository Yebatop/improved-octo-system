package dev.skirmish.module.killcam;

import dev.skirmish.ui.Ui;
import net.minecraft.util.Util;

/** Line icons of the replay library in the kit's style (24×24 view box, round caps), plus the loading spinner. */
final class LibraryIcons {
    private LibraryIcons() {
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

    /** Five-pointed star, outlined or filled. */
    static void star(Ui ui, float x, float y, float size, boolean filled, int color) {
        float s = size / 24f;
        float cx = x + 12f * s;
        float cy = y + 12.8f * s;
        float outer = 10.5f * s;
        float inner = 4.6f * s;
        float[] pts = new float[22];
        for (int i = 0; i <= 10; i++) {
            double a = Math.toRadians(-90 + 36 * i);
            float r = i % 2 == 0 ? outer : inner;
            pts[i * 2] = cx + (float) Math.cos(a) * r;
            pts[i * 2 + 1] = cy + (float) Math.sin(a) * r;
        }
        if (filled) {
            for (int i = 0; i < 10; i++) {
                ui.triangle(cx, cy, pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3], 0f, color);
            }
        }
        ui.polyline(2f * s, color, pts);
    }

    /** Pencil: {@code M4 20l4-1 11-11-3-3L5 16z M14 7l3 3}. */
    static void pencil(Ui ui, float x, float y, float size, int color) {
        path(ui, x, y, size, 2f, color, 4f, 20f, 8f, 19f, 19f, 8f, 16f, 5f, 5f, 16f, 4f, 20f);
        path(ui, x, y, size, 2f, color, 14f, 7f, 17f, 10f);
    }

    /** Bin: lid, handle and body. */
    static void trash(Ui ui, float x, float y, float size, int color) {
        path(ui, x, y, size, 2f, color, 4f, 7f, 20f, 7f);
        path(ui, x, y, size, 2f, color, 9.5f, 7f, 10f, 4f, 14f, 4f, 14.5f, 7f);
        path(ui, x, y, size, 2f, color, 6.5f, 7f, 7.5f, 20f, 16.5f, 20f, 17.5f, 7f);
        path(ui, x, y, size, 2f, color, 10.5f, 11f, 10.5f, 16f);
        path(ui, x, y, size, 2f, color, 13.5f, 11f, 13.5f, 16f);
    }

    /** Eight dots around a circle, the bright one moving clockwise (wall clock). */
    static void spinner(Ui ui, float cx, float cy, float size, float dot, int color) {
        int n = 8;
        long phase = (Util.getMillis() / 90) % n;
        float r = (size - dot) / 2f;
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(-90 + 360.0 * i / n);
            int age = (int) ((phase - i + n) % n);
            float alpha = 1f - age / (float) n * 0.85f;
            ui.pushAlpha(alpha);
            ui.circle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, dot, color);
            ui.popAlpha();
        }
    }
}
