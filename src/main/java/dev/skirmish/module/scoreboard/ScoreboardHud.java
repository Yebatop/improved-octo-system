package dev.skirmish.module.scoreboard;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.hud.SidebarBounds;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The sidebar panel. Layout is in design px like every HUD element. In the modern look «Label: value» lines become
 * two columns: an icon and the label in the mod's font on the left, the value on the right in its own colour; other
 * lines are set in the mod's font too. Lines with resource-pack glyphs (and every line in the classic look) are drawn
 * with the game font at its native GUI size (the pose scaled back by the design scale), so they stay crisp and keep
 * the glyphs and colours. Default place: vanilla's, the right edge at mid-height.
 */
final class ScoreboardHud extends HudBlock {
    static final String L = "layout.scoreboard.";
    private static final int FONT_LINE = 9;
    private static final TextColor DEFAULT_RED = TextColor.fromLegacyFormat(ChatFormatting.RED);

    private final ScoreboardModule module;
    private SidebarBounds.@Nullable Sidebar board;
    /** Row indices of {@link #board} in display order; {@link #DIVIDER} for a divider. */
    private List<Integer> order = List.of();
    static final int DIVIDER = -1;

    ScoreboardHud(ScoreboardModule module) {
        super(ScoreboardModule.ID, "skirmish.hud.element.scoreboard", new Placement(1f, 0.5f, 1f, 0.5f,
                -Theme.get().num(L + "edge"), 0));
        this.module = module;
    }

    @Override
    public boolean isSidebar() {
        return true;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public boolean shown() {
        return SidebarBounds.read(Minecraft.getInstance()) != null;
    }

    @Override
    public boolean hasContent() {
        return board != null;
    }

    @Override
    public void update(boolean preview) {
        SidebarBounds.Sidebar live = SidebarBounds.read(Minecraft.getInstance());
        if (live == null && preview) {
            live = sample();
        }
        if (live == null) {
            return;
        }
        board = live;
        List<Boolean> blank = new ArrayList<>();
        for (SidebarBounds.Row row : live.rows()) {
            blank.add(row.name().getString().isBlank() && (!module.numbers.get() || row.score().getString().isBlank()));
        }
        order = layout(blank, module.dividers.get());
        lines = lines(live, Theme.get());
    }

    /**
     * Display order for rows with the given blank flags: with dividers, runs of blank rows become one divider and
     * blank rows at the top and bottom are dropped; without, every row stays.
     */
    static List<Integer> layout(List<Boolean> blank, boolean dividers) {
        List<Integer> out = new ArrayList<>();
        if (!dividers) {
            for (int i = 0; i < blank.size(); i++) {
                out.add(i);
            }
            return out;
        }
        boolean pending = false;
        for (int i = 0; i < blank.size(); i++) {
            if (blank.get(i)) {
                pending = !out.isEmpty();
                continue;
            }
            if (pending) {
                out.add(DIVIDER);
                pending = false;
            }
            out.add(i);
        }
        return out;
    }

    private static SidebarBounds.Sidebar sample() {
        List<SidebarBounds.Row> rows = new ArrayList<>();
        rows.add(row(Component.literal("Ник: ").withStyle(ChatFormatting.GRAY).append(Component.literal("Player").withStyle(ChatFormatting.WHITE))));
        rows.add(row(Component.literal("Баланс: ").withStyle(ChatFormatting.GRAY).append(Component.literal("12 500¤").withStyle(ChatFormatting.GREEN))));
        rows.add(row(Component.literal(" ")));
        rows.add(row(Component.literal("Убийства: ").withStyle(ChatFormatting.GRAY).append(Component.literal("7").withStyle(ChatFormatting.RED))));
        rows.add(row(Component.literal("Онлайн: ").withStyle(ChatFormatting.GRAY).append(Component.literal("1 204").withStyle(ChatFormatting.AQUA))));
        rows.add(row(Component.literal("  ")));
        rows.add(row(Component.literal("#12 -◆-").withStyle(ChatFormatting.DARK_GRAY)));
        return new SidebarBounds.Sidebar(Component.literal("HolyWorld").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), rows);
    }

    private static SidebarBounds.Row row(Component name) {
        return new SidebarBounds.Row(name, Component.literal(""));
    }

    // ---- lines as drawn ----

    private static final int GAME = 0;
    private static final int PAIR = 1;
    private static final int TEXT = 2;
    private static final int RULE = 3;

    /**
     * One line as drawn: a divider, a line in the game font (resource-pack glyphs, or the classic look), a «label:
     * value» pair with an icon, or a plain line in the mod's font. {@code value} is drawn in {@code color}, or as
     * {@code valueGame} in the game font when it has glyphs; {@code score} is the right-hand number or null.
     */
    private record Line(int kind, @Nullable Component game, String icon, String label, String value,
                        @Nullable Component valueGame, int color, @Nullable Component score) {
    }

    private List<Line> lines = List.of();

    /** A piece of a line's text with its style. */
    private record Seg(String text, Style style) {
    }

    private List<Line> lines(SidebarBounds.Sidebar b, Theme theme) {
        List<Line> out = new ArrayList<>();
        boolean modern = module.look.get() == ScoreboardModule.Look.MODERN;
        int textColor = theme.color("text");
        for (int index : order) {
            if (index == DIVIDER) {
                out.add(new Line(RULE, null, "", "", "", null, 0, null));
                continue;
            }
            SidebarBounds.Row row = b.rows().get(index);
            Component score = null;
            if (showScore(row)) {
                score = row.score();
                if (DEFAULT_RED.equals(score.getStyle().getColor())) {
                    score = Component.literal(score.getString()).withStyle(Style.EMPTY.withColor(theme.color("text_3") & 0xFFFFFF));
                }
            }
            String plain = row.name().getString();
            int split = modern ? SidebarText.split(plain) : -1;
            if (!modern || split < 0 && !SidebarText.modFont(plain) || split > 0 && !SidebarText.modFont(plain.substring(0, split))) {
                out.add(new Line(GAME, row.name(), "", "", "", null, 0, score));
                continue;
            }
            List<Seg> segs = new ArrayList<>();
            row.name().visit((style, text) -> {
                segs.add(new Seg(text, style));
                return Optional.empty();
            }, Style.EMPTY);
            if (split < 0) {
                out.add(new Line(TEXT, null, "", "", plain.strip(), null, colorAt(segs, 0, textColor), score));
                continue;
            }
            String label = plain.substring(0, split).strip();
            String value = plain.substring(split + 1).strip();
            int valueStart = split + 1;
            while (valueStart < plain.length() && Character.isWhitespace(plain.charAt(valueStart))) {
                valueStart++;
            }
            Component valueGame = SidebarText.modFont(value) ? null : tail(segs, valueStart);
            out.add(new Line(PAIR, null, SidebarText.icon(label), label, value, valueGame, colorAt(segs, valueStart, textColor), score));
        }
        return out;
    }

    /** The colour of the character at {@code at} (white-ish default when it has none). */
    private static int colorAt(List<Seg> segs, int at, int fallback) {
        int pos = 0;
        for (Seg seg : segs) {
            if (at < pos + seg.text().length() && !seg.text().isBlank()) {
                TextColor c = seg.style().getColor();
                return c == null ? fallback : 0xFF000000 | c.getValue();
            }
            pos += seg.text().length();
        }
        return fallback;
    }

    /** The line from character {@code from} on, styles kept (for a value with resource-pack glyphs). */
    private static Component tail(List<Seg> segs, int from) {
        MutableComponent out = Component.empty();
        int pos = 0;
        for (Seg seg : segs) {
            int end = pos + seg.text().length();
            if (end > from) {
                out.append(Component.literal(seg.text().substring(Math.max(0, from - pos))).withStyle(seg.style()));
            }
            pos = end;
        }
        return out;
    }

    // ---- measuring ----

    private static float gui() {
        return Ui.designScale();
    }

    /** Width of a component in design px. */
    private static float width(Component text) {
        return Minecraft.getInstance().font.width(text) / gui();
    }

    private static float line() {
        return FONT_LINE / gui();
    }

    private boolean showScore(SidebarBounds.Row row) {
        return module.numbers.get() && !row.score().getString().isEmpty();
    }

    private boolean modernTitle() {
        return module.look.get() == ScoreboardModule.Look.MODERN && board != null && SidebarText.modFont(board.title().getString());
    }

    private float titleHeight(Ui ui) {
        return modernTitle() ? ui.lineHeight("sb_title") : line();
    }

    private float headerHeight(Ui ui) {
        if (!module.title.get() || board == null) {
            return 0f;
        }
        return titleHeight(ui) + ui.num(L + "title_gap") + ui.num(L + "underline") + ui.num(L + "rule_gap");
    }

    private float lineHeight(Ui ui, Line l) {
        return switch (l.kind()) {
            case PAIR, TEXT -> Math.max(ui.num(L + "icon"), Math.max(ui.lineHeight("sb_label"), ui.lineHeight("sb_value")));
            case RULE -> ui.num(L + "divider");
            default -> line();
        };
    }

    private float valueWidth(Ui ui, Line l) {
        return l.valueGame() != null ? width(l.valueGame()) : ui.textWidth("sb_value", l.value());
    }

    @Override
    public float width(Ui ui, boolean preview) {
        if (board == null) {
            return 1f;
        }
        float inner = !module.title.get() ? 0f : modernTitle() ? ui.textWidth("sb_title", board.title().getString()) : width(board.title());
        float gap = ui.num(L + "score_gap");
        float labels = 0f;
        float values = 0f;
        for (Line l : lines) {
            float score = l.score() == null ? 0f : gap + width(l.score());
            switch (l.kind()) {
                case GAME -> inner = Math.max(inner, width(l.game()) + score);
                case TEXT -> inner = Math.max(inner, ui.textWidth("sb_value", l.value()) + score);
                case PAIR -> {
                    labels = Math.max(labels, ui.textWidth("sb_label", l.label()));
                    values = Math.max(values, valueWidth(ui, l) + score);
                }
                default -> {
                }
            }
        }
        if (labels > 0f) {
            inner = Math.max(inner, ui.num(L + "icon") + ui.num(L + "icon_gap") + labels + ui.num(L + "col_gap") + values);
        }
        return Math.max(ui.num(L + "min_width"), (float) Math.ceil(inner + ui.num(L + "pad_x") * 2));
    }

    @Override
    public float height(Ui ui, boolean preview) {
        if (board == null) {
            return 1f;
        }
        float h = ui.num(L + "pad_y") * 2 + headerHeight(ui);
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            h += lineHeight(ui, l) + (i > 0 && l.kind() != RULE && lines.get(i - 1).kind() != RULE ? ui.num(L + "row_gap") : 0f);
        }
        return h;
    }

    // ---- drawing ----

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        SidebarBounds.Sidebar b = board;
        if (b == null) {
            return;
        }
        float w = width(ui, preview);
        float h = height(ui, preview);
        float opacity = (float) (module.opacity.get() / 100.0);
        ui.box(x, y, w, h, ui.theme().radius("tile"), scaleAlpha(ui.color("panel"), opacity), scaleAlpha(ui.color("stroke"), opacity));
        float padX = ui.num(L + "pad_x");
        float cy = y + ui.num(L + "pad_y");
        boolean shadow = module.shadow.get();
        if (module.title.get()) {
            float tw;
            if (modernTitle()) {
                String title = b.title().getString().strip();
                tw = ui.textWidth("sb_title", title);
                TextColor tc = b.title().getStyle().getColor();
                ui.text("sb_title", title, x + (w - tw) / 2f, cy, tc == null ? ui.color("text") : 0xFF000000 | tc.getValue());
            } else {
                tw = width(b.title());
                text(ui, b.title(), x + (w - tw) / 2f, cy, ui.color("text"), shadow);
            }
            cy += titleHeight(ui) + ui.num(L + "title_gap");
            float uw = Math.min(w - padX * 2, Math.max(tw, ui.num(L + "underline_min")));
            float uh = ui.num(L + "underline");
            ui.rect(x + (w - uw) / 2f, cy, uw, uh, uh / 2f, ui.color("accent"));
            cy += uh + ui.num(L + "rule_gap");
        }
        float right = x + w - padX;
        float gap = ui.num(L + "score_gap");
        float icon = ui.num(L + "icon");
        float iconGap = ui.num(L + "icon_gap");
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            if (i > 0 && l.kind() != RULE && lines.get(i - 1).kind() != RULE) {
                cy += ui.num(L + "row_gap");
            }
            float lh = lineHeight(ui, l);
            float valueRight = right;
            if (l.score() != null) {
                float sw = width(l.score());
                text(ui, l.score(), right - sw, cy + (lh - line()) / 2f, ui.color("text"), shadow);
                valueRight = right - sw - gap;
            }
            switch (l.kind()) {
                case RULE -> ui.hline(x + padX, cy + lh / 2f, w - padX * 2, ui.color("stroke"));
                case GAME -> text(ui, l.game(), x + padX, cy, ui.color("text"), shadow);
                case TEXT -> ui.textCentered("sb_value", l.value(), x + padX, cy, lh, l.color());
                default -> {
                    SidebarIcons.draw(ui, l.icon(), x + padX, cy + (lh - icon) / 2f, icon, ui.color("text_3"));
                    ui.textCentered("sb_label", l.label(), x + padX + icon + iconGap, cy, lh, ui.color("text_2"));
                    float vw = valueWidth(ui, l);
                    if (l.valueGame() != null) {
                        text(ui, l.valueGame(), valueRight - vw, cy + (lh - line()) / 2f, ui.color("text"), shadow);
                    } else {
                        ui.textCentered("sb_value", l.value(), valueRight - vw, cy, lh, l.color());
                    }
                }
            }
            cy += lh;
        }
    }

    /** A game-font component at its native GUI size, top-left at design px (x, y). */
    private static void text(Ui ui, Component text, float x, float y, int color, boolean shadow) {
        var pose = ui.graphics().pose();
        pose.pushMatrix();
        pose.translate(x, y);
        float back = 1f / gui();
        pose.scale(back, back);
        ui.graphics().drawString(Minecraft.getInstance().font, text, 0, 0, ui.fade(color), shadow);
        pose.popMatrix();
    }

    private static int scaleAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
