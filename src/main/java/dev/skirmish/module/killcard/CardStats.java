package dev.skirmish.module.killcard;

import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;

/**
 * Numbers and names shown on the card, copied from a finished fight on the client thread.
 *
 * @param damageKnown  false when the server hides the opponent's health: {@code damageDealt} is then meaningless
 * @param damageDealt  sum of observed health + absorption drops after my hits (an estimate)
 * @param time         end of the fight
 * @param server       display name of the server, null when hidden by the settings
 * @param preview      card made from the settings button, not from a real kill
 */
public record CardStats(String killer, String victim, boolean damageKnown, float damageDealt, int hitsDealt, int hitsTaken,
                        int totemsPopped, long durationMs, ZonedDateTime time, @Nullable String server, boolean preview) {
}
