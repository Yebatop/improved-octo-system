package dev.skirmish.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.setting.StringSetting;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Remembers the user's own values of vanilla options a module overrides, so turning the module off puts them back.
 * Kept in a hidden persisted {@link StringSetting} (JSON {@code {"key": {"user": "...", "ours": "..."}}}), which
 * survives a game restart while the module is on: options.txt then holds the module's values, but the user's
 * originals are still known. Pure Java so it can be unit tested.
 */
public final class OptionLedger {
    private final StringSetting store;

    public OptionLedger(StringSetting store) {
        this.store = store;
    }

    /**
     * The persisted storage for a ledger: a string setting that is never shown in the menu and that the menu's
     * "reset module" leaves alone (resetting it would forget the user's values before they were restored).
     */
    public static final class Store extends StringSetting {
        public Store(String id) {
            super(id, "", 4096, false);
            visibleWhen(() -> false);
        }

        @Override
        public void reset() {
        }
    }

    private JsonObject read() {
        try {
            JsonElement parsed = JsonParser.parseString(store.get());
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (RuntimeException ignored) {
            // Broken or empty: start over.
        }
        return new JsonObject();
    }

    private void write(JsonObject root) {
        store.set(root.size() == 0 ? "" : root.toString());
    }

    public boolean has(String key) {
        return read().has(key);
    }

    /** Records the user's value the first time this option is overridden; later calls keep the first value. */
    public void remember(String key, String userValue) {
        JsonObject root = read();
        if (root.has(key)) {
            return;
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("user", userValue);
        entry.addProperty("ours", userValue);
        root.add(key, entry);
        write(root);
    }

    /** Records the value the module wrote last. */
    public void written(String key, String value) {
        JsonObject root = read();
        JsonObject entry = root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
        if (!entry.has("user")) {
            entry.addProperty("user", value);
        }
        entry.addProperty("ours", value);
        root.add(key, entry);
        write(root);
    }

    /**
     * The user's value to put back, or null when there is nothing to restore: unknown key, or the option was changed
     * by hand after the module wrote it (then the user's newer choice is kept).
     */
    public @Nullable String restoreValue(String key, String current) {
        JsonObject root = read();
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return null;
        }
        JsonObject entry = root.getAsJsonObject(key);
        if (!entry.has("user")) {
            return null;
        }
        String ours = entry.has("ours") ? entry.get("ours").getAsString() : null;
        if (ours != null && !ours.equals(current)) {
            return null;
        }
        return entry.get("user").getAsString();
    }

    public Set<String> keys() {
        return new LinkedHashSet<>(read().keySet());
    }

    public void clear() {
        store.set("");
    }
}
