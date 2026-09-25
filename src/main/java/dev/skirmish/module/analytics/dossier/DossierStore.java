package dev.skirmish.module.analytics.dossier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All dossier records by UUID, bounded to {@code maxPlayers}: past that, the players seen longest ago are dropped.
 * (De)serialized as config/skirmish/dossier.json. Pure Java, unit tested.
 */
public final class DossierStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final int VERSION = 1;

    private final int maxPlayers;
    private final Map<UUID, DossierRecord> records = new HashMap<>();

    public DossierStore(int maxPlayers) {
        this.maxPlayers = Math.max(1, maxPlayers);
    }

    public @Nullable DossierRecord get(UUID uuid) {
        return records.get(uuid);
    }

    public int size() {
        return records.size();
    }

    public Collection<DossierRecord> all() {
        return records.values();
    }

    public DossierRecord recordFight(UUID uuid, DossierRecord.FightResult result) {
        DossierRecord r = records.computeIfAbsent(uuid, id -> new DossierRecord(id, result.name(), result.timeMs()));
        r.merge(result);
        prune();
        return r;
    }

    public DossierRecord recordDeath(UUID killer, String name, long timeMs) {
        DossierRecord r = records.computeIfAbsent(killer, id -> new DossierRecord(id, name, timeMs));
        r.death(name, timeMs);
        prune();
        return r;
    }

    /** Drops the players seen longest ago until at most {@code maxPlayers} remain; returns how many were dropped. */
    public int prune() {
        int excess = records.size() - maxPlayers;
        if (excess <= 0) {
            return 0;
        }
        List<DossierRecord> oldest = new ArrayList<>(records.values());
        oldest.sort(Comparator.comparingLong(DossierRecord::lastSeenMs));
        for (int i = 0; i < excess; i++) {
            records.remove(oldest.get(i).uuid());
        }
        return excess;
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonArray players = new JsonArray();
        List<DossierRecord> sorted = new ArrayList<>(records.values());
        sorted.sort(Comparator.comparingLong(DossierRecord::lastSeenMs).reversed());
        for (DossierRecord r : sorted) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", r.uuid().toString());
            o.addProperty("name", r.name);
            o.addProperty("fights", r.fights);
            o.addProperty("kills", r.kills);
            o.addProperty("deaths", r.deaths);
            o.addProperty("dealt", Math.round(r.dealt * 10) / 10.0);
            o.addProperty("taken", Math.round(r.taken * 10) / 10.0);
            o.addProperty("first_seen", r.firstSeenMs);
            o.addProperty("last_fight", r.lastFightMs);
            o.addProperty("last_seen", r.lastSeenMs);
            o.addProperty("last_outcome", r.lastOutcome);
            JsonArray gear = new JsonArray();
            r.gear.forEach(gear::add);
            o.add("gear", gear);
            players.add(o);
        }
        root.add("players", players);
        return GSON.toJson(root);
    }

    /** Reads {@link #toJson()}; malformed entries are skipped, the result is pruned to {@code maxPlayers}. */
    public static DossierStore fromJson(String json, int maxPlayers) {
        DossierStore store = new DossierStore(maxPlayers);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray players = root.getAsJsonArray("players");
        if (players == null) {
            return store;
        }
        for (JsonElement el : players) {
            try {
                JsonObject o = el.getAsJsonObject();
                UUID uuid = UUID.fromString(o.get("uuid").getAsString());
                DossierRecord r = new DossierRecord(uuid, o.get("name").getAsString(), longOr(o, "first_seen", 0));
                r.fights = intOr(o, "fights");
                r.kills = intOr(o, "kills");
                r.deaths = intOr(o, "deaths");
                r.dealt = o.has("dealt") ? o.get("dealt").getAsDouble() : 0;
                r.taken = o.has("taken") ? o.get("taken").getAsDouble() : 0;
                r.lastFightMs = longOr(o, "last_fight", -1);
                r.lastSeenMs = longOr(o, "last_seen", r.firstSeenMs);
                r.lastOutcome = o.has("last_outcome") ? o.get("last_outcome").getAsString() : "";
                List<String> gear = new ArrayList<>();
                if (o.has("gear")) {
                    o.getAsJsonArray("gear").forEach(g -> gear.add(g.getAsString()));
                }
                r.gear = List.copyOf(gear);
                store.records.put(uuid, r);
            } catch (RuntimeException ignored) {
                // One bad entry must not lose the rest.
            }
        }
        store.prune();
        return store;
    }

    private static int intOr(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsInt() : 0;
    }

    private static long longOr(JsonObject o, String key, long fallback) {
        return o.has(key) ? o.get(key).getAsLong() : fallback;
    }
}
