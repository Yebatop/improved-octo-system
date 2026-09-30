package dev.skirmish.module.worldmap;

import dev.skirmish.util.MaskedCoords;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Search zones from masked coordinates in chat («… Золотой Спавнер на координатах 1*4*, 5, 1*9*»): every cell the
 * hidden digits allow, drawn on the world map. Only what the server wrote, taken apart; kept for an hour, the
 * last {@link #MAX} of them, until you leave the server.
 */
public final class SearchZones {
    static final long KEEP_MS = 3_600_000;
    static final int MAX = 5;

    /** A zone: what the line was about, its cells, Y when given, its dimension and when it was seen. */
    public record Zone(String what, List<int[]> xs, @Nullable Integer y, List<int[]> zs, String dimension, long at,
                       String xMask, String zMask, java.util.BitSet visited) {
        /** Index of the cell (X range i, Z range j). */
        public int cell(int i, int j) {
            return i * zs.size() + j;
        }

        public int minX() {
            return xs.getFirst()[0];
        }

        public int maxX() {
            return xs.getLast()[1];
        }

        public int minZ() {
            return zs.getFirst()[0];
        }

        public int maxZ() {
            return zs.getLast()[1];
        }

        public int cells() {
            return xs.size() * zs.size();
        }
    }

    private static final Deque<Zone> ZONES = new ArrayDeque<>();

    private SearchZones() {
    }

    public static synchronized void add(MaskedCoords.Masked m, String dimension, long now) {
        ZONES.addFirst(new Zone(m.what(), m.xs(), m.y(), m.zs(), dimension, now, m.xMask(), m.zMask(), new java.util.BitSet()));
        while (ZONES.size() > MAX) {
            ZONES.removeLast();
        }
    }

    /** Zones in that dimension still kept, newest first. */
    public static synchronized List<Zone> in(String dimension, long now) {
        ZONES.removeIf(z -> now - z.at() > KEEP_MS);
        List<Zone> out = new ArrayList<>();
        for (Zone z : ZONES) {
            if (z.dimension().equals(dimension)) {
                out.add(z);
            }
        }
        return out;
    }

    /** The newest zone still kept, in any dimension. */
    public static synchronized @Nullable Zone latest(long now) {
        ZONES.removeIf(z -> now - z.at() > KEEP_MS);
        return ZONES.peekFirst();
    }

    /** Ends the hunt for that zone (or all of them). */
    public static synchronized void remove(@Nullable Zone zone) {
        if (zone == null) {
            ZONES.clear();
        } else {
            ZONES.remove(zone);
        }
    }

    static synchronized void clear() {
        ZONES.clear();
    }
}
