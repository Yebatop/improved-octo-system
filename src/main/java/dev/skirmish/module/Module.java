package dev.skirmish.module;

import dev.skirmish.debug.DebugLog;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Base class for every feature. Lifecycle, driven by {@link ModuleManager}:
 * <ol>
 *     <li>constructor: declare settings with {@link #add};</li>
 *     <li>{@link #onInitialize()} once at client start, after config.json is loaded: register Fabric events,
 *     HUD elements and combat listeners here (they must check {@link #isEnabled()} themselves);</li>
 *     <li>{@link #onEnable()} / {@link #onDisable()} on every toggle (and onEnable once after init when enabled);</li>
 *     <li>{@link #tick()} at the end of every client tick while enabled.</li>
 * </ol>
 * Keep module code free of blocking IO on the render thread; use {@link #log} for diagnostics.
 */
public abstract class Module {
    private final String id;
    private final boolean defaultEnabled;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    private boolean initialized;
    private Runnable saveHook = () -> {};

    /** Per-module switch for config/skirmish/debug.log, shown in the menu for every module. */
    public final BoolSetting debugLog;

    protected Module(String id, boolean defaultEnabled) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.defaultEnabled = defaultEnabled;
        this.enabled = defaultEnabled;
        this.debugLog = new BoolSetting("debug_log", false);
        this.debugLog.bind(this.id, () -> saveHook.run());
    }

    public final String id() {
        return id;
    }

    public String nameKey() {
        return "skirmish.module." + id + ".name";
    }

    public String descriptionKey() {
        return "skirmish.module." + id + ".description";
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    /** Modules that others depend on (e.g. the combat tracker) return false and stay enabled. */
    public boolean canToggle() {
        return true;
    }

    public final void setEnabled(boolean value) {
        applyEnabled(value);
        saveHook.run();
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    /** Applies a value from config.json without scheduling a save. */
    final void loadEnabled(boolean value) {
        applyEnabled(value);
    }

    private void applyEnabled(boolean value) {
        if (!canToggle()) {
            value = true;
        }
        if (enabled == value) {
            return;
        }
        enabled = value;
        if (!initialized) {
            return;
        }
        log(value ? "enabled" : "disabled");
        try {
            if (value) {
                onEnable();
            } else {
                onDisable();
            }
        } catch (Throwable t) {
            DebugLog.error(id, "toggle failed", t);
        }
    }

    protected final <S extends Setting<?>> S add(S setting) {
        setting.bind(id, () -> saveHook.run());
        settings.add(setting);
        return setting;
    }

    /** Declared settings, in menu order (without the built-in {@link #debugLog}). */
    public final List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    final void attach(Runnable saveHook) {
        this.saveHook = saveHook;
    }

    final void markInitialized() {
        initialized = true;
    }

    public final boolean isInitialized() {
        return initialized;
    }

    public void onInitialize() {
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public void tick() {
    }

    public final boolean isDebug() {
        return debugLog.get();
    }

    /** Writes to debug.log when this module's log toggle is on. */
    public final void log(String message) {
        if (debugLog.get()) {
            DebugLog.log(id, message);
        }
    }

    public final void log(String format, Object... args) {
        if (debugLog.get()) {
            DebugLog.log(id, String.format(Locale.ROOT, format, args));
        }
    }

    public final void error(String message, Throwable t) {
        DebugLog.error(id, message, t);
    }
}
