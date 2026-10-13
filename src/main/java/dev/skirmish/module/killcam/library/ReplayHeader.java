package dev.skirmish.module.killcam.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The small, human-readable part of a saved replay: stored as JSON in front of the compressed frames so the library
 * lists files without decoding them. {@link #title} and {@link #favourite} are the user's edits (rename, star).
 */
public final class ReplayHeader {
    public static final int MAX_TITLE = 48;
    private static final int MAX_TEXT = 128;
    private static final int MAX_TAGS = 16;

    public long createdMs;
    public ReplayKind kind = ReplayKind.CLIP;
    /** Server the replay was recorded on (display name, e.g. {@code mc.holyworld.ru}). */
    public String server = "";
    public String dimension = "";
    /** Length of the recording in ticks (20 per second). */
    public int ticks;
    /** My name when recording. */
    public String me = "";
    /** The killer (DEATH), the victim(s) (KILL) or the last player I fought with (CLIP); empty when nobody. */
    public String opponent = "";
    /** Name given by the user; empty = automatic. */
    public String title = "";
    /** Starred: never removed by the storage cleanup. */
    public boolean favourite;
    /** Automatic tags ("totem", "crit", "multikill"); searchable. */
    public List<String> tags = new ArrayList<>();
    public int players;
    public int hits;
    public int crits;
    public int totems;

    public double seconds() {
        return ticks / 20.0;
    }

    public ReplayHeader copy() {
        ReplayHeader c = new ReplayHeader();
        c.createdMs = createdMs;
        c.kind = kind;
        c.server = server;
        c.dimension = dimension;
        c.ticks = ticks;
        c.me = me;
        c.opponent = opponent;
        c.title = title;
        c.favourite = favourite;
        c.tags = new ArrayList<>(tags);
        c.players = players;
        c.hits = hits;
        c.crits = crits;
        c.totems = totems;
        return c;
    }

    /** Case-insensitive search over the names, title, server, dimension and tags; blank matches everything. */
    public boolean matches(String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return true;
        }
        if (contains(opponent, q) || contains(title, q) || contains(me, q) || contains(server, q) || contains(dimension, q)) {
            return true;
        }
        for (String tag : tags) {
            if (contains(tag, q)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String text, String lowerQuery) {
        return text.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("created", createdMs);
        o.addProperty("kind", kind.id());
        o.addProperty("server", server);
        o.addProperty("dimension", dimension);
        o.addProperty("ticks", ticks);
        o.addProperty("me", me);
        o.addProperty("opponent", opponent);
        o.addProperty("title", title);
        o.addProperty("favourite", favourite);
        JsonArray tagArray = new JsonArray();
        tags.forEach(tagArray::add);
        o.add("tags", tagArray);
        o.addProperty("players", players);
        o.addProperty("hits", hits);
        o.addProperty("crits", crits);
        o.addProperty("totems", totems);
        return o;
    }

    /** Reads {@link #toJson()} output; unknown keys are ignored, missing optional ones get defaults. */
    public static ReplayHeader fromJson(JsonObject o) throws ReplayFormatException {
        ReplayHeader h = new ReplayHeader();
        try {
            h.createdMs = o.get("created").getAsLong();
            ReplayKind kind = ReplayKind.parse(o.get("kind").getAsString());
            if (kind == null) {
                throw new ReplayFormatException("unknown replay kind " + o.get("kind"));
            }
            h.kind = kind;
            h.ticks = o.get("ticks").getAsInt();
            h.server = text(o, "server", MAX_TEXT);
            h.dimension = text(o, "dimension", MAX_TEXT);
            h.me = text(o, "me", MAX_TEXT);
            h.opponent = text(o, "opponent", MAX_TEXT);
            h.title = text(o, "title", MAX_TITLE);
            h.favourite = o.has("favourite") && o.get("favourite").getAsBoolean();
            if (o.has("tags") && o.get("tags").isJsonArray()) {
                for (JsonElement tag : o.getAsJsonArray("tags")) {
                    if (h.tags.size() < MAX_TAGS && tag.isJsonPrimitive()) {
                        h.tags.add(clip(tag.getAsString(), MAX_TEXT));
                    }
                }
            }
            h.players = number(o, "players");
            h.hits = number(o, "hits");
            h.crits = number(o, "crits");
            h.totems = number(o, "totems");
        } catch (ReplayFormatException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ReplayFormatException("bad header: " + e.getMessage());
        }
        if (h.ticks < 1) {
            throw new ReplayFormatException("bad header: " + h.ticks + " ticks");
        }
        return h;
    }

    private static String text(JsonObject o, String key, int max) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? "" : clip(e.getAsString(), max);
    }

    private static int number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? 0 : Math.max(0, e.getAsInt());
    }

    public static String clip(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    @Override
    public String toString() {
        return kind.id() + " vs " + (opponent.isEmpty() ? "-" : opponent) + " @ " + server + " " + dimension + ", "
                + String.format(Locale.ROOT, "%.1f s", seconds()) + (favourite ? ", favourite" : "")
                + (title.isEmpty() ? "" : ", \"" + title + "\"");
    }
}
