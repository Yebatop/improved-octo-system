package dev.skirmish.module.analytics.review;

import dev.skirmish.combat.Fight;
import dev.skirmish.combat.FightEndReason;

/** Derived numbers of an entry that several views share. */
final class ReviewText {
    private ReviewText() {
    }

    static boolean endsWithDeath(Fight fight) {
        FightEndReason r = fight.endReason();
        return r == FightEndReason.KILL || r == FightEndReason.OWN_DEATH || r == FightEndReason.OPPONENT_DIED;
    }

    /** Time actually fought (a timeout ends long after the last hit). */
    static long activeDuration(ReviewEntry e) {
        Fight f = e.fight();
        if (f == null) {
            return 0;
        }
        return FightStats.activeDurationMs(f.startMs(), f.endMs(), f.lastActivityMs(), endsWithDeath(f));
    }

    /** End of the time axis of the fight's charts. */
    static long axisEnd(ReviewEntry e) {
        Fight f = e.fight();
        if (f == null) {
            return e.timeMs();
        }
        FightLog log = e.log();
        long lastEvent = log == null ? f.lastActivityMs() : Math.max(log.lastEventMs(), f.lastActivityMs());
        return FightStats.axisEnd(f.startMs(), f.endMs(), lastEvent, endsWithDeath(f));
    }
}
