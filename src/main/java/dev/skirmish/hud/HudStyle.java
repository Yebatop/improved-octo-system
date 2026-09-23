package dev.skirmish.hud;

import dev.skirmish.ui.Ui;

/** Shared HUD surfaces from the mockup: translucent panel (radius 12) and pill, both with a 1 px stroke. */
public final class HudStyle {
    private HudStyle() {
    }

    public static void panel(Ui ui, float x, float y, float w, float h) {
        ui.box(x, y, w, h, ui.theme().radius("panel"), ui.color("panel"), ui.color("stroke"));
    }

    public static void pill(Ui ui, float x, float y, float w, float h) {
        ui.box(x, y, w, h, h / 2f, ui.color("panel"), ui.color("stroke"));
    }

    /** Panel padding + border on each side (box-sizing: border-box). */
    public static float insetX(Ui ui) {
        return ui.num("layout.panel_pad_x") + ui.num("stroke.width");
    }

    public static float insetY(Ui ui) {
        return ui.num("layout.panel_pad_y") + ui.num("stroke.width");
    }

    /** Header row height: max of the 15 px icon and the panel title line. */
    public static float headerHeight(Ui ui) {
        return Math.max(ui.num("layout.panel_icon"), ui.lineHeight("panel_title"));
    }

    /** Draws a panel header (icon slot, title, optional right-aligned meta); returns the icon's top-left. */
    public static float[] header(Ui ui, float x, float y, float w, String title, String meta) {
        float h = headerHeight(ui);
        float icon = ui.num("layout.panel_icon");
        float iy = y + (h - icon) / 2f;
        ui.textCentered("panel_title", title, x + icon + ui.num("layout.panel_header_gap"), y, h);
        if (meta != null && !meta.isEmpty()) {
            ui.textCentered("panel_meta", meta, x + w - ui.textWidth("panel_meta", meta), y, h);
        }
        return new float[]{x, iy};
    }
}
