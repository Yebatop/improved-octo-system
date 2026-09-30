package dev.skirmish.module.analytics.combo;

import dev.skirmish.combat.CombatTracker;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.module.analytics.AnalyticsHub;
import dev.skirmish.module.analytics.AnalyticsText;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Small panel: combo · last reach · CPS, each a value over a label. Default place: above the hotbar, left of centre
 * (the target card sits under the crosshair, cooldown rings above the hotbar centre).
 */
final class ComboHud extends HudBlock {
    private static final String L = "layout.analytics.combo.";
    /** Shown for this long after my last click or hit when "only in a fight" is on and no fight is active. */
    private static final long LINGER_MS = 4_000;

    private final ComboHudModule module;

    private record Cell(String value, String label, String color) {
    }

    ComboHud(ComboHudModule module) {
        super("combo", "skirmish.hud.element.combo", new Placement(0.5f, 1f, 1f, 1f,
                -Theme.get().num(L + "default_left"), -Theme.get().num(L + "default_bottom")));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && (module.combo.get() || module.reach.get() || module.cps.get());
    }

    @Override
    public boolean shown() {
        if (!module.onlyInFight.get()) {
            return true;
        }
        long now = System.currentTimeMillis();
        long last = Math.max(module.lastHitMs(), AnalyticsHub.get().lastClickMs());
        return !CombatTracker.get().activeFights().isEmpty() || last >= 0 && now - last < LINGER_MS;
    }

    @Override
    public boolean hasContent() {
        return true;
    }

    private List<Cell> cells(boolean preview) {
        long now = System.currentTimeMillis();
        List<Cell> cells = new ArrayList<>(3);
        if (module.combo.get()) {
            int n = preview ? 5 : module.combo(now);
            cells.add(new Cell(Integer.toString(n), Ui.tr("skirmish.analytics.combo.combo"), n >= 3 ? "accent" : "text"));
        }
        if (module.reach.get()) {
            cells.add(new Cell(preview ? AnalyticsText.reach(2.87) : AnalyticsText.reach(module.lastReach()),
                    Ui.tr("skirmish.analytics.combo.reach"), "text"));
        }
        if (module.cps.get()) {
            cells.add(new Cell(Integer.toString(preview ? 7 : AnalyticsHub.get().cps(now)), Ui.tr("skirmish.analytics.combo.cps"), "text"));
        }
        return cells;
    }

    private float cellWidth(Ui ui, List<Cell> cells) {
        float w = ui.num(L + "cell_min");
        for (Cell c : cells) {
            w = Math.max(w, Math.max(ui.textWidth("fa_combo_value", c.value()), ui.textWidth("fa_combo_label", c.label())));
        }
        return w;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        List<Cell> cells = cells(preview);
        int n = Math.max(1, cells.size());
        return HudStyle.insetX(ui) * 2 + n * cellWidth(ui, cells) + (n - 1) * (ui.num(L + "gap") * 2 + ui.num("stroke.width"));
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return ui.num("stroke.width") * 2 + ui.num(L + "pad_y") * 2 + ui.lineHeight("fa_combo_value") + ui.lineHeight("fa_combo_label");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.panel(ui, x, y, w, h);
        List<Cell> cells = cells(preview);
        float cellW = cellWidth(ui, cells);
        float gap = ui.num(L + "gap");
        float cx = x + HudStyle.insetX(ui);
        float top = y + ui.num("stroke.width") + ui.num(L + "pad_y");
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            if (i > 0) {
                cx += gap;
                ui.rect(cx, y + h * 0.25f, ui.num("stroke.width"), h * 0.5f, 0, ui.color("divider"));
                cx += ui.num("stroke.width") + gap;
            }
            ui.text("fa_combo_value", c.value(), cx + (cellW - ui.textWidth("fa_combo_value", c.value())) / 2f, top, ui.color(c.color()));
            ui.text("fa_combo_label", c.label(), cx + (cellW - ui.textWidth("fa_combo_label", c.label())) / 2f,
                    top + ui.lineHeight("fa_combo_value"));
            cx += cellW;
        }
    }
}
