package dev.skirmish.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.debug.DebugLog;
import dev.skirmish.module.Module;
import dev.skirmish.module.ModuleManager;
import dev.skirmish.setting.Setting;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * config/skirmish/config.json:
 * <pre>{"version":1,"modules":{"killcam":{"enabled":true,"debug_log":false,"settings":{...}}}}</pre>
 * Changes mark the config dirty; {@link #tick()} serializes on the client thread after a short debounce
 * and the file write happens on a background thread (temp file + atomic move).
 */
public final class ConfigManager {
    public static final int VERSION = 1;
    private static final long SAVE_DELAY_MS = 500;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Supplier<List<Module>> modules;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Skirmish config writer");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean dirty;
    private volatile long dirtySince;

    public ConfigManager(Path file, Supplier<List<Module>> modules) {
        this.file = file;
        this.modules = modules;
    }

    public Path file() {
        return file;
    }

    public void load() {
        if (!Files.exists(file)) {
            DebugLog.log("core", "config.json not found, using defaults");
            markDirty();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            apply(root);
            DebugLog.log("core", "config.json loaded");
        } catch (Exception e) {
            DebugLog.error("core", "config.json is unreadable, keeping defaults (backup: config.json.broken)", e);
            try {
                Files.copy(file, file.resolveSibling("config.json.broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
    }

    /** Applies a parsed config; unknown modules and settings are ignored, missing ones keep defaults. */
    public void apply(JsonElement root) {
        if (root == null || !root.isJsonObject()) {
            return;
        }
        JsonObject modulesJson = root.getAsJsonObject().getAsJsonObject("modules");
        if (modulesJson == null) {
            return;
        }
        for (Module module : modules.get()) {
            JsonElement element = modulesJson.get(module.id());
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            JsonElement enabled = json.get("enabled");
            if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean()) {
                ModuleManager.loadEnabled(module, enabled.getAsBoolean());
            }
            module.debugLog.fromJson(json.get("debug_log"));
            JsonObject settings = json.getAsJsonObject("settings");
            if (settings == null) {
                continue;
            }
            for (Setting<?> setting : module.settings()) {
                if (setting.isPersisted() && settings.has(setting.id())) {
                    setting.fromJson(settings.get(setting.id()));
                }
            }
        }
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonObject modulesJson = new JsonObject();
        for (Module module : modules.get()) {
            JsonObject json = new JsonObject();
            json.addProperty("enabled", module.isSwitchedOn());
            json.add("debug_log", module.debugLog.toJson());
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.settings()) {
                if (setting.isPersisted()) {
                    settings.add(setting.id(), setting.toJson());
                }
            }
            json.add("settings", settings);
            modulesJson.add(module.id(), json);
        }
        root.add("modules", modulesJson);
        return root;
    }

    public void markDirty() {
        if (!dirty) {
            dirtySince = System.currentTimeMillis();
            dirty = true;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Called every client tick: saves once the debounce delay has passed. */
    public void tick() {
        if (dirty && System.currentTimeMillis() - dirtySince >= SAVE_DELAY_MS) {
            saveAsync();
        }
    }

    public void saveAsync() {
        dirty = false;
        String text = GSON.toJson(toJson());
        writer.execute(() -> write(text));
    }

    /** Synchronous save, used on shutdown. */
    public void saveNow() {
        dirty = false;
        write(GSON.toJson(toJson()));
    }

    private synchronized void write(String text) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            DebugLog.log("core", "config.json saved");
        } catch (IOException e) {
            DebugLog.error("core", "config.json save failed", e);
        }
    }
}
