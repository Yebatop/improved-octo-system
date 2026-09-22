package dev.skirmish.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** A number in [min, max] snapped to {@code step}; shown as a slider. */
public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;
    private String unit = "";

    public NumberSetting(String id, double defaultValue, double min, double max, double step) {
        super(id, defaultValue);
        if (!(min < max) || step <= 0) {
            throw new IllegalArgumentException("Invalid range for " + id);
        }
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public NumberSetting unit(String unit) {
        this.unit = unit;
        return this;
    }

    public String unit() {
        return unit;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public int getInt() {
        return (int) Math.round(get());
    }

    public float getFloat() {
        return get().floatValue();
    }

    public boolean isInteger() {
        return step == Math.rint(step) && min == Math.rint(min);
    }

    @Override
    protected Double sanitize(Double candidate) {
        if (candidate == null || candidate.isNaN() || candidate.isInfinite()) {
            return defaultValue();
        }
        double clamped = Math.max(min, Math.min(max, candidate));
        double snapped = min + Math.round((clamped - min) / step) * step;
        snapped = Math.max(min, Math.min(max, snapped));
        // Strip floating point noise such as 0.30000000000000004.
        return Math.round(snapped * 1e6) / 1e6;
    }

    /** Slider position in [0, 1]. */
    public double toSlider() {
        return (get() - min) / (max - min);
    }

    public void setFromSlider(double position) {
        set(min + Math.max(0, Math.min(1, position)) * (max - min));
    }

    public String format() {
        double v = get();
        String text = isInteger() ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, stepDecimals() == 1 ? "%.1f" : "%.2f", v);
        return unit.isEmpty() ? text : text + unit;
    }

    private int stepDecimals() {
        return step >= 0.1 ? 1 : 2;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            load(json.getAsDouble());
        }
    }
}
