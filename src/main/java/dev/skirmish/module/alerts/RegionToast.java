package dev.skirmish.module.alerts;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;

import java.util.List;

/**
 * Toast for the region intrusion alert: the intruder's nick, then the previous intrusions (up to the history
 * setting) with how long ago they happened.
 */
final class RegionToast extends HudBlock {
    private static final String L = "layout.alerts.";
    private static final List<AlertsModule.Intrusion> PREVIEW = List.of(
            new AlertsModule.Intrusion("Griefer_228", 0), new AlertsModule.Intrusion("Steve", -125_000));

    private final AlertsModule module;

    RegionToast(AlertsModule module) {
        super("alerts_region", "skirmish.hud.element.alerts_region", new Placement(1, 0, 1, 0, -18, 150));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.regionAlerts.get();
    }

    @Override
    public boolean shown() {
        List<AlertsModule.Intrusion> list = module.intrusions();
        return !list.isEmpty() && System.currentTimeMillis() - list.getFirst().time() < Theme.get().num(L + "toast_ms");
    }

    @Override
    public boolean hasContent() {
        return !module.intrusions().isEmpty();
    }

    private List<AlertsModule.Intrusion> data(boolean preview) {
        List<AlertsModule.Intrusion> list = module.intrusions();
        if (preview && list.isEmpty()) {
            long now = System.currentTimeMillis();
            return PREVIEW.stream().map(i -> new AlertsModule.Intrusion(i.nick(), now + i.time())).toList();
        }
        return list.subList(0, Math.min(list.size(), 1 + module.regionHistory.getInt()));
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "toast_width");
    }

    private float headHeight(Ui ui) {
        return Math.max(ui.num(L + "icon"), ui.lineHeight("alert_title"));
    }

    @Override
    public float height(Ui ui, boolean preview) {
        int older = data(preview).size() - 1;
        float h = HudStyle.insetY(ui) * 2 + headHeight(ui) + ui.num(L + "line_gap") + ui.lineHeight("alert_body");
        if (older > 0) {
            h += ui.num(L + "history_gap") + ui.num("stroke.width") + ui.num(L + "history_gap")
                    + older * ui.lineHeight("alert_history") + (older - 1) * ui.num(L + "history_row_gap");
        }
        return h;
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        List<AlertsModule.Intrusion> list = data(preview);
        if (list.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.panel(ui, x, y, w, h);
        ui.border(x, y, w, h, ui.theme().radius("panel"), ui.num("stroke.width"), ui.color("alert_bad_stroke"));
        float icon = ui.num(L + "icon");
        float cx = x + HudStyle.insetX(ui);
        float cw = w - HudStyle.insetX(ui) * 2;
        float cy = y + HudStyle.insetY(ui);
        float head = headHeight(ui);
        AlertText.warningIcon(ui, cx, cy + (head - icon) / 2f, icon, ui.color("bad"));
        float tx = cx + icon + ui.num(L + "icon_gap");
        float tw = cx + cw - tx;
        String ago = ago(now - list.getFirst().time());
        float agoW = ui.textWidth("alert_meta", ago);
        ui.textCentered("alert_title", ui.ellipsize("alert_title", Ui.tr("skirmish.alerts.region.title"), tw - agoW - ui.num(L + "icon_gap")),
                tx, cy, head, ui.color("bad"));
        ui.textCentered("alert_meta", ago, cx + cw - agoW, cy, head);
        cy += head + ui.num(L + "line_gap");
        ui.text("alert_body", ui.ellipsize("alert_body", Ui.tr("skirmish.alerts.region.body", list.getFirst().nick()), tw), tx, cy);
        cy += ui.lineHeight("alert_body");
        if (list.size() > 1) {
            cy += ui.num(L + "history_gap");
            ui.hline(cx, cy, cw, ui.color("divider"));
            cy += ui.num("stroke.width") + ui.num(L + "history_gap");
            for (int i = 1; i < list.size(); i++) {
                AlertsModule.Intrusion intrusion = list.get(i);
                String when = ago(now - intrusion.time());
                float ww = ui.textWidth("alert_meta", when);
                ui.text("alert_history", ui.ellipsize("alert_history", intrusion.nick(), tw - ww - ui.num(L + "icon_gap")), tx, cy);
                ui.text("alert_meta", when, cx + cw - ww, cy + (ui.lineHeight("alert_history") - ui.lineHeight("alert_meta")) / 2f);
                cy += ui.lineHeight("alert_history") + ui.num(L + "history_row_gap");
            }
        }
    }

    /** «сейчас», «42 с», «3 мин», «2 ч». */
    static String ago(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s < 5) {
            return Ui.tr("skirmish.alerts.ago.now");
        }
        if (s < 60) {
            return Ui.tr("skirmish.alerts.ago.seconds", s);
        }
        if (s < 3600) {
            return Ui.tr("skirmish.alerts.ago.minutes", s / 60);
        }
        return Ui.tr("skirmish.alerts.ago.hours", s / 3600);
    }
}
