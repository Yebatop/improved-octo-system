package dev.skirmish.module.events;

import dev.skirmish.holyworld.HolyApi;
import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.SkirmishKeys;
import dev.skirmish.hud.DetailMode;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.KeyNames;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * «Ивенты» panel: live Lite events of the player's sub-server (name, rarity, coordinates seen in chat), or the
 * rarest events of all servers when the sub-server is unknown; Prime events running now and countdowns to the
 * next ones. Per «Подробности» the list is collapsed to the header and the first rows, expanded while the details
 * key is held (height eased over {@code motion.expand_ms}), or always full.
 */
final class EventsPanel extends HudBlock {
    /** Event rows (or notes) the collapsed panel keeps. */
    static final int COLLAPSED_ROWS = 2;
    private final EventsModule module;
    private final Anim expand = new Anim("expand_ms");
    private List<EventRows.Item> items = List.of();
    private List<EventRows.Item> collapsed = List.of();
    private @Nullable String meta;

    EventsPanel(EventsModule module) {
        super("events", "skirmish.hud.element.events", new Placement(0, 0, 0, 0,
                Theme.get().num(EventRows.L + "default_x"), Theme.get().num(EventRows.L + "default_y")));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.hud.get();
    }

    /** Steps aside in a fight through the HUD's fight focus (the fight panel uses the same corner by default). */
    @Override
    public boolean shown() {
        return HolyWorld.isConnected();
    }

    @Override
    public boolean hasContent() {
        return HolyWorld.isConnected();
    }

    @Override
    public void update(boolean preview) {
        expand.target(module.detailsExpanded());
        fill(preview);
        List<EventRows.Item> head = EventRows.collapsed(items, COLLAPSED_ROWS);
        int hidden = EventRows.hiddenRows(items, head);
        if (hidden > 0) {
            List<EventRows.Item> withNote = new ArrayList<>(head);
            withNote.add(new EventRows.Note(module.details.get() == DetailMode.HOLD && !SkirmishKeys.DETAILS.isUnbound()
                    ? Ui.tr("skirmish.events.more_hold", hidden, KeyNames.shortName(SkirmishKeys.DETAILS))
                    : Ui.tr("skirmish.events.more", hidden)));
            head = withNote;
        }
        collapsed = head;
    }

    private void fill(boolean preview) {
        if (preview && !HolyWorld.isConnected()) {
            items = sample();
            meta = "ДуоЛайт #17";
            return;
        }
        List<EventRows.Item> out = new ArrayList<>();
        boolean lite = module.showLite();
        boolean prime = module.showPrime();
        boolean both = lite && prime;
        ServerParser.ServerRef server = module.currentServer();
        meta = module.currentServerName();
        if (meta == null) {
            meta = Ui.tr("skirmish.events.all_servers");
        }
        if (!HolyApi.canRequest() && !module.hasLiteData() && !module.hasPrimeData()) {
            out.add(new EventRows.Note(Ui.tr("skirmish.events.api_off")));
            items = out;
            return;
        }
        int max = module.maxRows.getInt();
        if (lite) {
            if (both) {
                out.add(new EventRows.Section(Ui.tr("skirmish.events.section.lite")));
            }
            liteRows(out, server, max);
        }
        if (prime) {
            if (both) {
                out.add(new EventRows.Section(Ui.tr("skirmish.events.section.prime")));
            }
            primeRows(out, server, max);
        }
        // Nothing on: just the header, «ДуоЛайт #12 · ивентов нет», instead of a panel with one grey line.
        if (out.size() == 1 && out.getFirst() instanceof EventRows.Note note
                && (note.text().equals(Ui.tr("skirmish.events.none_here")) || note.text().equals(Ui.tr("skirmish.events.none")))) {
            out.clear();
            meta = meta + " · " + Ui.tr("skirmish.events.none_meta");
        }
        items = out;
    }

    private void liteRows(List<EventRows.Item> out, ServerParser.@Nullable ServerRef server, int max) {
        if (!module.hasLiteData()) {
            out.add(new EventRows.Note(Ui.tr("skirmish.events.loading")));
            return;
        }
        if (server != null && !server.isPrime()) {
            List<EventsJson.LiteEvent> mine = module.myLiteEvents();
            if (mine.isEmpty()) {
                out.add(new EventRows.Note(Ui.tr("skirmish.events.none_here")));
            }
            for (EventsJson.LiteEvent e : mine.subList(0, Math.min(max, mine.size()))) {
                KnownCoords.Entry at = module.coordsOf(e.name());
                String where = at == null ? null : at.coords().text() + dimensionSuffix(at.dimension());
                // Under the name: where it is (when chat said) and the countdown chat gave, else how long it goes on.
                String countdown = module.countdownText(e.name());
                EventClock.Estimate lasts = module.liteLasts(e);
                String time = countdown != null ? countdown : lasts == null ? null : EventsModule.lastsText(lasts);
                String sub = where == null ? time : time == null ? where : where + " · " + time;
                String tone = countdown != null ? "accent"
                        : lasts != null && "warn".equals(EventsModule.lastsTone(lasts)) ? "warn" : null;
                out.add(new EventRows.Row(e.name(), module.rarityOf(e), module.chipOf(e), "", "text", sub, where != null, tone));
            }
            return;
        }
        List<EventsJson.LiteEvent> all = module.allLiteEventsByRarity();
        if (all.isEmpty()) {
            out.add(new EventRows.Note(Ui.tr("skirmish.events.none")));
            return;
        }
        int shown = Math.min(max, all.size());
        for (EventsJson.LiteEvent e : all.subList(0, shown)) {
            EventClock.Estimate lasts = module.liteLasts(e);
            out.add(new EventRows.Row(e.name(), e.rarity(), EventRows.chipText(e.rarity(), e.rareRaw()),
                    module.serverName(e.serverId()), "text_3", lasts == null ? null : EventsModule.lastsText(lasts), false));
        }
        if (all.size() > shown) {
            out.add(new EventRows.Note(Ui.tr("skirmish.events.more", all.size() - shown)));
        }
    }

    private static String dimensionSuffix(String dimension) {
        return dimension.equals("minecraft:overworld") ? "" : " · " + Ui.tr("skirmish.events.dimension." + dimension.replace("minecraft:", ""));
    }

    private void primeRows(List<EventRows.Item> out, ServerParser.@Nullable ServerRef server, int max) {
        if (!module.hasPrimeData()) {
            out.add(new EventRows.Note(Ui.tr("skirmish.events.loading")));
            return;
        }
        boolean mine = server != null && server.isPrime();
        Instant now = Instant.now();
        int rows = 0;
        // Running / pending now: on my server, or grouped by event type with the servers listed.
        Map<String, TreeSet<String>> running = new LinkedHashMap<>();
        Map<String, Boolean> started = new LinkedHashMap<>();
        Map<String, EventClock.Estimate> lasts = new LinkedHashMap<>();
        for (EventsJson.PrimeEvent e : module.primeCurrent()) {
            if (mine && !e.server().equals(server.apiId())) {
                continue;
            }
            running.computeIfAbsent(e.plugin(), k -> new TreeSet<>()).add(e.server());
            started.merge(e.plugin(), e.running(), Boolean::logicalOr);
            EventClock.Estimate est = e.running() ? module.primeLasts(e) : null;
            if (est != null) {
                lasts.merge(e.plugin(), est, (a, b) -> a.elapsed() >= b.elapsed() ? a : b);
            }
        }
        for (Map.Entry<String, TreeSet<String>> e : running.entrySet()) {
            if (rows++ >= max) {
                break;
            }
            boolean isRunning = started.getOrDefault(e.getKey(), false);
            String sub = mine ? null : Ui.tr("skirmish.events.prime.servers", String.join(", ", e.getValue()));
            EventClock.Estimate est = lasts.get(e.getKey());
            if (isRunning && est != null) {
                // Running: how much is left (or how long it has been on) instead of the bare state.
                out.add(new EventRows.Row(EventsModule.primeName(e.getKey()), null, "", EventsModule.lastsText(est),
                        est.typical() < 0 ? Theme.get().string("events.state.running") : EventsModule.lastsTone(est), sub, false));
                continue;
            }
            out.add(new EventRows.Row(EventsModule.primeName(e.getKey()), null, "",
                    Ui.tr(isRunning ? "skirmish.events.state.running" : "skirmish.events.state.pending"),
                    Theme.get().string("events.state." + (isRunning ? "running" : "pending")), sub, false));
        }
        for (EventsJson.PrimeSlot slot : module.primeTimetable()) {
            if (rows++ >= max) {
                break;
            }
            long ms = slot.at().toEpochMilli() - now.toEpochMilli();
            boolean due = ms <= 0;
            out.add(new EventRows.Row(EventsModule.primeName(slot.key()), null, "",
                    due ? Ui.tr("skirmish.events.state.now") : EventRows.countdown(ms),
                    Theme.get().string("events.state." + (due ? "soon" : "countdown")),
                    Ui.tr("skirmish.events.at", EventRows.local(slot.at())), false));
        }
        if (rows == 0) {
            out.add(new EventRows.Note(Ui.tr("skirmish.events.none")));
        }
    }

    private static List<EventRows.Item> sample() {
        long in = 754_000;
        return List.of(
                new EventRows.Row("Контейнер", Rarity.LEGENDARY, EventRows.chipText(Rarity.LEGENDARY, ""), "", "text",
                        "-1520 71 830 · " + EventsModule.lastsText(new EventClock.Estimate(300_000, true, 1_020_000, 720_000)), true),
                new EventRows.Row("Опытный Тыпо", Rarity.EPIC, EventRows.chipText(Rarity.EPIC, ""), "", "text",
                        EventsModule.lastsText(new EventClock.Estimate(240_000, true, -1, -1)), false),
                new EventRows.Row(EventsModule.primeName("golden_bob"), null, "", EventRows.countdown(in), "text",
                        Ui.tr("skirmish.events.at", "21:00"), false));
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(EventRows.L + "width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        float t = expand.value();
        float small = EventRows.height(ui, collapsed);
        return Math.round(small + (EventRows.height(ui, items) - small) * t);
    }

    /** Collapsed list at rest; while expanding/expanded the full list, clipped to the eased panel height. */
    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        List<EventRows.Item> shown = expand.value() <= 0f ? collapsed : items;
        EventRows.render(ui, x, y, width(ui, preview), height(ui, preview), Ui.tr("skirmish.events.title"), meta, shown, false);
    }

    @Override
    public boolean stepsAsideInFight() {
        return true;
    }
}
