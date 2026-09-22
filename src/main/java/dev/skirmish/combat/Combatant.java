package dev.skirmish.combat;

import java.util.UUID;

/**
 * Minecraft-free view of an entity taking part in combat.
 *
 * @param entityId client entity id at the time of the event (changes when the entity re-enters tracking)
 * @param uuid     stable identity, used to key fights
 * @param name     plain name (player nick for players)
 * @param player   whether the entity is a player
 * @param self     whether it is the local player
 */
public record Combatant(int entityId, UUID uuid, String name, boolean player, boolean self) {
}
