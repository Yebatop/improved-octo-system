package dev.skirmish.module.runewindow;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;

import java.util.List;
import java.util.UUID;

/**
 * One chip per running window: «НЕУЯЗВИМ» over the nick, the seconds left on the right, a draining bar at the
 * bottom. A guess (fallback length) reads «НЕУЯЗВИМ?» in a muted color. Default place: right of the crosshair,
 * vertically centered (the combat tag sits above the crosshair, the target card below it).
 */
final class RuneWindowHud extends HudBlock {
    private static final String L = "layout.rune_window.";

    private final RuneWindowModule module;
    private List<RuneTimers.Timer> rows = List.of();

    RuneWindowHud(RuneWindowModule module) {
        super(RuneWindowModule.ID, "skirmish.hud.element.rune_window", new Placement(0.5f, 0.5f, 0f, 0.5f,
                Theme.get().num(L + "default_dx"), 0));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public boolean shown() {
        return !module.timers.isEmpty();
    }

    @Override
    public boolean hasContent() {
        return !rows.isEmpty();
    }

    @Override
    public void update(boolean preview) {
        List<RuneTimers.Timer> live = module.timers.live();
        if (!live.isEmpty() || preview) {
            rows = live.isEmpty() ? sample() : live;
        }
    }

    private static List<RuneTimers.Timer> sample() {
        long now = System.currentTimeMillis();
        long cycle = 3_000;
        long start = now - (now % cycle);
        return List.of(new RuneTimers.Timer(new UUID(0, 1), -1, "Enemy_3", RuneEffect.Kind.INVULNERABLE, start, cycle, false));
    }

    private static String title(RuneTimers.Timer t) {
        if (t.kind() == RuneEffect.Kind.RESTORED) {
            return Ui.tr("skirmish.rune_window.restored");
        }
        return Ui.tr("skirmish.rune_window.invulnerable") + (t.guessed() ? "?" : "");
    }

    private static String value(RuneTimers.Timer t, long now) {
        return t.kind() == RuneEffect.Kind.RESTORED ? "" : RuneTimers.seconds(t.remainingMs(now));
    }

    private float rowWidth(Ui ui, RuneTimers.Timer t) {
        float text = Math.max(ui.textWidth("rw_title", title(t)), ui.textWidth("rw_name", t.name()));
        float value = t.kind() == RuneEffect.Kind.RESTORED ? 0 : ui.num(L + "value_gap") + ui.textWidth("rw_value", "0.0");
        return Math.max(ui.num(L + "min_width"), HudStyle.insetX(ui) * 2 + ui.num(L + "stripe") + ui.num(L + "stripe_gap") + text + value);
    }

    private float rowHeight(Ui ui) {
        return ui.num("stroke.width") * 2 + ui.num(L + "pad_y") * 2 + Math.max(ui.lineHeight("rw_title") + ui.lineHeight("rw_name"),
                ui.lineHeight("rw_value")) + ui.num(L + "bar_gap") + ui.num(L + "bar");
    }

    @Override
    public float width(Ui ui, boolean preview) {
        float w = ui.num(L + "min_width");
        for (RuneTimers.Timer t : rows) {
            w = Math.max(w, rowWidth(ui, t));
        }
        return w;
    }

    @Override
    public float height(Ui ui, boolean preview) {
        int n = Math.max(1, rows.size());
        return n * rowHeight(ui) + (n - 1) * ui.num(L + "row_gap");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        long now = System.currentTimeMillis();
        float w = width(ui, preview);
        float rh = rowHeight(ui);
        float stroke = ui.num("stroke.width");
        for (RuneTimers.Timer t : rows) {
            boolean restored = t.kind() == RuneEffect.Kind.RESTORED;
            String tone = restored ? "good" : t.guessed() ? "rw_guess" : "rw_invulnerable";
            int color = ui.color(tone);
            HudStyle.panel(ui, x, y, w, rh);
            float inX = x + HudStyle.insetX(ui);
            float top = y + stroke + ui.num(L + "pad_y");
            float textH = ui.lineHeight("rw_title") + ui.lineHeight("rw_name");
            float stripe = ui.num(L + "stripe");
            ui.rect(inX, top, stripe, textH, stripe / 2f, color);
            float tx = inX + stripe + ui.num(L + "stripe_gap");
            ui.text("rw_title", title(t), tx, top, color);
            ui.text("rw_name", t.name(), tx, top + ui.lineHeight("rw_title"));
            if (!restored) {
                String v = value(t, now);
                float vw = ui.textWidth("rw_value", v);
                float vx = x + w - HudStyle.insetX(ui) - vw;
                ui.text("rw_value", v, vx, top + (textH - ui.lineHeight("rw_value")) / 2f, t.guessed() ? ui.color("rw_guess") : ui.color("text"));
            }
            float barY = top + textH + ui.num(L + "bar_gap");
            float barW = w - HudStyle.insetX(ui) * 2;
            float bar = ui.num(L + "bar");
            ui.rect(inX, barY, barW, bar, bar / 2f, ui.color("track"));
            float fill = t.fraction(now);
            if (fill > 0f) {
                ui.rect(inX, barY, Math.max(bar, barW * fill), bar, bar / 2f, color);
            }
            y += rh + ui.num(L + "row_gap");
        }
    }
}
