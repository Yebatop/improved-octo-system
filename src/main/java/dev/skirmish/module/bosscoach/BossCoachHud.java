package dev.skirmish.module.bosscoach;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The boss panel: name and HP percent, health in HP when the wiki gives it, the current phase with its wiki text, a
 * counter tip and my damage share / DPS. Default place: top centre under the vanilla boss bars (clear of three bars
 * at any GUI scale), below the waypoint pill and the logout banner.
 */
final class BossCoachHud extends HudBlock {
    static final String L = "layout.bosscoach.";
    private final BossCoachModule module;

    private record Row(String style, String text, @Nullable String color, float gapBefore) {
    }

    private String title = "";
    private String meta = "";
    private List<Row> rows = List.of();
    private boolean content;

    BossCoachHud(BossCoachModule module) {
        super(BossCoachModule.ID, "skirmish.hud.element.boss_coach", new Placement(0.5f, 0f, 0.5f, 0f, 0,
                Theme.get().num(L + "default_y")));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public boolean shown() {
        return module.primary() != null;
    }

    @Override
    public boolean hasContent() {
        return content;
    }

    @Override
    public void update(boolean preview) {
        BossCoachModule.Tracked t = module.primary();
        if (t == null && preview) {
            t = sample();
        }
        if (t == null) {
            return;
        }
        build(t, preview);
        content = true;
    }

    /** Детонатор at 62 % with some of my damage, for the HUD editor. */
    private BossCoachModule.@Nullable Tracked sample() {
        BossTable.Boss boss = module.table().bosses().stream().filter(b -> b.id().equals("detonator")).findFirst()
                .orElse(module.table().bosses().isEmpty() ? null : module.table().bosses().getFirst());
        if (boss == null) {
            return null;
        }
        BossCoachModule.Tracked t = new BossCoachModule.Tracked(UUID.randomUUID(), boss, "");
        long now = System.currentTimeMillis();
        t.meter.progress(1f, now - 8000, 450);
        t.meter.myHit(now - 4100, true);
        t.meter.progress(0.9f, now - 4000, 450);
        t.meter.progress(0.62f, now - 1000, 450);
        t.progress = 0.62f;
        return t;
    }

    private void build(BossCoachModule.Tracked t, boolean preview) {
        BossTable.Boss boss = t.boss;
        double percent = t.progress * 100.0;
        title = bossText(boss.id(), "");
        meta = Ui.decimal(percent, percent < 10 ? 1 : 0) + "%";
        List<Row> out = new ArrayList<>();
        float gap = Theme.get().num(L + "row_gap");
        float section = Theme.get().num(L + "section_gap");
        if (boss.hp() > 0) {
            out.add(new Row("bc_line", Ui.tr("skirmish.bosscoach.hp", group(Math.round(boss.hp() * t.progress)), group(boss.hp())),
                    null, gap));
        }
        if (boss.pair()) {
            BossCoachModule.Tracked other = null;
            for (BossCoachModule.Tracked o : module.onScreen()) {
                if (o != t && o.boss.id().equals(boss.id())) {
                    other = o;
                }
            }
            if (other != null) {
                double diff = Math.abs(t.progress - other.progress) * 100.0;
                out.add(new Row("bc_line", Ui.tr("skirmish.bosscoach.pair_gap", Ui.decimal(diff, 0)),
                        diff >= 10 ? "bad" : diff >= 5 ? "warn" : "good", gap));
            }
        }
        if (module.phases.get() && !boss.phases().isEmpty()) {
            BossTable.PhaseAt at = BossTable.phaseAt(boss, percent);
            if (at.index() < 0 && at.phase() != null) {
                out.add(new Row("bc_phase", Ui.tr("skirmish.bosscoach.before_phase", at.phase().from()), null, section));
            } else if (at.phase() != null) {
                out.add(new Row("bc_phase", Ui.tr("skirmish.bosscoach.phase", at.index() + 1, at.phase().from(), at.phase().to()),
                        null, section));
                String text = bossText(boss.id(), ".phase." + (at.index() + 1));
                if (!text.isEmpty()) {
                    out.add(new Row("bc_text", text, null, gap));
                }
            }
        }
        if (module.tips.get()) {
            String tip = bossText(boss.id(), ".tip");
            if (!tip.isEmpty()) {
                out.add(new Row("bc_tip", tip, null, section));
            }
            if (boss.top3()) {
                out.add(new Row("bc_text", Ui.tr("skirmish.bosscoach.top3"), "warn", gap));
            }
        }
        if (module.meter.get()) {
            out.add(new Row("bc_meter", meterLine(t, System.currentTimeMillis()), null, section));
        }
        rows = out;
    }

    private String meterLine(BossCoachModule.Tracked t, long now) {
        if (t.meter.myHits() == 0) {
            return Ui.tr("skirmish.bosscoach.no_hits");
        }
        String share = Ui.decimal(BossCoachModule.share(t) * 100.0, 0);
        double rate = t.meter.rate(now, module.dpsWindowMs());
        String dps = t.boss.hp() > 0 ? Ui.tr("skirmish.bosscoach.dps_hp", group(Math.round(rate * t.boss.hp())))
                : Ui.tr("skirmish.bosscoach.dps_pct", Ui.decimal(rate * 100.0, 1));
        return Ui.tr("skirmish.bosscoach.meter", share, dps);
    }

    /** "6 480": thousands separated by a narrow no-break space. */
    static String group(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(' ');
            }
            out.append(digits.charAt(i));
        }
        return (value < 0 ? "-" : "") + out;
    }

    static String bossText(String id, String suffix) {
        String key = "skirmish.bosscoach.boss." + id + suffix;
        String text = Ui.tr(key);
        return text.equals(key) ? (suffix.isEmpty() ? id : "") : text;
    }

    private float innerWidth(Ui ui) {
        return ui.num(L + "width") - HudStyle.insetX(ui) * 2;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        float h = HudStyle.insetY(ui) * 2 + HudStyle.headerHeight(ui);
        float inner = innerWidth(ui);
        for (Row row : rows) {
            h += row.gapBefore() + lines(ui, row, inner).size() * ui.lineHeight(row.style());
        }
        return h;
    }

    private List<String> lines(Ui ui, Row row, float inner) {
        List<String> wrapped = ui.wrap(row.style(), row.text(), inner);
        int max = (int) ui.num(L + "max_lines");
        if (wrapped.size() > max) {
            List<String> cut = new ArrayList<>(wrapped.subList(0, max));
            cut.set(max - 1, ui.ellipsize(row.style(), cut.get(max - 1) + " …", inner));
            return cut;
        }
        return wrapped;
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        HudStyle.panel(ui, x, y, w, height(ui, preview));
        float ix = x + HudStyle.insetX(ui);
        float iy = y + HudStyle.insetY(ui);
        float inner = innerWidth(ui);
        float hh = HudStyle.headerHeight(ui);
        float metaW = ui.textWidth("panel_meta", meta);
        ui.textCentered("panel_title", ui.ellipsize("panel_title", title, inner - metaW - ui.num(L + "row_gap") * 2), ix, iy, hh);
        ui.textCentered("panel_meta", meta, ix + inner - metaW, iy, hh);
        float cy = iy + hh;
        for (Row row : rows) {
            cy += row.gapBefore();
            for (String line : lines(ui, row, inner)) {
                if (row.color() != null) {
                    ui.text(row.style(), line, ix, cy, ui.color(row.color()));
                } else {
                    ui.text(row.style(), line, ix, cy);
                }
                cy += ui.lineHeight(row.style());
            }
        }
    }
}
