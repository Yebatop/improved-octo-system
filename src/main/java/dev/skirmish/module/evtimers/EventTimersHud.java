package dev.skirmish.module.evtimers;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A column of event chips (Sun Core, castle, Pandora Box) in the style of the item timers: accent bar in
 * the event's tone, name, value on the right (coloured parts for the castle's rarity counts), a note line and an
 * optional bar. In the top-left column under the events list and schedule, away from the crosshair.
 */
final class EventTimersHud extends HudBlock {
    private static final String L = EventTimersModule.L;
    private final EventTimersModule module;
    private List<Chip> chips = List.of();

    /** A coloured piece of the value text. */
    record Part(String text, int color) {
    }

    /** One chip; {@code bar} below 0 means none. */
    record Chip(String title, List<Part> value, String note, int noteColor, int tone, float bar) {
    }

    EventTimersHud(EventTimersModule module) {
        super(EventTimersModule.ID, "skirmish.hud.element.event_timers", new Placement(0, 0, 0, 0,
                Theme.get().num("layout.events.default_x"), Theme.get().num("layout.events.default_y")));
        this.module = module;
    }

    /** Under the events list and schedule in the top-left column (their place while both are hidden). */
    @Override
    public @Nullable String stackUnder() {
        return "event_schedule";
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
        Theme t = Theme.get();
        long now = Util.getMillis();
        List<Chip> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            BlockPos core = module.core();
            if (core != null) {
                out.add(sunChip(t, now, core));
            }
            if (module.nearCastle() && (!module.shulkers().isEmpty() || module.aimedShulker() != null)) {
                out.add(castleChip(t));
            }
            if (!module.chests().isEmpty()) {
                out.add(pandoraChip(t, now));
            }
        }
        if (preview && out.isEmpty()) {
            out.add(new Chip(Ui.tr("skirmish.evtimers.sun.title"), List.of(new Part("+0:34", t.color("text"))),
                    Ui.tr("skirmish.evtimers.sun.in_zone"), t.color("good"), t.color("ev_sun"), 0.35f));
        }
        chips = out;
    }

    private Chip sunChip(Theme t, long now, BlockPos core) {
        Minecraft mc = Minecraft.getInstance();
        long since = module.drops.sinceMs(now);
        String value = since < 0 ? "—" : "+" + Ui.duration(since);
        double radius = t.num(L + "core_zone");
        String note;
        int noteColor;
        if (EventTimersModule.inZone(mc.player.position(), core, radius)) {
            note = Ui.tr("skirmish.evtimers.sun.in_zone");
            noteColor = t.color("good");
        } else {
            double d = Math.sqrt(mc.player.position().distanceToSqr(core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5));
            note = Ui.tr("skirmish.evtimers.sun.to_zone", (int) Math.ceil(d - radius));
            noteColor = t.color("text_2");
        }
        return new Chip(Ui.tr("skirmish.evtimers.sun.title"), List.of(new Part(value, t.color("text"))), note, noteColor,
                t.color("ev_sun"), since < 0 ? -1f : DropClock.window(since));
    }

    private Chip castleChip(Theme t) {
        Map<CastleShulkers.Rarity, Integer> counts = EventTimersModule.count(module.shulkers());
        List<Part> value = new ArrayList<>();
        CastleShulkers.Rarity[] order = {CastleShulkers.Rarity.EPIC, CastleShulkers.Rarity.RARE, CastleShulkers.Rarity.COMMON};
        for (int i = 0; i < order.length; i++) {
            if (i > 0) {
                value.add(new Part(" · ", t.color("text_4")));
            }
            value.add(new Part(Integer.toString(counts.get(order[i])), t.color(rarityTone(order[i]))));
        }
        BlockPos aimed = module.aimedShulker();
        String note;
        int noteColor;
        if (aimed != null && module.shulkers().get(aimed) != null) {
            CastleShulkers.Rarity rarity = module.shulkers().get(aimed);
            int done = module.hits(aimed);
            note = Ui.tr("skirmish.evtimers.castle.aimed", rarityName(rarity), CastleShulkers.left(rarity, done), rarity.breaks, done);
            noteColor = t.color(rarityTone(rarity));
        } else {
            note = Ui.tr("skirmish.evtimers.castle.hint");
            noteColor = t.color("text_2");
        }
        return new Chip(Ui.tr("skirmish.evtimers.castle.title"), value, note, noteColor, t.color("ev_castle"), -1f);
    }

    private Chip pandoraChip(Theme t, long now) {
        long left = Long.MAX_VALUE;
        for (long at : module.chests().values()) {
            left = Math.min(left, PandoraLabels.LIFE_MS - (now - at));
        }
        String tone = PandoraLabels.tone(left);
        return new Chip(Ui.tr("skirmish.evtimers.pandora.title"),
                List.of(new Part(Ui.tr("skirmish.evtimers.seconds", PandoraLabels.secondsText(left)), t.color(tone))),
                Ui.tr("skirmish.evtimers.pandora.chests", module.chests().size()), t.color("text_2"), t.color("ev_pandora"),
                Math.max(0f, left / (float) PandoraLabels.LIFE_MS));
    }

    static String rarityTone(CastleShulkers.Rarity rarity) {
        return "ev_shulker_" + rarity.name().toLowerCase(Locale.ROOT);
    }

    static String rarityName(CastleShulkers.Rarity rarity) {
        return Ui.tr("skirmish.evtimers.castle.rarity." + rarity.name().toLowerCase(Locale.ROOT));
    }

    // ---- layout ----

    private float chipHeight(Ui ui, Chip chip) {
        float h = ui.num(L + "chip_pad_y") * 2 + ui.lineHeight("ev_title");
        if (!chip.note().isEmpty()) {
            h += ui.num(L + "chip_line_gap") + ui.lineHeight("ev_note");
        }
        if (chip.bar() >= 0f) {
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
        float w = width(ui, preview);
        float cy = y;
        for (Chip chip : chips) {
            float h = chipHeight(ui, chip);
            drawChip(ui, chip, x, cy, w, h);
            cy += h + ui.num(L + "chip_gap");
        }
    }

    private void drawChip(Ui ui, Chip chip, float x, float y, float w, float h) {
        ui.box(x, y, w, h, ui.theme().radius("tile"), ui.color("panel"), ui.color("stroke"));
        float padX = ui.num(L + "chip_pad_x");
        float padY = ui.num(L + "chip_pad_y");
        float accent = ui.num(L + "chip_accent");
        ui.rect(x + padX, y + padY, accent, h - padY * 2, accent / 2f, chip.tone());
        float tx = x + padX + accent + ui.num(L + "chip_accent_gap");
        float right = x + w - padX;

        float valueW = 0f;
        for (Part part : chip.value()) {
            valueW += ui.textWidth("ev_value", part.text());
        }
        float vx = right - valueW;
        for (Part part : chip.value()) {
            vx = ui.text("ev_value", part.text(), vx, y + padY, part.color());
        }
        String title = ui.ellipsize("ev_title", chip.title(), right - valueW - ui.num(L + "chip_accent_gap") - tx);
        ui.text("ev_title", title, tx, y + padY);
        float line = y + padY + ui.lineHeight("ev_title");
        if (!chip.note().isEmpty()) {
            line += ui.num(L + "chip_line_gap");
            ui.text("ev_note", ui.ellipsize("ev_note", chip.note(), right - tx), tx, line, chip.noteColor());
            line += ui.lineHeight("ev_note");
        }
        if (chip.bar() >= 0f) {
            float barY = line + ui.num(L + "chip_bar_gap");
            float bar = ui.num(L + "chip_bar");
            float barW = right - tx;
            ui.rect(tx, barY, barW, bar, bar / 2f, ui.color("track"));
            ui.rect(tx, barY, barW * Math.max(0f, Math.min(1f, chip.bar())), bar, bar / 2f, chip.tone());
        }
    }
}
