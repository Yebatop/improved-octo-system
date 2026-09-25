package dev.skirmish.module.commander;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.ModuleManager;
import dev.skirmish.module.events.EventSchedule;
import dev.skirmish.module.events.EventsJson;
import dev.skirmish.module.events.EventsModule;
import dev.skirmish.module.navigator.NavigatorModule;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.util.ServerContext;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * «Event Commander»: one assistant for events. A heads-up a few minutes before End capture, a vote, the restart or a
 * Prime event (with a sound); a waypoint made by itself from event coordinates in chat and removed when the event
 * is over; the nearest event with distance and ETA through your portals (Navigator), a key that routes you there,
 * and on site how long you have been there. Reads only what the Events module already has; never sends anything.
 */
public final class CommanderModule extends Module {
    public static final String ID = "event_commander";
    static final String L = "layout.commander.";
    static final String SOURCE = "event:";
    private static volatile @Nullable CommanderModule instance;

    final BoolSetting headsUp = add(new BoolSetting("heads_up", true));
    final NumberSetting lead = add(new NumberSetting("lead", 3, 1, 15, 1).unit(" мин"));
    final BoolSetting endCapture = add(new BoolSetting("end_capture", true));
    final BoolSetting vote = add(new BoolSetting("vote", true));
    final BoolSetting restart = add(new BoolSetting("restart", true));
    final BoolSetting bunker = add(new BoolSetting("bunker", false));
    final BoolSetting prime = add(new BoolSetting("prime", true));
    final BoolSetting sound = add(new BoolSetting("sound", true));
    final BoolSetting autoWaypoints = add(new BoolSetting("auto_waypoints", true));
    final NumberSetting waypointTtl = add(new NumberSetting("waypoint_ttl", 30, 5, 120, 5).unit(" мин"));
    final BoolSetting card = add(new BoolSetting("card", true));
    final KeySetting goKey = add(new KeySetting("go_key", "key.skirmish.commander.go"));

    /** An event with a waypoint: how far, how long to get there, whether I am there and since when. */
    public record Mark(Waypoint waypoint, String event, double blocks, double seconds, boolean viaPortal, boolean onSite, long siteSince) {
    }

    private final HeadsUp heads = new HeadsUp();
    private final Map<String, Long> missingSince = new HashMap<>();
    private final Map<String, Long> arrivedAt = new HashMap<>();
    private List<HeadsUp.Item> upcoming = List.of();
    private List<Mark> marks = List.of();
    private int ticks;

    public CommanderModule() {
        super(ID, true);
        lead.under(headsUp).visibleWhen(headsUp::get);
        endCapture.under(headsUp).visibleWhen(headsUp::get);
        vote.under(headsUp).visibleWhen(headsUp::get);
        restart.under(headsUp).visibleWhen(headsUp::get);
        bunker.under(headsUp).visibleWhen(headsUp::get);
        prime.under(headsUp).visibleWhen(headsUp::get);
        waypointTtl.under(autoWaypoints).visibleWhen(autoWaypoints::get);
    }

    public static @Nullable CommanderModule instance() {
        return instance;
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        instance = this;
        Hud.get().register(new CommanderHud(this));
        EventsModule.listenForCoords((name, x, y, z, dim) -> Minecraft.getInstance().execute(() -> onCoords(name, x, y, z, dim)));
    }

    private static @Nullable EventsModule events() {
        return ModuleManager.get().byId(EventsModule.ID) instanceof EventsModule e && e.isEnabled() ? e : null;
    }

    // ---- for the HUD and HolyWorld OS ----

    /** Upcoming starts, soonest first (whatever kinds are switched on). */
    public List<HeadsUp.Item> upcoming() {
        return upcoming;
    }

    /** Events with a waypoint on this server, nearest first. */
    public List<Mark> marks() {
        return marks;
    }

    HeadsUp.@Nullable Item headsUpNow() {
        if (!headsUp.get()) {
            return null;
        }
        return HeadsUp.next(upcoming, Instant.now(), Duration.ofMinutes(Math.round(lead.get())),
                Duration.ofMillis(Math.round(Theme.get().num(L + "started_show_ms"))));
    }

    // ---- chat coordinates → waypoints ----

    private void onCoords(String name, int x, @Nullable Integer y, int z, String dim) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || !autoWaypoints.get() || mc.player == null) {
            return;
        }
        String source = SOURCE + name.toLowerCase(Locale.ROOT);
        WaypointManager wm = WaypointManager.get();
        boolean wasSelected = false;
        for (Waypoint w : List.copyOf(wm.currentServer())) {
            if (source.equals(w.source())) {
                Waypoint sel = wm.selected();
                wasSelected |= sel != null && sel.id().equals(w.id());
                wm.remove(w.id());
            }
        }
        double wy = y != null ? y : surface(mc, dim, x, z);
        Waypoint wp = wm.add(Ui.tr("skirmish.commander.waypoint", name), x + 0.5, wy, z + 0.5, dim, source);
        if (wasSelected) {
            wm.select(wp.id());
        }
        missingSince.remove(name);
        log("event '%s' at %d %s %d (%s): waypoint %s", name, x, y == null ? "~" : y.toString(), z, dim, wp.id());
        double[] est = estimate(wp);
        String how = est == null ? Ui.tr("skirmish.commander.no_way") : distance(est[0]);
        mc.player.displayClientMessage(Component.translatable("skirmish.commander.marked", name, how), true);
        ping(mc, 1.2f);
    }

    private static double surface(Minecraft mc, String dim, int x, int z) {
        if (mc.level != null && dim.equals(ServerContext.dimension()) && mc.level.hasChunk(x >> 4, z >> 4)) {
            return mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        }
        return mc.player == null ? 70 : mc.player.getY();
    }

    // ---- ticking ----

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        ticks++;
        while (SkirmishKeys.COMMANDER_GO.consumeClick()) {
            goNearest(player);
        }
        if (ticks % 10 == 0) {
            upcoming = collectUpcoming(Instant.now());
            if (headsUp.get()) {
                HeadsUp.Item due = heads.due(upcoming, Instant.now(), Duration.ofMinutes(Math.round(lead.get())));
                if (due != null) {
                    log("heads-up: %s at %s", due.name(), due.at());
                    player.displayClientMessage(Component.translatable("skirmish.commander.soon", due.name(),
                            EventSchedule.clock(Duration.between(Instant.now(), due.at()).toMillis())), true);
                    ping(mc, 0.8f);
                }
            }
            marks = collectMarks(player);
        }
        if (ticks % 100 == 0) {
            expire();
        }
    }

    private List<HeadsUp.Item> collectUpcoming(Instant now) {
        List<HeadsUp.Item> out = new ArrayList<>();
        EventsModule ev = events();
        if (endCapture.get()) {
            Instant start = EventSchedule.endCapture(now).start();
            out.add(new HeadsUp.Item("end@" + start, Ui.tr("skirmish.commander.kind.end"), Ui.tr("skirmish.commander.note.end"), start));
        }
        if (restart.get()) {
            Instant at = EventSchedule.nextRestart(now);
            out.add(new HeadsUp.Item("restart@" + at, Ui.tr("skirmish.commander.kind.restart"), Ui.tr("skirmish.commander.note.restart"), at));
        }
        if (bunker.get()) {
            Instant at = EventSchedule.nextBunker(now);
            out.add(new HeadsUp.Item("bunker@" + at, Ui.tr("skirmish.commander.kind.bunker"), Ui.tr("skirmish.commander.note.bunker"), at));
        }
        if (ev != null) {
            if (vote.get() && !ev.onPrime()) {
                Instant at = ev.nextVote(now);
                if (at != null) {
                    out.add(new HeadsUp.Item("vote@" + at, Ui.tr("skirmish.commander.kind.vote"), Ui.tr("skirmish.commander.note.vote"), at));
                }
            }
            if (prime.get() && ev.onPrime()) {
                for (EventsJson.PrimeSlot slot : ev.primeSlots()) {
                    out.add(new HeadsUp.Item("prime@" + slot.key() + "@" + slot.at(), EventsModule.primeDisplayName(slot.key()),
                            Ui.tr("skirmish.commander.note.prime"), slot.at()));
                }
                for (EventsJson.PrimeEvent e : ev.myPrimeEvents()) {
                    Instant at = e.scheduledAt();
                    if (at != null && !e.running()) {
                        out.add(new HeadsUp.Item("prime@" + e.uuid(), EventsModule.primeDisplayName(e.plugin()),
                                Ui.tr("skirmish.commander.note.prime"), at));
                    }
                }
            }
        }
        out.sort(Comparator.comparing(HeadsUp.Item::at));
        return out;
    }

    private List<Mark> collectMarks(LocalPlayer player) {
        long now = System.currentTimeMillis();
        String dim = ServerContext.dimension();
        double radius = Theme.get().num(L + "site_radius");
        List<Mark> out = new ArrayList<>();
        for (Waypoint w : WaypointManager.get().currentServer()) {
            if (!w.source().startsWith(SOURCE)) {
                continue;
            }
            String event = eventName(w);
            boolean here = w.dimension().equals(dim) && Math.hypot(w.x() - player.getX(), w.z() - player.getZ()) <= radius;
            if (here) {
                arrivedAt.putIfAbsent(w.id(), now);
            } else {
                arrivedAt.remove(w.id());
            }
            double[] est = estimate(w);
            double blocks = est == null ? -1 : est[0];
            double seconds = est == null ? -1 : est[1];
            boolean portal = est != null && !w.dimension().equals(dim);
            out.add(new Mark(w, event, blocks, seconds, portal, here, arrivedAt.getOrDefault(w.id(), 0L)));
        }
        out.sort(Comparator.comparingDouble((Mark m) -> m.blocks() < 0 ? Double.MAX_VALUE : m.blocks()));
        return out;
    }

    /** The event's name: the waypoint name without the «Ивент: » prefix. */
    static String eventName(Waypoint w) {
        String prefix = Ui.tr("skirmish.commander.waypoint", "");
        return w.name().startsWith(prefix) ? w.name().substring(prefix.length()) : w.name();
    }

    /** Blocks and seconds to a waypoint through known portals; straight line at walking speed without Navigator. */
    private static double @Nullable [] estimate(Waypoint w) {
        NavigatorModule nav = NavigatorModule.instance();
        if (nav != null && nav.isEnabled()) {
            return nav.estimate(w.dimension(), w.x(), w.z());
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !w.dimension().equals(ServerContext.dimension())) {
            return null;
        }
        double d = Math.hypot(w.x() - p.getX(), w.z() - p.getZ());
        return new double[]{d, d / Theme.get().num(L + "walk_speed")};
    }

    /** Event waypoints go when their time is up or the event left the live list of my server. */
    private void expire() {
        long now = System.currentTimeMillis();
        long ttl = Math.round(waypointTtl.get() * 60_000);
        long grace = Math.round(Theme.get().num(L + "gone_grace_ms"));
        EventsModule ev = events();
        List<String> live = ev != null && ev.liteDataLoaded() && !ev.onPrime() ? ev.myLiteEventNames() : null;
        WaypointManager wm = WaypointManager.get();
        for (Waypoint w : List.copyOf(wm.currentServer())) {
            if (!w.source().startsWith(SOURCE)) {
                continue;
            }
            String event = eventName(w);
            boolean gone = false;
            if (live != null && live.stream().noneMatch(n -> n.equalsIgnoreCase(event))) {
                long since = missingSince.computeIfAbsent(event, k -> now);
                gone = now - since > grace;
            } else {
                missingSince.remove(event);
            }
            if (now - w.createdAt() > ttl || gone) {
                wm.remove(w.id());
                arrivedAt.remove(w.id());
                missingSince.remove(event);
                log("event waypoint '%s' removed (%s)", w.name(), gone ? "event over" : "expired");
            }
        }
    }

    private void goNearest(LocalPlayer player) {
        List<Mark> list = marks;
        if (list.isEmpty()) {
            player.displayClientMessage(Component.translatable("skirmish.commander.none"), true);
            return;
        }
        Mark m = list.getFirst();
        WaypointManager.get().select(m.waypoint().id());
        player.displayClientMessage(Component.translatable("skirmish.commander.going", m.event(),
                m.blocks() < 0 ? Ui.tr("skirmish.commander.no_way") : distance(m.blocks())), true);
    }

    private void ping(Minecraft mc, float pitch) {
        if (sound.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), pitch, 0.8f));
        }
    }

    static String distance(double blocks) {
        return blocks >= 1000 ? Ui.decimal(blocks / 1000.0, 1) + " " + Ui.tr("skirmish.elytra.km")
                : Math.round(blocks) + " " + Ui.tr("skirmish.elytra.m");
    }
}
