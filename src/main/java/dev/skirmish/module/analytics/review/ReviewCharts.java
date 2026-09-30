package dev.skirmish.module.analytics.review;

import dev.skirmish.module.analytics.AnalyticsText;
import dev.skirmish.module.analytics.ReachMath;
import dev.skirmish.ui.Ui;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Charts of the review screen, drawn with the UI kit: the hit timeline (my hits above the track, theirs below,
 * crits taller with a cap, totems as diamonds, the final moment as a bar), the reach of my hits and the health
 * curves. All share the horizontal time axis {@code [start, end]} over {@code [x, x + w]}.
 */
final class ReviewCharts {
    static final String L = "layout.analytics.review.";

    /** Tooltip collected while drawing, shown on top at the end of the frame. */
    static final class Tip {
        @Nullable List<String> lines;
        float x;
        float y;
        float bestDistance = Float.MAX_VALUE;

        void offer(float distance, float x, float y, List<String> lines) {
            if (distance < bestDistance) {
                bestDistance = distance;
                this.x = x;
                this.y = y;
                this.lines = lines;
            }
        }
    }

    /** Time axis of a chart. */
    record Axis(float x, float w, long start, long end) {
        float at(long timeMs) {
            long span = Math.max(1, end - start);
            return x + Math.max(0f, Math.min(1f, (timeMs - start) / (float) span)) * w;
        }
    }

    private ReviewCharts() {
    }

    static float laneLabelWidth(Ui ui) {
        return ui.num(L + "lane_label_width");
    }

    /** Height of {@link #timeline}. */
    static float timelineHeight(Ui ui) {
        return ui.num(L + "card_pad") * 2 + ui.num(L + "lane_height") * 2 + ui.num(L + "axis_gap") + ui.lineHeight("fa_axis");
    }

    /**
     * @param endColor colour token of the final-moment bar, null for none
     */
    static void timeline(Ui ui, float x, float y, float w, FightLog log, long start, long end, String opponent,
                         @Nullable String endColor, double mx, double my, Tip tip) {
        float h = timelineHeight(ui);
        card(ui, x, y, w, h);
        float pad = ui.num(L + "card_pad");
        float laneH = ui.num(L + "lane_height");
        float labelW = laneLabelWidth(ui);
        float top = y + pad;
        float cy = top + laneH;
        ui.textCentered("fa_lane", Ui.tr("skirmish.analytics.review.you"), x + pad, top, laneH, ui.color("accent"));
        ui.textCentered("fa_lane", ui.ellipsize("fa_lane", opponent, labelW - pad), x + pad, cy, laneH, ui.color("bad"));

        Axis axis = new Axis(x + pad + labelW, w - pad * 2 - labelW, start, end);
        grid(ui, axis, top, laneH * 2, cy + laneH + ui.num(L + "axis_gap"));
        float track = ui.num(L + "track");
        ui.rect(axis.x(), cy - track / 2f, axis.w(), track, track / 2f, ui.color("track"));

        float hitW = ui.num(L + "hit_width");
        float hitH = ui.num(L + "hit_height");
        float critH = ui.num(L + "crit_height");
        float capD = ui.num(L + "crit_dot");
        float gap = ui.num(L + "hit_gap");
        float radius = ui.theme().radius("marker");
        List<float[]> totems = new ArrayList<>();
        for (FightLog.Event e : log.events()) {
            float px = axis.at(e.timeMs());
            boolean mine = e.kind().mine();
            if (!e.kind().hit()) {
                totems.add(new float[]{px, mine ? cy - gap - hitH / 2f : cy + gap + hitH / 2f});
                continue;
            }
            float bh = e.crit() ? critH : hitH;
            float by = mine ? cy - gap - bh : cy + gap;
            int color = ui.color(mine ? "accent" : "bad");
            ui.rect(px - hitW / 2f, by, hitW, bh, radius, color);
            if (e.crit()) {
                ui.circle(px, mine ? by : by + bh, capD, ui.color("fa_crit"));
            }
            if (mx >= axis.x() - 4 && mx <= axis.x() + axis.w() + 4 && my >= top && my <= cy + laneH) {
                float d = (float) Math.abs(mx - px);
                boolean lane = mine ? my <= cy : my >= cy;
                if (d <= ui.num(L + "hover_radius") && lane) {
                    tip.offer(d, px, mine ? by : by + bh, hitTip(e, start, opponent));
                }
            }
        }
        float totem = ui.num(L + "totem");
        for (float[] t : totems) {
            diamond(ui, t[0], t[1], totem, ui.theme().radius("totem_marker"), ui.color("warn"));
        }
        if (endColor != null) {
            float px = axis.at(end);
            float eh = laneH * 2 - ui.num(L + "end_inset") * 2;
            ui.rect(px - hitW / 2f, top + ui.num(L + "end_inset"), hitW, eh, radius, ui.color(endColor));
        }
        timeLabels(ui, axis, cy + laneH + ui.num(L + "axis_gap"));
    }

    private static List<String> hitTip(FightLog.Event e, long start, String opponent) {
        List<String> lines = new ArrayList<>();
        boolean mine = e.kind() == FightLog.Kind.MY_HIT;
        String who = mine ? Ui.tr("skirmish.analytics.review.tip.my_hit") : Ui.tr("skirmish.analytics.review.tip.their_hit", opponent);
        lines.add(who + " · " + Ui.decimal((e.timeMs() - start) / 1000.0, 1) + " " + Ui.tr("skirmish.analytics.unit.s"));
        if (Float.isFinite(e.damage())) {
            lines.add(Ui.tr("skirmish.analytics.review.tip.damage", AnalyticsText.hp(e.damage())));
        }
        if (mine && Double.isFinite(e.reach())) {
            lines.add(Ui.tr("skirmish.analytics.review.tip.reach", AnalyticsText.reach(e.reach())));
        }
        if (e.crit()) {
            lines.add(Ui.tr("skirmish.analytics.review.tip.crit"));
        }
        return lines;
    }

    /** Height of {@link #reach} and {@link #health}. */
    static float chartHeight(Ui ui) {
        return ui.num(L + "card_pad") * 2 + ui.num(L + "chart_height") + ui.num(L + "axis_gap") + ui.lineHeight("fa_axis");
    }

    /** Reach of each of my hits over time, with the vanilla 3-block line. */
    static void reach(Ui ui, float x, float y, float w, FightLog log, long start, long end, double mx, double my, Tip tip) {
        float h = chartHeight(ui);
        card(ui, x, y, w, h);
        float pad = ui.num(L + "card_pad");
        float labelW = laneLabelWidth(ui);
        float top = y + pad;
        float ch = ui.num(L + "chart_height");
        Axis axis = new Axis(x + pad + labelW, w - pad * 2 - labelW, start, end);
        double max = ReachMath.max(FightStats.reaches(log.events()));
        float yMax = (float) Math.max(4.0, Double.isFinite(max) ? Math.ceil(max + 0.25) : 4.0);
        grid(ui, axis, top, ch, top + ch + ui.num(L + "axis_gap"));
        // Horizontal guides at 0 and at vanilla reach.
        ui.hline(axis.x(), top + ch, axis.w(), ui.color("stroke_08"));
        float ref = top + ch - (float) (ReachMath.VANILLA_REACH / yMax) * ch;
        dashed(ui, axis.x(), ref, axis.w(), ui.color("fa_reference"));
        axisLabel(ui, "0", x + pad, top + ch, labelW);
        axisLabel(ui, Ui.tr("skirmish.analytics.review.blocks_short", Ui.decimal(ReachMath.VANILLA_REACH, 0)), x + pad, ref, labelW);
        axisLabel(ui, Ui.tr("skirmish.analytics.review.blocks_short", Ui.decimal(yMax, 0)), x + pad, top, labelW);

        float dot = ui.num(L + "reach_dot");
        boolean any = false;
        for (FightLog.Event e : log.events()) {
            if (e.kind() != FightLog.Kind.MY_HIT || !Double.isFinite(e.reach())) {
                continue;
            }
            any = true;
            float px = axis.at(e.timeMs());
            float py = top + ch - (float) Math.min(1.0, e.reach() / yMax) * ch;
            ui.circle(px, py, dot, ui.color(e.crit() ? "fa_crit" : "accent"));
            float d = (float) Math.hypot(mx - px, my - py);
            if (d <= ui.num(L + "hover_radius") * 1.5f) {
                tip.offer(d, px, py - dot / 2f, hitTip(e, start, ""));
            }
        }
        if (!any) {
            String text = Ui.tr("skirmish.analytics.review.no_reach");
            ui.textCentered("fa_empty", text, axis.x() + (axis.w() - ui.textWidth("fa_empty", text)) / 2f, top, ch);
        }
        timeLabels(ui, axis, top + ch + ui.num(L + "axis_gap"));
    }

    /**
     * Health curves: mine ({@code good}) and, when the server sends real health, the opponent's ({@code bad}).
     * {@code marks} are times of hits on me drawn as ticks along the bottom (the death recap).
     */
    static void health(Ui ui, float x, float y, float w, HpSeries mine, @Nullable HpSeries theirs, float maxHp,
                       long start, long end, @Nullable List<long[]> marks, boolean relativeLabels) {
        float h = chartHeight(ui);
        card(ui, x, y, w, h);
        float pad = ui.num(L + "card_pad");
        float labelW = laneLabelWidth(ui);
        float top = y + pad;
        float ch = ui.num(L + "chart_height");
        Axis axis = new Axis(x + pad + labelW, w - pad * 2 - labelW, start, end);
        float yMax = Math.max(1f, Math.max(maxHp, Math.max(mine.max(), theirs == null ? 0f : theirs.max())));
        grid(ui, axis, top, ch, top + ch + ui.num(L + "axis_gap"));
        ui.hline(axis.x(), top + ch, axis.w(), ui.color("stroke_08"));
        axisLabel(ui, "0", x + pad, top + ch, labelW);
        axisLabel(ui, Ui.decimal(yMax, 0) + " HP", x + pad, top, labelW);
        float line = ui.num(L + "hp_line");
        if (theirs != null) {
            steps(ui, axis, theirs, top, ch, yMax, line, ui.color("bad"));
        }
        steps(ui, axis, mine, top, ch, yMax, line, ui.color("good"));
        if (marks != null) {
            float mh = ui.num(L + "mark_height");
            for (long[] m : marks) {
                float px = axis.at(m[0]);
                ui.rect(px - line / 2f, top + ch - mh, line, mh, 0, ui.color(m[1] == 1 ? "bad" : "warn"));
            }
        }
        if (relativeLabels) {
            float ly = top + ch + ui.num(L + "axis_gap");
            String left = "−" + Ui.decimal((end - start) / 1000.0, 0) + " " + Ui.tr("skirmish.analytics.unit.s");
            String right = Ui.tr("skirmish.analytics.review.death_moment");
            ui.text("fa_axis", left, axis.x(), ly);
            ui.text("fa_axis", right, axis.x() + axis.w() - ui.textWidth("fa_axis", right), ly);
        } else {
            timeLabels(ui, axis, top + ch + ui.num(L + "axis_gap"));
        }
    }

    /** Step line: health holds until the next sample, then jumps. */
    private static void steps(Ui ui, Axis axis, HpSeries s, float top, float ch, float yMax, float thickness, int color) {
        if (s.isEmpty()) {
            return;
        }
        float prevX = axis.at(s.time(0));
        float prevY = top + ch - Math.min(1f, s.value(0) / yMax) * ch;
        for (int i = 1; i < s.size(); i++) {
            if (s.time(i) < axis.start()) {
                prevY = top + ch - Math.min(1f, s.value(i) / yMax) * ch;
                continue;
            }
            float px = axis.at(s.time(i));
            float py = top + ch - Math.min(1f, s.value(i) / yMax) * ch;
            ui.line(prevX, prevY, px, prevY, thickness, color);
            ui.line(px, prevY, px, py, thickness, color);
            prevX = px;
            prevY = py;
        }
        float endX = axis.x() + axis.w();
        if (prevX < endX && s.time(s.size() - 1) < axis.end()) {
            ui.line(prevX, prevY, Math.min(endX, axis.at(Math.max(s.time(s.size() - 1), axis.end()))), prevY, thickness, color);
        }
    }

    // ---- shared pieces ----

    static void card(Ui ui, float x, float y, float w, float h) {
        ui.box(x, y, w, h, ui.theme().radius("tile"), ui.color("tile"), ui.color("stroke"));
    }

    /** Seconds between labelled ticks so that at most ~6 fit. */
    static long tickStepMs(long spanMs) {
        long[] steps = {1_000, 2_000, 5_000, 10_000, 15_000, 30_000, 60_000, 120_000, 300_000, 600_000};
        for (long s : steps) {
            if (spanMs / s <= 6) {
                return s;
            }
        }
        return 1_200_000;
    }

    private static void grid(Ui ui, Axis axis, float top, float h, float labelsY) {
        long span = axis.end() - axis.start();
        long step = tickStepMs(span);
        for (long t = step; t < span; t += step) {
            float px = axis.at(axis.start() + t);
            ui.rect(px, top, ui.num("stroke.width"), h, 0, ui.color("fa_grid"));
        }
    }

    private static void timeLabels(Ui ui, Axis axis, float y) {
        long span = axis.end() - axis.start();
        long step = tickStepMs(span);
        float lastRight = Float.NEGATIVE_INFINITY;
        for (long t = 0; t <= span; t += step) {
            String label = Ui.duration(t);
            float tw = ui.textWidth("fa_axis", label);
            float px = axis.at(axis.start() + t) - tw / 2f;
            px = Math.max(axis.x(), Math.min(axis.x() + axis.w() - tw, px));
            if (px > lastRight + 4) {
                ui.text("fa_axis", label, px, y);
                lastRight = px + tw;
            }
        }
        String endLabel = Ui.duration(span);
        float tw = ui.textWidth("fa_axis", endLabel);
        float px = axis.x() + axis.w() - tw;
        if (px > lastRight + 4) {
            ui.text("fa_axis", endLabel, px, y, ui.color("text_2"));
        }
    }

    private static void axisLabel(Ui ui, String text, float x, float centerY, float w) {
        float lh = ui.lineHeight("fa_axis");
        ui.text("fa_axis", ui.ellipsize("fa_axis", text, w - 4), x, centerY - lh / 2f);
    }

    private static void dashed(Ui ui, float x, float y, float w, int color) {
        float dash = 4f;
        float stroke = ui.num("stroke.width");
        for (float dx = 0; dx < w; dx += dash * 2) {
            ui.rect(x + dx, y, Math.min(dash, w - dx), stroke, 0, color);
        }
    }

    /** Square of side {@code size} rotated 45° around its centre. */
    static void diamond(Ui ui, float cx, float cy, float size, float radius, int color) {
        var pose = ui.graphics().pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(45));
        ui.rect(-size / 2f, -size / 2f, size, size, radius, color);
        pose.popMatrix();
    }

    /** Legend entry: a mark ("bar", "crit", "diamond", "dot", "line") and a label; returns the x after it. */
    static float legend(Ui ui, float x, float y, float h, String kind, String color, String label) {
        float mark = ui.num(L + "legend_mark");
        float cy = y + h / 2f;
        switch (kind) {
            case "diamond" -> diamond(ui, x + mark / 2f, cy, mark * 0.8f, ui.theme().radius("marker"), ui.color(color));
            case "dot" -> ui.circle(x + mark / 2f, cy, mark * 0.8f, ui.color(color));
            case "line" -> ui.rect(x, cy - ui.num(L + "hp_line") / 2f, mark, ui.num(L + "hp_line"), 1f, ui.color(color));
            default -> ui.rect(x + mark / 2f - ui.num(L + "hit_width") / 2f, cy - mark / 2f, ui.num(L + "hit_width"), mark,
                    ui.theme().radius("marker"), ui.color(color));
        }
        return ui.textCentered("fa_legend", label, x + mark + ui.num(L + "legend_item_gap"), y, h);
    }

    /** Draws the tooltip collected this frame. */
    static void drawTip(Ui ui, Tip tip) {
        List<String> lines = tip.lines;
        if (lines == null || lines.isEmpty()) {
            return;
        }
        float padX = ui.num(L + "tip_pad_x");
        float padY = ui.num(L + "tip_pad_y");
        float lh = ui.lineHeight("fa_tip");
        float w = 0;
        for (String l : lines) {
            w = Math.max(w, ui.textWidth("fa_tip", l));
        }
        w += padX * 2;
        float h = lh * lines.size() + padY * 2;
        float x = Math.max(0, Math.min(ui.width() - w, tip.x - w / 2f));
        float y = tip.y - ui.num(L + "tip_gap") - h;
        if (y < 0) {
            y = tip.y + ui.num(L + "tip_gap");
        }
        ui.box(x, y, w, h, ui.theme().radius("button_sm"), ui.color("window"), ui.color("stroke_12"));
        float ty = y + padY;
        for (int i = 0; i < lines.size(); i++) {
            ui.text("fa_tip", lines.get(i), x + padX, ty, ui.color(i == 0 ? "text" : "text_2"));
            ty += lh;
        }
    }
}
