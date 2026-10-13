package dev.skirmish.module.hwtimers;

import dev.skirmish.module.hwtimers.TimerTable.BlockSig;
import dev.skirmish.module.hwtimers.TimerTable.BreakSig;
import dev.skirmish.module.hwtimers.TimerTable.TimerDef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Groups server block updates around the local player into the shapes HolyWorld items leave: an ice sphere
 * (Ледяная волна) or obsidian broken by an explosion (raid block). Only updates the server sent and
 * that changed the block are fed in; my own placements arrive as confirmations of what the client already shows and
 * are filtered out before this class. Pure Java, covered by tests.
 */
public final class BlockBursts {
    /** Appear events older than this are dropped whatever the table says. */
    static final long KEEP_MS = 5_000;
    private static final int MAX_EVENTS = 4096;
    /** A block update may arrive shortly before the explosion packet of the same blast. */
    static final long EARLY_BREAK_MS = 300;

    record Appear(long pos, String blockId, double distance, long timeMs) {
    }

    record Explosion(double x, double y, double z, float radius, long timeMs) {
    }

    /** A recognised shape: the timer and the positions it covers. */
    public record Burst(TimerDef def, List<Long> positions) {
    }

    private final Deque<Appear> appears = new ArrayDeque<>();
    private final Deque<Explosion> explosions = new ArrayDeque<>();

    /** A block appeared {@code distance} blocks from me (air or a replaceable block became {@code blockId}). */
    public void appear(long pos, String blockId, double distance, long now) {
        appears.addLast(new Appear(pos, blockId, distance, now));
        while (appears.size() > MAX_EVENTS) {
            appears.removeFirst();
        }
    }

    /** A watched block that became air with no explosion known yet; the explosion packet may follow. */
    private record PendingBreak(TimerDef def, long pos, double x, double y, double z, long timeMs) {
    }

    /** A watched block an explosion broke. */
    public record Break(TimerDef def, long pos) {
    }

    private final Deque<PendingBreak> pendingBreaks = new ArrayDeque<>();

    /**
     * Records an explosion; returns the watched blocks that became air just before its packet arrived and that it
     * could have broken.
     */
    public List<Break> explosion(double x, double y, double z, float radius, long now) {
        Explosion e = new Explosion(x, y, z, radius, now);
        explosions.addLast(e);
        while (explosions.size() > 64) {
            explosions.removeFirst();
        }
        List<Break> out = new ArrayList<>();
        for (Iterator<PendingBreak> it = pendingBreaks.iterator(); it.hasNext(); ) {
            PendingBreak p = it.next();
            if (now - p.timeMs() <= EARLY_BREAK_MS && reaches(e, p.x(), p.y(), p.z())) {
                out.add(new Break(p.def(), p.pos()));
                it.remove();
            }
        }
        return out;
    }

    /**
     * {@code blockId} at (x, y, z) became air, {@code distanceToMe} blocks from me: the first timer whose explosion
     * signature it fits (a recent explosion close enough to have broken it), or null. A watched block with no such
     * explosion yet is kept for {@link #EARLY_BREAK_MS} in case the explosion packet comes right after.
     */
    public @Nullable TimerDef broken(long pos, double x, double y, double z, String blockId, double distanceToMe, long now,
                                     List<TimerDef> defs) {
        pendingBreaks.removeIf(p -> now - p.timeMs() > EARLY_BREAK_MS);
        TimerDef watched = null;
        for (TimerDef def : defs) {
            BreakSig sig = def.breaks();
            if (sig == null || !sig.blocks().contains(blockId) || distanceToMe > sig.nearMe()) {
                continue;
            }
            if (watched == null) {
                watched = def;
            }
            for (Explosion e : explosions) {
                if (now - e.timeMs() >= 0 && now - e.timeMs() <= sig.windowMs() && reaches(e, x, y, z)) {
                    return def;
                }
            }
        }
        if (watched != null && pendingBreaks.size() < 256) {
            pendingBreaks.addLast(new PendingBreak(watched, pos, x, y, z, now));
        }
        return null;
    }

    private static boolean reaches(Explosion e, double x, double y, double z) {
        double reach = Math.max(e.radius() * 2.0, 6.0);
        double dx = x - e.x();
        double dy = y - e.y();
        double dz = z - e.z();
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    /**
     * Timers whose block signature is met by the recent unclaimed appear events, in table order; the events used are
     * claimed so one burst starts one timer (an ice sphere is not also a box). While a timer is {@code running},
     * any matching block joins it (the rest of a sphere that arrives a tick later is not a new box).
     */
    public List<Burst> evaluate(List<TimerDef> defs, Predicate<String> running, long now) {
        prune(now);
        List<Burst> out = new ArrayList<>();
        for (TimerDef def : defs) {
            BlockSig sig = def.blocks();
            if (sig == null) {
                continue;
            }
            List<Appear> hits = new ArrayList<>();
            for (Appear a : appears) {
                if (now - a.timeMs() <= sig.windowMs() && a.distance() <= sig.radius() && sig.accepts(a.blockId())) {
                    hits.add(a);
                }
            }
            if (!hits.isEmpty() && hits.size() >= (running.test(def.id()) ? 1 : sig.min())) {
                appears.removeAll(hits);
                out.add(new Burst(def, hits.stream().map(Appear::pos).distinct().toList()));
            }
        }
        return out;
    }

    public void clear() {
        appears.clear();
        explosions.clear();
        pendingBreaks.clear();
    }

    int pending() {
        return appears.size();
    }

    private void prune(long now) {
        for (Iterator<Appear> it = appears.iterator(); it.hasNext(); ) {
            if (now - it.next().timeMs() > KEEP_MS) {
                it.remove();
            } else {
                break;
            }
        }
        explosions.removeIf(e -> now - e.timeMs() > KEEP_MS);
    }
}
