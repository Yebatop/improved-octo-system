package dev.skirmish.module.base;

import java.util.List;

/**
 * When a farm will be ripe: crops grow by random ticks, so the rate is taken from how the ripe count changed over
 * the last minutes and the rest is extrapolated. Pure Java.
 */
final class FarmMath {
    /** One look at a farm: when, how many ripe, how many in all. */
    record Sample(long at, int ripe, int total) {
    }

    private FarmMath() {
    }

    /**
     * Milliseconds until everything is ripe, 0 when it already is, -1 when it cannot be told yet (no growth seen in
     * {@code window}, or the farm changed size, e.g. you harvested).
     */
    static long etaMs(List<Sample> samples, long window) {
        if (samples.isEmpty()) {
            return -1;
        }
        Sample last = samples.getLast();
        if (last.total() > 0 && last.ripe() >= last.total()) {
            return 0;
        }
        Sample first = null;
        for (int i = samples.size() - 1; i >= 0; i--) {
            Sample s = samples.get(i);
            if (s.total() != last.total() || s.ripe() > last.ripe() || last.at() - s.at() > window) {
                break;
            }
            first = s;
        }
        if (first == null || first == last || last.ripe() <= first.ripe()) {
            return -1;
        }
        double rate = (last.ripe() - first.ripe()) / (double) (last.at() - first.at());
        return Math.round((last.total() - last.ripe()) / rate);
    }
}
