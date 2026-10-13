package dev.skirmish.module.analytics.review;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * «Кто и как тебя убил»: my killer, the damage I took in the last {@link #WINDOW_MS} before dying and my health
 * over that time.
 *
 * @param killer     killer name, null if nobody could be named
 * @param message    the death message as the server sent it (may be empty)
 * @param damage     damage events on me, oldest first
 * @param hp         my health + absorption, ending with 0 at the death
 * @param maxHp      my max health at the time (without absorption)
 */
public record DeathRecap(long timeMs, @Nullable String killer, @Nullable UUID killerUuid, String message,
                         List<DamageTaken> damage, HpSeries hp, float maxHp) {
    public static final long WINDOW_MS = 10_000;

    /** Sum of the known amounts. */
    public float totalDamage() {
        float sum = 0;
        for (DamageTaken d : damage) {
            if (Float.isFinite(d.amount())) {
                sum += d.amount();
            }
        }
        return sum;
    }

    /** Damage events by the killer. */
    public int hitsByKiller() {
        int n = 0;
        for (DamageTaken d : damage) {
            if (killerUuid != null && killerUuid.equals(d.sourceUuid())) {
                n++;
            }
        }
        return n;
    }
}
