package dev.skirmish.module.bosscoach;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * My share of one boss bar's drop. The server does not say who dealt damage to a boss; what the client knows is when
 * my hits landed (server-confirmed damage events on the mob I attacked) and when the bar went down. A drop that
 * comes within the attribution window after one of my hits is counted as mine, one drop per hit. In a crowd other
 * players' damage in the same tick is folded in, so the share is an estimate («≈»). Pure Java, covered by tests.
 */
public final class BossMeter {
    /** Bar fraction (0..1) lost per drop event, and when. */
    private record Drop(long timeMs, float amount) {
    }

    private record Hit(long timeMs, boolean onBoss) {
    }

    private static final int MAX_HITS = 64;
    private static final int MAX_DROPS = 2048;

    private final Deque<Hit> hits = new ArrayDeque<>();
    private final Deque<Drop> myDrops = new ArrayDeque<>();
    private float last = Float.NaN;
    private float total;
    private float mine;
    private int myHits;
    /** A hit on an entity named like the boss was seen: from then on only such hits count. */
    private boolean bossNamedHits;

    /** One of my hits landed; {@code onBoss} when the victim's name matched the boss. */
    public void myHit(long timeMs, boolean onBoss) {
        if (onBoss) {
            bossNamedHits = true;
        }
        hits.addLast(new Hit(timeMs, onBoss));
        while (hits.size() > MAX_HITS) {
            hits.removeFirst();
        }
        myHits++;
    }

    /** The bar's target value now (0..1); a drop since the last value is attributed. Rises (regeneration) are ignored. */
    public void progress(float value, long nowMs, long windowMs) {
        if (Float.isNaN(value)) {
            return;
        }
        float before = last;
        last = value;
        if (Float.isNaN(before) || value >= before) {
            return;
        }
        float drop = before - value;
        total += drop;
        for (Iterator<Hit> it = hits.iterator(); it.hasNext(); ) {
            Hit hit = it.next();
            long age = nowMs - hit.timeMs();
            if (age > windowMs) {
                it.remove();
                continue;
            }
            if (age < 0 || (bossNamedHits && !hit.onBoss())) {
                continue;
            }
            it.remove();
            mine += drop;
            push(myDrops, new Drop(nowMs, drop));
            return;
        }
    }

    private static void push(Deque<Drop> drops, Drop drop) {
        drops.addLast(drop);
        while (drops.size() > MAX_DROPS) {
            drops.removeFirst();
        }
    }

    /** Fraction of the whole bar I took down (0..1). */
    public float mine() {
        return mine;
    }

    /** Fraction of the whole bar that went down while tracked (0..1). */
    public float total() {
        return total;
    }

    public int myHits() {
        return myHits;
    }

    /** My share of what went down while tracked, 0..1; NaN before anything went down. */
    public float share() {
        return total > 0f ? Math.min(1f, mine / total) : Float.NaN;
    }

    /**
     * My bar loss per second over the last {@code windowMs}, in bar fraction per second (multiply by the boss's HP for
     * HP/s). Measured from my first attributed drop inside the window, at least one second.
     */
    public double rate(long nowMs, long windowMs) {
        float sum = 0f;
        long first = Long.MAX_VALUE;
        for (Drop d : myDrops) {
            if (nowMs - d.timeMs() <= windowMs) {
                sum += d.amount();
                first = Math.min(first, d.timeMs());
            }
        }
        if (sum <= 0f) {
            return 0.0;
        }
        double seconds = Math.max(1.0, (nowMs - first) / 1000.0);
        return sum / seconds;
    }

    public float lastValue() {
        return last;
    }
}
