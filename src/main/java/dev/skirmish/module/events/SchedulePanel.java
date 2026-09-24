package dev.skirmish.module.events;

import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * «Расписание» panel: HolyWorld's fixed schedule converted from MSK to local time (Захват Энда, Бункер, the
 * event vote when a vote was seen, daily restart). Lite rows are hidden on Prime.
 */
final class SchedulePanel extends HudBlock {
    private final EventsModule module;
    private List<EventRows.Item> items = List.of();
    private String meta = "";

    SchedulePanel(EventsModule module) {
        super("event_schedule", "skirmish.hud.element.event_schedule", new Placement(1, 0.5f, 1, 0.5f,
                Theme.get().num(EventRows.L + "schedule_default_x"), Theme.get().num(EventRows.L + "schedule_default_y")));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.schedule.get();
    }

    /** Hidden in a fight like the events list; left at its default place it sits under that list. */
    @Override
    public boolean shown() {
        return HolyWorld.isConnected() && dev.skirmish.combat.CombatTracker.get().activeFights().isEmpty();
    }

    @Override
    public boolean hasContent() {
        return HolyWorld.isConnected();
    }

    @Override
    public String stackUnder() {
        return "events";
    }

    @Override
    public void update(boolean preview) {
        Instant now = Instant.now();
        var msk = now.atZone(EventSchedule.MSK);
        meta = Ui.tr("skirmish.events.schedule.msk", String.format(Locale.ROOT, "%02d:%02d", msk.getHour(), msk.getMinute()));
        List<EventRows.Item> out = new ArrayList<>();
        String running = Theme.get().string("events.state.running");
        String countdown = Theme.get().string("events.state.countdown");
        boolean lite = module.showLite() || (preview && !HolyWorld.isConnected());
        if (lite) {
            EventSchedule.Window end = EventSchedule.endCapture(now);
            String span = EventRows.local(end.start()) + "–" + EventRows.local(end.end()).replaceAll("^\\D+ ", "");
            if (end.active(now)) {
                out.add(new EventRows.Row(Ui.tr("skirmish.events.schedule.end"), null, "",
                        Ui.tr("skirmish.events.state.left", EventRows.countdown(end.end().toEpochMilli() - now.toEpochMilli())),
                        running, span, false));
            } else {
                out.add(new EventRows.Row(Ui.tr("skirmish.events.schedule.end"), null, "",
                        EventRows.countdown(end.start().toEpochMilli() - now.toEpochMilli()), countdown, span, false));
            }
            Instant bunker = EventSchedule.nextBunker(now);
            out.add(new EventRows.Row(Ui.tr("skirmish.events.schedule.bunker"), null, "",
                    EventRows.countdown(bunker.toEpochMilli() - now.toEpochMilli()), countdown,
                    Ui.tr("skirmish.events.schedule.bunker.sub", EventRows.local(bunker)), false));
            EventsJson.Voting voting = module.myVoting();
            if (voting != null) {
                out.add(new EventRows.Row(Ui.tr("skirmish.events.schedule.vote"), null, "", Ui.tr("skirmish.events.state.running"),
                        running, candidates(voting), false));
            } else {
                Instant next = preview && module.voteAnchor() == null ? now.plusSeconds(1234)
                        : EventSchedule.nextVote(now, module.voteAnchor());
                if (next != null) {
                    out.add(new EventRows.Row(Ui.tr("skirmish.events.schedule.vote"), null, "",
                            "≈" + EventRows.countdown(next.toEpochMilli() - now.toEpochMilli()), countdown,
                            Ui.tr("skirmish.events.schedule.vote.sub", EventRows.local(next)), false));
                }
            }
        }
        Instant restart = EventSchedule.nextRestart(now);
        out.add(new EventRows.Row(Ui.tr("skirmish.events.schedule.restart"), null, "",
                EventRows.countdown(restart.toEpochMilli() - now.toEpochMilli()), countdown,
                Ui.tr("skirmish.events.at", EventRows.local(restart)), false));
        items = out;
    }

    private static String candidates(EventsJson.Voting voting) {
        StringBuilder sb = new StringBuilder();
        for (EventsJson.Candidate c : voting.candidates()) {
            if (!sb.isEmpty()) {
                sb.append(" · ");
            }
            sb.append(c.name()).append(' ').append(c.votes());
        }
        return sb.isEmpty() ? Ui.tr("skirmish.events.schedule.vote.open") : sb.toString();
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(EventRows.L + "schedule_width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return EventRows.height(ui, items);
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        EventRows.render(ui, x, y, width(ui, preview), Ui.tr("skirmish.events.schedule.title"), meta, items, true);
    }
}
