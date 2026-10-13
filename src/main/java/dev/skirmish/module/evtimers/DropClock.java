package dev.skirmish.module.evtimers;

/**
 * Sun Core drops: the wiki says loot falls every 20–60 s. Items that appear at the core within {@code GROUP_MS} of
 * each other are one drop. Shows the time since the last drop and how far into the 20–60 s window it is. Pure Java.
 */
final class DropClock {
    static final long MIN_GAP_MS = 20_000L;
    static final long MAX_GAP_MS = 60_000L;
    private static final long GROUP_MS = 1_500L;

    private long last = -1;
    private int drops;
    private long gapSum;
    private int gaps;

    /** An item appeared at the core; returns true when it starts a new drop. */
    boolean onItem(long now) {
        if (last >= 0 && now - last < GROUP_MS) {
            return false;
        }
        if (last >= 0) {
            long gap = now - last;
            if (gap <= MAX_GAP_MS * 2) {
                gapSum += gap;
                gaps++;
            }
        }
        last = now;
        drops++;
        return true;
    }

    boolean seen() {
        return last >= 0;
    }

    long sinceMs(long now) {
        return last < 0 ? -1 : now - last;
    }

    int drops() {
        return drops;
    }

    /** Mean gap between drops seen this session, or -1 with fewer than one gap. */
    long meanGapMs() {
        return gaps == 0 ? -1 : gapSum / gaps;
    }

    /** 0 until 20 s after the last drop, rising to 1 at 60 s (the next drop is due somewhere in between). */
    static float window(long sinceMs) {
        if (sinceMs <= MIN_GAP_MS) {
            return 0f;
        }
        return Math.min(1f, (sinceMs - MIN_GAP_MS) / (float) (MAX_GAP_MS - MIN_GAP_MS));
    }

    void reset() {
        last = -1;
        drops = 0;
        gapSum = 0;
        gaps = 0;
    }
}
