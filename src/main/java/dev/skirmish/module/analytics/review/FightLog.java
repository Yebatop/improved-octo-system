package dev.skirmish.module.analytics.review;

import dev.skirmish.combat.Combatant;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Timeline of one fight for the review screen: my hits (with reach, crit and the health the target lost), their
 * hits on me, totems of both sides, and both health curves. Filled on the client thread by {@link FightRecorder};
 * pure Java, unit tested.
 */
public final class FightLog {
    /** A crit animation and the hit it belongs to arrive in the same tick; either order is accepted. */
    public static final long CRIT_WINDOW_MS = 250;
    /** A health drop belongs to a hit at most this long before it (as in the combat tracker). */
    public static final long DAMAGE_WINDOW_MS = 600;
    static final int MAX_EVENTS = 2_000;

    public enum Kind {
        MY_HIT, THEIR_HIT, MY_TOTEM, THEIR_TOTEM;

        public boolean mine() {
            return this == MY_HIT || this == MY_TOTEM;
        }

        public boolean hit() {
            return this == MY_HIT || this == THEIR_HIT;
        }
    }

    public static final class Event {
        private final Kind kind;
        private final long timeMs;
        private final double reach;
        private float damage = Float.NaN;
        private boolean crit;

        Event(Kind kind, long timeMs, double reach) {
            this.kind = kind;
            this.timeMs = timeMs;
            this.reach = reach;
        }

        public Kind kind() {
            return kind;
        }

        public long timeMs() {
            return timeMs;
        }

        /** Eye-to-hitbox distance at my click (my hits only), NaN if unknown. */
        public double reach() {
            return reach;
        }

        /** Health + absorption the victim lost right after the hit, NaN if not seen. */
        public float damage() {
            return damage;
        }

        public boolean crit() {
            return crit;
        }
    }

    private final int fightId;
    private final Combatant opponent;
    private final long startMs;
    private final List<Event> events = new ArrayList<>();
    private final HpSeries myHp = new HpSeries(512, 500);
    private final HpSeries theirHp = new HpSeries(512, 500);
    private float myMaxHp;
    private float theirMaxHp;
    private long pendingMyCritMs = -1;
    private long pendingTheirCritMs = -1;

    public FightLog(int fightId, Combatant opponent, long startMs) {
        this.fightId = fightId;
        this.opponent = opponent;
        this.startMs = startMs;
    }

    public int fightId() {
        return fightId;
    }

    public Combatant opponent() {
        return opponent;
    }

    public long startMs() {
        return startMs;
    }

    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }

    public HpSeries myHp() {
        return myHp;
    }

    public HpSeries theirHp() {
        return theirHp;
    }

    public float myMaxHp() {
        return myMaxHp;
    }

    public float theirMaxHp() {
        return theirMaxHp;
    }

    /** Health sample of me ({@code mine}) or the opponent. */
    public void sample(boolean mine, long timeMs, float hp, float maxHp) {
        if (mine) {
            myHp.add(timeMs, hp);
            myMaxHp = Math.max(myMaxHp, maxHp);
        } else {
            theirHp.add(timeMs, hp);
            theirMaxHp = Math.max(theirMaxHp, maxHp);
        }
    }

    /** A hit: mine on the opponent or theirs on me. A crit seen just before it is applied. */
    public @Nullable Event hit(boolean mine, long timeMs, double reach) {
        if (events.size() >= MAX_EVENTS) {
            return null;
        }
        Event e = new Event(mine ? Kind.MY_HIT : Kind.THEIR_HIT, timeMs, mine ? reach : Double.NaN);
        long pending = mine ? pendingMyCritMs : pendingTheirCritMs;
        if (pending >= 0 && timeMs - pending <= CRIT_WINDOW_MS && timeMs >= pending) {
            e.crit = true;
        }
        if (mine) {
            pendingMyCritMs = -1;
        } else {
            pendingTheirCritMs = -1;
        }
        events.add(e);
        return e;
    }

    /**
     * Crit particles on the opponent ({@code mine}) or on me. Marks the latest hit of that side within
     * {@link #CRIT_WINDOW_MS}; otherwise remembers it for the next hit. Returns whether a hit was marked.
     */
    public boolean crit(boolean mine, long timeMs) {
        Event last = last(mine ? Kind.MY_HIT : Kind.THEIR_HIT);
        if (last != null && !last.crit && Math.abs(timeMs - last.timeMs) <= CRIT_WINDOW_MS) {
            last.crit = true;
            return true;
        }
        if (mine) {
            pendingMyCritMs = timeMs;
        } else {
            pendingTheirCritMs = timeMs;
        }
        return false;
    }

    /** Health lost by the opponent ({@code mine}: after my hit) or by me; goes to the latest hit without an amount. */
    public boolean damage(boolean mine, float amount, long timeMs) {
        if (!(amount > 0)) {
            return false;
        }
        Event last = last(mine ? Kind.MY_HIT : Kind.THEIR_HIT);
        if (last == null || !Float.isNaN(last.damage) || timeMs - last.timeMs > DAMAGE_WINDOW_MS || timeMs < last.timeMs) {
            return false;
        }
        last.damage = amount;
        return true;
    }

    public void totem(boolean mine, long timeMs) {
        if (events.size() < MAX_EVENTS) {
            events.add(new Event(mine ? Kind.MY_TOTEM : Kind.THEIR_TOTEM, timeMs, Double.NaN));
        }
    }

    private @Nullable Event last(Kind kind) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).kind == kind) {
                return events.get(i);
            }
        }
        return null;
    }

    /** Time of the latest event, or the start. */
    public long lastEventMs() {
        return events.isEmpty() ? startMs : events.getLast().timeMs;
    }
}
