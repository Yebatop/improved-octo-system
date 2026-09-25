package dev.skirmish.module.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable friend list: nicks with the player's UUID when it was known at the time of adding. Pure Java (no
 * Minecraft classes) so the persistence and matching are unit tested. Every change returns a new list.
 *
 * <p>Matching: when both the entry and the candidate have a UUID, the UUID decides (a renamed friend stays a friend,
 * a stranger who took the old nick does not); otherwise the nick is compared ignoring case.
 */
public final class FriendList {
    public static final FriendList EMPTY = new FriendList(List.of());
    /** More than anyone needs; keeps a hand-edited config.json from growing without bound. */
    public static final int MAX_SIZE = 500;
    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /** One friend. {@code uuid} is null when the player was added by nick while offline. */
    public record Entry(String name, @Nullable UUID uuid) {
        public boolean matches(@Nullable UUID otherUuid, @Nullable String otherName) {
            if (uuid != null && otherUuid != null) {
                return uuid.equals(otherUuid);
            }
            return otherName != null && name.equalsIgnoreCase(otherName);
        }
    }

    private final List<Entry> entries;

    private FriendList(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    public static FriendList of(List<Entry> entries) {
        FriendList list = EMPTY;
        for (Entry e : entries) {
            list = list.add(e.name(), e.uuid());
        }
        return list;
    }

    /** A Minecraft nick: 1–16 letters, digits or underscores. */
    public static boolean isValidName(@Nullable String name) {
        return name != null && NICK.matcher(name).matches();
    }

    public List<Entry> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public @Nullable Entry find(@Nullable UUID uuid, @Nullable String name) {
        for (Entry e : entries) {
            if (e.matches(uuid, name)) {
                return e;
            }
        }
        return null;
    }

    public boolean contains(@Nullable UUID uuid, @Nullable String name) {
        return find(uuid, name) != null;
    }

    /** Only the UUID is known (e.g. from a combat event); name-only entries cannot match it. */
    public boolean containsUuid(UUID uuid) {
        for (Entry e : entries) {
            if (uuid.equals(e.uuid())) {
                return true;
            }
        }
        return false;
    }

    public boolean containsName(String name) {
        for (Entry e : entries) {
            if (e.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a friend, or refreshes an existing entry (new nick after a rename, UUID learned later). Invalid nicks and
     * additions beyond {@link #MAX_SIZE} are ignored.
     */
    public FriendList add(String name, @Nullable UUID uuid) {
        if (!isValidName(name)) {
            return this;
        }
        List<Entry> next = new ArrayList<>(entries);
        for (int i = 0; i < next.size(); i++) {
            Entry e = next.get(i);
            if (e.matches(uuid, name) || (e.uuid() == null && e.name().equalsIgnoreCase(name))) {
                Entry updated = new Entry(name, uuid != null ? uuid : e.uuid());
                if (updated.equals(e)) {
                    return this;
                }
                next.set(i, updated);
                return new FriendList(dedupe(next));
            }
        }
        if (next.size() >= MAX_SIZE) {
            return this;
        }
        next.add(new Entry(name, uuid));
        return new FriendList(next);
    }

    /** Removes the entry with this nick (ignoring case) or this UUID string; unchanged when there is none. */
    public FriendList remove(String nameOrUuid) {
        UUID uuid = parseUuid(nameOrUuid);
        List<Entry> next = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            boolean hit = e.name().equalsIgnoreCase(nameOrUuid) || (uuid != null && uuid.equals(e.uuid()));
            if (!hit) {
                next.add(e);
            }
        }
        return next.size() == entries.size() ? this : new FriendList(next);
    }

    /** Removes whichever entry matches the player (by UUID first, then nick). */
    public FriendList remove(@Nullable UUID uuid, @Nullable String name) {
        List<Entry> next = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            if (!e.matches(uuid, name)) {
                next.add(e);
            }
        }
        return next.size() == entries.size() ? this : new FriendList(next);
    }

    /** Nicks sorted for display. */
    public List<String> names() {
        List<String> names = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            names.add(e.name());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** {@code [{"name":"Nick","uuid":"..."}, {"name":"Other"}]} */
    public JsonArray toJson() {
        JsonArray array = new JsonArray();
        for (Entry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("name", e.name());
            if (e.uuid() != null) {
                o.addProperty("uuid", e.uuid().toString());
            }
            array.add(o);
        }
        return array;
    }

    /**
     * Reads {@link #toJson()}; also accepts plain nick strings (hand-edited config). Invalid rows are skipped and
     * duplicates merged, so a broken entry never loses the rest of the list.
     */
    public static FriendList fromJson(@Nullable JsonElement json) {
        if (json == null || !json.isJsonArray()) {
            return EMPTY;
        }
        FriendList list = EMPTY;
        for (JsonElement element : json.getAsJsonArray()) {
            if (element.isJsonPrimitive()) {
                list = list.add(element.getAsString().trim(), null);
            } else if (element.isJsonObject()) {
                JsonObject o = element.getAsJsonObject();
                JsonElement name = o.get("name");
                JsonElement uuid = o.get("uuid");
                if (name != null && name.isJsonPrimitive()) {
                    list = list.add(name.getAsString().trim(), uuid != null && uuid.isJsonPrimitive() ? parseUuid(uuid.getAsString()) : null);
                }
            }
        }
        return list;
    }

    static @Nullable UUID parseUuid(@Nullable String text) {
        if (text == null || text.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** After an update two entries may describe the same player (same UUID or nick); keep the first. */
    private static List<Entry> dedupe(List<Entry> list) {
        List<Entry> out = new ArrayList<>(list.size());
        for (Entry e : list) {
            boolean dup = false;
            for (Entry kept : out) {
                if ((e.uuid() != null && e.uuid().equals(kept.uuid())) || kept.name().toLowerCase(Locale.ROOT).equals(e.name().toLowerCase(Locale.ROOT))) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                out.add(e);
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FriendList other && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "FriendList" + entries;
    }
}
