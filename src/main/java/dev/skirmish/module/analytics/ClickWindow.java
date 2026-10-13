package dev.skirmish.module.analytics;

/**
 * Clicks per second: attack attempts inside a sliding window. Keeps the last {@link #CAPACITY} timestamps in a ring
 * (far more than a human clicks per window). Pure Java, unit tested.
 */
public final class ClickWindow {
    static final int CAPACITY = 64;

    private final long windowMs;
    private final long[] times = new long[CAPACITY];
    private int next;
    private int size;

    public ClickWindow(long windowMs) {
        if (windowMs <= 0) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.windowMs = windowMs;
    }

    public void click(long timeMs) {
        times[next] = timeMs;
        next = (next + 1) % CAPACITY;
        size = Math.min(CAPACITY, size + 1);
    }

    /** Clicks within {@code (now - window, now]}. */
    public int count(long nowMs) {
        int n = 0;
        for (int i = 0; i < size; i++) {
            long t = times[(next - 1 - i + CAPACITY) % CAPACITY];
            if (nowMs - t >= windowMs) {
                break;
            }
            if (t <= nowMs) {
                n++;
            }
        }
        return n;
    }

    /** Clicks per second over the window. */
    public double perSecond(long nowMs) {
        return count(nowMs) * 1000.0 / windowMs;
    }

    /** Time of the latest click, or -1. */
    public long lastMs() {
        return size == 0 ? -1 : times[(next - 1 + CAPACITY) % CAPACITY];
    }

    public void clear() {
        size = 0;
        next = 0;
    }
}
