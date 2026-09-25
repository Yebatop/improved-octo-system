package dev.skirmish.module.recap;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * One finished fight of the session, copied from the combat tracker's {@code Fight} when it ended. Pure Java.
 *
 * @param opponent       opponent name
 * @param opponentUuid   opponent id (for the dossier line), null in tests
 * @param damage         my damage (health drops after my hits), meaningful only when {@code damageKnown}
 * @param damageKnown    whether the server sent the opponent's real health
 * @param hitsDealt      my hits that landed
 * @param hitsTaken      hits I took
 * @param opponentTotems totems the opponent popped
 * @param durationMs     fight length
 * @param result         how it ended
 */
public record RecapFight(String opponent, @Nullable UUID opponentUuid, float damage, boolean damageKnown, int hitsDealt,
                         int hitsTaken, int opponentTotems, long durationMs, Result result) {
    public enum Result {
        WIN, LOSS, OTHER
    }

    /**
     * Whether this fight beats {@code other} for «best fight»: a fight with known damage beats one without; then more
     * damage; then more hits; then more totems popped; ties keep the earlier one.
     */
    public boolean betterThan(@Nullable RecapFight other) {
        if (other == null) {
            return true;
        }
        if (damageKnown != other.damageKnown) {
            return damageKnown;
        }
        if (damageKnown && Float.compare(damage, other.damage) != 0) {
            return damage > other.damage;
        }
        if (hitsDealt != other.hitsDealt) {
            return hitsDealt > other.hitsDealt;
        }
        return opponentTotems > other.opponentTotems;
    }
}
