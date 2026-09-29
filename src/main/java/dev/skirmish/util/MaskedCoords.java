package dev.skirmish.util;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordinates with hidden digits, as HolyWorld announces some placements: «▶ Игрок … установил Золотой Спавнер на
 * координатах 1*4*, 5, 1*9*» (captured 2026-09; each * is one digit). A masked number stands for a set of block
 * ranges: «1*4*» is 1040–1049, 1140–1149, … 1940–1949. Pure Java.
 */
public final class MaskedCoords {
    /** At most this many ranges per axis; beyond it the axis is one range over all it can be. */
    static final int MAX_RANGES = 100;

    /** A masked position: what the line said before it, the X and Z ranges, and Y when it was given. */
    public record Masked(String what, List<int[]> xs, @Nullable Integer y, List<int[]> zs) {
        /** Number of cells (X range × Z range). */
        public int cells() {
            return xs.size() * zs.size();
        }
    }

    private static final String NUM = "(-?[\\d*]{1,8})";
    private static final Pattern TRIPLE = Pattern.compile(
            "(?<![\\p{L}\\d*])" + NUM + "\\s*[,;]?\\s+" + NUM + "\\s*[,;]?\\s+" + NUM + "(?![\\d*])");
    private static final Pattern WHERE = Pattern.compile("\\s*(?:на|по)?\\s*координат\\p{L}*\\s*:?\\s*$");

    private MaskedCoords() {
    }

    /** The masked position in a line, or null when it has none (plain coordinates are not masked ones). */
    public static @Nullable Masked find(String text) {
        Matcher m = TRIPLE.matcher(text);
        while (m.find()) {
            String x = m.group(1);
            String y = m.group(2);
            String z = m.group(3);
            if (!x.contains("*") && !z.contains("*")) {
                continue;
            }
            List<int[]> xs = ranges(x);
            List<int[]> zs = ranges(z);
            if (xs.isEmpty() || zs.isEmpty()) {
                continue;
            }
            Integer height = y.contains("*") ? null : parseInt(y);
            String before = text.substring(0, m.start());
            before = WHERE.matcher(before).replaceFirst("").replaceFirst("^[\\s▶►>•]+", "").strip();
            return new Masked(before, xs, height, zs);
        }
        return null;
    }

    /** The block ranges ({min, max}, ascending) a masked number stands for; empty when it is not one. */
    public static List<int[]> ranges(String mask) {
        boolean negative = mask.startsWith("-");
        String digits = negative ? mask.substring(1) : mask;
        if (digits.isEmpty() || !digits.matches("[\\d*]+")) {
            return List.of();
        }
        // Stars at the end make each range; the stars before them multiply the ranges.
        int tail = 0;
        while (tail < digits.length() && digits.charAt(digits.length() - 1 - tail) == '*') {
            tail++;
        }
        String head = digits.substring(0, digits.length() - tail);
        int headStars = (int) head.chars().filter(c -> c == '*').count();
        long span = pow10(tail);
        List<int[]> out = new ArrayList<>();
        if (pow10(headStars) > MAX_RANGES) {
            long lo = Long.parseLong(head.replace('*', '0')) * span;
            long hi = Long.parseLong(head.replace('*', '9')) * span + span - 1;
            out.add(signed(lo, hi, negative));
            return out;
        }
        int combos = (int) pow10(headStars);
        for (int c = 0; c < combos; c++) {
            StringBuilder b = new StringBuilder(head.length());
            int rest = c;
            int starIndex = headStars;
            for (int i = 0; i < head.length(); i++) {
                char ch = head.charAt(i);
                if (ch == '*') {
                    starIndex--;
                    b.append((char) ('0' + (rest / (int) pow10(starIndex)) % 10));
                } else {
                    b.append(ch);
                }
            }
            long base = b.isEmpty() ? 0 : Long.parseLong(b.toString()) * span;
            out.add(signed(base, base + span - 1, negative));
        }
        out.sort((a, b) -> Integer.compare(a[0], b[0]));
        return out;
    }

    private static int[] signed(long lo, long hi, boolean negative) {
        return negative ? new int[]{(int) -hi, (int) -lo} : new int[]{(int) lo, (int) hi};
    }

    private static long pow10(int n) {
        long p = 1;
        for (int i = 0; i < n; i++) {
            p *= 10;
        }
        return p;
    }

    private static @Nullable Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
