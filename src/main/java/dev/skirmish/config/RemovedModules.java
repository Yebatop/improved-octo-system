package dev.skirmish.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.debug.DebugLog;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cleanup after modules that were removed. «Спринт» (toggle_sprint) switched vanilla's toggle-sprint option on and
 * kept the user's own value in its settings; with the module gone, that value is put back once, on the first start.
 */
public final class RemovedModules {
    private RemovedModules() {
    }

    /**
     * Reads what the removed modules left in config.json. Call before the config is first saved (the save drops
     * unknown modules); the returned action applies it once the client's options exist.
     */
    public static Runnable read(Path configFile) {
        try {
            if (!Files.isRegularFile(configFile)) {
                return () -> { };
            }
            JsonObject root = JsonParser.parseString(Files.readString(configFile, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject modules = root.getAsJsonObject("modules");
            if (modules == null || !modules.has("toggle_sprint")) {
                return () -> { };
            }
            JsonObject sprint = modules.getAsJsonObject("toggle_sprint");
            boolean wasOn = sprint.has("enabled") && sprint.get("enabled").getAsBoolean();
            String user = userValue(sprint, "toggleSprint");
            if (!wasOn || user == null) {
                return () -> { };
            }
            return () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.options.toggleSprint().set(Boolean.parseBoolean(user));
                mc.options.save();
                DebugLog.log("core", "removed module toggle_sprint: vanilla toggleSprint restored to " + user);
            };
        } catch (Exception e) {
            DebugLog.error("core", "could not read removed modules from " + configFile, e);
            return () -> { };
        }
    }

    /** The user's own value recorded by the module's option ledger ({@code saved_vanilla}), or null. */
    private static String userValue(JsonObject module, String option) {
        JsonObject settings = module.getAsJsonObject("settings");
        if (settings == null || !settings.has("saved_vanilla")) {
            return null;
        }
        JsonElement raw = settings.get("saved_vanilla");
        String text = raw.isJsonPrimitive() ? raw.getAsString() : raw.toString();
        if (text.isBlank()) {
            return null;
        }
        JsonObject ledger = JsonParser.parseString(text).getAsJsonObject();
        JsonObject entry = ledger.getAsJsonObject(option);
        return entry == null || !entry.has("user") ? null : entry.get("user").getAsString();
    }
}
