package dev.skirmish.module.analytics;

/**
 * Reach as the server measures it for melee: the distance from the attacker's eye to the closest point of the
 * target's bounding box (0 when the eye is inside the box). Pure Java, unit tested.
 */
public final class ReachMath {
    /** Vanilla survival entity interaction range, in blocks. */
    public static final double VANILLA_REACH = 3.0;

    private ReachMath() {
    }

    /** Distance from point {@code (px, py, pz)} to the axis-aligned box {@code [min, max]}. */
    public static double distanceToBox(double px, double py, double pz,
                                       double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double dx = axis(px, minX, maxX);
        double dy = axis(py, minY, maxY);
        double dz = axis(pz, minZ, maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double axis(double p, double min, double max) {
        if (p < min) {
            return min - p;
        }
        return p > max ? p - max : 0;
    }

    /** Mean of the finite values, NaN when there are none. */
    public static double average(double[] values) {
        double sum = 0;
        int n = 0;
        for (double v : values) {
            if (Double.isFinite(v)) {
                sum += v;
                n++;
            }
        }
        return n == 0 ? Double.NaN : sum / n;
    }

    /** Largest finite value, NaN when there are none. */
    public static double max(double[] values) {
        double best = Double.NaN;
        for (double v : values) {
            if (Double.isFinite(v) && (Double.isNaN(best) || v > best)) {
                best = v;
            }
        }
        return best;
    }
}
