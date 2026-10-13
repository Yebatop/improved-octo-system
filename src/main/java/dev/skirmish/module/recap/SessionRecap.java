package dev.skirmish.module.recap;

import org.jspecify.annotations.Nullable;

/**
 * Totals of one play session (join → disconnect), frozen when taken. Pure Java.
 *
 * @param player        my name
 * @param server        server display name
 * @param startMs       join time
 * @param endMs         disconnect time, or the moment of the snapshot for a session still running
 * @param finished      whether the session ended (disconnect)
 * @param kills         kills attributed to me
 * @param deaths        my deaths
 * @param fights        fights that ended
 * @param wins          fights won by a kill
 * @param totemsPopped  totems my opponents popped in fights with me
 * @param myTotems      my own totems that popped
 * @param best          best fight (see {@link RecapFight#betterThan}), null without fights
 * @param coins         balance change read from the sidebar, null when no balance line was seen
 * @param levels        experience level change, null when never read
 */
public record SessionRecap(String player, String server, long startMs, long endMs, boolean finished, int kills, int deaths,
                           int fights, int wins, int totemsPopped, int myTotems, @Nullable RecapFight best,
                           @Nullable Long coins, @Nullable Integer levels) {
    public long playtimeMs() {
        return Math.max(0, endMs - startMs);
    }

    /** Kills per death; the kill count itself without deaths. */
    public double kd() {
        return deaths == 0 ? kills : kills / (double) deaths;
    }

    public boolean empty() {
        return kills == 0 && deaths == 0 && fights == 0 && myTotems == 0;
    }
}
