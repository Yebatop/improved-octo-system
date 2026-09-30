package dev.skirmish.module.menus;

import dev.skirmish.gui.ModuleIcons;
import dev.skirmish.ui.Ui;

/** Line icons of the pause menu's app tiles on a 24-unit grid. */
final class AppIcons {
    private static final float W = 1.8f;

    private AppIcons() {
    }

    static void draw(Ui ui, String icon, float x, float y, float size, int c) {
        float s = size / 24f;
        switch (icon) {
            case "skirmish" -> ModuleIcons.logo(ui, x, y, size, ui.color("accent"), c);
            case "base" -> { // house
                l(ui, x, y, s, c, 3, 11, 12, 3.5f);
                l(ui, x, y, s, c, 12, 3.5f, 21, 11);
                l(ui, x, y, s, c, 5.5f, 9.5f, 5.5f, 20.5f);
                l(ui, x, y, s, c, 18.5f, 9.5f, 18.5f, 20.5f);
                l(ui, x, y, s, c, 5.5f, 20.5f, 18.5f, 20.5f);
                ui.border(x + 10 * s, y + 14 * s, 4 * s, 6.5f * s, 1 * s, W, c);
            }
            case "os" -> { // four app squares
                for (int i = 0; i < 4; i++) {
                    float px = 4 + (i % 2) * 9;
                    float py = 4 + (i / 2) * 9;
                    ui.border(x + px * s, y + py * s, 7 * s, 7 * s, 2 * s, W, c);
                }
            }
            case "map" -> { // folded map
                l(ui, x, y, s, c, 3, 6, 9, 3.5f);
                l(ui, x, y, s, c, 9, 3.5f, 15, 6);
                l(ui, x, y, s, c, 15, 6, 21, 3.5f);
                l(ui, x, y, s, c, 21, 3.5f, 21, 18);
                l(ui, x, y, s, c, 21, 18, 15, 20.5f);
                l(ui, x, y, s, c, 15, 20.5f, 9, 18);
                l(ui, x, y, s, c, 9, 18, 3, 20.5f);
                l(ui, x, y, s, c, 3, 20.5f, 3, 6);
                l(ui, x, y, s, c, 9, 3.5f, 9, 18);
                l(ui, x, y, s, c, 15, 6, 15, 20.5f);
            }
            case "review" -> { // bars
                ui.rect(x + 4 * s, y + 13 * s, 4 * s, 7 * s, 1 * s, c);
                ui.rect(x + 10 * s, y + 8 * s, 4 * s, 12 * s, 1 * s, c);
                ui.rect(x + 16 * s, y + 4 * s, 4 * s, 16 * s, 1 * s, c);
            }
            case "replays" -> { // play in a circle
                ui.ring(x + 12 * s, y + 12 * s, 18 * s + W, W, c);
                ui.triangle(x + 10 * s, y + 8 * s, x + 10 * s, y + 16 * s, x + 16.5f * s, y + 12 * s, 0.5f, c);
            }
            case "pin" -> { // map pin
                ui.ring(x + 12 * s, y + 9.5f * s, 11 * s + W, W, c);
                l(ui, x, y, s, c, 7.5f, 13, 12, 21);
                l(ui, x, y, s, c, 16.5f, 13, 12, 21);
                ui.circle(x + 12 * s, y + 9.5f * s, 3.5f * s, c);
            }
            case "hud" -> { // screen with panels
                ui.border(x + 3 * s, y + 4 * s, 18 * s, 16 * s, 2.5f * s, W, c);
                ui.rect(x + 6 * s, y + 7 * s, 5 * s, 4 * s, 1 * s, c);
                ui.rect(x + 13 * s, y + 7 * s, 5 * s, 2 * s, 1 * s, c);
                ui.rect(x + 6 * s, y + 14 * s, 12 * s, 3 * s, 1 * s, c);
            }
            case "studio" -> { // a figure with a sparkle
                ui.ring(x + 10 * s, y + 7.5f * s, 7 * s, W, c);
                l(ui, x, y, s, c, 3.5f, 21, 4.5f, 16.5f);
                l(ui, x, y, s, c, 4.5f, 16.5f, 7.5f, 13.5f);
                l(ui, x, y, s, c, 7.5f, 13.5f, 12.5f, 13.5f);
                l(ui, x, y, s, c, 12.5f, 13.5f, 15.5f, 16.5f);
                l(ui, x, y, s, c, 15.5f, 16.5f, 16.5f, 21);
                l(ui, x, y, s, c, 19.5f, 3, 19.5f, 9);
                l(ui, x, y, s, c, 16.5f, 6, 22.5f, 6);
            }
            case "keys" -> { // keyboard
                ui.border(x + 2 * s, y + 6 * s, 20 * s, 13 * s, 2.5f * s, W, c);
                for (float kx : new float[]{6, 10, 14, 18}) {
                    ui.rect(x + (kx - 1) * s, y + 9 * s, 2 * s, 2 * s, 0.5f * s, c);
                }
                l(ui, x, y, s, c, 8, 15.5f, 16, 15.5f);
            }
            default -> ui.circle(x + 12 * s, y + 12 * s, 8 * s, c);
        }
    }

    private static void l(Ui ui, float x, float y, float s, int c, float x0, float y0, float x1, float y1) {
        ui.line(x + x0 * s, y + y0 * s, x + x1 * s, y + y1 * s, W, c);
    }
}
