package dev.skirmish.module.lag;

import java.util.ArrayDeque;

/**
 * Server TPS from world time updates. The server sends {@code ClientboundSetTimePacket} every 20 of its ticks with
 * its game time, so over a window {@code TPS = Δ game ticks / Δ wall seconds} (arrival times on the network thread).
 * Pure and synchronized: samples come from the network thread, reads from the render thread.
 */
public final class TpsEstimator {
    public static final double MAX_TPS = 20.0;
    /** Samples older than this are dropped from the average. */
    private final long windowNanos;
    /** Fewer samples than this give no estimate yet. */
    private static final int MIN_SAMPLES = 2;
    private static final int MAX_SAMPLES = 64;

    private record Sample(long nanos, long gameTime) {
    }

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();

    public TpsEstimator(long windowNanos) {
        this.windowNanos = windowNanos;
    }

    /** One time packet: arrival time (System.nanoTime) and the server's game time. */
    public synchronized void onTime(long nanos, long gameTime) {
        Sample last = samples.peekLast();
        if (last != null && (gameTime < last.gameTime() || nanos < last.nanos())) {
            // World change or server restart: the game time jumped back.
            samples.clear();
        }
        samples.addLast(new Sample(nanos, gameTime));
        while (samples.size() > MAX_SAMPLES || (samples.size() > MIN_SAMPLES && nanos - samples.peekFirst().nanos() > windowNanos)) {
            samples.pollFirst();
        }
    }

    public synchronized void reset() {
        samples.clear();
    }

    /**
     * Average TPS over the window, clamped to [0, 20] (a server catching up after a stall briefly runs faster);
     * NaN while there are too few samples. {@code nowNanos} counts the time since the last packet as ticks not
     * made yet, so a stalled server reads low before the next packet arrives.
     */
    public synchronized double tps(long nowNanos) {
        if (samples.size() < MIN_SAMPLES) {
            return Double.NaN;
        }
        Sample first = samples.peekFirst();
        Sample last = samples.peekLast();
        long ticks = last.gameTime() - first.gameTime();
        long spanNanos = last.nanos() - first.nanos();
        // A pause longer than the usual interval is part of the measurement too.
        long overdue = Math.max(0, nowNanos - last.nanos() - interval());
        spanNanos += overdue;
        if (spanNanos <= 0) {
            return Double.NaN;
        }
        double tps = ticks / (spanNanos / 1e9);
        return Math.max(0.0, Math.min(MAX_TPS, tps));
    }

    /** Average spacing of the samples (≈ 1 s at 20 TPS). */
    private long interval() {
        Sample first = samples.peekFirst();
        Sample last = samples.peekLast();
        int gaps = samples.size() - 1;
        return gaps <= 0 ? 1_000_000_000L : (last.nanos() - first.nanos()) / gaps;
    }

    public synchronized int size() {
        return samples.size();
    }
}
