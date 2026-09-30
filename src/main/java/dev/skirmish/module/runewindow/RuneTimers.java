package dev.skirmish.module.runewindow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Running invulnerability windows, one per player (a second pop restarts it), newest first. Pure Java, unit tested.
 */
final class RuneTimers {
    static final int MAX = 4;

    /**
     * One window.
     *
     * @param kind    INVULNERABLE (countdown) or RESTORED (short notice)
     * @param guessed the length is the user's fallback, not read from the item
     */
    record Timer(UUID uuid, int entityId, String name, RuneEffect.Kind kind, long startMs, long durationMs, boolean guessed) {
        long remainingMs(long now) {
            return Math.max(0, startMs + durationMs - now);
        }

        /** 1 at the pop, 0 at the end. */
        float fraction(long now) {
            return durationMs <= 0 ? 0f : Math.min(1f, remainingMs(now) / (float) durationMs);
        }
    }

    private final List<Timer> timers = new ArrayList<>();

    void start(Timer timer) {
        remove(timer.uuid());
        timers.addFirst(timer);
        while (timers.size() > MAX) {
            timers.removeLast();
        }
    }

    void remove(UUID uuid) {
        timers.removeIf(t -> t.uuid().equals(uuid));
    }

    /** Removes finished windows and returns them (oldest end first), so the caller can play the end sound. */
    List<Timer> expire(long now) {
        List<Timer> ended = new ArrayList<>();
        for (Iterator<Timer> it = timers.iterator(); it.hasNext(); ) {
            Timer t = it.next();
            if (t.remainingMs(now) <= 0) {
                ended.addFirst(t);
                it.remove();
            }
        }
        return ended;
    }

    List<Timer> live() {
        return List.copyOf(timers);
    }

    boolean isEmpty() {
        return timers.isEmpty();
    }

    void clear() {
        timers.clear();
    }

    /** "3.0", "0.4": tenths, rounded up so the chip never shows 0.0 while the window is open. */
    static String seconds(long remainingMs) {
        long tenths = (remainingMs + 99) / 100;
        return (tenths / 10) + "." + (tenths % 10);
    }
}
