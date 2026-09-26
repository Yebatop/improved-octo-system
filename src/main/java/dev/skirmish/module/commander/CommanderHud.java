package dev.skirmish.module.commander;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.module.events.EventSchedule;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Event Commander chips in the top-left column: a heads-up with a countdown when something starts soon, then the
 * nearest events with a waypoint — distance and ETA, or «на месте» with how long you have been there.
 */
final class CommanderHud extends HudBlock {
    private static final String L = CommanderModule.L;
    private final CommanderModule module;
    private List<Chip> chips = List.of();

    /** One chip: accent tone token, title, value on the right, a note, the note's tone and an optional bar. */
    private record Chip(String tone, String title, String value, String note, String noteTone, float bar) {
    }

    CommanderHud(CommanderModule module) {
        super("event_commander", "skirmish.hud.element.event_commander", new Placement(0, 0, 0, 0,
                Theme.get().num("layout.events.default_x"), Theme.get().num("layout.events.default_y")));
        this.module = module;
    }

    @Override
    public @Nullable String stackUnder() {
        return "base_os";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.card.get();
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
        List<Chip> out = new ArrayList<>();
        Instant now = Instant.now();
        HeadsUp.Item soon = module.headsUpNow();
        if (soon != null) {
            long left = Duration.between(now, soon.at()).toMillis();
            float lead = (float) (module.lead.get() * 60_000);
            out.add(left > 0
                    ? new Chip("warn", soon.name(), EventSchedule.clock(left), soon.note(), "text_2", Math.max(0f, Math.min(1f, left / lead)))
                    : new Chip("good", soon.name(), Ui.tr("skirmish.commander.started"), soon.note(), "good", -1f));
        }
        int max = Theme.get().integer(L + "max_marks");
        long nowMs = System.currentTimeMillis();
        for (CommanderModule.Mark m : module.marks()) {
            if (out.size() >= max + (soon != null ? 1 : 0)) {
                break;
            }
            if (m.onSite()) {
                out.add(new Chip("good", m.event(), Ui.tr("skirmish.commander.here"),
                        Ui.tr("skirmish.commander.here_for", EventSchedule.clock(nowMs - m.siteSince()),
                                EventSchedule.clock(nowMs - m.waypoint().createdAt())), "good", -1f));
            } else if (m.blocks() < 0) {
                out.add(new Chip("accent", m.event(), "—", Ui.tr("skirmish.commander.no_way_note"), "text_3", -1f));
            } else {
                String note = Ui.tr(m.viaPortal() ? "skirmish.commander.eta_portal" : "skirmish.commander.eta",
                        EventSchedule.clock(Math.round(m.seconds() * 1000)), EventSchedule.clock(nowMs - m.waypoint().createdAt()));
                out.add(new Chip("accent", m.event(), CommanderModule.distance(m.blocks()), note, "text_2", -1f));
            }
        }
        if (preview && out.isEmpty()) {
            out.add(new Chip("warn", Ui.tr("skirmish.commander.kind.end"), "2:41", Ui.tr("skirmish.commander.note.end"), "text_2", 0.9f));
            out.add(new Chip("accent", "Метеоритный дождь", "340 м", Ui.tr("skirmish.commander.eta", "1:05", "3:10"), "text_2", -1f));
        }
        chips = out;
    }

    // ---- layout (the event chip look) ----

    private float chipHeight(Ui ui, Chip chip) {
        float h = ui.num(L + "chip_pad_y") * 2 + ui.lineHeight("ev_title") + ui.num(L + "chip_line_gap") + ui.lineHeight("ev_note");
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
            ui.box(x, cy, w, h, ui.theme().radius("tile"), ui.color("panel"), ui.color("stroke"));
            float padX = ui.num(L + "chip_pad_x");
            float padY = ui.num(L + "chip_pad_y");
            float accent = ui.num(L + "chip_accent");
            int tone = ui.color(chip.tone());
            ui.rect(x + padX, cy + padY, accent, h - padY * 2, accent / 2f, tone);
            float tx = x + padX + accent + ui.num(L + "chip_accent_gap");
            float right = x + w - padX;
            float vw = ui.textWidth("ev_value", chip.value());
            ui.text("ev_value", chip.value(), right - vw, cy + padY, tone);
            ui.text("ev_title", ui.ellipsize("ev_title", chip.title(), right - vw - ui.num(L + "chip_accent_gap") - tx), tx, cy + padY);
            float line = cy + padY + ui.lineHeight("ev_title") + ui.num(L + "chip_line_gap");
            ui.text("ev_note", ui.ellipsize("ev_note", chip.note(), right - tx), tx, line, ui.color(chip.noteTone()));
            if (chip.bar() >= 0f) {
                float barY = line + ui.lineHeight("ev_note") + ui.num(L + "chip_bar_gap");
                float bar = ui.num(L + "chip_bar");
                ui.rect(tx, barY, right - tx, bar, bar / 2f, ui.color("track"));
                ui.rect(tx, barY, (right - tx) * chip.bar(), bar, bar / 2f, tone);
            }
            cy += h + ui.num(L + "chip_gap");
        }
    }
}
