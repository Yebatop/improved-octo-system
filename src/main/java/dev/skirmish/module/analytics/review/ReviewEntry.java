package dev.skirmish.module.analytics.review;

import dev.skirmish.combat.EquipmentSnapshot;
import dev.skirmish.combat.Fight;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * One row of the review list: a finished fight with its timeline, plus the death recap when the fight ended with my
 * death; or a death that happened outside any fight (then {@link #fight()} is null).
 */
public final class ReviewEntry {
    private final @Nullable Fight fight;
    private final @Nullable FightLog log;
    private final @Nullable EquipmentSnapshot gear;
    private final @Nullable DeathRecap death;
    private final ItemStack killerWeapon;
    private final long timeMs;

    ReviewEntry(@Nullable Fight fight, @Nullable FightLog log, @Nullable EquipmentSnapshot gear, @Nullable DeathRecap death,
                ItemStack killerWeapon, long timeMs) {
        this.fight = fight;
        this.log = log;
        this.gear = gear;
        this.death = death;
        this.killerWeapon = killerWeapon;
        this.timeMs = timeMs;
    }

    public @Nullable Fight fight() {
        return fight;
    }

    public @Nullable FightLog log() {
        return log;
    }

    /** Opponent equipment as last seen in the fight. */
    public @Nullable EquipmentSnapshot gear() {
        return gear;
    }

    public @Nullable DeathRecap death() {
        return death;
    }

    /** What my killer held at my death (empty when unknown). */
    public ItemStack killerWeapon() {
        return killerWeapon;
    }

    /** When the fight (or the death) ended. */
    public long timeMs() {
        return timeMs;
    }

    public @Nullable UUID opponentUuid() {
        if (fight != null) {
            return fight.opponent().uuid();
        }
        return death == null ? null : death.killerUuid();
    }

    /** Opponent name; for a death outside a fight the killer, or null. */
    public @Nullable String opponentName() {
        if (fight != null) {
            return fight.opponent().name();
        }
        return death == null ? null : death.killer();
    }

    public boolean killedByOpponent() {
        return fight != null && death != null && death.killerUuid() != null && death.killerUuid().equals(fight.opponent().uuid());
    }

    public Outcome outcome() {
        return Outcome.of(fight == null ? null : fight.endReason(), killedByOpponent());
    }

    /** Worth a hint: a kill, a death, or at least a few hits either way. */
    public boolean significant(int minHits) {
        if (death != null || fight == null) {
            return true;
        }
        Outcome outcome = outcome();
        return outcome == Outcome.WIN || fight.hitsDealt() + fight.hitsTaken() >= minHits;
    }
}
