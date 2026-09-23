package dev.skirmish.combat;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/** HUD elements of the combat tracker: the «Бой» panel (current fight) and the session score. */
final class CombatHud {
    private CombatHud() {
    }

    /** Current (or just finished) fight: damage dealt/taken, opponent totems, hits / attempts, duration. */
    static final class FightPanel extends HudBlock {
        private final CombatTrackerModule module;
        private @Nullable Fight fight;

        FightPanel(CombatTrackerModule module) {
            super("combat", "skirmish.hud.element.combat", new Placement(0, 0, 0, 0, 18, 150));
            this.module = module;
        }

        @Override
        public boolean enabled() {
            return module.combatPanel.get();
        }

        @Override
        public void update(boolean preview) {
            Fight best = null;
            for (Fight f : CombatTracker.get().activeFights()) {
                if (best == null || f.lastActivityMs() > best.lastActivityMs()) {
                    best = f;
                }
            }
            if (best == null) {
                Fight last = CombatTracker.get().lastFinished();
                long linger = Math.round(dev.skirmish.ui.Theme.get().num("layout.hud.combat_linger_ms"));
                if (last != null && System.currentTimeMillis() - last.endMs() < linger) {
                    best = last;
                }
            }
            fight = best;
        }

        @Override
        public boolean shown() {
            update(false);
            return fight != null;
        }

        @Override
        public boolean hasContent() {
            return fight != null || CombatTracker.get().lastFinished() != null;
        }

        private @Nullable Fight data() {
            return fight != null ? fight : CombatTracker.get().lastFinished();
        }

        @Override
        public float width(Ui ui, boolean preview) {
            return ui.num("layout.hud.combat_width");
        }

        @Override
        public float height(Ui ui, boolean preview) {
            float row = rowHeight(ui);
            float sep = ui.num("layout.panel_row_gap") * 2 + ui.num("stroke.width");
            return HudStyle.insetY(ui) * 2 + HudStyle.headerHeight(ui) + ui.num("layout.panel_gap") + row * 4 + sep * 3;
        }

        private static float rowHeight(Ui ui) {
            return Math.max(ui.lineHeight("row"), ui.lineHeight("row_value"));
        }

        @Override
        public void render(Ui ui, float x, float y, boolean preview) {
            float w = width(ui, preview);
            float h = height(ui, preview);
            HudStyle.panel(ui, x, y, w, h);
            float cx = x + HudStyle.insetX(ui);
            float cw = w - HudStyle.insetX(ui) * 2;
            float cy = y + HudStyle.insetY(ui);

            Fight f = preview ? null : data();
            String time = f == null ? "0:47" : Ui.duration(f.durationMs(System.currentTimeMillis()));
            float[] icon = HudStyle.header(ui, cx, cy, cw, Ui.tr("skirmish.hud.combat.title"), time);
            Icons.sword(ui, icon[0], icon[1], ui.num("layout.panel_icon"), 2f, ui.color("accent"), true);
            cy += HudStyle.headerHeight(ui) + ui.num("layout.panel_gap");

            String dealt = f == null ? Ui.decimal(34.5, 1) : Ui.decimal(f.damageDealt(), 1);
            String taken = f == null ? Ui.decimal(21.0, 1) : Ui.decimal(f.damageTaken(), 1);
            String totems = f == null ? "2" : Integer.toString(f.opponentTotems());
            String hits = f == null ? "18 / 23" : f.hitsDealt() + " / " + f.attackAttempts();
            String[][] rows = {
                    {"skirmish.hud.combat.dealt", dealt, "good"},
                    {"skirmish.hud.combat.taken", taken, "bad"},
                    {"skirmish.hud.combat.totems", totems, "text"},
                    {"skirmish.hud.combat.hits", hits, "text"}};
            float row = rowHeight(ui);
            float gap = ui.num("layout.panel_row_gap");
            for (int i = 0; i < rows.length; i++) {
                if (i > 0) {
                    cy += gap;
                    ui.hline(cx, cy, cw, ui.color("divider"));
                    cy += ui.num("stroke.width") + gap;
                }
                ui.text("row", Ui.tr(rows[i][0]), cx, cy);
                String value = rows[i][1];
                ui.text("row_value", value, cx + cw - ui.textWidth("row_value", value), cy, ui.color(rows[i][2]));
                cy += row;
            }
        }
    }

    /** Kills, deaths and K/D since joining the server. */
    static final class SessionPanel extends HudBlock {
        private final CombatTrackerModule module;

        SessionPanel(CombatTrackerModule module) {
            super("session", "skirmish.hud.element.session", new Placement(1, 1, 1, 1, -18, -70));
            this.module = module;
        }

        @Override
        public boolean enabled() {
            return module.sessionPanel.get();
        }

        @Override
        public boolean shown() {
            return Minecraft.getInstance().player != null;
        }

        @Override
        public float width(Ui ui, boolean preview) {
            return ui.num("layout.hud.session_width");
        }

        @Override
        public float height(Ui ui, boolean preview) {
            return HudStyle.insetY(ui) * 2 + HudStyle.headerHeight(ui) + ui.num("layout.panel_gap")
                    + ui.lineHeight("stat_value") + ui.num("layout.hud.session_value_gap") + ui.lineHeight("stat_label");
        }

        @Override
        public void render(Ui ui, float x, float y, boolean preview) {
            float w = width(ui, preview);
            HudStyle.panel(ui, x, y, w, height(ui, preview));
            float cx = x + HudStyle.insetX(ui);
            float cw = w - HudStyle.insetX(ui) * 2;
            float cy = y + HudStyle.insetY(ui);
            float[] icon = HudStyle.header(ui, cx, cy, cw, Ui.tr("skirmish.hud.session.title"), null);
            Icons.clock(ui, icon[0], icon[1], ui.num("layout.panel_icon"), 2f, ui.color("accent"));
            cy += HudStyle.headerHeight(ui) + ui.num("layout.panel_gap");

            int kills = preview ? 7 : module.session().kills();
            int deaths = preview ? 3 : module.session().deaths();
            String kd = Ui.decimal(deaths == 0 ? kills : kills / (double) deaths, 2);
            String[][] cells = {
                    {Integer.toString(kills), Ui.plural("skirmish.hud.session.kills", kills), "text"},
                    {Integer.toString(deaths), Ui.plural("skirmish.hud.session.deaths", deaths), "text"},
                    {kd, Ui.tr("skirmish.hud.session.kd"), "accent"}};
            float gap = ui.num("layout.hud.session_grid_gap");
            float col = (cw - gap * 2) / 3f;
            for (int i = 0; i < cells.length; i++) {
                float colX = cx + i * (col + gap);
                ui.text("stat_value", cells[i][0], colX, cy, ui.color(cells[i][2]));
                ui.text("stat_label", cells[i][1], colX, cy + ui.lineHeight("stat_value") + ui.num("layout.hud.session_value_gap"));
            }
        }
    }
}
