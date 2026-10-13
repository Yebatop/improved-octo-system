package dev.skirmish.module.hunt;

import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.worldmap.SearchZones;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.ui.Ui;
import dev.skirmish.util.ServerContext;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * «Hunt»: when the server hides digits of where something was placed («Игрок … установил Золотой Спавнер на
 * координатах 1*4*, 5, 1*9*»), a hunt starts. A card shows the mask with its hidden digits, the nearest cell you
 * have not been in yet with its distance and an arrow, how many of the cells you have been through, and — standing
 * in one — how far down the given height is. A waypoint follows the nearest cell (compass, pill, map), and the
 * world map shades the cells you have been in. Everything comes from the chat line and your own position; nothing
 * about the world is looked up. Ends with the key, {@code /skirmish hunt stop}, after an hour, or on leaving.
 * Feature Control id {@code hunt}.
 */
public final class HuntModule extends Module {
    public static final String ID = "hunt";
    static final String SOURCE = "hunt:";
    private static @Nullable HuntModule instance;

    final BoolSetting card = add(new BoolSetting("card", true));
    final BoolSetting waypoint = add(new BoolSetting("waypoint", true));
    final BoolSetting sound = add(new BoolSetting("sound", true));
    private final KeySetting stopKey = add(new KeySetting("stop_key", "key.skirmish.hunt.stop"));

    private SearchZones.@Nullable Zone zone;
    private HuntMath.@Nullable Target target;
    private int inCell = -1;
    private long startedAt;
    private int ticks;

    public HuntModule() {
        super(ID, true);
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
        Hud.get().register(new HuntHud(this));
    }

    @Override
    protected void onDisable() {
        clearWaypoint();
        zone = null;
        target = null;
    }

    static @Nullable HuntModule active() {
        HuntModule m = instance;
        return m != null && m.isEnabled() ? m : null;
    }

    /** Ends the running hunt (the key and {@code /skirmish hunt stop}). */
    public static void stop() {
        HuntModule m = instance;
        if (m != null && m.zone != null) {
            SearchZones.remove(m.zone);
            m.clearWaypoint();
            m.zone = null;
            m.target = null;
        }
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (dev.skirmish.SkirmishKeys.HUNT_STOP.consumeClick()) {
            stop();
        }
        if (mc.player == null || ++ticks % 5 != 0) {
            return;
        }
        long now = Util.getMillis();
        SearchZones.Zone latest = SearchZones.latest(now);
        if (latest != zone) {
            clearWaypoint();
            zone = latest;
            target = null;
            startedAt = now;
            if (zone != null) {
                log("hunt started: %s, %d cells", zone.what(), zone.cells());
                if (sound.get()) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.6f, 0.6f));
                }
            }
        }
        SearchZones.Zone z = zone;
        if (z == null) {
            return;
        }
        boolean here = z.dimension().equals(ServerContext.dimension());
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        inCell = here ? HuntMath.cellAt(z.xs(), z.zs(), px, pz) : -1;
        if (inCell >= 0 && !z.visited().get(inCell)) {
            z.visited().set(inCell);
            log("hunt: in cell %d (%d/%d visited)", inCell, z.visited().cardinality(), z.cells());
        }
        HuntMath.Target next = HuntMath.nearest(z.xs(), z.zs(), z.visited(), px, pz);
        boolean moved = next == null || target == null || next.index() != target.index();
        target = next;
        if (moved) {
            placeWaypoint(z, next);
        }
    }

    private void placeWaypoint(SearchZones.Zone z, HuntMath.@Nullable Target t) {
        clearWaypoint();
        if (t == null || !waypoint.get()) {
            return;
        }
        String[] subject = HuntMath.subject(z.what());
        double y = z.y() != null ? z.y() : Minecraft.getInstance().player == null ? 64 : Minecraft.getInstance().player.getY();
        WaypointManager.get().add(Ui.tr("skirmish.hunt.waypoint", subject[0]), t.x(), y, t.z(), z.dimension(), SOURCE + t.index());
    }

    private void clearWaypoint() {
        WaypointManager wm = WaypointManager.get();
        for (Waypoint w : java.util.List.copyOf(wm.all())) {
            if (w.source() != null && w.source().startsWith(SOURCE)) {
                wm.remove(w.id());
            }
        }
    }

    // ---- for the card and the map ----

    SearchZones.@Nullable Zone zone() {
        return zone;
    }

    HuntMath.@Nullable Target target() {
        return target;
    }

    int inCell() {
        return inCell;
    }

    long startedAt() {
        return startedAt;
    }

    /** The cell the hunt's waypoint points at on the map (−1 when none). */
    public static int targetCell() {
        HuntModule m = active();
        return m == null || m.target == null ? -1 : m.target.index();
    }
}
