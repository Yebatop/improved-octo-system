package dev.skirmish.module.events;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Remembers which event instances were already seen, so only new ones raise a toast. The first update after a
 * reset is a baseline: events that were running when the player joined are not announced. Pure Java.
 */
public final class InstanceDiff<T> {
    private final Function<T, String> id;
    private final Set<String> seen = new HashSet<>();
    private boolean primed;

    public InstanceDiff(Function<T, String> id) {
        this.id = id;
    }

    /** Items whose id was not in the previous updates (empty on the baseline update). */
    public List<T> update(List<T> current) {
        List<T> fresh = new ArrayList<>();
        Set<String> now = new HashSet<>();
        for (T item : current) {
            String key = id.apply(item);
            now.add(key);
            if (primed && !seen.contains(key)) {
                fresh.add(item);
            }
        }
        seen.clear();
        seen.addAll(now);
        primed = true;
        return fresh;
    }

    public void reset() {
        seen.clear();
        primed = false;
    }
}
