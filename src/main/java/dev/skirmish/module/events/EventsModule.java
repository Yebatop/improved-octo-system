package dev.skirmish.module.events;

import dev.skirmish.module.Category;
import com.google.gson.JsonElement;
import dev.skirmish.holyworld.HolyApi;
import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.hud.DetailMode;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.setting.StringSetting;
import dev.skirmish.ui.Theme;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * HolyWorld events: live Lite events of the player's sub-server and Prime countdowns (api.holyworld.me through
 * {@link HolyApi}), the fixed schedule (End capture, bunker, vote, restart) in local time, toasts for new events,
 * and a clickable "[+метка]" after coordinates in chat. Reads only what the client receives; never sends commands.
 */
public final class EventsModule extends Module {
    public static final String ID = "events";

    static final String EVENTS = "/v1/events";
    static final String SERVERS = "/v1/servers";
    static final String VOTINGS = "/v1/votings";
    static final String PRIME_TIMETABLE = "/v2/prime/events/timetable";
    static final String PRIME_CURRENT = "/v2/prime/events/current";
    private static final Duration EVENTS_TTL = Duration.ofSeconds(30);
    private static final Duration SERVERS_TTL = Duration.ofDays(1);
    private static final Duration VOTINGS_IDLE_TTL = Duration.ofSeconds(60);
    private static final Duration VOTINGS_ACTIVE_TTL = Duration.ofSeconds(15);
    private static final Duration PRIME_TIMETABLE_TTL = Duration.ofMinutes(10);
    private static final Duration PRIME_CURRENT_TTL = Duration.ofSeconds(60);
    /** A new chat vote line re-anchors the 65-min cycle only when the previous anchor is older than this. */
    private static final Duration VOTE_REANCHOR = Duration.ofMinutes(30);

    /** Which part of the panel to show. */
    public enum Section {
        AUTO, LITE, PRIME, BOTH
    }

    /** Which new events raise a toast. */
    public enum ToastScope {
        OFF, MY_SERVER, ALL
    }

    final BoolSetting hud = (BoolSetting) add(new BoolSetting("hud", true)).feature("event_hud");
    final BoolSetting schedule = (BoolSetting) add(new BoolSetting("schedule", true)).feature("event_hud");
    final EnumSetting<ToastScope> toasts = add(new EnumSetting<>("toasts", ToastScope.MY_SERVER));
    final EnumSetting<Section> section = add(new EnumSetting<>("section", Section.AUTO));
    final StringSetting server = add(new StringSetting("server", "", 32, false));
    final NumberSetting maxRows = add(new NumberSetting("max_rows", 5, 2, 10, 1));
    /** Panel collapsed to the first rows, expanded while {@link dev.skirmish.SkirmishKeys#DETAILS} is held, or always full. */
    final EnumSetting<DetailMode> details = add(new EnumSetting<>("details", DetailMode.HOLD));
    private final KeySetting detailsKey = add(new KeySetting("details_key", "key.skirmish.details"));
    final BoolSetting waypoints = (BoolSetting) add(new BoolSetting("waypoints", true)).feature("event_waypoints");
    final BoolSetting everywhere = (BoolSetting) add(new BoolSetting("everywhere", false)).feature("event_waypoints");

    private final ServerDetector detector = new ServerDetector();
    private final KnownCoords coords = new KnownCoords(Math.round(Theme.get().num("layout.events.coords_max_age_ms")));
    private final InstanceDiff<EventsJson.LiteEvent> liteDiff = new InstanceDiff<>(EventsJson.LiteEvent::instanceId);
    private final InstanceDiff<EventsJson.PrimeEvent> primeDiff = new InstanceDiff<>(EventsJson.PrimeEvent::uuid);
    private final EventToast toast = new EventToast(this);
    private final ChatWaypoints chat = new ChatWaypoints(this);

    private Map<String, String> servers = Map.of();
    private List<EventsJson.LiteEvent> liteEvents = List.of();
    private List<EventsJson.PrimeEvent> primeCurrent = List.of();
    private List<EventsJson.PrimeSlot> primeTimetable = List.of();
    private List<EventsJson.Voting> votings = List.of();
    private @Nullable JsonElement rawServers;
    private @Nullable JsonElement rawEvents;
    private @Nullable JsonElement rawCurrent;
    private @Nullable JsonElement rawTimetable;
    private @Nullable JsonElement rawVotings;
    private ServerParser.@Nullable ServerRef current;
    private @Nullable Instant voteAnchor;
    private @Nullable String anchoredVoting;
    private int detectCountdown;

    @Override
    public Category category() {
        return Category.WORLD;
    }

    public EventsModule() {
        super(ID, true);
        toasts.feature("event_hud");
        everywhere.under(waypoints).visibleWhen(waypoints::get);
        details.under(hud).visibleWhen(hud::get);
        detailsKey.under(hud).visibleWhen(() -> hud.get() && details.get() == DetailMode.HOLD);
    }

    /** The events panel shows its full list now (mode FULL, or HOLD with the details key held). */
    boolean detailsExpanded() {
        return details.get().expanded(dev.skirmish.SkirmishKeys.DETAILS.isDown());
    }

    @Override
    public void onInitialize() {
        Hud.get().register(new EventsPanel(this));
        Hud.get().register(new SchedulePanel(this));
        Hud.get().register(toast);
        ClientReceiveMessageEvents.MODIFY_GAME.register(chat::modify);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(this::resetSession));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::resetSession));
    }

    @Override
    protected void onDisable() {
        toast.clear();
    }

    private void resetSession() {
        detector.reset();
        coords.clear();
        liteDiff.reset();
        primeDiff.reset();
        current = null;
        voteAnchor = null;
        anchoredVoting = null;
        toast.clear();
        detectCountdown = 0;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !HolyWorld.isConnected()) {
            return;
        }
        if (--detectCountdown <= 0) {
            detectCountdown = Theme.get().integer("layout.events.detect_interval_ticks");
            detect(mc);
        }
        boolean live = hud.get() || toastScope() != ToastScope.OFF;
        boolean wantLite = showLite();
        boolean wantPrime = showPrime();
        if (wantLite || current == null) {
            JsonElement json = HolyApi.poll(SERVERS, SERVERS_TTL);
            if (json != rawServers) {
                rawServers = json;
                servers = EventsJson.servers(json);
                log("servers: %d display names", servers.size());
            }
        }
        if (live && wantLite) {
            JsonElement json = HolyApi.poll(EVENTS, EVENTS_TTL);
            if (json != rawEvents) {
                rawEvents = json;
                liteEvents = EventsJson.liteEvents(json, "");
                onNewLite(liteDiff.update(liteEvents));
            }
        }
        if (schedule.get() && wantLite) {
            boolean active = myVoting() != null;
            JsonElement json = HolyApi.poll(VOTINGS, active ? VOTINGS_ACTIVE_TTL : VOTINGS_IDLE_TTL);
            if (json != rawVotings) {
                rawVotings = json;
                votings = EventsJson.votings(json, "");
                EventsJson.Voting mine = myVoting();
                if (mine != null && !mine.instanceId().equals(anchoredVoting)) {
                    anchoredVoting = mine.instanceId();
                    voteAnchor = Instant.now();
                    log("vote %s seen in the API, 65-min cycle anchored", mine.instanceId());
                }
            }
        }
        if (live && wantPrime) {
            JsonElement table = HolyApi.poll(PRIME_TIMETABLE, PRIME_TIMETABLE_TTL);
            if (table != rawTimetable) {
                rawTimetable = table;
                primeTimetable = EventsJson.primeTimetable(table);
            }
            JsonElement now = HolyApi.poll(PRIME_CURRENT, PRIME_CURRENT_TTL);
            if (now != rawCurrent) {
                rawCurrent = now;
                primeCurrent = EventsJson.primeCurrent(now);
                onNewPrime(primeDiff.update(primeCurrent));
            }
        }
    }

    private void detect(Minecraft mc) {
        ServerParser.ServerRef override = ServerParser.parseOverride(server.get(), servers);
        ServerParser.ServerRef next = override != null ? override : detector.detect(mc, servers);
        if (!Objects.equals(next == null ? null : next.apiId() + next.mode(), current == null ? null : current.apiId() + current.mode())) {
            log(next == null ? "sub-server unknown" : "sub-server %s (%s, from %s)", next == null ? null : next.apiId(),
                    next == null ? null : next.mode(), next == null ? null : next.source());
            if (current != null && next != null && voteAnchor != null) {
                voteAnchor = null;
                anchoredVoting = null;
            }
        }
        current = next;
    }

    private void onNewLite(List<EventsJson.LiteEvent> fresh) {
        ToastScope scope = toastScope();
        for (EventsJson.LiteEvent e : fresh) {
            log("new Lite event %s (%s, %s) on %s", e.name(), e.id(), e.rareRaw(), e.serverId());
            boolean mine = current != null && !current.isPrime() && current.apiId().equals(e.serverId());
            boolean show = switch (scope) {
                case OFF -> false;
                case ALL -> true;
                case MY_SERVER -> mine || (current == null && e.rarity() == Rarity.LEGENDARY);
            };
            if (show && showLite()) {
                toast.push(e.name(), e.rarity(), e.rareRaw(), serverName(e.serverId()));
            }
        }
    }

    private void onNewPrime(List<EventsJson.PrimeEvent> fresh) {
        ToastScope scope = toastScope();
        for (EventsJson.PrimeEvent e : fresh) {
            log("new Prime event %s (%s, %s) on %s", e.plugin(), e.template(), e.state(), e.server());
            boolean mine = current != null && current.isPrime() && current.apiId().equals(e.server());
            boolean show = switch (scope) {
                case OFF -> false;
                case ALL -> true;
                case MY_SERVER -> mine;
            };
            if (show && showPrime()) {
                toast.push(primeName(e.plugin()), Rarity.UNKNOWN, "", primeServerName(e.server()));
            }
        }
    }

    /** A system chat line (called by {@link ChatWaypoints} before it adds links). */
    void onChatLine(String plain, List<ChatCoords.Coords> found, @Nullable String eventName, String dimension) {
        detector.onChat(plain, servers);
        Duration untilVote = ChatCoords.voteCountdown(plain);
        if (untilVote != null) {
            voteAnchor = Instant.now().plus(untilVote);
            log("next vote from chat: in %d s", untilVote.toSeconds());
        } else if (ChatCoords.isVoteStart(plain)) {
            Instant now = Instant.now();
            if (voteAnchor == null || Duration.between(voteAnchor, now).compareTo(VOTE_REANCHOR) > 0) {
                voteAnchor = now;
                log("vote start seen in chat, 65-min cycle anchored");
            }
        }
        if (found.isEmpty()) {
            return;
        }
        String name = eventName;
        if (name == null && mentionsEvent(plain)) {
            List<EventsJson.LiteEvent> mine = myLiteEvents();
            if (mine.size() == 1) {
                name = mine.getFirst().name();
            }
        }
        if (name != null) {
            coords.put(name, found.getFirst(), dimension, System.currentTimeMillis());
            log("coordinates of '%s' from chat: %s (line: %s)", name, found.getFirst().text(), plain);
        }
    }

    private static boolean mentionsEvent(String plain) {
        String t = ServerParser.normalize(plain);
        return t.contains("ивент") || t.contains("event") || t.contains("событи");
    }

    // ---- state for the HUD ----

    ToastScope toastScope() {
        return toasts.isBlocked() ? ToastScope.OFF : toasts.get();
    }

    ServerParser.@Nullable ServerRef currentServer() {
        return current;
    }

    /** Lite part visible: chosen, or AUTO and not known to be on Prime. */
    boolean showLite() {
        return switch (section.get()) {
            case LITE, BOTH -> true;
            case PRIME -> false;
            case AUTO -> detectedMode() != ServerParser.Mode.PRIME;
        };
    }

    boolean showPrime() {
        return switch (section.get()) {
            case PRIME, BOTH -> true;
            case LITE -> false;
            case AUTO -> detectedMode() != ServerParser.Mode.LITE;
        };
    }

    private ServerParser.Mode detectedMode() {
        if (current != null) {
            return current.mode();
        }
        ServerParser.Mode mode = detector.mode();
        return mode == ServerParser.Mode.LITE || mode == ServerParser.Mode.PRIME ? mode : ServerParser.Mode.UNKNOWN;
    }

    boolean hasLiteData() {
        return rawEvents != null;
    }

    boolean hasPrimeData() {
        return rawTimetable != null || rawCurrent != null;
    }

    List<EventsJson.LiteEvent> liteEvents() {
        return liteEvents;
    }

    /** Events on the player's own Lite server, rarest first. */
    List<EventsJson.LiteEvent> myLiteEvents() {
        if (current == null || current.isPrime()) {
            return List.of();
        }
        List<EventsJson.LiteEvent> out = new ArrayList<>();
        for (EventsJson.LiteEvent e : liteEvents) {
            if (e.serverId().equals(current.apiId())) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparing(EventsJson.LiteEvent::rarity).reversed());
        return out;
    }

    /** Every server's events, rarest first, then by server. */
    List<EventsJson.LiteEvent> allLiteEventsByRarity() {
        List<EventsJson.LiteEvent> out = new ArrayList<>(liteEvents);
        out.sort(Comparator.comparing(EventsJson.LiteEvent::rarity).reversed()
                .thenComparing(e -> serverName(e.serverId())));
        return out;
    }

    List<EventsJson.PrimeEvent> primeCurrent() {
        return primeCurrent;
    }

    List<EventsJson.PrimeSlot> primeTimetable() {
        return primeTimetable;
    }

    EventsJson.@Nullable Voting myVoting() {
        if (current == null || current.isPrime()) {
            return null;
        }
        for (EventsJson.Voting v : votings) {
            if (v.serverId().equals(current.apiId())) {
                return v;
            }
        }
        return null;
    }

    @Nullable Instant voteAnchor() {
        return voteAnchor;
    }

    KnownCoords.@Nullable Entry coordsOf(String eventName) {
        return coords.get(eventName, System.currentTimeMillis());
    }

    /** Live event names from the API (to name waypoints after the event). */
    Set<String> liveEventNames() {
        Set<String> names = new LinkedHashSet<>();
        for (EventsJson.LiteEvent e : liteEvents) {
            names.add(e.name());
        }
        return names;
    }

    /** "ДуоЛайт #17" for LITE_ANARCHY_17 (from /v1/servers), or the id itself. */
    String serverName(String apiId) {
        return servers.getOrDefault(apiId, apiId);
    }

    /** Header text for the detected server. */
    @Nullable String currentServerName() {
        if (current == null) {
            return null;
        }
        return current.isPrime() ? primeServerName(current.apiId()) : serverName(current.apiId());
    }

    static String primeServerName(String server) {
        return dev.skirmish.ui.Ui.tr("skirmish.events.prime.server", server);
    }

    /** Translated Prime event type ({@code skirmish.events.prime.<key>}), or the key humanized. */
    static String primeName(String key) {
        String lang = "skirmish.events.prime." + key.toLowerCase(java.util.Locale.ROOT);
        return net.minecraft.locale.Language.getInstance().has(lang) ? dev.skirmish.ui.Ui.tr(lang) : EventsJson.humanize(key);
    }
}
