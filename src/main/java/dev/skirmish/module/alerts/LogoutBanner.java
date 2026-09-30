package dev.skirmish.module.alerts;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.module.alerts.parse.LossItems;
import dev.skirmish.ui.Ui;

import java.util.List;

/** «Режим шалкера» banner: shown while logging out would drop shulker boxes, backpacks or Elements. */
final class LogoutBanner extends HudBlock {
    private static final String L = "layout.alerts.";
    private static final LossItems.Tally PREVIEW = new LossItems.Tally(3, 0, 0);

    private final AlertsModule module;

    LogoutBanner(AlertsModule module) {
        super("alerts_logout", "skirmish.hud.element.alerts_logout", new Placement(0.5f, 0f, 0.5f, 0f, 0, dev.skirmish.ui.Theme.get().num("layout.hud.top_column_dy")));
        this.module = module;
    }

    @Override
    public @org.jspecify.annotations.Nullable String stackUnder() {
        return "combat_tag";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.logoutWarning.get();
    }

    @Override
    public boolean shown() {
        return module.logoutRisk();
    }

    private LossItems.Tally data(boolean preview) {
        LossItems.Tally t = module.tally();
        return preview && !t.atRisk() ? PREVIEW : t;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "banner_width");
    }

    private List<String> body(Ui ui, boolean preview) {
        String text = Ui.tr("skirmish.alerts.banner.body", AlertText.losses(data(preview)));
        return ui.wrap("alert_body", text, textWidth(ui, preview));
    }

    private float textWidth(Ui ui, boolean preview) {
        return width(ui, preview) - HudStyle.insetX(ui) * 2 - ui.num(L + "icon") - ui.num(L + "icon_gap");
    }

    private float headHeight(Ui ui) {
        return Math.max(ui.num(L + "icon"), ui.lineHeight("alert_title"));
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return HudStyle.insetY(ui) * 2 + headHeight(ui) + ui.num(L + "line_gap")
                + body(ui, preview).size() * ui.lineHeight("alert_body");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.panel(ui, x, y, w, h);
        ui.border(x, y, w, h, ui.theme().radius("panel"), ui.num("stroke.width"), ui.color("alert_warn_stroke"));
        float icon = ui.num(L + "icon");
        float cx = x + HudStyle.insetX(ui);
        float cy = y + HudStyle.insetY(ui);
        float head = headHeight(ui);
        AlertText.warningIcon(ui, cx, cy + (head - icon) / 2f, icon, ui.color("warn"));
        float tx = cx + icon + ui.num(L + "icon_gap");
        ui.textCentered("alert_title", ui.ellipsize("alert_title", AlertText.title(data(preview)), textWidth(ui, preview)), tx, cy, head);
        cy += head + ui.num(L + "line_gap");
        for (String line : body(ui, preview)) {
            ui.text("alert_body", line, tx, cy);
            cy += ui.lineHeight("alert_body");
        }
    }
}
