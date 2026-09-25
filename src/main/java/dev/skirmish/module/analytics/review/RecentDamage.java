package dev.skirmish.module.analytics.review;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Rolling list of damage events on me (the last {@code keepMs}); health drops and crit animations that follow a
 * damage event are attached to it. Pure Java, unit tested.
 */
final class RecentDamage {
    private final long keepMs;
    private final ArrayDeque<DamageTaken> entries = new ArrayDeque<>();
    private long pendingCritMs = -1;

    RecentDamage(long keepMs) {
        this.keepMs = keepMs;
    }

    void add(DamageTaken entry) {
        if (pendingCritMs >= 0 && entry.timeMs() >= pendingCritMs && entry.timeMs() - pendingCritMs <= FightLog.CRIT_WINDOW_MS) {
            entry.crit = true;
        }
        pendingCritMs = -1;
        entries.addLast(entry);
        prune(entry.timeMs());
    }

    /** Health I lost at {@code timeMs}: goes to the latest event without an amount within the damage window. */
    boolean amount(float lost, long timeMs) {
        if (!(lost > 0)) {
            return false;
        }
        DamageTaken last = entries.peekLast();
        if (last == null || !Float.isNaN(last.amount) || timeMs < last.timeMs() || timeMs - last.timeMs() > FightLog.DAMAGE_WINDOW_MS) {
            return false;
        }
        last.amount = lost;
        return true;
    }

    /** Crit particles on me: marks the latest event within the crit window, else waits for the next one. */
    boolean crit(long timeMs) {
        DamageTaken last = entries.peekLast();
        if (last != null && !last.crit && Math.abs(timeMs - last.timeMs()) <= FightLog.CRIT_WINDOW_MS) {
            last.crit = true;
            return true;
        }
        pendingCritMs = timeMs;
        return false;
    }

    /** Events in {@code [fromMs, toMs]}, oldest first. */
    List<DamageTaken> window(long fromMs, long toMs) {
        List<DamageTaken> out = new ArrayList<>();
        for (DamageTaken d : entries) {
            if (d.timeMs() >= fromMs && d.timeMs() <= toMs) {
                out.add(d);
            }
        }
        return out;
    }

    void prune(long nowMs) {
        Iterator<DamageTaken> it = entries.iterator();
        while (it.hasNext() && nowMs - it.next().timeMs() > keepMs) {
            it.remove();
        }
    }

    int size() {
        return entries.size();
    }

    void clear() {
        entries.clear();
        pendingCritMs = -1;
    }
}
