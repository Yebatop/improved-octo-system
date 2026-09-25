package dev.skirmish.module.recap;

import org.jspecify.annotations.Nullable;

/**
 * Adds up one session: kills, deaths, fights, totems, the best fight, and the first and last balance and experience
 * level seen. Fed by {@link SessionRecapModule} on the client thread. Pure Java, covered by tests.
 */
public final class RecapTracker {
    private boolean active;
    private String player = "";
    private String server = "";
    private long startMs;
    private int kills;
    private int deaths;
    private int fights;
    private int wins;
    private int totemsPopped;
    private int myTotems;
    private @Nullable RecapFight best;
    private @Nullable Long firstBalance;
    private @Nullable Long lastBalance;
    private @Nullable Integer firstLevel;
    private @Nullable Integer lastLevel;

    /** Starts a new session (everything counted so far is dropped). */
    public void begin(long nowMs, String player, String server) {
        active = true;
        this.player = player;
        this.server = server;
        startMs = nowMs;
        kills = 0;
        deaths = 0;
        fights = 0;
        wins = 0;
        totemsPopped = 0;
        myTotems = 0;
        best = null;
        firstBalance = null;
        lastBalance = null;
        firstLevel = null;
        lastLevel = null;
    }

    public boolean active() {
        return active;
    }

    /** Ends the session and returns its totals; null when none was running. */
    public @Nullable SessionRecap end(long nowMs) {
        if (!active) {
            return null;
        }
        SessionRecap recap = snapshot(nowMs, true);
        active = false;
        return recap;
    }

    public SessionRecap snapshot(long nowMs, boolean finished) {
        Long coins = firstBalance == null || lastBalance == null ? null : lastBalance - firstBalance;
        Integer levels = firstLevel == null || lastLevel == null ? null : lastLevel - firstLevel;
        return new SessionRecap(player, server, startMs, Math.max(startMs, nowMs), finished, kills, deaths, fights, wins,
                totemsPopped, myTotems, best, coins, levels);
    }

    public void kill() {
        if (active) {
            kills++;
        }
    }

    public void death() {
        if (active) {
            deaths++;
        }
    }

    public void myTotem() {
        if (active) {
            myTotems++;
        }
    }

    /**
     * A fight ended. {@code shown} false (the opponent stayed invisible to me the whole fight) still counts the totals
     * but never makes it the named best fight.
     */
    public void fightEnded(RecapFight fight, boolean shown) {
        if (!active) {
            return;
        }
        fights++;
        if (fight.result() == RecapFight.Result.WIN) {
            wins++;
        }
        totemsPopped += fight.opponentTotems();
        if (shown && fight.hitsDealt() > 0 && fight.betterThan(best)) {
            best = fight;
        }
    }

    /** Balance as shown on the sidebar. */
    public void balance(long value) {
        if (!active) {
            return;
        }
        if (firstBalance == null) {
            firstBalance = value;
        }
        lastBalance = value;
    }

    /** My experience level. */
    public void level(int value) {
        if (!active) {
            return;
        }
        if (firstLevel == null) {
            firstLevel = value;
        }
        lastLevel = value;
    }
}
