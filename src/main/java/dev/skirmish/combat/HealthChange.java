package dev.skirmish.combat;

import org.jspecify.annotations.Nullable;

/**
 * A decrease of health + absorption observed through synced entity data.
 *
 * @param attributedTo the attacker of the damage event that preceded this drop (within a short window), if any
 */
public record HealthChange(Combatant entity, float oldHealth, float oldAbsorption, float newHealth, float newAbsorption,
                           float maxHealth, @Nullable Combatant attributedTo, long timeMs) {
    public float lost() {
        return (oldHealth + oldAbsorption) - (newHealth + newAbsorption);
    }
}
