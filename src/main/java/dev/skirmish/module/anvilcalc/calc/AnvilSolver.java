package dev.skirmish.module.anvilcalc.calc;

import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Mode;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Operand;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Reason;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Skipped;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Status;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Step;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Unreachable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the cheapest merge order. The target is the best reachable enchantment set (highest sum of levels, so
 * conflicting books resolve to the cheaper alternative); among the plans reaching it the one with the lowest total
 * level cost wins.
 * <p>
 * Exact mode is a DP over subsets of {base} ∪ books: every subset keeps the Pareto front of (result enchantments,
 * repair cost, total cost) over all binary merge trees with every ordered left/right split, so both the prior work
 * penalty and the left/right asymmetry are accounted for. O(3^(n+1) · F²) combine calls, F = front size per subset.
 */
public final class AnvilSolver {
    private static final int BASE = -1;

    private AnvilSolver() {
    }

    private static final class Node {
        final long mask;
        final Piece piece;
        final long cost;
        final int stepCost;
        final int maxStep;
        final int element;
        final @Nullable Node left;
        final @Nullable Node right;

        Node(long mask, Piece piece, int element) {
            this.mask = mask;
            this.piece = piece;
            this.cost = 0;
            this.stepCost = 0;
            this.maxStep = 0;
            this.element = element;
            this.left = null;
            this.right = null;
        }

        Node(Node left, Node right, int stepCost, Piece piece) {
            this.mask = left.mask | right.mask;
            this.piece = piece;
            this.cost = left.cost + right.cost + stepCost;
            this.stepCost = stepCost;
            this.maxStep = Math.max(stepCost, Math.max(left.maxStep, right.maxStep));
            this.element = Integer.MIN_VALUE;
            this.left = left;
            this.right = right;
        }

        boolean leaf() {
            return left == null;
        }
    }

    private record Found(Node best, int states) {
    }

    /** Better result first: higher level sum, then cheaper, then fewer books, then lower mask for determinism. */
    private static final Comparator<Node> BEST = Comparator.<Node>comparingInt(n -> -n.piece.levelSum())
            .thenComparingLong(n -> n.cost)
            .thenComparingInt(n -> Long.bitCount(n.mask))
            .thenComparingLong(n -> n.mask);

    public static AnvilPlan solve(AnvilInput input) {
        long started = System.nanoTime();
        AnvilRules rules = new AnvilRules(input.catalog(), input.creative(), input.profile(), input.baseArmor());
        Piece base = input.base();

        List<Skipped> skipped = new ArrayList<>();
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < input.books().size(); i++) {
            if (input.dubious().contains(i)) {
                skipped.add(new Skipped(i, Reason.DUBIOUS, null));
                continue;
            }
            Skipped reason = classify(rules, base, i, input.books().get(i), base, true);
            if (reason == null) {
                candidates.add(i);
            } else {
                skipped.add(reason);
            }
        }
        if (candidates.size() > 62) {
            throw new IllegalArgumentException("Too many books: " + candidates.size());
        }
        if (candidates.isEmpty()) {
            return new AnvilPlan(Status.NOTHING_TO_DO, List.of(), 0, base, Mode.EXACT, System.nanoTime() - started, 0, 0,
                    List.of(), skipped, List.of(), null);
        }

        List<Node> leaves = new ArrayList<>();
        leaves.add(new Node(1L, base, BASE));
        for (int j = 0; j < candidates.size(); j++) {
            leaves.add(new Node(1L << (j + 1), input.books().get(candidates.get(j)), candidates.get(j)));
        }
        boolean exact = candidates.size() <= input.exactLimit();
        int limit = input.stepLimit();

        Found full = exact ? exact(rules, leaves, Integer.MAX_VALUE, -1L) : greedy(rules, leaves, Integer.MAX_VALUE);
        int states = full.states();
        Node chosen = full.best();
        Status status = Status.OK;
        Unreachable unreachable = null;
        List<Integer> leftOut = new ArrayList<>();
        if (chosen.maxStep >= limit) {
            Found limited = exact ? exact(rules, leaves, limit, -1L) : greedy(rules, leaves, limit);
            states += limited.states();
            if (limited.best().piece.levelSum() >= chosen.piece.levelSum()) {
                chosen = limited.best();
            } else {
                Node over = chosen;
                if (exact) {
                    Found fewest = exact(rules, leaves, limit, over.mask);
                    states += fewest.states();
                    chosen = fewest.best();
                } else {
                    chosen = limited.best();
                }
                status = Status.LIMITED;
                List<Step> overSteps = new ArrayList<>();
                emit(over, overSteps);
                Map<Integer, Integer> overLimit = new LinkedHashMap<>();
                for (int s = 0; s < overSteps.size(); s++) {
                    if (overSteps.get(s).cost() >= limit) {
                        overLimit.put(s, overSteps.get(s).cost());
                    }
                }
                unreachable = new Unreachable(overSteps, (int) Math.min(over.cost, Integer.MAX_VALUE), overLimit, over.piece);
                for (Node leaf : leaves) {
                    if (leaf.element != BASE && (over.mask & leaf.mask) != 0 && (chosen.mask & leaf.mask) == 0) {
                        leftOut.add(leaf.element);
                    }
                }
            }
        }

        List<Step> steps = new ArrayList<>();
        emit(chosen, steps);
        List<Integer> used = new ArrayList<>();
        for (Node leaf : leaves) {
            if (leaf.element == BASE || (chosen.mask & leaf.mask) == 0) {
                continue;
            }
            used.add(leaf.element);
        }
        for (Node leaf : leaves) {
            if (leaf.element == BASE || (chosen.mask & leaf.mask) != 0) {
                continue;
            }
            if (leftOut.contains(leaf.element)) {
                skipped.add(new Skipped(leaf.element, Reason.OVER_LIMIT, null));
            } else {
                Skipped reason = classify(rules, base, leaf.element, leaf.piece, chosen.piece, false);
                skipped.add(reason != null ? reason : new Skipped(leaf.element, Reason.NOT_NEEDED, null));
            }
        }
        skipped.sort(Comparator.comparingInt(Skipped::book));
        if (steps.isEmpty() && status == Status.OK) {
            status = Status.NOTHING_TO_DO;
        }
        return new AnvilPlan(status, steps, (int) Math.min(chosen.cost, Integer.MAX_VALUE), chosen.piece,
                exact ? Mode.EXACT : Mode.GREEDY, System.nanoTime() - started, states, candidates.size(), used, skipped, leftOut, unreachable);
    }

    /**
     * Why a book cannot improve {@code reference}, or null when it might. With {@code prefilter} only certain cases
     * count (the base's own enchantments are never removed, so conflicts with them and maxed levels are final).
     */
    static @Nullable Skipped classify(AnvilRules rules, Piece base, int index, Piece book, Piece reference, boolean prefilter) {
        boolean anyApplicable = false;
        String conflict = null;
        for (Map.Entry<String, Integer> entry : book.enchants().entrySet()) {
            String id = entry.getKey();
            if (!rules.applicable(base, id)) {
                continue;
            }
            anyApplicable = true;
            EnchantInfo info = rules.info(id);
            String conflictsWith = null;
            for (String other : reference.enchants().keySet()) {
                if (!other.equals(id) && (!info.compatibleWith(other) || !rules.info(other).compatibleWith(id))) {
                    conflictsWith = other;
                    break;
                }
            }
            if (conflictsWith != null) {
                if (conflict == null) {
                    conflict = conflictsWith;
                }
                continue;
            }
            int have = reference.level(id);
            boolean nothingNew = prefilter ? have >= info.maxLevel() : have >= Math.min(entry.getValue(), info.maxLevel());
            if (!nothingNew) {
                return null;
            }
        }
        if (!anyApplicable) {
            return new Skipped(index, Reason.NOT_APPLICABLE, null);
        }
        return conflict != null ? new Skipped(index, Reason.CONFLICT, conflict) : new Skipped(index, Reason.NOTHING_NEW, null);
    }

    /** @param within when not -1, only subsets of this mask count and the most books wins over the level sum */
    private static Found exact(AnvilRules rules, List<Node> leaves, int limit, long within) {
        int n = leaves.size();
        long allMasks = 1L << n;
        @SuppressWarnings("unchecked")
        List<Node>[] states = new List[(int) allMasks];
        int created = 0;
        for (Node leaf : leaves) {
            List<Node> single = new ArrayList<>(1);
            single.add(leaf);
            states[(int) leaf.mask] = single;
        }
        boolean baseIsBook = leaves.get(0).piece.book();
        for (int mask = 1; mask < allMasks; mask++) {
            if (Integer.bitCount(mask) < 2 || (within != -1L && (mask & ~within) != 0)) {
                continue;
            }
            List<Node> front = new ArrayList<>();
            for (int left = (mask - 1) & mask; left > 0; left = (left - 1) & mask) {
                int right = mask ^ left;
                if (!baseIsBook && (right & 1) != 0) {
                    continue;
                }
                List<Node> lefts = states[left];
                List<Node> rights = states[right];
                if (lefts == null || rights == null) {
                    continue;
                }
                for (Node a : lefts) {
                    for (Node b : rights) {
                        if ((long) a.piece.repairCost() + b.piece.repairCost() + 1 >= limit) {
                            continue;
                        }
                        AnvilRules.Outcome outcome = rules.combine(a.piece, b.piece);
                        if (!outcome.ok() || outcome.cost() >= limit) {
                            continue;
                        }
                        created++;
                        insert(front, new Node(a, b, outcome.cost(), outcome.result()));
                    }
                }
            }
            if (!front.isEmpty()) {
                states[mask] = front;
            }
        }
        Comparator<Node> order = within == -1L ? BEST
                : Comparator.<Node>comparingInt(node -> -Long.bitCount(node.mask)).thenComparing(BEST);
        Node best = leaves.get(0);
        for (int mask = 1; mask < allMasks; mask += 2) {
            if (states[mask] == null) {
                continue;
            }
            for (Node node : states[mask]) {
                if (order.compare(node, best) < 0) {
                    best = node;
                }
            }
        }
        return new Found(best, created);
    }

    /** Keeps, per distinct enchantment map, the states not dominated in (repair cost, total cost). */
    private static void insert(List<Node> front, Node candidate) {
        for (Iterator<Node> it = front.iterator(); it.hasNext(); ) {
            Node existing = it.next();
            if (!existing.piece.enchants().equals(candidate.piece.enchants())) {
                continue;
            }
            if (existing.piece.repairCost() <= candidate.piece.repairCost() && existing.cost <= candidate.cost) {
                return;
            }
            if (candidate.piece.repairCost() <= existing.piece.repairCost() && candidate.cost <= existing.cost) {
                it.remove();
            }
        }
        front.add(candidate);
    }

    private static Found greedy(AnvilRules rules, List<Node> leaves, int limit) {
        Found balanced = greedyRun(rules, leaves, limit, true);
        Found cheapest = greedyRun(rules, leaves, limit, false);
        int states = balanced.states() + cheapest.states();
        Comparator<Node> order = Comparator.<Node>comparingInt(n -> -n.piece.levelSum())
                .thenComparingInt(n -> -Long.bitCount(n.mask))
                .thenComparingLong(n -> n.cost);
        Node best = order.compare(cheapest.best(), balanced.best()) < 0 ? cheapest.best() : balanced.best();
        return new Found(best, states);
    }

    /**
     * Repeatedly performs one merge among all current pieces. Balanced: lowest max prior work of the two inputs
     * first (keeps the tree shallow), then cheapest step; otherwise cheapest step first.
     */
    private static Found greedyRun(AnvilRules rules, List<Node> leaves, int limit, boolean balanced) {
        List<Node> pieces = new ArrayList<>(leaves);
        int created = 0;
        while (pieces.size() > 1) {
            Node best = null;
            long bestKey = Long.MAX_VALUE;
            int bestA = -1;
            int bestB = -1;
            for (int a = 0; a < pieces.size(); a++) {
                for (int b = 0; b < pieces.size(); b++) {
                    if (a == b) {
                        continue;
                    }
                    Node left = pieces.get(a);
                    Node right = pieces.get(b);
                    AnvilRules.Outcome outcome = rules.combine(left.piece, right.piece);
                    if (!outcome.ok() || outcome.cost() >= limit) {
                        continue;
                    }
                    long work = Math.max(left.piece.repairCost(), right.piece.repairCost());
                    long key = balanced ? (work << 32) | outcome.cost() : ((long) outcome.cost() << 32) | work;
                    if (key < bestKey) {
                        bestKey = key;
                        best = new Node(left, right, outcome.cost(), outcome.result());
                        bestA = a;
                        bestB = b;
                    }
                }
            }
            if (best == null) {
                break;
            }
            created++;
            pieces.remove(Math.max(bestA, bestB));
            pieces.remove(Math.min(bestA, bestB));
            pieces.add(best);
        }
        for (Node node : pieces) {
            if ((node.mask & 1L) != 0) {
                return new Found(node, created);
            }
        }
        throw new IllegalStateException("base lost");
    }

    private static Operand emit(Node node, List<Step> out) {
        if (node.leaf()) {
            return node.element == BASE ? Operand.BASE : Operand.book(node.element);
        }
        Operand left = emit(node.left, out);
        Operand right = emit(node.right, out);
        out.add(new Step(left, right, node.stepCost, node.piece));
        return Operand.step(out.size() - 1);
    }
}
