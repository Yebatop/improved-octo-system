package dev.skirmish.module.menus;

import dev.skirmish.gui.ModuleIcons;
import dev.skirmish.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;

/**
 * Background of the connecting / loading / saving screens: the space backdrop, the logo and name above the screen's
 * own text and a tip at the bottom. Vanilla still draws its status text, progress and buttons on top.
 */
public final class LoadingBackdrop {
    private static final String L = "layout.loading.";
    private static final long SEED = System.nanoTime() / 1_000_000_000L;

    private LoadingBackdrop() {
    }

    public static void draw(GuiGraphics graphics) {
        long now = Util.getMillis();
        Ui ui = Ui.begin(graphics);
        try {
            float w = ui.width();
            float h = ui.height();
            MenuBackdrop.draw(ui, w, h, now, true, MainMenuModule.quasarOn());
            float logo = ui.num(L + "logo");
            float nameW = ui.textWidth("ld_title", "Skirmish");
            float total = logo + ui.num(L + "logo_gap") + nameW;
            float x = (w - total) / 2f;
            float y = h * ui.num(L + "top");
            ModuleIcons.logo(ui, x, y, logo, ui.color("accent"), ui.color("text"));
            ui.textCentered("ld_title", "Skirmish", x + logo + ui.num(L + "logo_gap"), y, logo);
            if (MainMenuModule.tipsOn()) {
                String tip = MenuTips.text(MenuTips.index(now, Math.round(ui.num(L + "tip_ms")), SEED));
                float max = Math.min(w - 40f, ui.num(L + "tip_width"));
                java.util.List<String> lines = ui.wrap("ld_tip", tip, max);
                float ty = h - ui.num(L + "bottom") - lines.size() * ui.lineHeight("ld_tip");
                for (String line : lines) {
                    ui.text("ld_tip", line, (w - ui.textWidth("ld_tip", line)) / 2f, ty);
                    ty += ui.lineHeight("ld_tip");
                }
            }
        } finally {
            ui.end();
        }
    }

    /** The loading bar: rounded track with an accent fill, in GUI px like vanilla's. */
    public static void bar(GuiGraphics graphics, int x, int y, int w, int h, float progress) {
        Ui ui = Ui.begin(graphics);
        try {
            float s = (float) Ui.toDesign(1.0);
            float bx = x * s;
            float by = y * s;
            float bw = w * s;
            float bh = Math.max(ui.num(L + "bar"), h * s);
            ui.rect(bx, by, bw, bh, bh / 2f, ui.color("track"));
            ui.rect(bx, by, Math.max(bh, bw * Math.max(0f, Math.min(1f, progress))), bh, bh / 2f, ui.color("accent"));
        } finally {
            ui.end();
        }
    }
}
