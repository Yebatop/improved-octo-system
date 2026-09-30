package dev.skirmish.setting;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Features that must be switched off and hidden right now: the server's Feature Control blocklist plus the local
 * safe-mode list (see {@code dev.skirmish.holyworld.FeatureControl}). Pure Java so modules and settings can ask it
 * without Minecraft classes. A blocked feature is not just disabled: modules, settings, HUD elements and menu rows
 * tied to it disappear.
 */
public final class FeatureGate {
    private static volatile Set<String> blocked = Set.of();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Set<String> DECLARED = new java.util.concurrent.ConcurrentSkipListSet<>();

    private FeatureGate() {
    }

    /** Registers a feature id that the mod reports to the server (modules, settings and setting values do this). */
    public static void declare(String featureId) {
        if (featureId != null && !featureId.isBlank()) {
            DECLARED.add(featureId);
        }
    }

    /** Every feature id the mod has, in a stable order. */
    public static List<String> declared() {
        return List.copyOf(DECLARED);
    }

    public static boolean isBlocked(String featureId) {
        return featureId != null && blocked.contains(featureId);
    }

    public static Set<String> blocked() {
        return blocked;
    }

    public static void setBlocked(Collection<String> ids) {
        Set<String> next = Set.copyOf(ids);
        if (!next.equals(blocked)) {
            blocked = next;
            for (Runnable listener : LISTENERS) {
                listener.run();
            }
        }
    }

    public static void onChange(Runnable listener) {
        LISTENERS.add(listener);
    }
}
