package dev.skirmish.module.hwos;

import dev.skirmish.ui.Ui;

/** Line icons of the OS apps on a 24-unit grid. */
final class OsIcons {
    private static final float W = 1.8f;

    private OsIcons() {
    }

    static void draw(Ui ui, String app, float x, float y, float size, int c) {
        float s = size / 24f;
        switch (app) {
            case "events" -> { // calendar with a star-ish dot
                ui.border(x + 3 * s, y + 5 * s, 18 * s, 16 * s, 3 * s, W, c);
                l(ui, x, y, s, c, 3, 10, 21, 10);
                l(ui, x, y, s, c, 8, 3, 8, 7);
                l(ui, x, y, s, c, 16, 3, 16, 7);
                ui.circle(x + 12 * s, y + 15.5f * s, 4 * s, c);
            }
            case "economy" -> { // rising chart
                l(ui, x, y, s, c, 3, 20, 21, 20);
                l(ui, x, y, s, c, 4, 16, 9, 11);
                l(ui, x, y, s, c, 9, 11, 13, 14);
                l(ui, x, y, s, c, 13, 14, 20, 6);
                l(ui, x, y, s, c, 16, 6, 20, 6);
                l(ui, x, y, s, c, 20, 6, 20, 10);
            }
            case "anarchies" -> { // three servers
                for (int i = 0; i < 3; i++) {
                    ui.border(x + 4 * s, y + (3 + i * 6.5f) * s, 16 * s, 5 * s, 1.5f * s, W, c);
                    ui.circle(x + 7.5f * s, y + (5.5f + i * 6.5f) * s, 2 * s, c);
                }
            }
            case "guide" -> { // open book
                l(ui, x, y, s, c, 12, 6, 12, 20);
                l(ui, x, y, s, c, 12, 6, 4, 4);
                l(ui, x, y, s, c, 4, 4, 4, 18);
                l(ui, x, y, s, c, 4, 18, 12, 20);
                l(ui, x, y, s, c, 12, 6, 20, 4);
                l(ui, x, y, s, c, 20, 4, 20, 18);
                l(ui, x, y, s, c, 20, 18, 12, 20);
            }
            default -> { // profile: head and shoulders
                ui.ring(x + 12 * s, y + 8.5f * s, 8 * s + W, W, c);
                l(ui, x, y, s, c, 5, 20, 7, 15.5f);
                l(ui, x, y, s, c, 7, 15.5f, 17, 15.5f);
                l(ui, x, y, s, c, 17, 15.5f, 19, 20);
            }
        }
    }

    private static void l(Ui ui, float x, float y, float s, int c, float x0, float y0, float x1, float y1) {
        ui.line(x + x0 * s, y + y0 * s, x + x1 * s, y + y1 * s, W, c);
    }
}
