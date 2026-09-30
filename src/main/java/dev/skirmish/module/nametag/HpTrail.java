package dev.skirmish.module.nametag;

/**
 * The health bar's motion for one player: the fill follows health quickly, and after a loss a lighter "damage trail"
 * stays at the old value for a moment and then drains down to the new one, like fighting-game health bars. Pure
 * (time passed in), so it is unit tested.
 */
public final class HpTrail {
    /** How long the trail holds before draining, and how long draining takes (ms). */
    static final long HOLD_MS = 350;
    static final long DRAIN_MS = 450;
    /** Time for the fill to catch up with a new value (ms). */
    static final long FILL_MS = 120;

    private float target = -1f;
    private float fillFrom;
    private long fillAt;
    private float trailFrom;
    private long hitAt;

    /** Feed the current health fraction; returns this. */
    public HpTrail update(float fraction, long now) {
        if (target < 0f) {
            target = fraction;
            fillFrom = fraction;
            trailFrom = fraction;
            fillAt = now - FILL_MS;
            hitAt = now - HOLD_MS - DRAIN_MS;
            return this;
        }
        if (fraction != target) {
            float shownFill = fill(now);
            if (fraction < target) {
                // Keep the trail where it is if it is still draining, so combos stack into one long trail.
                trailFrom = Math.max(trail(now), shownFill);
                hitAt = now;
            }
            fillFrom = shownFill;
            fillAt = now;
            target = fraction;
        }
        return this;
    }

    /** Filled part of the bar. */
    public float fill(long now) {
        float t = Math.min(1f, Math.max(0f, (now - fillAt) / (float) FILL_MS));
        return fillFrom + (target - fillFrom) * easeOut(t);
    }

    /** End of the damage trail (at least the fill). */
    public float trail(long now) {
        long since = now - hitAt;
        if (since <= HOLD_MS) {
            return Math.max(trailFrom, target);
        }
        float t = Math.min(1f, (since - HOLD_MS) / (float) DRAIN_MS);
        return Math.max(target, trailFrom + (target - trailFrom) * easeOut(t));
    }

    /** 0..1: how fresh the last hit is (for a short flash), 0 once the trail has drained. */
    public float hitFlash(long now) {
        long since = now - hitAt;
        return since >= HOLD_MS ? 0f : 1f - since / (float) HOLD_MS;
    }

    private static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }
}
