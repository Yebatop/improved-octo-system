package dev.skirmish.module.lag;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;

/**
 * «● TPS 19,8 · 45 мс · 0,3 с» pill. Default place: bottom-right corner under the session score (which sits 70 px
 * up), clear of the hotbar, the chat and the right-hand scoreboard.
 */
final class LagHud extends HudBlock {
    private static final String L = "layout.awareness.";
    private final LagMeterModule module;
    private String[] parts = {"", "", ""};
    private String[] colors = {"text", "text", "text"};

    LagHud(LagMeterModule module) {
        super("lag_meter", "skirmish.hud.element.lag_meter", new Placement(1f, 1f, 1f, 1f,
                Theme.get().num(L + "lag_default_x"), Theme.get().num(L + "lag_default_y")));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.hud.get();
    }

    @Override
    public boolean shown() {
        return LagMeterModule.inWorld() && (module.show.get() == LagMeterModule.Show.ALWAYS || module.lagging());
    }

    @Override
    public boolean hasContent() {
        return LagMeterModule.inWorld();
    }

    @Override
    public void update(boolean preview) {
        double tps = preview && !LagMeterModule.inWorld() ? 19.8 : module.tps();
        int ping = preview && !LagMeterModule.inWorld() ? 45 : LagMeterModule.ping();
        long silence = preview && !LagMeterModule.inWorld() ? 300 : module.silenceMs();
        parts = new String[]{
                Ui.tr("skirmish.lag.tps", Double.isNaN(tps) ? "—" : Ui.decimal(tps, 1)),
                Ui.tr("skirmish.lag.ping", ping < 0 ? "—" : Integer.toString(ping)),
                Ui.tr("skirmish.lag.silence", Ui.decimal(silence / 1000.0, 1))};
        colors = new String[]{
                Double.isNaN(tps) ? "text_3" : LagText.tpsColor(tps),
                ping < 0 ? "text_3" : LagText.pingColor(ping),
                LagText.silenceColor(silence, Math.round(module.warnAfter.get() * 1000))};
    }

    @Override
    public float width(Ui ui, boolean preview) {
        float w = ui.num(L + "lag_pad_x") * 2 + ui.num(L + "lag_dot") + ui.num(L + "lag_gap");
        for (int i = 0; i < parts.length; i++) {
            w += ui.textWidth("aw_lag", parts[i]);
            if (i > 0) {
                w += ui.num(L + "lag_gap") * 2 + ui.textWidth("aw_lag_sep", "·");
            }
        }
        return w;
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return ui.num(L + "lag_height");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.pill(ui, x, y, w, h);
        float dot = ui.num(L + "lag_dot");
        float cx = x + ui.num(L + "lag_pad_x");
        ui.circle(cx + dot / 2f, y + h / 2f, dot, ui.color(LagText.worst(colors)));
        cx += dot + ui.num(L + "lag_gap");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                cx += ui.num(L + "lag_gap");
                cx = ui.textCentered("aw_lag_sep", "·", cx, y, h);
                cx += ui.num(L + "lag_gap");
            }
            cx = ui.textCentered("aw_lag", parts[i], cx, y, h, ui.color(colors[i]));
        }
    }
}
