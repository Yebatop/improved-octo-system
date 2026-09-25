package dev.skirmish.module.hwtimers;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.module.hwtimers.TimerBoard.Chip;
import dev.skirmish.module.hwtimers.TimerTable.TimerDef;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * A column of chips, one per running timer: accent bar in the timer's tone, name (with "?" while it was only
 * guessed from an unconfirmed server string or a heuristic), time left (or time since start for open-ended ones), an
 * optional one-line note and a countdown bar. Default place: left of the crosshair, clear of the combat-tag panel
 * above and the target card below (both centred, at most 256 px wide).
 */
final class ItemTimersHud extends HudBlock {
    static final String L = "layout.hwtimers.";
    private final ItemTimersModule module;
    private List<Chip> chips = List.of();

    ItemTimersHud(ItemTimersModule module) {
        super(ItemTimersModule.ID, "skirmish.hud.element.hw_item_timers", new Placement(0.5f, 0.5f, 1f, 0.5f,
                -Theme.get().num(L + "default_dx"), 0));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public boolean shown() {
        update(false);
        return !chips.isEmpty();
    }

    @Override
    public boolean hasContent() {
        return !chips.isEmpty();
    }

    @Override
    public void update(boolean preview) {
        long now = System.currentTimeMillis();
        List<Chip> live = Minecraft.getInstance().player == null ? List.of() : module.visibleChips(now);
        if (preview && live.isEmpty()) {
            live = sample(now);
        }
        int max = (int) Theme.get().num(L + "max_chips");
        chips = live.size() > max ? live.subList(0, max) : live;
    }

    /** Stan with 11 s left and a raid block with 4:12 left, for the HUD editor. */
    private List<Chip> sample(long now) {
        List<Chip> out = new ArrayList<>();
        TimerBoard board = new TimerBoard();
        TimerDef stan = module.table().byId("stan");
        TimerDef raid = module.table().byId("raid_block");
        if (stan != null) {
            out.add(board.start(stan, now, 11_000L, TimerBoard.Source.CHAT, false, true));
        }
        if (raid != null) {
            out.add(board.start(raid, now, 252_000L, TimerBoard.Source.EXPLOSION, true, true));
        }
        return out;
    }

    private boolean withNote(Chip chip) {
        return module.notes.get() && !note(chip.def()).isEmpty();
    }

    private float chipHeight(Ui ui, Chip chip) {
        float h = ui.num(L + "chip_pad_y") * 2 + ui.lineHeight("hwt_title");
        if (withNote(chip)) {
            h += ui.num(L + "chip_line_gap") + ui.lineHeight("hwt_note");
        }
        if (chip.countsDown()) {
            h += ui.num(L + "chip_bar_gap") + ui.num(L + "chip_bar");
        }
        return h;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "chip_width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        float h = 0f;
        for (int i = 0; i < chips.size(); i++) {
            h += chipHeight(ui, chips.get(i)) + (i > 0 ? ui.num(L + "chip_gap") : 0f);
        }
        return Math.max(h, 1f);
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        long now = System.currentTimeMillis();
        float w = width(ui, preview);
        float cy = y;
        for (Chip chip : chips) {
            float h = chipHeight(ui, chip);
            drawChip(ui, chip, x, cy, w, h, now);
            cy += h + ui.num(L + "chip_gap");
        }
    }

    private void drawChip(Ui ui, Chip chip, float x, float y, float w, float h, long now) {
        int tone = ui.color(chip.def().tone());
        ui.box(x, y, w, h, ui.theme().radius("tile"), ui.color("panel"), ui.color("stroke"));
        float padX = ui.num(L + "chip_pad_x");
        float padY = ui.num(L + "chip_pad_y");
        float accent = ui.num(L + "chip_accent");
        ui.rect(x + padX, y + padY, accent, h - padY * 2, accent / 2f, tone);
        float tx = x + padX + accent + ui.num(L + "chip_accent_gap");
        float right = x + w - padX;

        String time = timeText(chip, now);
        float timeW = ui.textWidth("hwt_time", time);
        ui.text("hwt_time", time, right - timeW, y + padY, ui.color(chip.countsDown() && chip.remainingMs(now) < 3000 ? "bad" : "text"));
        float titleMax = right - timeW - ui.num(L + "chip_accent_gap") - tx;
        String title = name(chip.def());
        String mark = chip.guess() ? " ?" : "";
        title = ui.ellipsize("hwt_title", title, titleMax - ui.textWidth("hwt_title", mark));
        float after = ui.text("hwt_title", title, tx, y + padY);
        if (!mark.isEmpty()) {
            ui.text("hwt_title", mark, after, y + padY, ui.color("text_3"));
        }
        float line = y + padY + ui.lineHeight("hwt_title");
        if (withNote(chip)) {
            line += ui.num(L + "chip_line_gap");
            ui.text("hwt_note", ui.ellipsize("hwt_note", note(chip.def()), right - tx), tx, line);
            line += ui.lineHeight("hwt_note");
        }
        if (chip.countsDown()) {
            float barY = line + ui.num(L + "chip_bar_gap");
            float bar = ui.num(L + "chip_bar");
            float barW = right - tx;
            ui.rect(tx, barY, barW, bar, bar / 2f, ui.color("track"));
            ui.rect(tx, barY, barW * chip.fraction(now), bar, bar / 2f, tone);
        }
    }

    /** "12 с" under a minute, "4:59" above; "+0:12" for open-ended chips. */
    static String timeText(Chip chip, long now) {
        if (!chip.countsDown()) {
            return "+" + Ui.duration(chip.elapsedMs(now));
        }
        long ms = chip.remainingMs(now);
        if (ms >= 60_000) {
            return Ui.duration(ms + 999);
        }
        return Ui.tr("skirmish.hwtimers.seconds", (ms + 999) / 1000);
    }

    static String name(TimerDef def) {
        String key = "skirmish.hwtimers.timer." + def.id();
        String text = Ui.tr(key);
        return text.equals(key) ? def.id() : text;
    }

    static String note(TimerDef def) {
        String key = "skirmish.hwtimers.timer." + def.id() + ".note";
        String text = Ui.tr(key);
        return text.equals(key) ? "" : text;
    }
}
