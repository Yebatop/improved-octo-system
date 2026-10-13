package dev.skirmish.module.alerts;

import dev.skirmish.module.alerts.parse.LossItems;
import dev.skirmish.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** Shared wording and the warning glyph of the alerts module. */
final class AlertText {
    private AlertText() {
    }

    /** «3 шалкера, 2 рюкзака, 16 элементов» (only the non-zero parts). */
    static String losses(LossItems.Tally tally) {
        List<String> parts = new ArrayList<>();
        // In shulker mode every box drops; Elements drop even without shulker mode.
        if (tally.shulkerMode()) {
            add(parts, tally.shulkers(), "skirmish.alerts.count.shulker");
            add(parts, tally.backpacks(), "skirmish.alerts.count.backpack");
        }
        add(parts, tally.elements(), "skirmish.alerts.count.element");
        return String.join(", ", parts);
    }

    private static void add(List<String> parts, int n, String key) {
        if (n > 0) {
            parts.add(n + " " + Ui.plural(key, n));
        }
    }

    /** Banner title: which server mode is on. */
    static String title(LossItems.Tally tally) {
        if (tally.shulkerMode() && tally.elements() > 0) {
            return Ui.tr("skirmish.alerts.banner.title.both");
        }
        return Ui.tr(tally.shulkerMode() ? "skirmish.alerts.banner.title.shulker" : "skirmish.alerts.banner.title.element");
    }

    /** Rounded triangle with an exclamation mark, {@code size} design px square. */
    static void warningIcon(Ui ui, float x, float y, float size, int color) {
        float r = size * 0.12f;
        ui.triangle(x + size / 2f, y + r, x + size - r * 0.5f, y + size - r, x + r * 0.5f, y + size - r, r, color);
        String mark = "!";
        float tw = ui.textWidth("alert_icon", mark);
        float lh = ui.lineHeight("alert_icon");
        // The triangle's visual center sits below the box center.
        ui.text("alert_icon", mark, x + (size - tw) / 2f, y + (size - lh) / 2f + size * 0.12f);
    }
}
