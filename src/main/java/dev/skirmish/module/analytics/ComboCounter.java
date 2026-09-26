package dev.skirmish.module.analytics;

/**
 * My consecutive hits without being hit. A hit taken resets the combo; so does a pause longer than the reset time
 * between two of my hits. Pure Java, unit tested.
 */
public final class ComboCounter {
    private long resetAfterMs;
    private int current;
    private int best;
    private long lastHitMs = -1;

    public ComboCounter(long resetAfterMs) {
        this.resetAfterMs = resetAfterMs;
    }

    public void setResetAfterMs(long resetAfterMs) {
        this.resetAfterMs = resetAfterMs;
    }

    /** One of my hits landed. Returns the combo after it. */
    public int hit(long timeMs) {
        if (current > 0 && expired(timeMs)) {
            current = 0;
        }
        current++;
        best = Math.max(best, current);
        lastHitMs = timeMs;
        return current;
    }

    /** I was hit. */
    public void taken(long timeMs) {
        current = 0;
    }

    /** The combo as of {@code nowMs} (0 once the reset time passed since my last hit). */
    public int current(long nowMs) {
        return current > 0 && expired(nowMs) ? 0 : current;
    }

    /** Longest combo since the last {@link #reset}. */
    public int best() {
        return best;
    }

    public long lastHitMs() {
        return lastHitMs;
    }

    private boolean expired(long nowMs) {
        return resetAfterMs > 0 && lastHitMs >= 0 && nowMs - lastHitMs > resetAfterMs;
    }

    public void reset() {
        current = 0;
        best = 0;
        lastHitMs = -1;
    }
}
