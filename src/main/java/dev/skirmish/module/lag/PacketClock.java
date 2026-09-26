package dev.skirmish.module.lag;

/**
 * Arrival time of the last packet from the server, written on the network thread by {@code ConnectionMixin} and
 * read on the render thread. Pure (nanoTime values are passed in) so the stall rules are unit tested.
 */
public final class PacketClock {
    /** 0 = nothing received on this connection yet. */
    private volatile long lastNanos;
    private volatile long connectedNanos;

    public void onPacket(long nanos) {
        lastNanos = nanos;
    }

    /** New connection: nothing counts as a stall until the first packet (or {@code graceNanos}) has passed. */
    public void reset(long nanos) {
        lastNanos = 0;
        connectedNanos = nanos;
    }

    public boolean hasPackets() {
        return lastNanos != 0;
    }

    /** Milliseconds since the last packet (since connecting when none arrived yet). */
    public long silenceMs(long nowNanos) {
        long last = lastNanos != 0 ? lastNanos : connectedNanos;
        if (last == 0) {
            return 0;
        }
        return Math.max(0, (nowNanos - last) / 1_000_000L);
    }

    /** The server has been silent longer than {@code thresholdMs}: it stalled (or the connection did). */
    public static boolean stalled(long silenceMs, long thresholdMs) {
        return thresholdMs > 0 && silenceMs > thresholdMs;
    }

    /** Whole seconds for the banner: 1.6 s → 1, but never 0 once stalled. */
    public static long bannerSeconds(long silenceMs) {
        return Math.max(1, silenceMs / 1000);
    }
}
