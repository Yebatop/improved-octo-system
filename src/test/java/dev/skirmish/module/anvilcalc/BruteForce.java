package dev.skirmish.module.anvilcalc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Independent reference: tries every sequence of anvil operations on the pieces (every ordered pair at every point,
 * memoized by the multiset of pieces) with its own re-implementation of AnvilMenu.createResult, and returns the best
 * result by the same objective as the solver (highest level sum on the base, then lowest total cost).
 */
final class BruteForce {
    record Result(int levelSum, int cost, Map<String, Integer> enchants) {
    }

    private record P(boolean base, boolean book, int rc, TreeMap<String, Integer> ench) {
        String key() {
            return (base ? "*" : "") + (book ? "B" : "I") + rc + ench;
        }
    }

    private final Map<String, Vanilla.Def> defs = new HashMap<>();
    private final Set<String> supported;
    private final boolean creative;
    private final int limit;
    private final Map<String, Result> memo = new HashMap<>();

    BruteForce(Set<String> supported, boolean creative, int limit) {
        for (Vanilla.Def def : Vanilla.DEFS) {
            defs.put(def.id(), def);
        }
        this.supported = supported;
        this.creative = creative;
        this.limit = creative ? Integer.MAX_VALUE : limit;
    }

    Result solve(boolean baseIsBook, int baseRc, Map<String, Integer> base, List<Integer> bookRcs, List<Map<String, Integer>> books) {
        List<P> pieces = new ArrayList<>();
        pieces.add(new P(true, baseIsBook, baseRc, new TreeMap<>(base)));
        for (int i = 0; i < books.size(); i++) {
            pieces.add(new P(false, true, bookRcs.get(i), new TreeMap<>(books.get(i))));
        }
        return search(pieces);
    }

    private Result search(List<P> pieces) {
        List<String> keys = new ArrayList<>();
        for (P p : pieces) {
            keys.add(p.key());
        }
        keys.sort(null);
        String key = keys.toString();
        Result cached = memo.get(key);
        if (cached != null) {
            return cached;
        }
        P base = pieces.stream().filter(P::base).findFirst().orElseThrow();
        Result best = new Result(sum(base.ench()), 0, base.ench());
        for (int a = 0; a < pieces.size(); a++) {
            for (int b = 0; b < pieces.size(); b++) {
                if (a == b) {
                    continue;
                }
                P left = pieces.get(a);
                P right = pieces.get(b);
                int[] cost = new int[1];
                P merged = merge(left, right, cost);
                if (merged == null || cost[0] >= limit) {
                    continue;
                }
                List<P> next = new ArrayList<>(pieces);
                next.remove(Math.max(a, b));
                next.remove(Math.min(a, b));
                next.add(merged);
                Result sub = search(next);
                Result candidate = new Result(sub.levelSum(), sub.cost() + cost[0], sub.enchants());
                if (candidate.levelSum() > best.levelSum()
                        || candidate.levelSum() == best.levelSum() && candidate.cost() < best.cost()) {
                    best = candidate;
                }
            }
        }
        memo.put(key, best);
        return best;
    }

    private P merge(P left, P right, int[] costOut) {
        if (!right.book()) {
            return null;
        }
        TreeMap<String, Integer> result = new TreeMap<>(left.ench());
        int charged = 0;
        boolean applied = false;
        boolean rejected = false;
        for (Map.Entry<String, Integer> e : right.ench().entrySet()) {
            String id = e.getKey();
            int have = result.getOrDefault(id, 0);
            int level = have == e.getValue() ? e.getValue() + 1 : Math.max(e.getValue(), have);
            boolean ok = creative || left.book() || supported.contains(id);
            for (String other : result.keySet()) {
                if (!other.equals(id) && exclusive(id, other)) {
                    ok = false;
                    charged++;
                }
            }
            if (!ok) {
                rejected = true;
                continue;
            }
            applied = true;
            Vanilla.Def def = defs.get(id);
            level = Math.min(level, def.maxLevel());
            result.put(id, level);
            charged += Math.max(1, def.anvilCost() / 2) * level;
        }
        if ((rejected && !applied) || charged <= 0) {
            return null;
        }
        costOut[0] = left.rc() + right.rc() + charged;
        return new P(left.base() || right.base(), left.book(), 2 * Math.max(left.rc(), right.rc()) + 1, result);
    }

    private static boolean exclusive(String a, String b) {
        for (List<String> group : Vanilla.EXCLUSIVE) {
            if (group.contains(a) && group.contains(b)) {
                return true;
            }
        }
        return false;
    }

    private static int sum(Map<String, Integer> ench) {
        return ench.values().stream().mapToInt(Integer::intValue).sum();
    }
}
