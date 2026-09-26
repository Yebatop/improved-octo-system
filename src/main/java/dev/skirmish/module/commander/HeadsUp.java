package dev.skirmish.module.commander;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Heads-up for things that start soon (End capture, a vote, the restart, a Prime event): each start is announced
 * once when it comes within the lead time, and the soonest one is shown with a countdown. Pure Java.
 */
public final class HeadsUp {
    /** Something that starts at {@code at}; {@code key} tells two starts apart (kind plus time). */
    public record Item(String key, String name, String note, Instant at) {
    }

    private final Map<String, Instant> announced = new HashMap<>();

    /** The first item that just came within {@code lead} and was not announced yet (then it is marked), or null. */
    @Nullable Item due(List<Item> items, Instant now, Duration lead) {
        announced.values().removeIf(at -> at.isBefore(now.minus(Duration.ofHours(1))));
        Item first = null;
        for (Item item : items) {
            Duration left = Duration.between(now, item.at());
            if (left.isNegative() || left.compareTo(lead) > 0 || announced.containsKey(item.key())) {
                continue;
            }
            announced.put(item.key(), item.at());
            if (first == null || item.at().isBefore(first.at())) {
                first = item;
            }
        }
        return first;
    }

    /** The soonest item starting within {@code lead}, or one that started less than {@code after} ago. */
    static @Nullable Item next(List<Item> items, Instant now, Duration lead, Duration after) {
        Item best = null;
        for (Item item : items) {
            Duration left = Duration.between(now, item.at());
            if (left.compareTo(lead) > 0 || left.compareTo(after.negated()) < 0) {
                continue;
            }
            if (best == null || item.at().isBefore(best.at())) {
                best = item;
            }
        }
        return best;
    }
}
