package dev.skirmish.module.lag;

/** Pure color choice for the lag pill (theme color tokens; unit tested). */
final class LagText {
    private LagText() {
    }

    static String tpsColor(double tps) {
        return tps >= 18.0 ? "good" : tps >= 14.0 ? "warn" : "bad";
    }

    static String pingColor(int ping) {
        return ping < 120 ? "good" : ping < 250 ? "warn" : "bad";
    }

    /** Normal traffic arrives at least once a second (world time), so a second of silence is already odd. */
    static String silenceColor(long silenceMs, long warnMs) {
        if (warnMs > 0 && silenceMs > warnMs) {
            return "bad";
        }
        return silenceMs > 1000 ? "warn" : "text";
    }

    /** The dot shows the worst of the three. */
    static String worst(String[] colors) {
        String worst = "good";
        for (String c : colors) {
            if (c.equals("bad")) {
                return "bad";
            }
            if (c.equals("warn")) {
                worst = "warn";
            }
        }
        return worst;
    }
}
