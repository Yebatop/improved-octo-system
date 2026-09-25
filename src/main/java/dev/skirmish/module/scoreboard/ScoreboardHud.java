package dev.skirmish.module.scoreboard;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.hud.SidebarBounds;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The sidebar panel. Layout is in design px like every HUD element; the server's lines are drawn with the game
 * font at its native GUI size (the pose is scaled back by the design scale), so they stay crisp and keep
 * resource-pack glyphs and colours. Default place: vanilla's, the right edge at mid-height.
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

    private float headerHeight(Ui ui) {
        if (!module.title.get() || board == null) {
            return 0f;
        }
        return line() + ui.num(L + "title_gap") + ui.num(L + "underline") + ui.num(L + "rule_gap");
    }

    @Override
    public float width(Ui ui, boolean preview) {
        if (board == null) {
            return 1f;
        }
        float inner = module.title.get() ? width(board.title()) : 0f;
        float gap = ui.num(L + "score_gap");
        for (int index : order) {
            if (index == DIVIDER) {
                continue;
            }
            SidebarBounds.Row row = board.rows().get(index);
            float w = width(row.name()) + (showScore(row) ? gap + width(row.score()) : 0f);
            inner = Math.max(inner, w);
        }
        return Math.max(ui.num(L + "min_width"), (float) Math.ceil(inner + ui.num(L + "pad_x") * 2));
    }

    @Override
    public float height(Ui ui, boolean preview) {
        if (board == null) {
            return 1f;
        }
        float h = ui.num(L + "pad_y") * 2 + headerHeight(ui);
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i) == DIVIDER) {
                h += ui.num(L + "divider");
            } else {
                h += line() + (i > 0 && order.get(i - 1) != DIVIDER ? ui.num(L + "row_gap") : 0f);
            }
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
            float tw = width(b.title());
            text(ui, b.title(), x + (w - tw) / 2f, cy, ui.color("text"), shadow);
            cy += line() + ui.num(L + "title_gap");
            float uw = Math.min(w - padX * 2, Math.max(tw, ui.num(L + "underline_min")));
            float uh = ui.num(L + "underline");
            ui.rect(x + (w - uw) / 2f, cy, uw, uh, uh / 2f, ui.color("accent"));
            cy += uh + ui.num(L + "rule_gap");
        }
        float right = x + w - padX;
        float gap = ui.num(L + "row_gap");
        for (int i = 0; i < order.size(); i++) {
            int index = order.get(i);
            if (index == DIVIDER) {
                float d = ui.num(L + "divider");
                ui.hline(x + padX, cy + d / 2f, w - padX * 2, ui.color("stroke"));
                cy += d;
                continue;
            }
            if (i > 0 && order.get(i - 1) != DIVIDER) {
                cy += gap;
            }
            SidebarBounds.Row row = b.rows().get(index);
            text(ui, row.name(), x + padX, cy, ui.color("text"), shadow);
            if (showScore(row)) {
                Component score = row.score();
                boolean plainRed = DEFAULT_RED.equals(score.getStyle().getColor());
                if (plainRed) {
                    score = Component.literal(score.getString());
                }
                text(ui, score, right - width(score), cy, ui.color(plainRed ? "text_3" : "text"), shadow);
            }
            cy += line();
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
