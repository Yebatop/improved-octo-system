package dev.skirmish.module.navigator;

import dev.skirmish.SkirmishClient;
import dev.skirmish.SkirmishKeys;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.util.ServerContext;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * «Navigator»: GPS for the anarchy. It remembers every Nether portal you go through (both ends, per server), plans
 * the fastest way to the selected waypoint across the Overworld and the Nether (Dijkstra over your portals) and
 * guides you: a route card with the steps and ETA, a compass bar with your waypoints, light beams over the target
 * and the next portal, breadcrumbs of where you walked (also on the World Map) and a «Домой» key. Read-only; Feature
 * Control id {@code navigator}.
 */
public final class NavigatorModule extends Module {
    public static final String ID = "navigator";
    static final String L = "layout.navigator.";
    private static volatile @Nullable NavigatorModule instance;

    public enum Beams {
        OFF, TARGET, ALL
    }

    final BoolSetting compass = add(new BoolSetting("compass", true));
    final BoolSetting routeCard = add(new BoolSetting("route", true));
    final EnumSetting<Beams> beams = add(new EnumSetting<>("beams", Beams.TARGET));
    final BoolSetting breadcrumbs = add(new BoolSetting("breadcrumbs", true));
    final KeySetting homeKey = add(new KeySetting("home_key", "key.skirmish.navigator.home"));

    private final PortalBook portals = new PortalBook(SkirmishClient.configDir().resolve("portals.json"));
    private final Map<String, double[]> lastPortal = new HashMap<>();
    private final Map<String, Integer> lastPortalTick = new HashMap<>();
    private final Map<String, ArrayDeque<double[]>> crumbs = new HashMap<>();
    private final ArrayDeque<double[]> speedSamples = new ArrayDeque<>();
    private @Nullable String lastDim;
    private @Nullable String pendingFromDim;
    private double @Nullable [] pendingFrom;
    private int arrivalTicks = -1;
    private RoutePlanner.@Nullable Route route;
    private @Nullable Waypoint routeTarget;
    private int ticks;

    public NavigatorModule() {
        super(ID, true);
    }

    public static @Nullable NavigatorModule instance() {
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
        portals.load();
        Hud.get().register(new CompassBar(this));
        Hud.get().register(new RouteCard(this));
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (isEnabled() && beams.get() != Beams.OFF) {
                BeamRenderer.render(this, context);
            }
        });
    }

    // ---- state for the HUD, beams and the map ----

    RoutePlanner.@Nullable Route route() {
        return route;
    }

    @Nullable Waypoint routeTarget() {
        return routeTarget;
    }

    /** The point to head for now in the current dimension (next portal or the target), or null. */
    public double @Nullable [] nextPoint() {
        RoutePlanner.Route r = route;
        if (r == null || r.legs().isEmpty()) {
            return null;
        }
        RoutePlanner.Leg leg = r.legs().getFirst();
        return leg.dim().equals(ServerContext.dimension()) ? new double[]{leg.tx(), leg.tz(), leg.portal() ? 1 : 0} : null;
    }

    /** Route polyline in one dimension: the player (when there) and the ends of that dimension's legs, as {x, z}. */
    public List<double[]> routePoints(String dim) {
        RoutePlanner.Route r = route;
        java.util.ArrayList<double[]> out = new java.util.ArrayList<>();
        if (r == null) {
            return out;
        }
        for (RoutePlanner.Leg leg : r.legs()) {
            if (!leg.dim().equals(dim)) {
                continue;
            }
            if (out.isEmpty()) {
                out.add(new double[]{leg.fx(), leg.fz()});
            }
            out.add(new double[]{leg.tx(), leg.tz()});
        }
        return out;
    }

    /** Known portals of this server as {x, z} in the given dimension. */
    public List<double[]> portalsIn(String dim) {
        boolean nether = RoutePlanner.NETHER.equals(dim);
        return portals.forServer(ServerContext.serverKey()).stream()
                .map(e -> nether ? new double[]{e.nx, e.nz} : new double[]{e.ox, e.oz}).toList();
    }

    /** Breadcrumbs of this server and dimension, oldest first, as {x, y, z, time}. */
    public List<double[]> crumbs() {
        ArrayDeque<double[]> d = crumbs.get(contextKey());
        return d == null ? List.of() : List.copyOf(d);
    }

    public boolean breadcrumbsOn() {
        return isEnabled() && breadcrumbs.get();
    }

    /** Recent horizontal speed in blocks/s, at least walking speed. */
    double speed() {
        if (speedSamples.size() < 2) {
            return Theme.get().num(L + "walk_speed");
        }
        double[] a = speedSamples.getFirst();
        double[] b = speedSamples.getLast();
        double secs = (b[2] - a[2]) / 1000.0;
        double v = secs <= 0 ? 0 : Math.hypot(b[0] - a[0], b[1] - a[1]) / secs;
        return Math.max(Theme.get().num(L + "walk_speed"), v);
    }

    /** ETA in seconds for the current route, or -1. */
    long etaSeconds() {
        RoutePlanner.Route r = route;
        if (r == null) {
            return -1;
        }
        double walk = 0;
        int portalsCount = 0;
        for (RoutePlanner.Leg leg : r.legs()) {
            walk += leg.length();
            if (leg.portal()) {
                portalsCount++;
            }
        }
        return Math.round(walk / speed() + portalsCount * Theme.get().num(L + "portal_seconds"));
    }

    private static String contextKey() {
        return ServerContext.serverKey() + "|" + ServerContext.dimension();
    }

    // ---- ticking ----

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long now = Util.getMillis();
        ticks++;
        String dim = ServerContext.dimension();
        while (SkirmishKeys.NAVIGATOR_HOME.consumeClick()) {
            goHome(player, dim);
        }
        trackPortals(player, dim);
        if (breadcrumbs.get()) {
            recordCrumb(player, now);
        }
        speedSamples.add(new double[]{player.getX(), player.getZ(), now});
        while (speedSamples.size() > 2 && now - speedSamples.getFirst()[2] > Theme.get().num(L + "speed_window_ms")) {
            speedSamples.removeFirst();
        }
        if (ticks % 10 == 0) {
            replan(player, dim);
        }
    }

    private void trackPortals(LocalPlayer player, String dim) {
        BlockPos feet = player.blockPosition();
        if (player.level().getBlockState(feet).is(Blocks.NETHER_PORTAL) || player.level().getBlockState(feet.above()).is(Blocks.NETHER_PORTAL)) {
            lastPortal.put(dim, new double[]{player.getX(), player.getY(), player.getZ()});
            lastPortalTick.put(dim, ticks);
        }
        if (lastDim != null && !lastDim.equals(dim)) {
            // Only a switch made while standing in the portal: a /spawn or /home typed next to one is not a crossing.
            Integer at = lastPortalTick.get(lastDim);
            boolean crossing = at != null && ticks - at <= Theme.get().integer(L + "portal_contact_ticks") && isPair(lastDim, dim);
            if (crossing) {
                pendingFromDim = lastDim;
                pendingFrom = lastPortal.get(lastDim);
                arrivalTicks = Theme.get().integer(L + "arrival_ticks");
            }
        }
        lastDim = dim;
        if (arrivalTicks > 0 && --arrivalTicks == 0 && pendingFrom != null && pendingFromDim != null) {
            if (!portalNear(player)) {
                log("dimension switch without a portal at the arrival point, not a portal crossing");
                pendingFrom = null;
                pendingFromDim = null;
                return;
            }
            double[] to = new double[]{player.getX(), player.getY(), player.getZ()};
            boolean fromOverworld = RoutePlanner.OVERWORLD.equals(pendingFromDim);
            double[] ow = fromOverworld ? pendingFrom : to;
            double[] ne = fromOverworld ? to : pendingFrom;
            boolean fresh = portals.record(ServerContext.serverKey(), ow[0], ow[1], ow[2], ne[0], ne[1], ne[2], System.currentTimeMillis());
            log("portal %s: overworld %d %d %d ↔ nether %d %d %d", fresh ? "learned" : "refreshed",
                    (int) ow[0], (int) ow[1], (int) ow[2], (int) ne[0], (int) ne[1], (int) ne[2]);
            if (fresh) {
                player.displayClientMessage(Component.translatable("skirmish.navigator.portal_learned"), true);
            }
            pendingFrom = null;
            pendingFromDim = null;
        }
    }

    /** A nether portal block within two blocks of the player (where a portal crossing lands you). */
    private static boolean portalNear(LocalPlayer player) {
        BlockPos feet = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 2, 2))) {
            if (player.level().getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPair(String a, String b) {
        return RoutePlanner.OVERWORLD.equals(a) && RoutePlanner.NETHER.equals(b) || RoutePlanner.NETHER.equals(a) && RoutePlanner.OVERWORLD.equals(b);
    }

    private void recordCrumb(LocalPlayer player, long now) {
        ArrayDeque<double[]> d = crumbs.computeIfAbsent(contextKey(), k -> new ArrayDeque<>());
        double[] last = d.peekLast();
        double step = Theme.get().num(L + "crumb_step");
        if (last == null || Math.hypot(player.getX() - last[0], player.getZ() - last[2]) >= step || Math.abs(player.getY() - last[1]) >= step) {
            d.add(new double[]{player.getX(), player.getY(), player.getZ(), now});
        }
        long keep = Math.round(Theme.get().num(L + "crumb_keep_ms"));
        int max = Theme.get().integer(L + "crumb_max");
        while (!d.isEmpty() && (now - d.getFirst()[3] > keep || d.size() > max)) {
            d.removeFirst();
        }
    }

    private void replan(LocalPlayer player, String dim) {
        Waypoint target = WaypointManager.get().selected();
        if (target == null || !target.server().equals(ServerContext.serverKey())) {
            route = null;
            routeTarget = null;
            return;
        }
        RoutePlanner.Route r = RoutePlanner.plan(new RoutePlanner.Point(dim, player.getX(), player.getZ()),
                new RoutePlanner.Point(target.dimension(), target.x(), target.z()),
                portals.links(ServerContext.serverKey()), Theme.get().num(L + "portal_cost"));
        if (routeTarget == null || !routeTarget.id().equals(target.id()) || (route == null) != (r == null)
                || route != null && r != null && route.legs().size() != r.legs().size()) {
            log("route to '%s': %s", target.name(), r == null ? "no way known" : r.legs().size() + " leg(s), " + Math.round(r.total()) + " blocks");
        }
        route = r;
        routeTarget = target;
    }

    /** «Домой»: routes to the waypoint named Дом/Home (or the home one), creating it here when there is none. */
    private void goHome(LocalPlayer player, String dim) {
        WaypointManager manager = WaypointManager.get();
        Waypoint home = home();
        if (home == null) {
            home = manager.add(Ui.tr("skirmish.navigator.home_name"), player.getX(), player.getY(), player.getZ(), dim, "home");
            player.displayClientMessage(Component.translatable("skirmish.navigator.home_saved"), true);
        } else {
            player.displayClientMessage(Component.translatable("skirmish.navigator.home_route"), true);
        }
        manager.select(home.id());
        replan(player, dim);
    }

    /** The home waypoint of this server: source «home», or named Дом/Home/База/Base. */
    public static @Nullable Waypoint home() {
        Waypoint named = null;
        for (Waypoint w : WaypointManager.get().currentServer()) {
            if ("home".equals(w.source())) {
                return w;
            }
            String n = w.name().trim().toLowerCase(Locale.ROOT);
            if (named == null && (n.equals("дом") || n.equals("home") || n.equals("база") || n.equals("base"))) {
                named = w;
            }
        }
        return named;
    }
}
