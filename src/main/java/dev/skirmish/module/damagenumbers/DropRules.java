package dev.skirmish.module.damagenumbers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Turns synced health into numbers: the change of health + absorption since the last sample, what color it gets
 * and whether "only my hits" lets it through. Times are ms; -1 means "never". Pure Java, unit tested.
 */
final class DropRules {
    /** Smaller changes are rounding noise of the synced floats. */
    static final float MIN_CHANGE = 0.05f;
    /** My click → the server's damage event → the synced health: a drop this soon after my damage event is mine. */
    static final long MY_HIT_WINDOW_MS = 750;
    /** Heals are shown under "only my hits" for entities I hit this recently (my opponent regenerating). */
    static final long MY_TARGET_MS = 15_000;
    /** Crit particles and the health drop of the same hit arrive within a tick or two of each other. */
    static final long CRIT_WINDOW_MS = 400;
    /** A totem pop sets health to 1 in the same tick; later absorption arrives with the effects. */
    static final long TOTEM_WINDOW_MS = 1_000;

    private final Map<Integer, Float> last = new HashMap<>();

    /** Change of {@code total} (health + absorption) since the last sample of this entity; NaN on the first. */
    float sample(int entityId, float total) {
        Float previous = last.put(entityId, total);
        if (previous == null) {
            return Float.NaN;
        }
        float delta = total - previous;
        return Math.abs(delta) < MIN_CHANGE ? 0f : delta;
    }

    void retain(IntPredicate present) {
        last.keySet().removeIf(id -> !present.test(id));
    }

    void forget(int entityId) {
        last.remove(entityId);
    }

    void clear() {
        last.clear();
    }

    int size() {
        return last.size();
    }

    static NumberField.Kind kindOfDrop(long now, long critMs, long totemMs) {
        if (totemMs >= 0 && now - totemMs <= TOTEM_WINDOW_MS) {
            return NumberField.Kind.TOTEM;
        }
        if (critMs >= 0 && now - critMs <= CRIT_WINDOW_MS) {
            return NumberField.Kind.CRIT;
        }
        return NumberField.Kind.HIT;
    }

    /** Whether a drop may be shown: always, or with "only my hits" when my damage event on it just happened. */
    static boolean showDrop(boolean onlyMine, long now, long myHitMs) {
        return !onlyMine || myHitMs >= 0 && now - myHitMs <= MY_HIT_WINDOW_MS;
    }

    static boolean showHeal(boolean heals, boolean onlyMine, long now, long myHitMs) {
        return heals && (!onlyMine || myHitMs >= 0 && now - myHitMs <= MY_TARGET_MS);
    }
}
