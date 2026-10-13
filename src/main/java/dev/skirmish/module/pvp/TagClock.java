package dev.skirmish.module.pvp;

/**
 * Time bookkeeping for the combat-tag ring. Pure Java, times in wall-clock ms.
 * <ul>
 *     <li>Server mode: the board shows whole seconds; {@link #serverSeconds} smooths them between updates and keeps
 *     the peak since the last reset (a hit restarts the tag, so the peak is the full duration) for the ring.</li>
 *     <li>Local mode: a timer of the configured length restarted by {@link #hit}.</li>
 * </ul>
 */
final class TagClock {
    private int shown = -1;
    private long shownSince;
    private int peak;
    private long lastHitMs = -1;

    /** Feeds the server's current value (whole seconds). */
    void serverSeconds(int seconds, long nowMs) {
        if (seconds != shown) {
            if (shown < 0 || seconds > shown) {
                peak = seconds;
            }
            shown = seconds;
            shownSince = nowMs;
        }
    }

    void clearServer() {
        shown = -1;
        peak = 0;
    }

    /** Server time left in seconds, decreasing smoothly within the current whole second. */
    float serverRemaining(long nowMs) {
        if (shown < 0) {
            return 0f;
        }
        float elapsed = (nowMs - shownSince) / 1000f;
        return Math.max(Math.max(0f, shown - 1f), shown - elapsed);
    }

    /** Ring fill in [0, 1] for server mode. */
    float serverFraction(long nowMs) {
        return peak <= 0 ? 0f : Math.min(1f, serverRemaining(nowMs) / peak);
    }

    /** A PvP hit involving the local player: restarts the local timer. */
    void hit(long nowMs) {
        lastHitMs = nowMs;
    }

    void clearLocal() {
        lastHitMs = -1;
    }

    /** Local time left in seconds, 0 when no hit or expired. */
    float localRemaining(long nowMs, long durationMs) {
        if (lastHitMs < 0) {
            return 0f;
        }
        return Math.max(0f, (durationMs - (nowMs - lastHitMs)) / 1000f);
    }
}
