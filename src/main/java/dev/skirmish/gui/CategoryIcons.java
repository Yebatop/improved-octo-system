package dev.skirmish.gui;

import dev.skirmish.module.Category;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Ui;

/**
 * Line icons of the menu's category tabs and shortcuts, in the 24-unit view box and stroke style of {@link Icons}.
 */
final class CategoryIcons {
    private static final float STROKE = 2f;

    private CategoryIcons() {
    }

    static void draw(Ui ui, Category category, float x, float y, float size, int color) {
        switch (category) {
            case COMBAT -> Icons.sword(ui, x, y, size, STROKE, color, true);
            case VISUAL -> Icons.eye(ui, x, y, size, color);
            case WORLD -> globe(ui, x, y, size, color);
            case UTILITY -> briefcase(ui, x, y, size, color);
            case INTERFACE -> sliders(ui, x, y, size, color);
        }
    }

    private static void path(Ui ui, float x, float y, float size, int color, float... points) {
        float s = size / 24f;
        float[] scaled = new float[points.length];
        for (int i = 0; i < points.length; i += 2) {
            scaled[i] = x + points[i] * s;
            scaled[i + 1] = y + points[i + 1] * s;
        }
        ui.polyline(STROKE * s, color, scaled);
    }

    /** Circle r9, the equator and a meridian ellipse. */
    private static void globe(Ui ui, float x, float y, float size, int color) {
        float s = size / 24f;
        ui.ring(x + 12f * s, y + 12f * s, (18f + STROKE) * s, STROKE * s, color);
        path(ui, x, y, size, color, 3f, 12f, 21f, 12f);
        int n = 16;
        float[] pts = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            pts[i * 2] = 12f + 4f * (float) Math.sin(a);
            pts[i * 2 + 1] = 12f - 9f * (float) Math.cos(a);
        }
        path(ui, x, y, size, color, pts);
    }

    /** Case 20×14 with a handle. */
    private static void briefcase(Ui ui, float x, float y, float size, int color) {
        float s = size / 24f;
        ui.border(x + 2f * s, y + 7f * s, 20f * s, 14f * s, 2.5f * s, STROKE * s, color);
        path(ui, x, y, size, color, 8f, 7f, 8f, 4.5f, 9.5f, 3f, 14.5f, 3f, 16f, 4.5f, 16f, 7f);
        path(ui, x, y, size, color, 2f, 13f, 22f, 13f);
    }

    /** Three horizontal sliders with knobs. */
    private static void sliders(Ui ui, float x, float y, float size, int color) {
        path(ui, x, y, size, color, 3f, 5f, 21f, 5f);
        path(ui, x, y, size, color, 3f, 12f, 21f, 12f);
        path(ui, x, y, size, color, 3f, 19f, 21f, 19f);
        path(ui, x, y, size, color, 15f, 2.5f, 15f, 7.5f);
        path(ui, x, y, size, color, 8f, 9.5f, 8f, 14.5f);
        path(ui, x, y, size, color, 17f, 16.5f, 17f, 21.5f);
    }

    /** Layout grid (HUD editor shortcut). */
    static void layout(Ui ui, float x, float y, float size, int color) {
        float s = size / 24f;
        ui.border(x + 3f * s, y + 3f * s, 18f * s, 18f * s, 2.5f * s, STROKE * s, color);
        path(ui, x, y, size, color, 3f, 9f, 21f, 9f);
        path(ui, x, y, size, color, 9f, 9f, 9f, 21f);
    }

    /** Map pin (waypoints shortcut). */
    static void pin(Ui ui, float x, float y, float size, int color) {
        float s = size / 24f;
        int n = 14;
        float[] pts = new float[(n + 2) * 2];
        // Arc of the head from the lower left around the top to the lower right, then down to the tip.
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(220 - 260.0 * i / n);
            pts[i * 2] = 12f + 7f * (float) Math.cos(a);
            pts[i * 2 + 1] = 10f - 7f * (float) Math.sin(a);
        }
        pts[(n + 1) * 2] = 12f;
        pts[(n + 1) * 2 + 1] = 22f;
        float[] closed = new float[pts.length + 2];
        System.arraycopy(pts, 0, closed, 0, pts.length);
        closed[pts.length] = pts[0];
        closed[pts.length + 1] = pts[1];
        path(ui, x, y, size, color, closed);
        ui.ring(x + 12f * s, y + 10f * s, (5f + STROKE) * s, STROKE * s, color);
    }

    /** Terminal window with a prompt (debug log switch). */
    static void log(Ui ui, float x, float y, float size, int color) {
        float s = size / 24f;
        ui.border(x + 2.5f * s, y + 4f * s, 19f * s, 16f * s, 2.5f * s, STROKE * s, color);
        path(ui, x, y, size, color, 7f, 9.5f, 10f, 12f, 7f, 14.5f);
        path(ui, x, y, size, color, 12.5f, 15f, 17f, 15f);
    }

    /** Magnifier (search field). */
    static void search(Ui ui, float x, float y, float size, int color) {
        float s = size / 24f;
        ui.ring(x + 11f * s, y + 11f * s, (14f + STROKE) * s, STROKE * s, color);
        path(ui, x, y, size, color, 16.5f, 16.5f, 21f, 21f);
    }

    /** Chevron pointing left (‹) or right (›). */
    static void chevron(Ui ui, float x, float y, float size, int color, boolean left) {
        if (left) {
            path(ui, x, y, size, color, 15f, 6f, 9f, 12f, 15f, 18f);
        } else {
            path(ui, x, y, size, color, 9f, 6f, 15f, 12f, 9f, 18f);
        }
    }
}
