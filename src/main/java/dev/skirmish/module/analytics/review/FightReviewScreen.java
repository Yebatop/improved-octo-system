package dev.skirmish.module.analytics.review;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.combat.EquipmentSnapshot;
import dev.skirmish.combat.Fight;
import dev.skirmish.combat.FightEndReason;
import dev.skirmish.module.analytics.AnalyticsText;
import dev.skirmish.module.analytics.dossier.DossierModule;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.IconButton;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.ui.widget.Widget;
import dev.skirmish.ui.widget.WindowFrame;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * «Разбор боя» window: the last fights (and deaths outside fights) on the left, the selected one on the right —
 * header with outcome, stat tiles, hit timeline, reach of my hits, health curves, the opponent's gear and, after my
 * death, «Кто и как тебя убил». Movable and resizable like the other windows; content scrolls.
 */
final class FightReviewScreen extends UiScreen {
    private static final String L = ReviewCharts.L;

    private final FightReviewModule module;
    private final WindowFrame frame = new WindowFrame("fight_review", L);
    private @Nullable ReviewEntry selected;
    private final Map<ReviewEntry, Row> rows = new IdentityHashMap<>();
    private final IconButton close = new IconButton("fill_06", "rec_16", "button_sm", (u, b) -> {
        float s = u.num(L + "close_icon");
        float[] at = b.iconAt(s);
        Icons.close(u, at[0], at[1], s, 2.2f, u.color("text_2"));
    }, this::onClose);
    private float listScroll;
    private float listMax;
    private float scroll;
    private float maxScroll;
    private float listRight;

    FightReviewScreen(FightReviewModule module, @Nullable ReviewEntry selected, @Nullable Screen parent) {
        super(Component.translatable("skirmish.analytics.review.title"), parent);
        this.module = module;
        this.selected = selected;
    }

    private void select(ReviewEntry entry) {
        if (entry != selected) {
            selected = entry;
            scroll = 0;
        }
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        ui.rect(0, 0, ui.width(), ui.height(), 0, ui.color("backdrop"));
        float stroke = ui.num("stroke.width");
        frame.layout(ui);
        widget(ui, frame.mover, mx, my);
        float w = frame.w();
        float h = frame.h();
        float ox = frame.x();
        float oy = frame.y() + Math.round((1f - appearProgress()) * ui.num("layout.appear_offset"));
        ui.box(ox, oy, w, h, ui.theme().radius("window"), ui.color("window"), ui.color("stroke_07"));
        float x = ox + stroke;
        float y = oy + stroke;
        float cw = w - stroke * 2;
        float ch = h - stroke * 2;
        float listW = Math.min(ui.num(L + "list_width"), cw * 0.4f);
        List<ReviewEntry> entries = module.entries();
        if (selected != null && !entries.contains(selected)) {
            selected = entries.isEmpty() ? null : entries.getFirst();
        }
        drawList(ui, entries, x, y, listW, ch, mx, my);
        listRight = x + listW;
        ReviewCharts.Tip tip = new ReviewCharts.Tip();
        drawContent(ui, x + listW, y, cw - listW, ch, mx, my, tip);
        widget(ui, frame.grip, mx, my);
        ReviewCharts.drawTip(ui, tip);
    }

    // ---- list ----

    private void drawList(Ui ui, List<ReviewEntry> entries, float x, float y, float w, float h, double mx, double my) {
        float stroke = ui.num("stroke.width");
        float inner = ui.theme().radius("window") - stroke;
        pushClip(ui, x, y, x + w, y + h);
        ui.rect(x, y, w + inner * 2, h, inner, ui.color("sidebar"));
        popClip(ui);
        ui.rect(x + w - stroke, y, stroke, h, 0, ui.color("stroke"));

        float padX = ui.num(L + "list_pad_x");
        float padY = ui.num(L + "list_pad_y");
        float cx = x + padX;
        float cwid = w - stroke - padX * 2;
        float cy = y + padY;
        String title = Ui.tr("skirmish.analytics.review.list");
        ui.text("fa_list_title", title, cx + ui.num(L + "row_pad_x"), cy);
        String count = Integer.toString(entries.size());
        ui.text("fa_list_title", count, cx + cwid - ui.num(L + "row_pad_x") - ui.textWidth("fa_list_title", count), cy, ui.color("text_4"));
        cy += ui.lineHeight("fa_list_title") + ui.num(L + "list_header_gap");

        float top = cy;
        float bottom = y + h - padY;
        if (entries.isEmpty()) {
            for (String line : ui.wrap("fa_list_sub", Ui.tr("skirmish.analytics.review.list_empty"), cwid - ui.num(L + "row_pad_x") * 2)) {
                ui.text("fa_list_sub", line, cx + ui.num(L + "row_pad_x"), cy);
                cy += ui.lineHeight("fa_list_sub");
            }
            return;
        }
        pushClip(ui, x, top, x + w, bottom);
        float rowH = ui.num(L + "row_height");
        float ry = top - listScroll;
        for (ReviewEntry e : entries) {
            Row row = rows.computeIfAbsent(e, Row::new);
            row.bounds(cx, ry, cwid, rowH);
            widget(ui, row, mx, my);
            ry += rowH + ui.num(L + "row_gap");
        }
        popClip(ui);
        rows.keySet().retainAll(entries);
        listMax = Math.max(0f, ry + listScroll - top - (bottom - top));
        listScroll = Math.max(0f, Math.min(listScroll, listMax));
    }

    private final class Row extends Widget {
        private final ReviewEntry entry;
        private final Anim active = new Anim("hover_ms");

        Row(ReviewEntry entry) {
            this.entry = entry;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            float a = active.target(entry == selected).value();
            int idle = Anim.lerpColor(ui.color("fill_00"), ui.color("fill_04"), hovered());
            ui.rect(x, y, w, h, ui.theme().radius("button"), Anim.lerpColor(idle, ui.color("accent_16"), a));
            float padX = ui.num(L + "row_pad_x");
            float dot = ui.num(L + "row_dot");
            Outcome outcome = entry.outcome();
            ui.circle(x + padX + dot / 2f, y + h / 2f, dot, ui.color(outcome.color()));
            float tx = x + padX + dot + ui.num(L + "row_dot_gap");
            Fight f = entry.fight();
            String score = f == null ? "" : f.hitsDealt() + "–" + f.hitsTaken();
            float scoreW = ui.textWidth("fa_list_score", score);
            float textW = x + w - padX - tx - (score.isEmpty() ? 0 : scoreW + ui.num(L + "row_dot_gap"));
            float textH = ui.lineHeight("fa_list_name") + ui.num(L + "row_text_gap") + ui.lineHeight("fa_list_sub");
            float ty = y + (h - textH) / 2f;
            String name = entry.opponentName() == null ? Ui.tr("skirmish.analytics.review.unknown") : entry.opponentName();
            if (f == null) {
                name = Ui.tr("skirmish.analytics.review.death_title");
            }
            ui.text("fa_list_name", ui.ellipsize("fa_list_name", name, textW), tx, ty);
            String sub = Ui.tr(outcome.langKey()) + " · " + AnalyticsText.ago(System.currentTimeMillis() - entry.timeMs());
            ui.text("fa_list_sub", ui.ellipsize("fa_list_sub", sub, textW), tx, ty + ui.lineHeight("fa_list_name") + ui.num(L + "row_text_gap"));
            if (!score.isEmpty()) {
                ui.textCentered("fa_list_score", score, x + w - padX - scoreW, y, h);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                select(entry);
                return true;
            }
            return false;
        }
    }

    // ---- content ----

    private void drawContent(Ui ui, float x, float y, float w, float h, double mx, double my, ReviewCharts.Tip tip) {
        float padX = ui.num(L + "content_pad_x");
        float padY = ui.num(L + "content_pad_y");
        float cx = x + padX;
        float cw = w - padX * 2;
        float cy = y + padY;
        float closeS = ui.num(L + "close_size");
        close.bounds(x + w - padX - closeS + ui.num(L + "close_inset"), y + padY - ui.num(L + "close_inset"), closeS, closeS);

        ReviewEntry e = selected;
        if (e == null) {
            ui.text("fa_title", Ui.tr("skirmish.analytics.review.title"), cx, cy);
            cy += ui.lineHeight("fa_title") + ui.num(L + "header_gap");
            for (String line : ui.wrap("fa_sub", Ui.tr("skirmish.analytics.review.empty"), cw - closeS)) {
                ui.text("fa_sub", line, cx, cy);
                cy += ui.lineHeight("fa_sub");
            }
            widget(ui, close, mx, my);
            return;
        }

        // Header: name + outcome chip, then duration / age / first hit, then the dossier score.
        Fight f = e.fight();
        String name = f == null ? Ui.tr("skirmish.analytics.review.death_title") : e.opponentName();
        Outcome outcome = e.outcome();
        String chip = Ui.tr(outcome.langKey()).toUpperCase(java.util.Locale.ROOT);
        float chipW = ui.textWidth("fa_chip", chip) + ui.num(L + "chip_pad_x") * 2;
        float titleW = cw - closeS - chipW - ui.num(L + "chip_gap") * 2;
        String title = ui.ellipsize("fa_title", name == null ? "?" : name, titleW);
        float after = ui.text("fa_title", title, cx, cy);
        float chipH = ui.num(L + "chip_height");
        float chipY = cy + (ui.lineHeight("fa_title") - chipH) / 2f;
        String bg = outcome.good() ? "fa_good_16" : outcome.bad() ? "fa_bad_16" : "fa_neutral_16";
        ui.rect(after + ui.num(L + "chip_gap"), chipY, chipW, chipH, ui.theme().radius("chip"), ui.color(bg));
        ui.textCentered("fa_chip", chip, after + ui.num(L + "chip_gap") + ui.num(L + "chip_pad_x"), chipY, chipH, ui.color(outcome.color()));
        widget(ui, close, mx, my);
        cy += ui.lineHeight("fa_title") + ui.num(L + "header_gap");

        List<String> subs = new ArrayList<>();
        FightLog log = e.log();
        if (f != null) {
            subs.add(Ui.tr("skirmish.analytics.review.duration", Ui.duration(ReviewText.activeDuration(e))));
        }
        subs.add(AnalyticsText.ago(System.currentTimeMillis() - e.timeMs()));
        if (log != null) {
            Boolean first = FightStats.iHitFirst(log.events());
            if (first != null) {
                subs.add(first ? Ui.tr("skirmish.analytics.review.first_me") : Ui.tr("skirmish.analytics.review.first_them"));
            }
        }
        ui.text("fa_sub", ui.ellipsize("fa_sub", String.join(" · ", subs), cw), cx, cy);
        cy += ui.lineHeight("fa_sub");
        String dossier = e.opponentUuid() == null ? null : DossierModule.summary(e.opponentUuid());
        if (dossier != null) {
            cy += ui.num(L + "row_text_gap");
            ui.text("fa_sub", ui.ellipsize("fa_sub", dossier, cw), cx, cy, ui.color("text_3"));
            cy += ui.lineHeight("fa_sub");
        }
        cy += ui.num(L + "header_gap");
        ui.hline(cx, cy, cw, ui.color("stroke"));
        cy += ui.num("stroke.width");

        float top = cy;
        float bottom = y + h - padY / 2f;
        pushClip(ui, x, top, x + w, bottom);
        float sy = top - scroll + ui.num(L + "section_gap") * 0.75f;
        float gap = ui.num(L + "section_gap");
        if (e.death() != null) {
            sy = deathRecap(ui, e, e.death(), cx, sy, cw, mx, my) + gap;
        }
        if (f != null) {
            sy = tiles(ui, f, log, cx, sy, cw) + gap;
            if (log != null) {
                long start = f.startMs();
                long end = ReviewText.axisEnd(e);
                String endColor = f.endReason() == FightEndReason.KILL ? "good"
                        : f.endReason() == FightEndReason.OWN_DEATH ? "rec" : f.endReason() == FightEndReason.OPPONENT_DIED ? "text_3" : null;
                sy = section(ui, Ui.tr("skirmish.analytics.review.timeline"), null, cx, sy, cw);
                ReviewCharts.timeline(ui, cx, sy, cw, log, start, end, e.opponentName(), endColor, mx, my, tip);
                sy += ReviewCharts.timelineHeight(ui);
                sy = legend(ui, cx, sy, endColor) + gap;

                double avg = FightStats.averageReach(log.events());
                String meta = Double.isFinite(avg) ? Ui.tr("skirmish.analytics.review.reach_meta", AnalyticsText.reach(avg),
                        AnalyticsText.reach(FightStats.maxReach(log.events()))) : null;
                sy = section(ui, Ui.tr("skirmish.analytics.review.reach"), meta, cx, sy, cw);
                ReviewCharts.reach(ui, cx, sy, cw, log, start, end, mx, my, tip);
                sy += ReviewCharts.chartHeight(ui) + gap;

                boolean theirs = f.isDamageKnown() && !log.theirHp().isEmpty();
                sy = section(ui, Ui.tr("skirmish.analytics.review.health"), null, cx, sy, cw);
                ReviewCharts.health(ui, cx, sy, cw, log.myHp(), theirs ? log.theirHp() : null,
                        Math.max(log.myMaxHp(), theirs ? log.theirMaxHp() : 0f), start, end, null, false);
                sy += ReviewCharts.chartHeight(ui);
                float lh = ui.num(L + "legend_height");
                float lx = ReviewCharts.legend(ui, cx, sy + ui.num(L + "legend_top"), lh, "line", "good", Ui.tr("skirmish.analytics.review.you"));
                if (theirs) {
                    ReviewCharts.legend(ui, lx + ui.num(L + "legend_gap"), sy + ui.num(L + "legend_top"), lh, "line", "bad", e.opponentName());
                } else {
                    ui.textCentered("fa_legend", Ui.tr("skirmish.analytics.review.their_hp_hidden"), lx + ui.num(L + "legend_gap"),
                            sy + ui.num(L + "legend_top"), lh, ui.color("text_3"));
                }
                sy += ui.num(L + "legend_top") + lh + gap;
            }
            sy = gear(ui, e.gear(), cx, sy, cw) + gap;
        }
        popClip(ui);
        float content = sy + scroll - top;
        maxScroll = Math.max(0f, content - (bottom - top));
        scroll = Math.max(0f, Math.min(scroll, maxScroll));
        if (maxScroll > 0f) {
            float track = bottom - top;
            float barW = ui.num("layout.menu.scrollbar_width");
            float barH = Math.max(ui.num("layout.menu.scrollbar_min"), track * track / (track + maxScroll));
            float barY = top + (track - barH) * (scroll / maxScroll);
            ui.rect(x + w - padX / 2f - barW / 2f, barY, barW, barH, barW / 2f, ui.color("stroke_12"));
        }
    }

    /** Section label with optional right-aligned meta; returns the y below it. */
    private static float section(Ui ui, String label, @Nullable String meta, float x, float y, float w) {
        ui.text("fa_section", label.toUpperCase(java.util.Locale.ROOT), x, y);
        if (meta != null) {
            ui.text("fa_legend", meta, x + w - ui.textWidth("fa_legend", meta), y);
        }
        return y + Math.max(ui.lineHeight("fa_section"), ui.lineHeight("fa_legend")) + ui.num(L + "section_label_gap");
    }

    private static float legend(Ui ui, float x, float y, @Nullable String endColor) {
        float lh = ui.num(L + "legend_height");
        float ly = y + ui.num(L + "legend_top");
        float gap = ui.num(L + "legend_gap");
        float cx = ReviewCharts.legend(ui, x, ly, lh, "bar", "accent", Ui.tr("skirmish.analytics.review.legend.my_hit"));
        cx = ReviewCharts.legend(ui, cx + gap, ly, lh, "bar", "bad", Ui.tr("skirmish.analytics.review.legend.their_hit"));
        cx = ReviewCharts.legend(ui, cx + gap, ly, lh, "dot", "fa_crit", Ui.tr("skirmish.analytics.review.legend.crit"));
        cx = ReviewCharts.legend(ui, cx + gap, ly, lh, "diamond", "warn", Ui.tr("skirmish.analytics.review.legend.totem"));
        if (endColor != null) {
            ReviewCharts.legend(ui, cx + gap, ly, lh, "bar", endColor, Ui.tr("skirmish.analytics.review.legend.end"));
        }
        return ly + lh;
    }

    /** 4 × 2 stat tiles; returns the y below them. */
    private float tiles(Ui ui, Fight f, @Nullable FightLog log, float x, float y, float w) {
        List<FightLog.Event> events = log == null ? List.of() : log.events();
        boolean known = f.isDamageKnown();
        int attempts = f.attackAttempts();
        int percent = attempts == 0 ? 0 : Math.round(f.hitsDealt() * 100f / attempts);
        Boolean first = FightStats.iHitFirst(events);
        double avg = FightStats.averageReach(events);
        String opponent = f.opponent().name();
        String[][] tiles = {
                {known ? AnalyticsText.hp(f.damageDealt()) : "—", Ui.tr(known ? "skirmish.analytics.review.tile.dealt" : "skirmish.analytics.review.tile.dealt_hidden"), "good"},
                {AnalyticsText.hp(f.damageTaken()), Ui.tr("skirmish.analytics.review.tile.taken"), "bad"},
                {f.hitsDealt() + " / " + attempts, Ui.tr("skirmish.analytics.review.tile.hits", percent), "text"},
                {FightStats.crits(events, true) + " : " + FightStats.crits(events, false), Ui.tr("skirmish.analytics.review.tile.crits"), "fa_crit"},
                {Integer.toString(FightStats.longestCombo(events)), Ui.tr("skirmish.analytics.review.tile.combo"), "accent"},
                {f.myTotems() + " : " + f.opponentTotems(), Ui.tr("skirmish.analytics.review.tile.totems"), "warn"},
                {AnalyticsText.reach(avg), Ui.tr("skirmish.analytics.review.tile.reach"), "text"},
                {first == null ? "—" : first ? Ui.tr("skirmish.analytics.review.you") : opponent, Ui.tr("skirmish.analytics.review.tile.first"), "text"}};
        int cols = Math.max(1, Math.round(ui.num(L + "tile_columns")));
        float gap = ui.num(L + "tile_gap");
        float tw = (w - gap * (cols - 1)) / cols;
        float padX = ui.num(L + "tile_pad_x");
        float padY = ui.num(L + "tile_pad_y");
        float th = padY * 2 + ui.lineHeight("fa_tile_value") + ui.num(L + "tile_value_gap") + ui.lineHeight("fa_tile_label");
        for (int i = 0; i < tiles.length; i++) {
            float tx = x + (i % cols) * (tw + gap);
            float ty = y + (i / cols) * (th + gap);
            ui.box(tx, ty, tw, th, ui.theme().radius("tile"), ui.color("tile"), ui.color("stroke"));
            ui.text("fa_tile_value", ui.ellipsize("fa_tile_value", tiles[i][0], tw - padX * 2), tx + padX, ty + padY, ui.color(tiles[i][2]));
            ui.text("fa_tile_label", ui.ellipsize("fa_tile_label", tiles[i][1], tw - padX * 2), tx + padX,
                    ty + padY + ui.lineHeight("fa_tile_value") + ui.num(L + "tile_value_gap"));
        }
        int rowsN = (tiles.length + cols - 1) / cols;
        return y + rowsN * th + (rowsN - 1) * gap;
    }

    /** Opponent equipment as last seen in the fight. */
    private static float gear(Ui ui, @Nullable EquipmentSnapshot gear, float x, float y, float w) {
        if (gear == null || gear.isEmpty()) {
            return y;
        }
        y = section(ui, Ui.tr("skirmish.analytics.review.gear"), null, x, y, w);
        float icon = ui.num(L + "gear_icon");
        float gap = ui.num(L + "gear_gap");
        float cx = x;
        for (EquipmentSlot slot : EquipmentSnapshot.SLOTS) {
            ItemStack stack = gear.get(slot);
            ui.rect(cx, y, icon + gap, icon + gap, ui.theme().radius("slot"), ui.color("tile"));
            if (!stack.isEmpty()) {
                item(ui, stack, cx + gap / 2f, y + gap / 2f, icon);
            }
            cx += icon + gap * 2;
        }
        return y + icon + gap;
    }

    static void item(Ui ui, ItemStack stack, float x, float y, float size) {
        var pose = ui.graphics().pose();
        pose.pushMatrix();
        pose.translate(Math.round(x), Math.round(y));
        pose.scale(size / 16f, size / 16f);
        ui.graphics().renderItem(stack, 0, 0);
        pose.popMatrix();
    }

    /** «Кто и как тебя убил»; returns the y below it. */
    private float deathRecap(Ui ui, ReviewEntry e, DeathRecap d, float x, float y, float w, double mx, double my) {
        y = section(ui, Ui.tr("skirmish.analytics.review.death"), null, x, y, w);
        float pad = ui.num(L + "card_pad");
        float icon = ui.num(L + "death_icon");
        List<DamageTaken> list = new ArrayList<>(d.damage());
        java.util.Collections.reverse(list);
        int maxRows = Math.round(ui.num(L + "death_rows_max"));
        int shown = Math.min(maxRows, list.size());
        float rowH = ui.num(L + "death_row_height");
        List<String> message = d.message().isBlank() ? List.of() : ui.wrap("fa_row", d.message(), w - pad * 2);
        float headH = Math.max(icon, ui.lineHeight("fa_killer") + ui.lineHeight("fa_row"));
        float listH = list.isEmpty() ? ui.lineHeight("fa_row") : shown * rowH + (list.size() > shown ? ui.lineHeight("fa_row") : 0);
        float h = pad * 2 + headH + (message.isEmpty() ? 0 : ui.num(L + "death_gap") + message.size() * ui.lineHeight("fa_row"))
                + ui.num(L + "death_gap") + listH;
        ReviewCharts.card(ui, x, y, w, h);
        float cx = x + pad;
        float cy = y + pad;
        float cw = w - pad * 2;

        // Killer line: weapon icon, name, what they held; total damage on the right.
        ItemStack weapon = e.killerWeapon();
        ui.rect(cx, cy + (headH - icon) / 2f, icon, icon, ui.theme().radius("slot"), ui.color("fill_06"));
        if (!weapon.isEmpty()) {
            float inset = ui.num(L + "death_icon_inset");
            item(ui, weapon, cx + inset, cy + (headH - icon) / 2f + inset, icon - inset * 2);
        }
        float tx = cx + icon + ui.num(L + "row_dot_gap");
        String total = "−" + AnalyticsText.hp(d.totalDamage()) + " HP";
        String totalSub = Ui.tr("skirmish.analytics.review.death_total", list.size(), DeathRecap.WINDOW_MS / 1000);
        float rightW = Math.max(ui.textWidth("fa_killer", total), ui.textWidth("fa_row", totalSub));
        float nameW = cw - (tx - cx) - rightW - ui.num(L + "chip_gap");
        float textTop = cy + (headH - ui.lineHeight("fa_killer") - ui.lineHeight("fa_row")) / 2f;
        String killer = d.killer() == null ? Ui.tr("skirmish.analytics.review.killer_unknown") : d.killer();
        ui.text("fa_killer", ui.ellipsize("fa_killer", killer, nameW), tx, textTop, ui.color(d.killer() == null ? "text_2" : "text"));
        String held = weapon.isEmpty() ? Ui.tr("skirmish.analytics.review.weapon_unknown") : weapon.getHoverName().getString();
        ui.text("fa_row", ui.ellipsize("fa_row", held, nameW), tx, textTop + ui.lineHeight("fa_killer"));
        ui.text("fa_killer", total, cx + cw - ui.textWidth("fa_killer", total), textTop, ui.color("bad"));
        ui.text("fa_row", totalSub, cx + cw - ui.textWidth("fa_row", totalSub), textTop + ui.lineHeight("fa_killer"));
        cy += headH;
        if (!message.isEmpty()) {
            cy += ui.num(L + "death_gap");
            for (String line : message) {
                ui.text("fa_row", line, cx, cy, ui.color("text_3"));
                cy += ui.lineHeight("fa_row");
            }
        }
        cy += ui.num(L + "death_gap");

        // Damage rows, newest first: time before death, source, type, crit, amount.
        if (list.isEmpty()) {
            ui.text("fa_row", Ui.tr("skirmish.analytics.review.death_no_damage"), cx, cy, ui.color("text_3"));
        }
        float timeW = ui.num(L + "death_time_width");
        for (int i = 0; i < shown; i++) {
            DamageTaken t = list.get(i);
            float ry = cy + i * rowH;
            if (i > 0) {
                ui.hline(cx, ry, cw, ui.color("divider"));
            }
            String time = "−" + Ui.decimal((d.timeMs() - t.timeMs()) / 1000.0, 1) + " " + Ui.tr("skirmish.analytics.unit.s");
            ui.textCentered("fa_row_value", time, cx, ry, rowH, ui.color("text_3"));
            float sx = cx + timeW;
            String amount = Float.isFinite(t.amount()) ? "−" + AnalyticsText.hp(t.amount()) : "—";
            float amountW = ui.textWidth("fa_row_value", amount);
            float avail = cw - timeW - amountW - ui.num(L + "chip_gap");
            String source = t.source() == null ? null : (t.guessed() ? "≈ " : "") + t.source();
            if (source != null) {
                sx = ui.textCentered("fa_row_strong", ui.ellipsize("fa_row_strong", source, avail * 0.55f), sx, ry, rowH,
                        ui.color(d.killerUuid() != null && d.killerUuid().equals(t.sourceUuid()) ? "text" : "text_2"));
                sx += ui.num(L + "row_text_gap") * 3;
            }
            String type = AnalyticsText.damageType(t.type());
            sx = ui.textCentered("fa_row", ui.ellipsize("fa_row", type, Math.max(0, cx + timeW + avail - sx)), sx, ry, rowH);
            if (t.crit()) {
                ui.textCentered("fa_row_strong", " " + Ui.tr("skirmish.analytics.review.crit"), sx, ry, rowH, ui.color("fa_crit"));
            }
            ui.textCentered("fa_row_value", amount, cx + cw - amountW, ry, rowH, ui.color(Float.isFinite(t.amount()) ? "bad" : "text_3"));
        }
        if (list.size() > shown) {
            ui.text("fa_row", Ui.tr("skirmish.analytics.review.more", list.size() - shown), cx, cy + shown * rowH, ui.color("text_3"));
        }
        y += h + ui.num(L + "section_gap");

        // My health over the last seconds, with the hits as ticks (red: the killer's).
        if (!d.hp().isEmpty()) {
            y = section(ui, Ui.tr("skirmish.analytics.review.death_hp"), null, x, y, w);
            List<long[]> marks = new ArrayList<>();
            for (DamageTaken t : d.damage()) {
                marks.add(new long[]{t.timeMs(), d.killerUuid() != null && d.killerUuid().equals(t.sourceUuid()) ? 1 : 0});
            }
            ReviewCharts.health(ui, x, y, w, d.hp(), null, d.maxHp(), d.timeMs() - DeathRecap.WINDOW_MS, d.timeMs(), marks, true);
            y += ReviewCharts.chartHeight(ui);
        }
        return y;
    }

    // ---- input ----

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float step = (float) scrollY * Theme.get().num("layout.menu.scroll_step");
        if (Ui.toDesign(mouseX) < listRight) {
            listScroll = Math.max(0f, Math.min(listMax, listScroll - step));
        } else {
            scroll = Math.max(0f, Math.min(maxScroll, scroll - step));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (SkirmishKeys.FIGHT_REVIEW.matches(event)) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_DOWN) {
            List<ReviewEntry> entries = module.entries();
            if (!entries.isEmpty()) {
                int i = selected == null ? -1 : entries.indexOf(selected);
                int next = event.key() == GLFW.GLFW_KEY_UP ? Math.max(0, i - 1) : Math.min(entries.size() - 1, i + 1);
                select(entries.get(next));
            }
            return true;
        }
        return super.keyPressed(event);
    }
}
