package dev.skirmish.module.anvilcalc.calc;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Solver output. Book indices refer to {@link AnvilInput#books()}; {@code candidates} is the number of books left after
 * dropping the ones that cannot add anything.
 */
public record AnvilPlan(Status status, List<Step> steps, int total, Piece result, Mode mode, long nanos, int states, int candidates,
                        List<Integer> usedBooks, List<Skipped> skipped, List<Integer> leftOut,
                        @Nullable Unreachable unreachable) {
    public enum Status {
        /** Every useful book is merged; {@link #steps} is the cheapest order found. */
        OK,
        /** The best result needs a step at or above the limit; {@link #steps} is the best plan within it. */
        LIMITED,
        /** No book can add anything to the item. */
        NOTHING_TO_DO
    }

    public enum Mode {
        EXACT,
        GREEDY
    }

    public enum OperandKind {
        BASE,
        BOOK,
        STEP
    }

    /** {@code index}: the book index for BOOK, the 0-based step index for STEP. */
    public record Operand(OperandKind kind, int index) {
        public static final Operand BASE = new Operand(OperandKind.BASE, -1);

        public static Operand book(int index) {
            return new Operand(OperandKind.BOOK, index);
        }

        public static Operand step(int index) {
            return new Operand(OperandKind.STEP, index);
        }
    }

    public record Step(Operand left, Operand right, int cost, Piece result) {
    }

    public enum Reason {
        /** None of its enchantments can go on this item (not a supported item). */
        NOT_APPLICABLE,
        /** An enchantment conflicts with one on the item (or the result); {@code enchant} names it. */
        CONFLICT,
        /** The result already has all its enchantments at the same or a higher level. */
        NOTHING_NEW,
        /** Left out so that every step stays below the limit. */
        OVER_LIMIT,
        /** Merging it costs more than it adds (e.g. a lower level of an enchantment that is raised anyway). */
        NOT_NEEDED
    }

    /** {@code enchant}: for CONFLICT, the enchantment on the result it conflicts with. */
    public record Skipped(int book, Reason reason, @Nullable String enchant) {
    }

    /**
     * Why the best result is out of reach: the cheapest unrestricted plan, its total and its steps at or above the
     * limit (0-based indices into {@code steps}).
     */
    public record Unreachable(List<Step> steps, int total, Map<Integer, Integer> overLimit, Piece result) {
    }
}
