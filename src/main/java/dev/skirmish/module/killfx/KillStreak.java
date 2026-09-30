package dev.skirmish.module.killfx;

/**
 * My kills since my last death (and the best run of the session). A kill of the same player twice within a moment
 * is one kill (the tracker may report a death twice across a relog). Pure Java, unit tested.
 */
final class KillStreak {
    /** Two kill reports of the same victim this close together are one kill. */
    static final long DUPLICATE_MS = 3_000;

    private int current;
    private int best;
    private String lastVictim = "";
    private long lastKillMs = -1;

    /** Counts a kill and returns the streak including it. */
    int kill(String victim, long now) {
        if (victim.equalsIgnoreCase(lastVictim) && lastKillMs >= 0 && now - lastKillMs < DUPLICATE_MS) {
            return current;
        }
        current++;
        best = Math.max(best, current);
        lastVictim = victim;
        lastKillMs = now;
        return current;
    }

    /** I died: the streak starts over. */
    void death() {
        current = 0;
        lastVictim = "";
        lastKillMs = -1;
    }

    /** New session (disconnect): everything starts over. */
    void reset() {
        death();
        best = 0;
    }

    int current() {
        return current;
    }

    int best() {
        return best;
    }
}
