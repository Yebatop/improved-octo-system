package dev.skirmish.module.analytics.review;

import dev.skirmish.module.analytics.ReachMath;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Numbers of the review screen computed from a {@link FightLog}. Pure Java, unit tested. */
public final class FightStats {
    /** Space after the last event on the timeline when the fight had no final moment (timeout, world change). */
    public static final long TAIL_MS = 1_000;

    private FightStats() {
    }

    /** Longest run of my hits with none of theirs in between (totems do not break it). */
    public static int longestCombo(List<FightLog.Event> events) {
        int run = 0;
        int best = 0;
        for (FightLog.Event e : events) {
            if (e.kind() == FightLog.Kind.MY_HIT) {
                run++;
                best = Math.max(best, run);
            } else if (e.kind() == FightLog.Kind.THEIR_HIT) {
                run = 0;
            }
        }
        return best;
    }

    public static int count(List<FightLog.Event> events, FightLog.Kind kind) {
        int n = 0;
        for (FightLog.Event e : events) {
            if (e.kind() == kind) {
                n++;
            }
        }
        return n;
    }

    public static int crits(List<FightLog.Event> events, boolean mine) {
        int n = 0;
        for (FightLog.Event e : events) {
            if (e.crit() && e.kind() == (mine ? FightLog.Kind.MY_HIT : FightLog.Kind.THEIR_HIT)) {
                n++;
            }
        }
        return n;
    }

    /** Reach of each of my hits (NaN where unknown). */
    public static double[] reaches(List<FightLog.Event> events) {
        return events.stream().filter(e -> e.kind() == FightLog.Kind.MY_HIT).mapToDouble(FightLog.Event::reach).toArray();
    }

    public static double averageReach(List<FightLog.Event> events) {
        return ReachMath.average(reaches(events));
    }

    public static double maxReach(List<FightLog.Event> events) {
        return ReachMath.max(reaches(events));
    }

    /** Who landed the first hit: {@code true} me, {@code false} them, null without hits. */
    public static @Nullable Boolean iHitFirst(List<FightLog.Event> events) {
        for (FightLog.Event e : events) {
            if (e.kind().hit()) {
                return e.kind() == FightLog.Kind.MY_HIT;
            }
        }
        return null;
    }

    /**
     * End of the time axis: the fight's end when it ended with a death, else the last event plus {@link #TAIL_MS}
     * (a timeout ends long after the last hit), never before {@code start + 1 s}.
     */
    public static long axisEnd(long startMs, long endMs, long lastEventMs, boolean endsWithDeath) {
        long end = endsWithDeath && endMs >= startMs ? Math.max(endMs, lastEventMs) : lastEventMs + TAIL_MS;
        if (endMs >= startMs && !endsWithDeath) {
            end = Math.min(end, Math.max(endMs, lastEventMs));
        }
        return Math.max(end, startMs + 1_000);
    }

    /** Time actually fought: until the final moment, or until the last hit for a timeout. */
    public static long activeDurationMs(long startMs, long endMs, long lastActivityMs, boolean endsWithDeath) {
        long end = endsWithDeath ? endMs : Math.min(endMs >= 0 ? endMs : lastActivityMs, lastActivityMs);
        return Math.max(0, end - startMs);
    }
}
