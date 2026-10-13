package dev.skirmish.ui.widget;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.debug.DebugLog;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Ui;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A movable, resizable window of a {@link UiScreen}: drag it by any empty spot (the {@link #mover} widget, drawn
 * first so real controls stay on top), resize it with the {@link #grip} in the bottom-right corner, double-click
 * the grip to restore the default size. Position, size and per-window flags (e.g. collapsed menu categories) are
 * kept in config/skirmish/windows.json.
 */
public final class WindowFrame {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, float[]> RECTS = new HashMap<>();
    private static final Map<String, Set<String>> FLAGS = new HashMap<>();
    private static boolean loaded;

    private final String id;
    private final String layout;
    /** Design px: x, y, w, h; NaN until the first layout. */
    private float x = Float.NaN;
    private float y;
    private float w = Float.NaN;
    private float h = Float.NaN;
    private float grabX;
    private float grabY;
    private float startW;
    private float startH;
    private long lastGripClick;

    public final Widget mover = new Widget() {
        @Override
        protected void draw(Ui ui, double mx, double my) {
        }

        @Override
        public boolean clickable() {
            return false;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0) {
                return false;
            }
            grabX = (float) mx - WindowFrame.this.x;
            grabY = (float) my - WindowFrame.this.y;
            return true;
        }

        @Override
        public void mouseDragged(double mx, double my, int button) {
            WindowFrame.this.x = (float) mx - grabX;
            WindowFrame.this.y = (float) my - grabY;
        }

        @Override
        public void mouseReleased(double mx, double my, int button) {
            save();
        }
    };

    public final Widget grip = new Widget() {
        @Override
        protected void draw(Ui ui, double mx, double my) {
            // Three short diagonal strokes in the corner, brighter on hover.
            int color = Anim.lerpColor(ui.color("text_4"), ui.color("text_2"), hovered());
            float s = w;
            float lw = ui.num("stroke.width") * 1.5f;
            for (int i = 1; i <= 3; i++) {
                float d = s * i / 3.5f;
                ui.line(x + s - d, y + s - 1, x + s - 1, y + s - d, lw, color);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0) {
                return false;
            }
            long now = System.currentTimeMillis();
            if (now - lastGripClick < 350) {
                WindowFrame.this.w = Float.NaN;
                lastGripClick = 0;
                return true;
            }
            lastGripClick = now;
            grabX = (float) mx;
            grabY = (float) my;
            startW = WindowFrame.this.w;
            startH = WindowFrame.this.h;
            return true;
        }

        @Override
        public void mouseDragged(double mx, double my, int button) {
            WindowFrame.this.w = startW + (float) mx - grabX;
            WindowFrame.this.h = startH + (float) my - grabY;
        }

        @Override
        public void mouseReleased(double mx, double my, int button) {
            save();
        }
    };

    /**
     * @param id     key in windows.json
     * @param layout theme prefix with {@code width}, {@code height}, {@code min_width}, {@code min_height}
     */
    public WindowFrame(String id, String layout) {
        this.id = id;
        this.layout = layout;
        load();
        float[] r = RECTS.get(id);
        if (r != null) {
            x = r[0];
            y = r[1];
            w = r[2];
            h = r[3];
        }
    }

    /** Clamps the window to the screen (centred at the default size the first time); call once per frame. */
    public void layout(Ui ui) {
        float edge = ui.num("layout.screen_edge");
        float maxW = ui.width() - edge * 2;
        float maxH = ui.height() - edge * 2;
        if (Float.isNaN(w) || Float.isNaN(h)) {
            w = ui.num(layout + "width");
            h = ui.num(layout + "height");
            x = Float.NaN;
        }
        w = clamp(w, Math.min(ui.num(layout + "min_width"), maxW), maxW);
        h = clamp(h, Math.min(ui.num(layout + "min_height"), maxH), maxH);
        if (Float.isNaN(x)) {
            x = (ui.width() - w) / 2f;
            y = (ui.height() - h) / 2f;
        }
        x = Math.round(clamp(x, 0, ui.width() - w));
        y = Math.round(clamp(y, 0, ui.height() - h));
        w = Math.round(w);
        h = Math.round(h);
        mover.bounds(x, y, w, h);
        float g = ui.num("layout.window_grip");
        grip.bounds(x + w - g - ui.num("layout.window_grip_inset"), y + h - g - ui.num("layout.window_grip_inset"), g, g);
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float w() {
        return w;
    }

    public float h() {
        return h;
    }

    /** A per-window on/off flag (e.g. a collapsed section), saved with the window. */
    public boolean flag(String name) {
        return FLAGS.getOrDefault(id, Set.of()).contains(name);
    }

    public void setFlag(String name, boolean on) {
        Set<String> set = FLAGS.computeIfAbsent(id, k -> new HashSet<>());
        if (on ? set.add(name) : set.remove(name)) {
            save();
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- windows.json ----

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("skirmish").resolve("windows.json");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("windows").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                RECTS.put(e.getKey(), new float[]{o.get("x").getAsFloat(), o.get("y").getAsFloat(),
                        o.get("w").getAsFloat(), o.get("h").getAsFloat()});
                Set<String> flags = new HashSet<>();
                if (o.has("flags")) {
                    o.getAsJsonArray("flags").forEach(f -> flags.add(f.getAsString()));
                }
                FLAGS.put(e.getKey(), flags);
            }
        } catch (Exception e) {
            DebugLog.error("ui", "could not read " + file, e);
        }
    }

    private void save() {
        if (!Float.isNaN(w)) {
            RECTS.put(id, new float[]{x, y, w, h});
        } else {
            RECTS.remove(id);
        }
        JsonObject windows = new JsonObject();
        Set<String> ids = new HashSet<>(RECTS.keySet());
        ids.addAll(FLAGS.keySet());
        for (String key : ids) {
            JsonObject o = new JsonObject();
            float[] r = RECTS.get(key);
            if (r == null) {
                continue;
            }
            o.addProperty("x", r[0]);
            o.addProperty("y", r[1]);
            o.addProperty("w", r[2]);
            o.addProperty("h", r[3]);
            JsonArray flags = new JsonArray();
            FLAGS.getOrDefault(key, Set.of()).forEach(flags::add);
            o.add("flags", flags);
            windows.add(key, o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("windows", windows);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("windows.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            DebugLog.error("ui", "could not write " + file, e);
        }
    }
}
