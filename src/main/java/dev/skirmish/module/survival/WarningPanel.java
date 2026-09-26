package dev.skirmish.module.survival;

import dev.skirmish.hud.HudStyle;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Centered warning banner shared by the survival alerts and the lag detector: warning triangle, a big title and
 * optional smaller lines, a tinted border, and a slow pulse ({@code motion.aw_flash_ms}) while {@code flash} is on.
 */
public final class WarningPanel {
    private static final String L = "layout.awareness.";

    private WarningPanel() {
    }

    public static float width(Ui ui, String title, List<String> lines) {
        float text = ui.textWidth("aw_alert_title", title);
        for (String line : lines) {
            text = Math.max(text, ui.textWidth("aw_alert_line", line));
        }
        float w = HudStyle.insetX(ui) * 2 + ui.num(L + "alert_icon") + ui.num(L + "alert_icon_gap") + text;
        return Math.max(ui.num(L + "alert_min_width"), w);
    }

    public static float height(Ui ui, List<String> lines) {
        float head = Math.max(ui.num(L + "alert_icon"), ui.lineHeight("aw_alert_title"));
        float h = HudStyle.insetY(ui) * 2 + head;
        if (!lines.isEmpty()) {
            h += ui.num(L + "alert_line_gap") + lines.size() * ui.lineHeight("aw_alert_line");
        }
        return h;
    }

    /** Pulse in [min, 1] for flashing elements; 1 when not flashing. */
    public static float pulse(boolean flash) {
        if (!flash) {
            return 1f;
        }
        float period = Theme.get().num("motion.aw_flash_ms");
        double phase = (Util.getMillis() % (long) period) / (double) period;
        float min = Theme.get().num(L + "alert_flash_min");
        return (float) (min + (1 - min) * (0.5 + 0.5 * Math.cos(phase * Math.PI * 2)));
    }

    /**
     * @param tone "bad" or "warn": icon, title and border color
     */
    public static void render(Ui ui, float x, float y, String title, List<String> lines, String tone, boolean flash) {
        float w = width(ui, title, lines);
        float h = height(ui, lines);
        float radius = ui.theme().radius("panel");
        HudStyle.panel(ui, x, y, w, h);
        float pulse = pulse(flash);
        ui.pushAlpha(pulse);
        ui.rect(x, y, w, h, radius, ui.color("aw_alert_tint_" + tone));
        ui.border(x, y, w, h, radius, ui.num(L + "alert_stroke"), ui.color("aw_alert_stroke_" + tone));
        ui.popAlpha();
        float icon = ui.num(L + "alert_icon");
        float head = Math.max(icon, ui.lineHeight("aw_alert_title"));
        float cx = x + HudStyle.insetX(ui);
        float cy = y + HudStyle.insetY(ui);
        ui.pushAlpha(pulse);
        warningIcon(ui, cx, cy + (head - icon) / 2f, icon, ui.color(tone));
        ui.popAlpha();
        float tx = cx + icon + ui.num(L + "alert_icon_gap");
        ui.textCentered("aw_alert_title", title, tx, cy, head, ui.color(tone));
        cy += head + ui.num(L + "alert_line_gap");
        for (String line : lines) {
            ui.text("aw_alert_line", line, tx, cy);
            cy += ui.lineHeight("aw_alert_line");
        }
    }

    /** Rounded triangle with an exclamation mark. */
    static void warningIcon(Ui ui, float x, float y, float size, int color) {
        float r = size * 0.12f;
        ui.triangle(x + size / 2f, y + r, x + size - r * 0.5f, y + size - r, x + r * 0.5f, y + size - r, r, color);
        String mark = "!";
        float tw = ui.textWidth("aw_alert_icon", mark);
        float lh = ui.lineHeight("aw_alert_icon");
        ui.text("aw_alert_icon", mark, x + (size - tw) / 2f, y + (size - lh) / 2f + size * 0.12f);
    }
}
