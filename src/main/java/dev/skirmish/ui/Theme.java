package dev.skirmish.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Design tokens from assets/skirmish/theme.json (colors with alpha, radii, spacing, text styles, motion, layout).
 * Pure Java: shared by the in-game UI kit and the Java2D KillCard renderer. Screens must read every color and size
 * from here; only keys live in code.
 */
public final class Theme {
    public static final String RESOURCE = "/assets/skirmish/theme.json";

    private static volatile Theme instance;

    private final JsonObject root;
    private final Map<String, Integer> colors = new HashMap<>();
    private final Map<String, Integer> accents = new LinkedHashMap<>();
    private final Map<String, TextStyle> styles = new HashMap<>();
    private final Map<String, Float> numbers = new HashMap<>();
    private final Map<String, java.util.List<Integer>> lists = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile String accentKey;

    /**
     * A text token: size in design px, weight 500–800, color key, letter spacing, font family and CSS line-height
     * factor (0 = "normal").
     */
    public record TextStyle(float size, int weight, String color, float tracking, boolean mono, float lineHeight) {
    }

    private Theme(JsonObject root) {
        this.root = root;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("colors").entrySet()) {
            colors.put(e.getKey(), parseColor(e.getValue().getAsString()));
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("accents").entrySet()) {
            accents.put(e.getKey(), parseColor(e.getValue().getAsString()));
        }
        JsonObject font = root.getAsJsonObject("font");
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("text").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            boolean mono = o.has("mono") && o.get("mono").getAsBoolean();
            styles.put(e.getKey(), new TextStyle(
                    o.get("size").getAsFloat(),
                    o.get("weight").getAsInt(),
                    o.get("color").getAsString(),
                    o.has("tracking") ? o.get("tracking").getAsFloat() : 0f,
                    mono,
                    o.has("line_height") ? o.get("line_height").getAsFloat() : 0f));
        }
        flatten("radius", root.getAsJsonObject("radius"));
        flatten("stroke", root.getAsJsonObject("stroke"));
        flatten("motion", root.getAsJsonObject("motion"));
        flatten("layout", root.getAsJsonObject("layout"));
        flatten("font", font);
    }

    private void flatten(String prefix, JsonObject object) {
        for (Map.Entry<String, JsonElement> e : object.entrySet()) {
            String key = prefix + "." + e.getKey();
            JsonElement v = e.getValue();
            if (v.isJsonObject()) {
                flatten(key, v.getAsJsonObject());
            } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                numbers.put(key, v.getAsFloat());
            }
        }
    }

    public static Theme get() {
        Theme theme = instance;
        if (theme == null) {
            synchronized (Theme.class) {
                if (instance == null) {
                    instance = load();
                }
                theme = instance;
            }
        }
        return theme;
    }

    static Theme load() {
        JsonObject root = read(RESOURCE);
        // "include": fragments under assets/skirmish/ (one per feature area) deep-merged into the root.
        if (root.has("include")) {
            for (JsonElement path : root.getAsJsonArray("include")) {
                merge(root, read("/assets/skirmish/" + path.getAsString()));
            }
        }
        return parse(root);
    }

    private static JsonObject read(String resource) {
        try (InputStream in = Theme.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is missing");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Deep merge: objects merge key by key, anything else in {@code extra} replaces the value in {@code into}. */
    static void merge(JsonObject into, JsonObject extra) {
        for (Map.Entry<String, JsonElement> e : extra.entrySet()) {
            JsonElement current = into.get(e.getKey());
            if (current != null && current.isJsonObject() && e.getValue().isJsonObject()) {
                merge(current.getAsJsonObject(), e.getValue().getAsJsonObject());
            } else {
                into.add(e.getKey(), e.getValue().deepCopy());
            }
        }
    }

    public static Theme parse(JsonObject root) {
        return new Theme(root);
    }

    /** {@code #RRGGBB} or {@code #RRGGBBAA} (CSS order) to ARGB. */
    public static int parseColor(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) {
            return 0xFF000000 | Integer.parseUnsignedInt(h, 16);
        }
        if (h.length() == 8) {
            int rgba = (int) Long.parseLong(h, 16);
            return (rgba >>> 8) | ((rgba & 0xFF) << 24);
        }
        throw new IllegalArgumentException("Bad color " + hex);
    }

    /** Selects one of {@code accents} (e.g. "violet"); null restores the default accent. */
    public void setAccent(String key) {
        this.accentKey = key != null && accents.containsKey(key) ? key : null;
    }

    public Map<String, Integer> accents() {
        return accents;
    }

    /**
     * ARGB color of a token. Keys starting with {@code accent} follow the selected accent: the RGB comes from the
     * accent, the alpha from the token (so "accent_16" stays 16 % opaque in every accent).
     */
    public int color(String key) {
        Integer c = colors.get(key);
        if (c == null) {
            throw new IllegalArgumentException("Unknown color token " + key);
        }
        if (accentKey != null && key.startsWith("accent")) {
            int accent = accents.get(accentKey);
            return (c & 0xFF000000) | (accent & 0x00FFFFFF);
        }
        return c;
    }

    public float num(String path) {
        Float v = numbers.get(path);
        if (v == null) {
            throw new IllegalArgumentException("Unknown theme value " + path);
        }
        return v;
    }

    public int integer(String path) {
        return Math.round(num(path));
    }

    public float radius(String key) {
        return num("radius." + key);
    }

    public TextStyle text(String key) {
        TextStyle style = styles.get(key);
        if (style == null) {
            throw new IllegalArgumentException("Unknown text style " + key);
        }
        return style;
    }

    /** Integer array at a dotted path (e.g. "font.sizes"). */
    public java.util.List<Integer> ints(String path) {
        return lists.computeIfAbsent(path, p -> {
            JsonElement e = root;
            for (String part : p.split("\\.")) {
                e = e.getAsJsonObject().get(part);
            }
            java.util.List<Integer> out = new java.util.ArrayList<>();
            for (JsonElement v : e.getAsJsonArray()) {
                out.add(v.getAsInt());
            }
            return java.util.List.copyOf(out);
        });
    }

    public String string(String path) {
        String[] parts = path.split("\\.");
        JsonElement e = root;
        for (String p : parts) {
            e = e.getAsJsonObject().get(p);
        }
        return e.getAsString();
    }

    /** Font ascent in px, rounded to whole pixels as Blink does for "normal" line boxes. */
    private float ascent(TextStyle style) {
        return Math.round(num(style.mono() ? "font.mono_ascent" : "font.sans_ascent") * style.size());
    }

    private float descent(TextStyle style) {
        return Math.round(num(style.mono() ? "font.mono_descent" : "font.sans_descent") * style.size());
    }

    /** Line box height of a style in design px (CSS "normal" or the style's line_height). */
    public float lineHeight(TextStyle style) {
        float content = ascent(style) + descent(style);
        return style.lineHeight() > 0f ? style.size() * style.lineHeight() : content;
    }

    /** Distance from the top of the line box to the baseline (half-leading + ascent, as in CSS). */
    public float baseline(TextStyle style) {
        return (lineHeight(style) - ascent(style) - descent(style)) / 2f + ascent(style);
    }
}
