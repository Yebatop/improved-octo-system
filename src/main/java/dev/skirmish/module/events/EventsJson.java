package dev.skirmish.module.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * api.holyworld.me JSON → model. Pure Java and defensive: the API documents that Prime responses are passed
 * through unchanged and {@code rare} values are inconsistent, so unknown fields are ignored and broken entries
 * skipped instead of failing the whole response.
 */
public final class EventsJson {
    private EventsJson() {
    }

    /** One active Lite event ({@code /v1/events}). */
    public record LiteEvent(String serverId, String instanceId, String id, String name, String rareRaw, Rarity rarity) {
    }

    /** One Prime event from {@code /v2/prime/events/current}; {@code server} is "1".."5". */
    public record PrimeEvent(String uuid, String server, String plugin, String template, String state,
                             @Nullable Instant scheduledAt, @Nullable Instant startedAt) {
        public boolean running() {
            return "RUNNING".equalsIgnoreCase(state);
        }
    }

    /** Next start of a Prime event type ({@code /v2/prime/events/timetable}). */
    public record PrimeSlot(String key, Instant at) {
    }

    public record Candidate(String name, int votes, Rarity rarity) {
    }

    /** One active Lite vote ({@code /v1/votings}). */
    public record Voting(String serverId, String instanceId, List<Candidate> candidates) {
    }

    /** {@code /v1/servers}: id → display name ("LITE_ANARCHY_17" → "ДуоЛайт #17"). */
    public static Map<String, String> servers(@Nullable JsonElement json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null || !json.isJsonObject()) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
            String display = string(e.getValue());
            if (display != null && !display.isBlank()) {
                out.put(e.getKey(), display);
            }
        }
        return out;
    }

    /**
     * {@code /v1/events}: the all-servers object {@code {serverId: [event...]}}, or the array of {@code ?server=}
     * (then every event gets {@code serverForArray}).
     */
    public static List<LiteEvent> liteEvents(@Nullable JsonElement json, String serverForArray) {
        List<LiteEvent> out = new ArrayList<>();
        if (json == null) {
            return out;
        }
        if (json.isJsonArray()) {
            readEvents(serverForArray, json.getAsJsonArray(), out);
        } else if (json.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonArray()) {
                    readEvents(e.getKey(), e.getValue().getAsJsonArray(), out);
                }
            }
        }
        return out;
    }

    private static void readEvents(String server, JsonArray array, List<LiteEvent> out) {
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject o = element.getAsJsonObject();
            String instance = string(o.get("instanceId"));
            String id = string(o.get("id"));
            JsonObject meta = o.has("metadata") && o.get("metadata").isJsonObject() ? o.getAsJsonObject("metadata") : new JsonObject();
            String name = string(meta.get("displayName"));
            String rare = string(meta.get("rare"));
            if (instance == null && id == null) {
                continue;
            }
            if (id == null) {
                id = "?";
            }
            if (instance == null) {
                instance = server + ":" + id;
            }
            if (name == null || name.isBlank()) {
                name = humanize(id);
            }
            out.add(new LiteEvent(server, instance, id, name, rare == null ? "" : rare, Rarity.parse(rare)));
        }
    }

    /** {@code /v2/prime/events/current}. */
    public static List<PrimeEvent> primeCurrent(@Nullable JsonElement json) {
        List<PrimeEvent> out = new ArrayList<>();
        if (json == null || !json.isJsonArray()) {
            return out;
        }
        for (JsonElement element : json.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject o = element.getAsJsonObject();
            String uuid = null;
            JsonElement id = o.get("id");
            if (id != null && id.isJsonObject()) {
                uuid = string(id.getAsJsonObject().get("uuid"));
            } else {
                uuid = string(id);
            }
            String plugin = string(o.get("plugin"));
            String server = string(o.get("server"));
            if (plugin == null) {
                continue;
            }
            Instant scheduled = instant(o.get("scheduledAt"));
            if (uuid == null) {
                uuid = server + ":" + plugin + ":" + scheduled;
            }
            String template = string(o.get("template"));
            String state = string(o.get("state"));
            out.add(new PrimeEvent(uuid, server == null ? "" : server, plugin, template == null ? "" : template,
                    state == null ? "" : state, scheduled, instant(o.get("startedAt"))));
        }
        return out;
    }

    /** {@code /v2/prime/events/timetable}, sorted by start time. */
    public static List<PrimeSlot> primeTimetable(@Nullable JsonElement json) {
        List<PrimeSlot> out = new ArrayList<>();
        if (json == null || !json.isJsonObject()) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
            Instant at = instant(e.getValue());
            if (at != null) {
                out.add(new PrimeSlot(e.getKey(), at));
            }
        }
        out.sort(Comparator.comparing(PrimeSlot::at));
        return out;
    }

    /** {@code /v1/votings}: the all-servers object, or the array of {@code ?server=}. */
    public static List<Voting> votings(@Nullable JsonElement json, String serverForArray) {
        List<Voting> out = new ArrayList<>();
        if (json == null) {
            return out;
        }
        if (json.isJsonArray()) {
            readVotings(serverForArray, json.getAsJsonArray(), out);
        } else if (json.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonArray()) {
                    readVotings(e.getKey(), e.getValue().getAsJsonArray(), out);
                }
            }
        }
        return out;
    }

    private static void readVotings(String server, JsonArray array, List<Voting> out) {
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject o = element.getAsJsonObject();
            String instance = string(o.get("instanceId"));
            if (instance == null) {
                instance = server + ":" + string(o.get("id"));
            }
            List<Candidate> candidates = new ArrayList<>();
            JsonElement list = o.get("candidates");
            if (list != null && list.isJsonArray()) {
                for (JsonElement c : list.getAsJsonArray()) {
                    if (!c.isJsonObject()) {
                        continue;
                    }
                    JsonObject co = c.getAsJsonObject();
                    JsonObject meta = co.has("metadata") && co.get("metadata").isJsonObject() ? co.getAsJsonObject("metadata") : new JsonObject();
                    String name = string(meta.get("displayName"));
                    if (name == null) {
                        name = string(co.get("name"));
                    }
                    if (name == null) {
                        name = humanize(String.valueOf(string(co.get("id"))));
                    }
                    int votes = 0;
                    JsonElement v = co.get("votes");
                    if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                        votes = v.getAsInt();
                    }
                    candidates.add(new Candidate(name, votes, Rarity.parse(string(meta.get("rare")))));
                }
            }
            candidates.sort(Comparator.comparingInt(Candidate::votes).reversed());
            out.add(new Voting(server, instance, List.copyOf(candidates)));
        }
    }

    private static @Nullable String string(@Nullable JsonElement e) {
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static @Nullable Instant instant(@Nullable JsonElement e) {
        String s = string(e);
        if (s == null) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ex) {
            try {
                return java.time.OffsetDateTime.parse(s).toInstant();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    /** {@code golden_bob} / {@code GOLDEN_BOB} → "Golden bob". */
    public static String humanize(String key) {
        String text = key.replace('_', ' ').trim().toLowerCase(java.util.Locale.ROOT);
        return text.isEmpty() ? key : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
