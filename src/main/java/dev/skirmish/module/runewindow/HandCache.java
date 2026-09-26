package dev.skirmish.module.runewindow;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The totem each nearby player held, sampled every tick, so the item is still known when the pop arrives: the
 * server consumes the totem and sends the equipment change around the same time as the pop event, sometimes before
 * it. {@code T} is the reading of the item ({@link RuneEffect} in game). Pure Java, unit tested.
 */
final class HandCache<T> {
    /** A totem that vanished from the hands this recently is still the one that popped. */
    static final long GRACE_MS = 1_500;

    private record Entry<T>(@Nullable T current, @Nullable T lastTotem, long lastTotemMs) {
    }

    private final Map<UUID, Entry<T>> entries = new HashMap<>();

    /** Latest sample: {@code totem} is the reading of the totem in the hands, null when none is visible. */
    void update(UUID player, @Nullable T totem, long now) {
        Entry<T> old = entries.get(player);
        if (totem != null) {
            entries.put(player, new Entry<>(totem, totem, now));
        } else {
            entries.put(player, new Entry<>(null, old == null ? null : old.lastTotem(), old == null ? -1 : old.lastTotemMs()));
        }
    }

    /**
     * The totem that just popped: the one in the hands now, else the last one seen within {@link #GRACE_MS}; null
     * when this player was never sampled or held no totem lately.
     */
    @Nullable T popped(UUID player, long now) {
        Entry<T> e = entries.get(player);
        if (e == null) {
            return null;
        }
        if (e.current() != null) {
            return e.current();
        }
        return e.lastTotem() != null && now - e.lastTotemMs() <= GRACE_MS ? e.lastTotem() : null;
    }

    boolean sampled(UUID player) {
        return entries.containsKey(player);
    }

    /** Forgets players that are no longer around. */
    void retain(Set<UUID> present) {
        entries.keySet().retainAll(present);
    }

    void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }
}
