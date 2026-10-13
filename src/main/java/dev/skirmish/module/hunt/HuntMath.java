package dev.skirmish.module.hunt;

import org.jspecify.annotations.Nullable;

import java.util.BitSet;
import java.util.List;

/**
 * Geometry of a hunt over masked coordinates: the cells are every X range × every Z range the hidden digits allow
 * (block ranges, inclusive). Which cell you stand in, and the nearest one you have not been in yet. Pure Java.
 */
final class HuntMath {
    private HuntMath() {
    }

    /** A cell: its index (X range × Z-count + Z range), its centre and the horizontal distance to it. */
    record Target(int index, double x, double z, double distance) {
    }

    /** The cell containing (x, z), or −1. */
    static int cellAt(List<int[]> xs, List<int[]> zs, double x, double z) {
        int i = rangeAt(xs, Math.floor(x));
        int j = rangeAt(zs, Math.floor(z));
        return i < 0 || j < 0 ? -1 : i * zs.size() + j;
    }

    private static int rangeAt(List<int[]> ranges, double v) {
        for (int i = 0; i < ranges.size(); i++) {
            if (v >= ranges.get(i)[0] && v <= ranges.get(i)[1]) {
                return i;
            }
        }
        return -1;
    }

    /** The nearest cell not in {@code visited} (by distance to its nearest edge), or null when all are visited. */
    static @Nullable Target nearest(List<int[]> xs, List<int[]> zs, BitSet visited, double x, double z) {
        Target best = null;
        double bestEdge = Double.MAX_VALUE;
        for (int i = 0; i < xs.size(); i++) {
            int[] xr = xs.get(i);
            double dx = axis(xr, x);
            for (int j = 0; j < zs.size(); j++) {
                int index = i * zs.size() + j;
                if (visited.get(index)) {
                    continue;
                }
                int[] zr = zs.get(j);
                double dz = axis(zr, z);
                double edge = dx * dx + dz * dz;
                if (edge < bestEdge) {
                    bestEdge = edge;
                    double cx = (xr[0] + xr[1] + 1) / 2.0;
                    double cz = (zr[0] + zr[1] + 1) / 2.0;
                    best = new Target(index, cx, cz, Math.hypot(cx - x, cz - z));
                }
            }
        }
        return best;
    }

    /** Distance from v to the range [r0, r1 + 1) on one axis (0 inside). */
    private static double axis(int[] r, double v) {
        if (v < r[0]) {
            return r[0] - v;
        }
        return v > r[1] + 1 ? v - (r[1] + 1) : 0;
    }

    /** «1*4*» split into characters for display: digits as they are, each hidden digit as null. */
    static Character[] maskChars(String mask) {
        Character[] out = new Character[mask.length()];
        for (int i = 0; i < mask.length(); i++) {
            char c = mask.charAt(i);
            out[i] = c == '*' ? null : c;
        }
        return out;
    }

    /** The object in «Игрок Nick установил Золотой Спавнер» and who placed it: {what, who} (who may be empty). */
    static String[] subject(String what) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "^(?:Игрок\\s+)?(\\S+)\\s+(?:установил|поставил|разместил|спрятал)\\p{L}*\\s+(.+)$",
                java.util.regex.Pattern.UNICODE_CASE | java.util.regex.Pattern.CASE_INSENSITIVE).matcher(what.strip());
        if (m.find()) {
            return new String[]{m.group(2).strip(), m.group(1)};
        }
        return new String[]{what.strip(), ""};
    }
}
