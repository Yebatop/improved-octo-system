package dev.skirmish.waypoint;

import dev.skirmish.debug.DebugLog;
import dev.skirmish.util.ServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Public API for waypoints (used by the menu, the /skirmish command, ClanShare...). Client thread only.
 * Every change is written to config/skirmish/waypoints.json on a background thread.
 */
public final class WaypointManager {
    private static @Nullable WaypointManager instance;

    private final WaypointStore store = new WaypointStore();
    private final Path file;
    private final WaypointsModule module;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Skirmish waypoint writer");
        t.setDaemon(true);
        return t;
    });

    WaypointManager(Path file, WaypointsModule module) {
        this.file = file;
        this.module = module;
    }

    static WaypointManager install(Path file, WaypointsModule module) {
        instance = new WaypointManager(file, module);
        return instance;
    }

    public static WaypointManager get() {
        if (instance == null) {
            throw new IllegalStateException("WaypointManager is created by WaypointsModule");
        }
        return instance;
    }

    void load() {
        try {
            int skipped = store.load(file);
            module.log("loaded " + store.all().size() + " waypoints from " + file.getFileName() + (skipped > 0 ? ", skipped " + skipped + " broken entries" : ""));
        } catch (Exception e) {
            DebugLog.error(module.id(), "waypoints.json is unreadable, starting empty", e);
        }
    }

    /** Creates a waypoint on the current server. */
    public Waypoint add(String name, double x, double y, double z, String dimension, String source) {
        Waypoint waypoint = store.add(name, x, y, z, dimension, ServerContext.serverKey(), source, System.currentTimeMillis());
        module.log("added '" + waypoint.name() + "' at " + waypoint.coordsText() + " " + dimension + " on " + waypoint.server() + " (" + source + ")");
        save();
        return waypoint;
    }

    /** Creates a waypoint at the local player's feet, or returns null when not in a world. */
    public @Nullable Waypoint addHere(String name) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        return add(name, player.getX(), player.getY(), player.getZ(), ServerContext.dimension(), "manual");
    }

    public boolean remove(String id) {
        Waypoint waypoint = store.byId(id);
        boolean removed = store.remove(id);
        if (removed) {
            module.log("removed '" + waypoint.name() + "'");
            save();
        }
        return removed;
    }

    public boolean rename(String id, String name) {
        boolean renamed = store.rename(id, name);
        if (renamed) {
            save();
        }
        return renamed;
    }

    /** Sets the HUD arrow target; null clears it. */
    public void select(@Nullable String id) {
        store.select(id);
        Waypoint selected = store.selected();
        module.log(selected == null ? "arrow target cleared" : "arrow target: '" + selected.name() + "'");
        save();
    }

    public @Nullable Waypoint selected() {
        return store.selected();
    }

    public @Nullable Waypoint byId(String id) {
        return store.byId(id);
    }

    public @Nullable Waypoint byIdPrefix(String prefix) {
        return store.byIdPrefix(prefix);
    }

    /** Waypoints of the current server and dimension (rendered in the world). */
    public List<Waypoint> current() {
        return store.forContext(ServerContext.serverKey(), ServerContext.dimension());
    }

    /** Waypoints of the current server in all dimensions (shown in the list). */
    public List<Waypoint> currentServer() {
        return store.forServer(ServerContext.serverKey());
    }

    public List<Waypoint> all() {
        return store.all();
    }

    /** Selects the next waypoint of the current dimension (keybind). */
    public @Nullable Waypoint cycleSelection() {
        List<Waypoint> here = current();
        if (here.isEmpty()) {
            select(null);
            return null;
        }
        Waypoint selected = store.selected();
        int index = selected == null ? -1 : here.indexOf(selected);
        Waypoint next = here.get((index + 1) % here.size());
        select(next.id());
        return next;
    }

    private void save() {
        String text = store.toJsonString();
        writer.execute(() -> {
            try {
                WaypointStore.write(file, text);
            } catch (IOException e) {
                DebugLog.error(module.id(), "waypoints.json save failed", e);
            }
        });
    }
}
