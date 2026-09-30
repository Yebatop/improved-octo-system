package dev.skirmish.module.hwtimers;

import dev.skirmish.module.hwtimers.TimerTable.TimerDef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The timers running on the local player, one chip per table entry. Triggers from several sources (chat, own
 * effects, block bursts) merge into the same chip: the later end wins unless a line names the exact time left.
 * Pure Java, driven by wall-clock milliseconds; covered by tests.
 */
public final class TimerBoard {
    /** Where a chip's latest trigger came from. */
    public enum Source {
        CHAT, EFFECT, BLOCKS, EXPLOSION, TOTEM, BOSS_BAR
    }

    public static final class Chip {
        private final TimerDef def;
        private long startMs;
        private long endMs;
        private Source source;
        private boolean guess;
        private final Set<Long> positions = new HashSet<>();
        private int trackedPositions;

        Chip(TimerDef def, long startMs, long endMs, Source source, boolean guess) {
            this.def = def;
            this.startMs = startMs;
            this.endMs = endMs;
            this.source = source;
            this.guess = guess;
        }

        public TimerDef def() {
            return def;
        }

        public String id() {
            return def.id();
        }

        public long startMs() {
            return startMs;
        }

        public long endMs() {
            return endMs;
        }

        public Source source() {
            return source;
        }

        /** Recognised by a heuristic or an unconfirmed server string only. */
        public boolean guess() {
            return guess;
        }

        public boolean countsDown() {
            return def.countsDown();
        }

        public long remainingMs(long now) {
            return Math.max(0, endMs - now);
        }

        public long elapsedMs(long now) {
            return Math.max(0, now - startMs);
        }

        /** Remaining share of the countdown in [0, 1]; 1 for open-ended chips. */
        public float fraction(long now) {
            if (!countsDown()) {
                return 1f;
            }
            long total = Math.max(1, endMs - startMs);
            return Math.max(0f, Math.min(1f, (float) remainingMs(now) / total));
        }

        /** Block positions ({@code BlockPos.asLong}) the chip belongs to: the ice sphere, the box, raid-blocked spots. */
        public Set<Long> positions() {
            return positions;
        }

        /** How many positions were first recorded (for "most of it is gone"). */
        public int trackedPositions() {
            return trackedPositions;
        }
    }

    private final Map<String, Chip> chips = new LinkedHashMap<>();

    /**
     * Starts the timer or merges into the running one. {@code durationMs} null uses the table's length (or its
     * {@code max_seconds} bound for open-ended timers); {@code exact} makes a named time-left replace the end even
     * when shorter.
     */
    public Chip start(TimerDef def, long now, @Nullable Long durationMs, Source source, boolean guess, boolean exact) {
        long length = durationMs != null ? Math.max(0, durationMs)
                : (def.countsDown() ? def.seconds() : def.maxSeconds()) * 1000L;
        long end = now + length;
        // A countdown that is already under way (effect time left, "осталось 3:12") keeps its full length as the
        // ring's total, so the ring shows the real share left.
        long full = def.seconds() * 1000L;
        long start = def.countsDown() && length <= full ? end - full : now;
        Chip chip = chips.get(def.id());
        if (chip == null || chip.endMs <= now) {
            chip = new Chip(def, start, end, source, guess);
            chips.put(def.id(), chip);
            return chip;
        }
        if ((exact && def.countsDown()) || end > chip.endMs) {
            chip.endMs = end;
            if (def.countsDown()) {
                chip.startMs = start;
            }
        }
        chip.source = source;
        chip.guess = chip.guess && guess;
        return chip;
    }

    /** Keeps a status chip (boss bar, open-ended) alive until {@code untilMs}. */
    public Chip hold(TimerDef def, long now, long untilMs, Source source, boolean guess) {
        Chip chip = chips.get(def.id());
        if (chip == null || chip.endMs <= now) {
            chip = new Chip(def, now, untilMs, source, guess);
            chips.put(def.id(), chip);
        } else {
            chip.endMs = Math.max(chip.endMs, untilMs);
        }
        return chip;
    }

    /** Adds positions to a running chip (they count as its original size). */
    public void track(Chip chip, Collection<Long> positions) {
        for (Long p : positions) {
            if (chip.positions.add(p)) {
                chip.trackedPositions++;
            }
        }
    }

    public void end(String id) {
        chips.remove(id);
    }

    public @Nullable Chip get(String id) {
        return chips.get(id);
    }

    public void clear() {
        chips.clear();
    }

    /** Drops chips whose time is up. */
    public void expire(long now) {
        chips.values().removeIf(chip -> chip.endMs <= now);
    }

    /** Running chips: countdowns by time left (soonest first), then open-ended ones by start. */
    public List<Chip> active(long now) {
        List<Chip> out = new ArrayList<>();
        for (Chip chip : chips.values()) {
            if (chip.endMs > now) {
                out.add(chip);
            }
        }
        out.sort(Comparator.comparing((Chip c) -> !c.countsDown())
                .thenComparingLong(c -> c.countsDown() ? c.endMs : c.startMs));
        return out;
    }

    /**
     * Whether a block-bound chip should end: fewer than a third of its blocks (at least one) are still there.
     * Chips without tracked blocks never end this way.
     */
    public static boolean mostlyGone(int remaining, int tracked) {
        if (tracked <= 0) {
            return false;
        }
        return remaining < Math.max(1, (int) Math.ceil(tracked / 3.0));
    }
}
