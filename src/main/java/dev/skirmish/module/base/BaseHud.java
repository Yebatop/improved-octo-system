package dev.skirmish.module.base;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.module.market.parse.PriceFormat;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The base chip in the top-left column (under the event chips): «База», the storage value (or the number of
 * chests), and the alerts — a stranger in the base, low stock, a ripe farm, a region countdown running out. Shown
 * while you are in the base or something needs your attention.
 */
final class BaseHud extends HudBlock {
    private static final String L = BaseModule.L;
    private final BaseModule module;
    private String value = "";
    private List<BaseModule.Alert> rows = List.of();
    private boolean show;

    BaseHud(BaseModule module) {
        super("base_os", "skirmish.hud.element.base_os", new Placement(0, 0, 0, 0,
                Theme.get().num("layout.events.default_x"), Theme.get().num("layout.events.default_y")));
        this.module = module;
    }

    @Override
    public @Nullable String stackUnder() {
        return "event_timers";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.hud.get();
    }

    @Override
    public boolean shown() {
        update(false);
        return show;
    }

    @Override
    public boolean hasContent() {
        return show;
    }

    @Override
    public void update(boolean preview) {
        List<BaseModule.Alert> alerts = module.alerts();
        int max = Theme.get().integer(L + "hud_rows");
        rows = alerts.size() > max ? alerts.subList(0, max) : alerts;
        boolean inside = module.insideBase();
        show = module.region() != null && (inside || !alerts.isEmpty());
        value = module.pricedKinds() > 0 ? money(module.value())
                : Ui.tr("skirmish.base.chests", module.indexedChests().size());
        if (preview && !show) {
            show = true;
            value = "1,2" + Ui.tr("skirmish.market.suffix.kk");
            rows = List.of(new BaseModule.Alert(Ui.tr("skirmish.base.alert.low", "Тотем бессмертия", 2, 5), "warn"),
                    new BaseModule.Alert(Ui.tr("skirmish.base.alert.farm", "Пшеница", 64), "good"));
        } else if (show && rows.isEmpty()) {
            rows = List.of(new BaseModule.Alert(Ui.tr("skirmish.base.all_quiet"), "text_3"));
        }
    }

    static String money(double v) {
        char decimal = Ui.decimal(0.5, 1).contains(",") ? ',' : '.';
        return PriceFormat.compact(v, decimal, Ui.tr("skirmish.market.suffix.k"), Ui.tr("skirmish.market.suffix.kk"),
                Ui.tr("skirmish.market.suffix.kkk"));
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "hud_width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return ui.num(L + "hud_pad_y") * 2 + ui.lineHeight("base_title")
                + rows.size() * (ui.num(L + "hud_row_gap") + ui.lineHeight("base_row"));
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float h = height(ui, preview);
        ui.box(x, y, w, h, ui.theme().radius("tile"), ui.color("panel"), ui.color("stroke"));
        float padX = ui.num(L + "hud_pad_x");
        float padY = ui.num(L + "hud_pad_y");
        float accent = ui.num(L + "hud_accent");
        boolean alarm = rows.stream().anyMatch(r -> r.tone().equals("bad"));
        ui.rect(x + padX, y + padY, accent, h - padY * 2, accent / 2f, ui.color(alarm ? "bad" : "base_tone"));
        float tx = x + padX + accent + ui.num(L + "hud_accent_gap");
        float right = x + w - padX;
        float vw = ui.textWidth("base_value", value);
        ui.text("base_value", value, right - vw, y + padY, ui.color("base_tone"));
        ui.text("base_title", ui.ellipsize("base_title", Ui.tr("skirmish.base.title_short"), right - vw - 8 - tx), tx, y + padY);
        float cy = y + padY + ui.lineHeight("base_title");
        for (BaseModule.Alert row : rows) {
            cy += ui.num(L + "hud_row_gap");
            ui.text("base_row", ui.ellipsize("base_row", row.text(), right - tx), tx, cy, ui.color(row.tone()));
            cy += ui.lineHeight("base_row");
        }
    }
}
