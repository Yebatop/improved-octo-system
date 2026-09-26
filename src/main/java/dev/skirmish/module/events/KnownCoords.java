package dev.skirmish.module.events;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Coordinates of events seen in chat (announcements, the player's own {@code /event} output), keyed by event name,
 * so the HUD can show them next to the API's event on the player's server. Entries expire. Pure Java.
 */
public final class KnownCoords {
    public record Entry(String name, ChatCoords.Coords coords, String dimension, long seenAtMs) {
    }

    private final long maxAgeMs;
    private final Map<String, Entry> byName = new HashMap<>();

    public KnownCoords(long maxAgeMs) {
        this.maxAgeMs = maxAgeMs;
    }

    public void put(String eventName, ChatCoords.Coords coords, String dimension, long nowMs) {
        byName.put(key(eventName), new Entry(eventName, coords, dimension, nowMs));
    }

    public @Nullable Entry get(String eventName, long nowMs) {
        Entry e = byName.get(key(eventName));
        if (e == null) {
            return null;
        }
        if (nowMs - e.seenAtMs() > maxAgeMs) {
            byName.remove(key(eventName));
            return null;
        }
        return e;
    }

    public void clear() {
        byName.clear();
    }

    private static String key(String name) {
        return ServerParser.normalize(name).toLowerCase(Locale.ROOT);
    }
}
