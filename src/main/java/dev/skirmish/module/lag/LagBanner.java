package dev.skirmish.module.lag;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.module.survival.SurvivalBanner;
import dev.skirmish.module.survival.WarningPanel;
import dev.skirmish.ui.Ui;

import java.util.List;

/**
 * «Сервер не отвечает N с — не двигайся». Default place: the survival warning's spot above the crosshair (and the
 * combat tag), right under that warning when both are up.
 */
final class LagBanner extends HudBlock {
    private final LagMeterModule module;
    private String title = "";
    private List<String> lines = List.of();

    LagBanner(LagMeterModule module) {
        super("lag_warning", "skirmish.hud.element.lag_warning", SurvivalBanner.bannerPlacement());
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.warning.get();
    }

    @Override
    public boolean shown() {
        return module.stalled();
    }

    @Override
    public String stackUnder() {
        return SurvivalBanner.ID;
    }

    @Override
    public void update(boolean preview) {
        long silence = preview && !module.stalled() ? 3200 : module.silenceMs();
        title = Ui.tr("skirmish.lag.banner.title", PacketClock.bannerSeconds(silence));
        double tps = module.tps();
        lines = List.of(Ui.tr("skirmish.lag.banner.body"),
                Ui.tr("skirmish.lag.banner.meta", Ui.decimal(silence / 1000.0, 1), Double.isNaN(tps) ? "—" : Ui.decimal(tps, 1)));
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return WarningPanel.width(ui, title, lines);
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return WarningPanel.height(ui, lines);
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        WarningPanel.render(ui, x, y, title, lines, "bad", true);
    }
}
