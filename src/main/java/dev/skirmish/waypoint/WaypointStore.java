package dev.skirmish.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory list of waypoints plus JSON (de)serialization of waypoints.json. No Minecraft types (unit tested).
 * <pre>{"version":1,"selected":"id-or-null","waypoints":[{"id":..,"name":..,"x":..,"y":..,"z":..,
 * "dimension":"minecraft:overworld","server":"lite.holyworld.ru","createdAt":..,"color":16733525,"source":"manual"}]}</pre>
 */
public final class WaypointStore {
    public static final int VERSION = 1;
    public static final int MAX_NAME_LENGTH = 64;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int[] PALETTE = {0xFF5555, 0x55FF55, 0x5599FF, 0xFFFF55, 0xFF55FF, 0x55FFFF, 0xFFAA00, 0xFFFFFF};

    private final List<Waypoint> waypoints = new ArrayList<>();
    private @Nullable String selectedId;

    public List<Waypoint> all() {
        return Collections.unmodifiableList(waypoints);
    }

    public List<Waypoint> forContext(String server, String dimension) {
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            if (waypoint.server().equals(server) && waypoint.dimension().equals(dimension)) {
                result.add(waypoint);
            }
        }
        return result;
    }

    public List<Waypoint> forServer(String server) {
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            if (waypoint.server().equals(server)) {
                result.add(waypoint);
            }
        }
        return result;
    }

    public Waypoint add(String name, double x, double y, double z, String dimension, String server, String source, long now) {
        String clean = sanitizeName(name);
        int color = PALETTE[Math.floorMod(waypoints.size(), PALETTE.length)];
        Waypoint waypoint = new Waypoint(UUID.randomUUID().toString(), clean, x, y, z, dimension, server, now, color, source);
        waypoints.add(waypoint);
        return waypoint;
    }

    public static String sanitizeName(String name) {
        String clean = name == null ? "" : name.replaceAll("\\p{Cntrl}", "").replace('§', '?').trim();
        if (clean.isEmpty()) {
            clean = "Waypoint";
        }
        return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH) : clean;
    }

    public boolean remove(String id) {
        boolean removed = waypoints.removeIf(w -> w.id().equals(id));
        if (removed && id.equals(selectedId)) {
            selectedId = null;
        }
        return removed;
    }

    public @Nullable Waypoint byId(String id) {
        for (Waypoint waypoint : waypoints) {
            if (waypoint.id().equals(id)) {
                return waypoint;
            }
        }
        return null;
    }

    /** Resolves a full id or a unique prefix (commands show the first 8 characters). */
    public @Nullable Waypoint byIdPrefix(String prefix) {
        Waypoint found = null;
        for (Waypoint waypoint : waypoints) {
            if (waypoint.id().startsWith(prefix)) {
                if (found != null) {
                    return null;
                }
                found = waypoint;
            }
        }
        return found;
    }

    public boolean rename(String id, String name) {
        for (int i = 0; i < waypoints.size(); i++) {
            if (waypoints.get(i).id().equals(id)) {
                waypoints.set(i, waypoints.get(i).withName(sanitizeName(name)));
                return true;
            }
        }
        return false;
    }

    public @Nullable String selectedId() {
        return selectedId;
    }

    public void select(@Nullable String id) {
        this.selectedId = id == null || byId(id) == null ? null : id;
    }

    public @Nullable Waypoint selected() {
        return selectedId == null ? null : byId(selectedId);
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("selected", selectedId);
        JsonArray array = new JsonArray();
        for (Waypoint w : waypoints) {
            JsonObject o = new JsonObject();
            o.addProperty("id", w.id());
            o.addProperty("name", w.name());
            o.addProperty("x", w.x());
            o.addProperty("y", w.y());
            o.addProperty("z", w.z());
            o.addProperty("dimension", w.dimension());
            o.addProperty("server", w.server());
            o.addProperty("createdAt", w.createdAt());
            o.addProperty("color", w.color());
            o.addProperty("source", w.source());
            array.add(o);
        }
        root.add("waypoints", array);
        return root;
    }

    /** Replaces the content with a parsed file; malformed entries are skipped. Returns the number skipped. */
    public int fromJson(JsonElement json) {
        waypoints.clear();
        selectedId = null;
        if (json == null || !json.isJsonObject()) {
            return 0;
        }
        JsonObject root = json.getAsJsonObject();
        int skipped = 0;
        JsonElement array = root.get("waypoints");
        if (array != null && array.isJsonArray()) {
            for (JsonElement element : array.getAsJsonArray()) {
                try {
                    JsonObject o = element.getAsJsonObject();
                    waypoints.add(new Waypoint(
                            o.get("id").getAsString(),
                            sanitizeName(o.get("name").getAsString()),
                            o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
                            o.get("dimension").getAsString(),
                            o.get("server").getAsString(),
                            o.has("createdAt") ? o.get("createdAt").getAsLong() : 0L,
                            o.has("color") ? o.get("color").getAsInt() : 0xFFFFFF,
                            o.has("source") ? o.get("source").getAsString() : "manual"));
                } catch (RuntimeException e) {
                    skipped++;
                }
            }
        }
        JsonElement selected = root.get("selected");
        if (selected != null && selected.isJsonPrimitive()) {
            select(selected.getAsString());
        }
        return skipped;
    }

    public String toJsonString() {
        return GSON.toJson(toJson());
    }

    public int load(Path file) throws IOException {
        if (!Files.exists(file)) {
            waypoints.clear();
            selectedId = null;
            return 0;
        }
        return fromJson(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
    }

    public static void write(Path file, String text) throws IOException {
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
