package dev.skirmish.module.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The storage index over the containers you opened: totals per item with the chests that hold it, search, the
 * difference between two looks into a chest and the items below their minimum. Pure Java.
 */
final class StorageIndex {
    /** One item kind across the base: total and where it lies (chest key → count, most first). */
    record Total(String key, String id, String name, long count, List<Where> where) {
    }

    record Where(String chest, int count) {
    }

    /** A change between two looks into one chest. */
    record Change(String key, String name, int delta) {
    }

    /** An item below its minimum. */
    record Low(String key, String name, long have, int min) {
    }

    private StorageIndex() {
    }

    static List<Total> totals(Collection<BaseData.Chest> chests) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, BaseData.Stack> sample = new LinkedHashMap<>();
        Map<String, List<Where>> where = new LinkedHashMap<>();
        for (BaseData.Chest chest : chests) {
            for (BaseData.Stack s : chest.items) {
                if (s.count <= 0) {
                    continue;
                }
                counts.computeIfAbsent(s.key, k -> new long[1])[0] += s.count;
                sample.putIfAbsent(s.key, s);
                where.computeIfAbsent(s.key, k -> new ArrayList<>()).add(new Where(chest.key(), s.count));
            }
        }
        List<Total> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : counts.entrySet()) {
            BaseData.Stack s = sample.get(e.getKey());
            List<Where> w = where.get(e.getKey());
            w.sort(Comparator.comparingInt(Where::count).reversed());
            out.add(new Total(e.getKey(), s.id, s.name, e.getValue()[0], List.copyOf(w)));
        }
        out.sort(Comparator.comparingLong(Total::count).reversed().thenComparing(Total::name));
        return out;
    }

    /**
     * Items whose name or id contains every word of the query (any case, ё = е); an empty query keeps all.
     */
    static List<Total> search(List<Total> totals, String query) {
        String[] words = normalize(query).split("\\s+");
        List<Total> out = new ArrayList<>();
        for (Total t : totals) {
            String hay = normalize(t.name()) + " " + normalize(t.id());
            boolean all = true;
            for (String w : words) {
                if (!w.isEmpty() && !hay.contains(w)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                out.add(t);
            }
        }
        return out;
    }

    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').replace("minecraft:", "").replace('_', ' ').strip();
    }

    /** What changed between two looks into a chest, biggest first. */
    static List<Change> diff(List<BaseData.Stack> before, List<BaseData.Stack> after) {
        Map<String, Integer> delta = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (BaseData.Stack s : before) {
            delta.merge(s.key, -s.count, Integer::sum);
            names.putIfAbsent(s.key, s.name);
        }
        for (BaseData.Stack s : after) {
            delta.merge(s.key, s.count, Integer::sum);
            names.put(s.key, s.name);
        }
        List<Change> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : delta.entrySet()) {
            if (e.getValue() != 0) {
                out.add(new Change(e.getKey(), names.get(e.getKey()), e.getValue()));
            }
        }
        out.sort(Comparator.comparingInt((Change c) -> Math.abs(c.delta())).reversed());
        return out;
    }

    /** "+32 Алмаз, −5 Золотое яблоко" (at most {@code max} parts, then "…"). */
    static String changeText(List<Change> changes, int max) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < changes.size() && i < max; i++) {
            Change c = changes.get(i);
            if (i > 0) {
                out.append(", ");
            }
            out.append(c.delta() > 0 ? "+" : "−").append(Math.abs(c.delta())).append(' ').append(c.name());
        }
        if (changes.size() > max) {
            out.append(", …");
        }
        return out.toString();
    }

    /** Items with a minimum whose total is below it (missing items count as 0). */
    static List<Low> low(List<Total> totals, Map<String, Integer> minimums, Map<String, String> names) {
        Map<String, Total> byKey = new LinkedHashMap<>();
        for (Total t : totals) {
            byKey.put(t.key(), t);
        }
        List<Low> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : minimums.entrySet()) {
            Total t = byKey.get(e.getKey());
            long have = t == null ? 0 : t.count();
            if (have < e.getValue()) {
                String name = t != null ? t.name() : names.getOrDefault(e.getKey(), e.getKey());
                out.add(new Low(e.getKey(), name, have, e.getValue()));
            }
        }
        out.sort(Comparator.comparingDouble((Low l) -> l.have() / (double) Math.max(1, l.min())));
        return out;
    }

    /** "3 ст. + 12" for counts of 64-stacks, plain count below a stack. */
    static String stacks(long count, int stackSize) {
        if (stackSize <= 1 || count < stackSize) {
            return Long.toString(count);
        }
        long full = count / stackSize;
        long rest = count % stackSize;
        return rest == 0 ? full + "×" + stackSize : full + "×" + stackSize + " + " + rest;
    }
}
