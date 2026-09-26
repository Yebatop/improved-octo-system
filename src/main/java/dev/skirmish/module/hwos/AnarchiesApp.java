package dev.skirmish.module.hwos;

import dev.skirmish.module.ModuleManager;
import dev.skirmish.module.events.EventsModule;
import dev.skirmish.ui.Ui;

import java.util.List;

/**
 * Anarchies app: every Lite anarchy of the public API, grouped (Solo, Duo, Trio, …), as tiles with its live events
 * in their rarity colours and a running vote; yours is marked. Read-only: it never joins or sends anything.
 */
final class AnarchiesApp implements HwOsScreen.OsApp {
    @Override
    public String id() {
        return "anarchies";
    }

    @Override
    public void draw(HwOsScreen s, Ui ui, float x, float y, float w, float h, double mx, double my) {
        String L = HwOsScreen.L;
        EventsModule ev = ModuleManager.get().byId(EventsModule.ID) instanceof EventsModule e && e.isEnabled() ? e : null;
        List<EventsModule.Anarchy> all = ev == null ? List.of() : ev.anarchies();
        if (all.isEmpty()) {
            HwOsScreen.para(ui, "menu_row_desc", Ui.tr(ev == null ? "skirmish.hwos.events.events_off" : "skirmish.hwos.anarchies.loading"),
                    x, y, w, ui.color("text_3"));
            return;
        }
        List<OsData.Group> groups = OsData.groups(all.stream().map(EventsModule.Anarchy::name).toList());
        float gap = ui.num(L + "tile_gap");
        int cols = Math.max(2, (int) ((w + gap) / (ui.num(L + "server_tile_min") + gap)));
        float tw = (w - gap * (cols - 1)) / cols;
        float lh = ui.lineHeight("menu_row_desc");
        float pad = ui.num(L + "tile_pad");
        s.clip(ui, x - 4, y, x + w + 4, y + h);
        float top = y - s.scroll();
        float cy = top;
        for (OsData.Group g : groups) {
            int live = 0;
            for (int i : g.members()) {
                live += all.get(i).events().isEmpty() ? 0 : 1;
            }
            cy = HwOsScreen.section(ui, g.kind() + " · " + Ui.tr("skirmish.hwos.anarchies.with_events", live, g.members().size()), x, cy);
            float rowTop = cy;
            float rowH = 0;
            int col = 0;
            for (int i : g.members()) {
                EventsModule.Anarchy a = all.get(i);
                int lines = Math.max(1, Math.min(3, a.events().size())) + (a.vote().isEmpty() ? 0 : 1);
                float th = pad * 2 + ui.lineHeight("hwos_row") + 4 + lines * lh;
                float tx = x + col * (tw + gap);
                ui.box(tx, rowTop, tw, th, ui.theme().radius("tile"), ui.color(a.mine() ? "accent_16" : "fill_05"),
                        ui.color(a.mine() ? "accent" : "stroke"));
                float ly = rowTop + pad;
                String badge = a.mine() ? Ui.tr("skirmish.hwos.you_here") : "";
                float bw = badge.isEmpty() ? 0 : ui.textWidth("menu_row_desc", badge);
                if (!badge.isEmpty()) {
                    ui.text("menu_row_desc", badge, tx + tw - pad - bw, ly + 1, ui.color("accent"));
                }
                ui.text("hwos_row", ui.ellipsize("hwos_row", a.name(), tw - pad * 2 - bw - 6), tx + pad, ly);
                ly += ui.lineHeight("hwos_row") + 4;
                if (a.events().isEmpty()) {
                    ui.text("menu_row_desc", Ui.tr("skirmish.hwos.anarchies.quiet"), tx + pad, ly, ui.color("text_4"));
                    ly += lh;
                }
                for (int k = 0; k < Math.min(3, a.events().size()); k++) {
                    EventsModule.LiveEvent e = a.events().get(k);
                    ui.circle(tx + pad + 3, ly + lh / 2f, 6, ui.color(HwOsScreen.rarityTone(e.rarity())));
                    ui.text("menu_row_desc", ui.ellipsize("menu_row_desc", e.name() + " · " + Ui.tr("skirmish.events.rarity." + e.rarity().key()),
                            tw - pad * 2 - 12), tx + pad + 12, ly, ui.color(HwOsScreen.rarityTone(e.rarity())));
                    ly += lh;
                }
                if (!a.vote().isEmpty()) {
                    ui.text("menu_row_desc", ui.ellipsize("menu_row_desc", Ui.tr("skirmish.hwos.anarchies.vote", String.join(", ", a.vote())),
                            tw - pad * 2), tx + pad, ly, ui.color("warn"));
                }
                rowH = Math.max(rowH, th);
                col++;
                if (col == cols) {
                    col = 0;
                    rowTop += rowH + gap;
                    rowH = 0;
                }
            }
            cy = rowTop + (col > 0 ? rowH + gap : 0) + 6;
        }
        s.unclip(ui);
        s.content(cy - top, h);
    }
}
