package dev.skirmish.module;

import dev.skirmish.config.ConfigManager;
import dev.skirmish.debug.DebugLog;
import dev.skirmish.setting.FeatureGate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();
    private static final long ERROR_LOG_INTERVAL_MS = 10_000;

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Map<String, Long> lastTickError = new HashMap<>();
    private ConfigManager config;

    public static ModuleManager get() {
        return INSTANCE;
    }

    public <M extends Module> M register(M module) {
        FeatureGate.declare(module.featureId());
        if (modules.putIfAbsent(module.id(), module) != null) {
            throw new IllegalStateException("Duplicate module id " + module.id());
        }
        module.attach(this::markDirty);
        return module;
    }

    public void setConfig(ConfigManager config) {
        this.config = config;
    }

    public void markDirty() {
        if (config != null) {
            config.markDirty();
        }
    }

    public List<Module> all() {
        return Collections.unmodifiableList(new ArrayList<>(modules.values()));
    }

    public Module byId(String id) {
        return modules.get(id);
    }

    public <M extends Module> M get(Class<M> type) {
        for (Module module : modules.values()) {
            if (type.isInstance(module)) {
                return type.cast(module);
            }
        }
        throw new IllegalArgumentException("Module not registered: " + type.getName());
    }

    /** Loads values into a module without triggering a save; used by {@link ConfigManager}. */
    public static void loadEnabled(Module module, boolean enabled) {
        module.loadEnabled(enabled);
    }

    public void initializeAll() {
        FeatureGate.onChange(() -> modules.values().forEach(Module::syncActive));
        for (Module module : modules.values()) {
            try {
                module.onInitialize();
                module.markInitialized();
                module.syncActive();
                DebugLog.log("core", "module " + module.id() + " initialized, enabled=" + module.isEnabled());
            } catch (Throwable t) {
                module.markInitialized();
                DebugLog.error(module.id(), "initialization failed", t);
            }
        }
    }

    public void tickAll() {
        for (Module module : modules.values()) {
            if (!module.isEnabled() || !module.isInitialized()) {
                continue;
            }
            try {
                module.tick();
            } catch (Throwable t) {
                long now = System.currentTimeMillis();
                Long last = lastTickError.get(module.id());
                if (last == null || now - last > ERROR_LOG_INTERVAL_MS) {
                    lastTickError.put(module.id(), now);
                    DebugLog.error(module.id(), "tick failed", t);
                }
            }
        }
    }
}
