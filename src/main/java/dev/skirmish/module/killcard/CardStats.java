package dev.skirmish.module.killcard;

import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;

/**
 * Numbers and names shown on the card, copied from a finished fight on the client thread.
 *
 * @param killerHealth   my health + absorption when the opponent died, -1 when unknown
 * @param damageKnown    false when the server hides the opponent's health: {@code damageDealt} is then meaningless
 * @param damageDealt    sum of observed health + absorption drops after my hits (an estimate)
 * @param attackAttempts my attack attempts during the fight (for the hit rate)
 * @param killerFace     8×8 ARGB face with the hat layer, null when the skin could not be read
 * @param time           end of the fight
 * @param server         display name of the server, null when hidden by the settings
 * @param preview        card made from the settings button, not from a real kill
 */
public record CardStats(String killer, String victim, float killerHealth, boolean damageKnown, float damageDealt,
                        int hitsDealt, int attackAttempts, int totemsPopped, long durationMs, ZonedDateTime time,
                        @Nullable String server, boolean preview, int @Nullable [] killerFace, int @Nullable [] victimFace) {
}
