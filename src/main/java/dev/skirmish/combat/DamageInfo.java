package dev.skirmish.combat;

import org.jspecify.annotations.Nullable;

/**
 * One server-confirmed damage event ({@code ClientboundDamageEventPacket}).
 *
 * @param attacker   the causing entity (shooter for projectiles), null for environmental damage
 * @param direct     the direct entity (the arrow itself), null when absent
 * @param damageType damage type id, e.g. {@code minecraft:player_attack}
 * @param timeMs     wall clock of the event
 */
public record DamageInfo(Combatant victim, @Nullable Combatant attacker, @Nullable Combatant direct, String damageType, long timeMs) {
    public boolean byMe() {
        return attacker != null && attacker.self();
    }

    public boolean onMe() {
        return victim.self();
    }
}
