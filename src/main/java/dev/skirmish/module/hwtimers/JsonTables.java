package dev.skirmish.module.hwtimers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Loads the editable data tables of the HolyWorld timer modules: a copy in {@code config/skirmish/tables/<name>.json}
 * wins over the bundled resource, so server wording can be tuned from a capture without a rebuild. Pure Java (the
 * caller passes the config directory), shared by item timers, the TNT timer and the boss coach.
 */
public final class JsonTables {
    private JsonTables() {
    }

    /** What was read and from where ({@code source} is the file path or the resource name, for the debug log). */
    public record Loaded(JsonObject root, String source, @Nullable String problem) {
    }

    /**
     * The override file when it exists and parses, else the bundled {@code resource}; {@code problem} names an
     * override that could not be read (the bundled table is used then).
     */
    public static Loaded load(@Nullable Path configDir, String name, String resource) {
        String problem = null;
        if (configDir != null) {
            Path file = configDir.resolve("tables").resolve(name + ".json");
            if (Files.isRegularFile(file)) {
                try {
                    JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    if (json.isJsonObject()) {
                        return new Loaded(json.getAsJsonObject(), file.toString(), null);
                    }
                    problem = file + " is not a JSON object";
                } catch (Exception e) {
                    problem = file + ": " + e.getMessage();
                }
            }
        }
        return new Loaded(bundled(resource), resource, problem);
    }

    /** The bundled resource, or an empty object when it is missing or broken. */
    public static JsonObject bundled(String resource) {
        try (InputStream in = JsonTables.class.getResourceAsStream(resource)) {
            if (in == null) {
                return new JsonObject();
            }
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
        } catch (IOException | RuntimeException e) {
            return new JsonObject();
        }
    }

    // ---- small readers: missing or mistyped fields fall back to the default ----

    public static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    public static double number(JsonObject o, String key, double fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return e.getAsDouble();
    }

    public static boolean bool(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
    }

    public static @Nullable JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /** String entries of an array (a single string counts as a one-element array). */
    public static List<String> strings(JsonObject o, String key) {
        JsonElement e = o.get(key);
        List<String> out = new ArrayList<>();
        if (e == null) {
            return out;
        }
        if (e.isJsonPrimitive()) {
            out.add(e.getAsString());
        } else if (e.isJsonArray()) {
            for (JsonElement item : e.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    out.add(item.getAsString());
                }
            }
        }
        return out;
    }

    public static List<JsonObject> objects(JsonObject o, String key) {
        JsonElement e = o.get(key);
        List<JsonObject> out = new ArrayList<>();
        if (e instanceof JsonArray array) {
            for (JsonElement item : array) {
                if (item.isJsonObject()) {
                    out.add(item.getAsJsonObject());
                }
            }
        }
        return out;
    }

    /**
     * Compiles regexes; a broken pattern is reported to {@code problems} and skipped so one typo in a hand-edited
     * table does not disable the rest.
     */
    public static List<Pattern> patterns(List<String> sources, String owner, List<String> problems) {
        List<Pattern> out = new ArrayList<>(sources.size());
        for (String source : sources) {
            try {
                out.add(Pattern.compile(source, Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException e) {
                problems.add(owner + ": bad pattern " + source + " (" + e.getDescription() + ")");
            }
        }
        return out;
    }
}
