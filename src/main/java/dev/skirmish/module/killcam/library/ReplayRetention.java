package dev.skirmish.module.killcam.library;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Storage cleanup policy: while there are more than {@code maxCount} replays or they take more than
 * {@code maxBytes}, the oldest replay that is neither a favourite nor the protected one (the replay just saved) is
 * deleted. Favourites are never deleted, even when they alone exceed the limits.
 */
public final class ReplayRetention {
    private ReplayRetention() {
    }

    /** One stored replay as the policy sees it; {@code id} is the file name. */
    public record Item(String id, long createdMs, long bytes, boolean favourite) {
    }

    /** Replays to delete, oldest first. */
    public static List<Item> select(List<Item> items, int maxCount, long maxBytes, @Nullable String keep) {
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingLong(Item::createdMs).thenComparing(Item::id));
        int count = sorted.size();
        long total = 0;
        for (Item item : sorted) {
            total += Math.max(0, item.bytes());
        }
        List<Item> delete = new ArrayList<>();
        for (Item item : sorted) {
            if (count <= Math.max(0, maxCount) && total <= Math.max(0, maxBytes)) {
                break;
            }
            if (item.favourite() || item.id().equals(keep)) {
                continue;
            }
            delete.add(item);
            count--;
            total -= Math.max(0, item.bytes());
        }
        return delete;
    }
}
