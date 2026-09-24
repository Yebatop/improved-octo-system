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

    /** Steps aside during a fight: the fight panel uses the same corner by default. */
    @Override
    public boolean shown() {
        return HolyWorld.isConnected() && dev.skirmish.combat.CombatTracker.get().activeFights().isEmpty();
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
                String sub = at == null ? null : at.coords().text() + dimensionSuffix(at.dimension());
                out.add(new EventRows.Row(e.name(), e.rarity(), EventRows.chipText(e.rarity(), e.rareRaw()), "", "text", sub, true));
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
            out.add(new EventRows.Row(e.name(), e.rarity(), EventRows.chipText(e.rarity(), e.rareRaw()),
                    module.serverName(e.serverId()), "text_3", null, false));
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
        for (EventsJson.PrimeEvent e : module.primeCurrent()) {
            if (mine && !e.server().equals(server.apiId())) {
                continue;
            }
            running.computeIfAbsent(e.plugin(), k -> new TreeSet<>()).add(e.server());
            started.merge(e.plugin(), e.running(), Boolean::logicalOr);
        }
        for (Map.Entry<String, TreeSet<String>> e : running.entrySet()) {
            if (rows++ >= max) {
                break;
            }
            boolean isRunning = started.getOrDefault(e.getKey(), false);
            String sub = mine ? null : Ui.tr("skirmish.events.prime.servers", String.join(", ", e.getValue()));
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
                new EventRows.Row("Контейнер", Rarity.LEGENDARY, EventRows.chipText(Rarity.LEGENDARY, ""), "", "text", "-1520 71 830", true),
                new EventRows.Row("Опытный Тыпо", Rarity.EPIC, EventRows.chipText(Rarity.EPIC, ""), "", "text", null, false),
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
}
