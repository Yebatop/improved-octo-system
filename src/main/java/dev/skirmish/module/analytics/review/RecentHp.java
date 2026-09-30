package dev.skirmish.module.analytics.review;

import java.util.ArrayDeque;

/** My health over the last {@code keepMs}, sampled every client tick (unchanged values are skipped). Pure Java. */
final class RecentHp {
    private record Sample(long timeMs, float value) {
    }

    private final long keepMs;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();

    RecentHp(long keepMs) {
        this.keepMs = keepMs;
    }

    void add(long timeMs, float value) {
        Sample last = samples.peekLast();
        if (last != null && last.value() == value && timeMs - last.timeMs() < 500) {
            return;
        }
        samples.addLast(new Sample(timeMs, value));
        // Keep one sample older than the window so the curve starts at the right height.
        while (samples.size() > 1) {
            Sample first = samples.pollFirst();
            Sample second = samples.peekFirst();
            if (second == null || timeMs - second.timeMs() <= keepMs) {
                samples.addFirst(first);
                break;
            }
        }
    }

    /** Samples in {@code [fromMs, toMs]} as a series, ending with {@code endValue} at {@code toMs}. */
    HpSeries series(long fromMs, long toMs, float endValue) {
        HpSeries full = new HpSeries(Math.max(4, samples.size() + 2), 1);
        for (Sample s : samples) {
            full.add(s.timeMs(), s.value());
        }
        HpSeries out = full.window(fromMs, toMs);
        out.add(toMs, endValue);
        return out;
    }

    int size() {
        return samples.size();
    }

    void clear() {
        samples.clear();
    }
}
