package dev.skirmish.module.elytra;

/** Elytra HUD maths. Pure Java. */
final class FlightMath {
    private FlightMath() {
    }

    /** Exponential smoothing of a speed sample (blocks/s). */
    static double smooth(double previous, double sample, double factor) {
        return previous < 0 ? sample : previous + (sample - previous) * factor;
    }

    /**
     * Seconds of gliding left: vanilla takes 1 durability per second of flight and an elytra stops working with
     * 1 left.
     */
    static int glideSeconds(int usesLeft) {
        return Math.max(0, usesLeft - 1);
    }

    /** Seconds to cover {@code distance} at {@code speed} blocks/s, or -1 when too slow to tell. */
    static long etaSeconds(double distance, double speed) {
        if (speed < 1.0) {
            return -1;
        }
        return Math.round(distance / speed);
    }

    /** "850 м" under 1 km, "1,2 км" above (the separator comes from {@code decimalSeparator}). */
    static String distanceText(double blocks, char decimalSeparator, String m, String km) {
        if (blocks < 1000) {
            return Math.round(blocks) + " " + m;
        }
        String text = String.format(java.util.Locale.ROOT, "%.1f", blocks / 1000.0).replace('.', decimalSeparator);
        return text + " " + km;
    }
}
