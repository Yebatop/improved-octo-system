package dev.skirmish.module.scoreboard;

import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Ui;

/** Line icons for the sidebar labels on a 24-unit grid (see {@link SidebarText#icon}). */
final class SidebarIcons {
    private static final float W = 2f;

    private SidebarIcons() {
    }

    static void draw(Ui ui, String icon, float x, float y, float size, int c) {
        float s = size / 24f;
        switch (icon) {
            case "coin" -> {
                ui.ring(x + 12 * s, y + 12 * s, 18 * s + W, W, c);
                l(ui, x, y, s, c, 12, 7, 12, 17);
                l(ui, x, y, s, c, 9.5f, 9.5f, 14.5f, 9.5f);
                l(ui, x, y, s, c, 9.5f, 14.5f, 14.5f, 14.5f);
            }
            case "shield" -> {
                l(ui, x, y, s, c, 12, 3, 20, 6);
                l(ui, x, y, s, c, 20, 6, 19, 14);
                l(ui, x, y, s, c, 19, 14, 12, 21);
                l(ui, x, y, s, c, 12, 21, 5, 14);
                l(ui, x, y, s, c, 5, 14, 4, 6);
                l(ui, x, y, s, c, 4, 6, 12, 3);
            }
            case "signal" -> {
                for (int i = 0; i < 4; i++) {
                    float bx = 4 + i * 5.2f;
                    float top = 18 - i * 4.5f;
                    ui.rect(x + bx * s, y + top * s, 3 * s, (20 - top) * s, 1 * s, c);
                }
            }
            case "sword" -> Icons.sword(ui, x, y, size, W / s, c, true);
            case "skull" -> {
                ui.ring(x + 12 * s, y + 10.5f * s, 15 * s + W, W, c);
                l(ui, x, y, s, c, 8.5f, 17, 8.5f, 21);
                l(ui, x, y, s, c, 15.5f, 17, 15.5f, 21);
                l(ui, x, y, s, c, 8.5f, 21, 15.5f, 21);
                ui.circle(x + 9.3f * s, y + 11 * s, 3.2f * s, c);
                ui.circle(x + 14.7f * s, y + 11 * s, 3.2f * s, c);
            }
            case "user" -> {
                ui.ring(x + 12 * s, y + 8.5f * s, 8 * s + W, W, c);
                l(ui, x, y, s, c, 5, 20, 7, 15.5f);
                l(ui, x, y, s, c, 7, 15.5f, 17, 15.5f);
                l(ui, x, y, s, c, 17, 15.5f, 19, 20);
            }
            case "star" -> {
                float[] p = new float[12];
                for (int i = 0; i < 6; i++) {
                    double a = Math.toRadians(-90 + i * 144);
                    p[i * 2] = x + (12 + 9 * (float) Math.cos(a)) * s;
                    p[i * 2 + 1] = y + (13 + 9 * (float) Math.sin(a)) * s;
                }
                ui.polyline(W, c, p);
            }
            case "people" -> {
                ui.ring(x + 9 * s, y + 9 * s, 7 * s + W, W, c);
                ui.ring(x + 16.5f * s, y + 10 * s, 5.5f * s + W, W, c);
                l(ui, x, y, s, c, 3, 20, 5, 15);
                l(ui, x, y, s, c, 5, 15, 13, 15);
                l(ui, x, y, s, c, 13, 15, 15, 20);
                l(ui, x, y, s, c, 16, 15, 20, 15);
                l(ui, x, y, s, c, 20, 15, 21.5f, 19);
            }
            case "globe" -> {
                ui.ring(x + 12 * s, y + 12 * s, 18 * s + W, W, c);
                l(ui, x, y, s, c, 3, 12, 21, 12);
                l(ui, x, y, s, c, 12, 3, 9, 12);
                l(ui, x, y, s, c, 9, 12, 12, 21);
                l(ui, x, y, s, c, 12, 3, 15, 12);
                l(ui, x, y, s, c, 15, 12, 12, 21);
            }
            case "clock" -> Icons.clock(ui, x, y, size, W / s, c);
            default -> ui.circle(x + 12 * s, y + 12 * s, 6 * s, c);
        }
    }

    private static void l(Ui ui, float x, float y, float s, int c, float x0, float y0, float x1, float y1) {
        ui.line(x + x0 * s, y + y0 * s, x + x1 * s, y + y1 * s, W, c);
    }
}
