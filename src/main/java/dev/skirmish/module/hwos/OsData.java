package dev.skirmish.module.hwos;

import dev.skirmish.module.market.parse.PriceHistory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Data shaping for HolyWorld OS: anarchy groups, auction deals, the sidebar's «Label: value» lines. Pure Java. */
final class OsData {
    /** A group of anarchies by kind («СолоЛайт», «ДуоЛайт», …) and its members' indices in the input. */
    record Group(String kind, List<Integer> members) {
    }

    /** A price well under the usual one: the item, the latest price, the median and how much cheaper (0..1). */
    record Deal(String key, double price, double median, double off, long at) {
    }

    record Pair(String label, String value) {
    }

    private static final Pattern NUMBERED = Pattern.compile("^(.*?)\\s*#\\s*(\\d+)\\s*$");
    private static final List<String> ORDER = List.of("СолоЛайт", "ДуоЛайт", "ТриоЛайт");
    private static final Pattern PAIR = Pattern.compile("^\\s*([^:]{1,24}?)\\s*:\\s*(.+?)\\s*$");

    private OsData() {
    }

    /** Groups names like «ДуоЛайт #17» by kind: Solo, Duo, Trio first, then the rest by name; numbers ascending. */
    static List<Group> groups(List<String> names) {
        Map<String, List<Integer>> byKind = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            byKind.computeIfAbsent(kind(names.get(i)), k -> new ArrayList<>()).add(i);
        }
        List<Group> out = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : byKind.entrySet()) {
            List<Integer> members = new ArrayList<>(e.getValue());
            members.sort(Comparator.comparingInt((Integer i) -> number(names.get(i))).thenComparing(names::get));
            out.add(new Group(e.getKey(), members));
        }
        out.sort(Comparator.comparingInt((Group g) -> {
            int i = ORDER.indexOf(g.kind());
            return i < 0 ? ORDER.size() : i;
        }).thenComparing(Group::kind));
        return out;
    }

    static String kind(String name) {
        Matcher m = NUMBERED.matcher(name);
        return m.matches() ? m.group(1).strip() : name.strip();
    }

    static int number(String name) {
        Matcher m = NUMBERED.matcher(name);
        return m.matches() ? Integer.parseInt(m.group(2)) : Integer.MAX_VALUE;
    }

    /**
     * Items whose latest sample (not older than {@code fresh}) is at most {@code ratio} of the median of the samples
     * of the last {@code window}, with at least {@code minSamples} samples; biggest discount first.
     */
    static List<Deal> deals(Map<String, List<PriceHistory.Sample>> history, long now, long window, long fresh, double ratio, int minSamples) {
        List<Deal> out = new ArrayList<>();
        for (Map.Entry<String, List<PriceHistory.Sample>> e : history.entrySet()) {
            List<Double> prices = new ArrayList<>();
            PriceHistory.Sample latest = null;
            for (PriceHistory.Sample s : e.getValue()) {
                if (now - s.time() <= window) {
                    prices.add(s.unitPrice());
                }
                if (latest == null || s.time() >= latest.time()) {
                    latest = s;
                }
            }
            if (latest == null || prices.size() < minSamples || now - latest.time() > fresh) {
                continue;
            }
            double median = median(prices);
            if (median > 0 && latest.unitPrice() <= median * ratio) {
                out.add(new Deal(e.getKey(), latest.unitPrice(), median, 1 - latest.unitPrice() / median, latest.time()));
            }
        }
        out.sort(Comparator.comparingDouble(Deal::off).reversed());
        return out;
    }

    static double median(List<Double> values) {
        List<Double> v = new ArrayList<>(values);
        v.sort(Double::compare);
        int n = v.size();
        if (n == 0) {
            return 0;
        }
        return n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2.0;
    }

    /** «Баланс: 12 500» style lines of the sidebar (colour codes and private-use glyphs removed). */
    static List<Pair> pairs(List<String> lines) {
        List<Pair> out = new ArrayList<>();
        for (String raw : lines) {
            String line = clean(raw);
            Matcher m = PAIR.matcher(line);
            if (m.matches() && !m.group(2).isBlank() && !m.group(1).isBlank()) {
                out.add(new Pair(m.group(1).strip(), m.group(2).strip()));
            }
        }
        return out;
    }

    static String clean(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (c >= '\uE000' && c <= '\uF8FF') {
                continue;
            }
            out.append(c);
        }
        return out.toString().replaceAll("\\s+", " ").strip();
    }

    /** A countdown for a list: "1д 4ч" or "16ч 41м" from an hour up, the clock text {@code clock} below. */
    static String span(long ms, String clock, String d, String h, String m) {
        long minutes = ms / 60_000;
        long days = minutes / 1440;
        long hours = minutes / 60 % 24;
        long mins = minutes % 60;
        if (days > 0) {
            return hours > 0 ? days + d + " " + hours + h : days + d;
        }
        if (minutes >= 60) {
            return mins > 0 ? hours + h + " " + mins + m : hours + h;
        }
        return clock;
    }

    /** The custom-name part of a market key («minecraft:player_head|сфера армоталити»), or null for a plain item. */
    static String customName(String key) {
        int bar = key.indexOf('|');
        return bar < 0 ? null : key.substring(bar + 1);
    }
}
