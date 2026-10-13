package dev.skirmish.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.skirmish.debug.DebugLog;
import dev.skirmish.module.Module;
import dev.skirmish.setting.Setting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One-time changes to saved settings, applied after the config is loaded. Each migration runs once per install; the
 * ids of those done are kept in {@code config/skirmish/migrations.txt}.
 */
public final class Migrations {
    /**
     * «Clean HUD»: quieter defaults for the busy combat HUD. Combo off, no dossier panel, item counter hides zeros,
     * lag pill only while lagging, no food / pearl / apple nags.
     */
    static final String CLEAN_HUD = "clean_hud_1";

    private static final Map<String, Map<String, JsonElement>> CLEAN_HUD_SETTINGS = Map.of(
            "dossier", Map.of("hud", new JsonPrimitive(false)),
            "item_counter", Map.of("hide_zero", new JsonPrimitive(true)),
            "lag_meter", Map.of("show", new JsonPrimitive("LAGGING")),
            "survival_alerts", Map.of("food", new JsonPrimitive(false), "pearls", new JsonPrimitive(false),
                    "gapples", new JsonPrimitive(false)));

    private Migrations() {
    }

    /** Runs the pending migrations; returns whether any setting changed (the config then needs saving). */
    public static boolean run(Path dir, List<Module> modules) {
        Path file = dir.resolve("migrations.txt");
        List<String> done = new ArrayList<>();
        try {
            if (Files.isRegularFile(file)) {
                done.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            DebugLog.error("core", "could not read " + file, e);
            return false;
        }
        if (done.contains(CLEAN_HUD)) {
            return false;
        }
        cleanHud(modules);
        done.add(CLEAN_HUD);
        try {
            Files.createDirectories(dir);
            Files.write(file, done, StandardCharsets.UTF_8);
        } catch (IOException e) {
            DebugLog.error("core", "could not write " + file, e);
        }
        DebugLog.log("core", "migration " + CLEAN_HUD + " applied");
        return true;
    }

    static void cleanHud(List<Module> modules) {
        for (Module module : modules) {
            if (module.id().equals("combo_hud")) {
                module.setEnabled(false);
            }
            Map<String, JsonElement> values = CLEAN_HUD_SETTINGS.get(module.id());
            if (values == null) {
                continue;
            }
            for (Setting<?> setting : module.settings()) {
                JsonElement value = values.get(setting.id());
                if (value != null) {
                    setting.fromJson(value);
                }
            }
        }
    }
}
