package dev.skirmish.module.analytics.review;

/**
 * Health samples (time, health + absorption) of one entity for a chart. Bounded: a sample equal to the previous one
 * is skipped until {@code step} ms passed, and when full every other sample is dropped (the last one is kept) and
 * the step doubles, so a long fight keeps its shape. Pure Java, unit tested.
 */
public final class HpSeries {
    private final long[] times;
    private final float[] values;
    private int size;
    private long stepMs;
    private float max;

    public HpSeries(int capacity, long stepMs) {
        if (capacity < 4) {
            throw new IllegalArgumentException("capacity");
        }
        this.times = new long[capacity];
        this.values = new float[capacity];
        this.stepMs = Math.max(1, stepMs);
    }

    public void add(long timeMs, float value) {
        if (!Float.isFinite(value)) {
            return;
        }
        if (size > 0) {
            long lastT = times[size - 1];
            if (timeMs < lastT) {
                return;
            }
            if (values[size - 1] == value && timeMs - lastT < stepMs) {
                return;
            }
        }
        if (size == times.length) {
            decimate();
        }
        times[size] = timeMs;
        values[size] = value;
        size++;
        max = Math.max(max, value);
    }

    /** Keeps samples 0, 2, 4, ... and the last one; doubles the step. */
    private void decimate() {
        int last = size - 1;
        int n = 0;
        for (int i = 0; i < last; i += 2) {
            times[n] = times[i];
            values[n] = values[i];
            n++;
        }
        times[n] = times[last];
        values[n] = values[last];
        size = n + 1;
        stepMs *= 2;
    }

    public int size() {
        return size;
    }

    public long time(int i) {
        return times[i];
    }

    public float value(int i) {
        return values[i];
    }

    /** Largest value ever added. */
    public float max() {
        return max;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Value at {@code timeMs}: the latest sample not after it (the first sample before the series starts), NaN if empty. */
    public float at(long timeMs) {
        if (size == 0) {
            return Float.NaN;
        }
        float v = values[0];
        for (int i = 0; i < size && times[i] <= timeMs; i++) {
            v = values[i];
        }
        return v;
    }

    /** A copy of the samples in {@code [fromMs, toMs]}, starting with the value at {@code fromMs}. */
    public HpSeries window(long fromMs, long toMs) {
        HpSeries out = new HpSeries(Math.max(4, size + 1), 1);
        if (size == 0) {
            return out;
        }
        out.add(fromMs, at(fromMs));
        for (int i = 0; i < size; i++) {
            if (times[i] > fromMs && times[i] <= toMs) {
                out.add(times[i], values[i]);
            }
        }
        return out;
    }
}
