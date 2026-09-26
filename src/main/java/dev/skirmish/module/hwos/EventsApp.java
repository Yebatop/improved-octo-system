package dev.skirmish.module.hwos;

import dev.skirmish.module.ModuleManager;
import dev.skirmish.module.commander.CommanderModule;
import dev.skirmish.module.commander.HeadsUp;
import dev.skirmish.module.events.EventSchedule;
import dev.skirmish.module.events.EventsJson;
import dev.skirmish.module.events.EventsModule;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Widget;
import dev.skirmish.waypoint.WaypointManager;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Events app: what starts soon (Event Commander), events with a waypoint near you (click to head there), the Prime
 * timetable and the best events on all Lite anarchies right now.
 */
final class EventsApp implements HwOsScreen.OsApp {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private final Map<String, Widget> marks = new HashMap<>();

    @Override
    public String id() {
        return "events";
    }

    private static EventsModule events() {
        return ModuleManager.get().byId(EventsModule.ID) instanceof EventsModule e && e.isEnabled() ? e : null;
    }

    @Override
    public void draw(HwOsScreen s, Ui ui, float x, float y, float w, float h, double mx, double my) {
        String L = HwOsScreen.L;
        float gap = ui.num(L + "col_gap");
        float colW = (w - gap) / 2f;
        float rowH = ui.lineHeight("hwos_row") + ui.num(L + "row_gap");
        Instant now = Instant.now();
        CommanderModule commander = CommanderModule.instance();
        EventsModule ev = events();
        s.clip(ui, x - 4, y, x + w + 4, y + h);
        float top = y - s.scroll();

        // Left: soon, near me.
        float ly = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.events.soon"), x, top);
        List<HeadsUp.Item> soon = commander == null || !commander.isEnabled() ? List.of() : commander.upcoming();
        if (soon.isEmpty()) {
            ly = HwOsScreen.para(ui, "menu_row_desc", Ui.tr(commander == null || !commander.isEnabled()
                    ? "skirmish.hwos.events.commander_off" : "skirmish.hwos.events.nothing_soon"), x, ly, colW, ui.color("text_3"));
        }
        for (HeadsUp.Item item : soon.subList(0, Math.min(6, soon.size()))) {
            long left = Duration.between(now, item.at()).toMillis();
            String when = CLOCK.format(item.at().atZone(ZoneId.systemDefault()));
            String value = left > 0 ? countdown(left) : Ui.tr("skirmish.commander.started");
            row(ui, x, ly, colW, item.name(), item.note() + " · " + when, value, left > 0 && left < 600_000 ? "warn" : "text");
            ly += rowH * 2;
        }
        ly += gap / 2;
        ly = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.events.near"), x, ly);
        List<CommanderModule.Mark> list = commander == null || !commander.isEnabled() ? List.of() : commander.marks();
        if (list.isEmpty()) {
            ly = HwOsScreen.para(ui, "menu_row_desc", Ui.tr("skirmish.hwos.events.no_marks"), x, ly, colW, ui.color("text_3"));
        }
        for (CommanderModule.Mark m : list) {
            String id = m.waypoint().id();
            Widget area = marks.computeIfAbsent(id, k -> new ClickArea(() -> {
                WaypointManager.get().select(k);
                s.close();
            }));
            area.bounds(x - 4, ly - 2, colW + 8, rowH * 2);
            s.use(ui, area, mx, my);
            if (area.contains(mx, my)) {
                ui.rect(x - 4, ly - 2, colW + 8, rowH * 2, ui.theme().radius("button_sm"), ui.color("fill_05"));
            }
            String value = m.onSite() ? Ui.tr("skirmish.commander.here") : m.blocks() < 0 ? "—" : distance(m.blocks());
            String note = m.onSite() ? Ui.tr("skirmish.hwos.events.on_site")
                    : m.seconds() < 0 ? Ui.tr("skirmish.commander.no_way_note")
                    : Ui.tr("skirmish.hwos.events.eta", EventSchedule.clock(Math.round(m.seconds() * 1000)));
            row(ui, x, ly, colW, m.event(), note, value, m.onSite() ? "good" : "accent");
            ly += rowH * 2;
        }

        // Right: Prime timetable, Lite now.
        float rx = x + colW + gap;
        float ry = top;
        if (ev != null && !ev.primeSlots().isEmpty()) {
            ry = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.events.prime"), rx, ry);
            List<EventsJson.PrimeSlot> slots = new ArrayList<>(ev.primeSlots());
            slots.sort(Comparator.comparing(EventsJson.PrimeSlot::at));
            for (EventsJson.PrimeSlot slot : slots) {
                long left = Duration.between(now, slot.at()).toMillis();
                String when = CLOCK.format(slot.at().atZone(ZoneId.systemDefault()));
                row(ui, rx, ry, colW, EventsModule.primeDisplayName(slot.key()), when,
                        left > 0 ? countdown(left) : Ui.tr("skirmish.commander.started"), "text");
                ry += rowH * 2;
            }
            ry += gap / 2;
        }
        ry = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.events.lite_now"), rx, ry);
        List<String[]> live = new ArrayList<>();
        if (ev != null) {
            List<EventsModule.Anarchy> all = new ArrayList<>(ev.anarchies());
            all.sort(Comparator.comparing((EventsModule.Anarchy a) -> a.events().isEmpty() ? dev.skirmish.module.events.Rarity.UNKNOWN
                    : a.events().getFirst().rarity()).reversed());
            for (EventsModule.Anarchy a : all) {
                for (EventsModule.LiveEvent e : a.events()) {
                    live.add(new String[]{e.name(), a.name() + (a.mine() ? " · " + Ui.tr("skirmish.hwos.you_here") : ""),
                            Ui.tr("skirmish.events.rarity." + e.rarity().key()), HwOsScreen.rarityTone(e.rarity())});
                }
            }
        }
        if (live.isEmpty()) {
            ry = HwOsScreen.para(ui, "menu_row_desc", Ui.tr(ev == null ? "skirmish.hwos.events.events_off" : "skirmish.hwos.events.no_live"),
                    rx, ry, colW, ui.color("text_3"));
        }
        for (String[] e : live) {
            row(ui, rx, ry, colW, e[0], e[1], e[2], e[3]);
            ry += rowH * 2;
        }
        s.unclip(ui);
        s.content(Math.max(ly, ry) - top, h);
    }

    /** Two-line row: title and value on the first line, a note under it. */
    static void row(Ui ui, float x, float y, float w, String title, String note, String value, String tone) {
        float vw = ui.textWidth("hwos_value", value);
        ui.text("hwos_value", value, x + w - vw, y, ui.color(tone));
        ui.text("hwos_row", ui.ellipsize("hwos_row", title, w - vw - 12), x, y);
        ui.text("menu_row_desc", ui.ellipsize("menu_row_desc", note, w), x, y + ui.lineHeight("hwos_row") + 1);
    }

    /** "1д 4ч" / "16ч 41м" for long waits, a clock under an hour. */
    static String countdown(long ms) {
        return OsData.span(ms, EventSchedule.clock(ms), Ui.tr("skirmish.base.unit.d"), Ui.tr("skirmish.base.unit.h"), Ui.tr("skirmish.base.unit.m"));
    }

    static String distance(double blocks) {
        return blocks >= 1000 ? Ui.decimal(blocks / 1000.0, 1) + " " + Ui.tr("skirmish.elytra.km")
                : Math.round(blocks) + " " + Ui.tr("skirmish.elytra.m");
    }

    /** An invisible clickable area. */
    static final class ClickArea extends Widget {
        private final Runnable click;

        ClickArea(Runnable click) {
            this.click = click;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                click.run();
                return true;
            }
            return false;
        }
    }
}
