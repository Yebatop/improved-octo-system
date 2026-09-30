package dev.skirmish.util;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Vanilla options a module sets while it is on (exactly as if the user changed them in Options), with the user's
 * own values restored when it is turned off or blocked. See {@link OptionLedger} for how the originals are kept.
 * Client thread only.
 */
public final class VanillaOverrides {
    private final OptionLedger ledger;
    private final Map<String, Binding<?>> bindings = new LinkedHashMap<>();

    private record Binding<T>(Function<Options, OptionInstance<T>> option, Function<String, T> parse, Supplier<T> desired) {
    }

    public VanillaOverrides(OptionLedger ledger) {
        this.ledger = ledger;
    }

    public VanillaOverrides bool(String key, Function<Options, OptionInstance<Boolean>> option, Supplier<Boolean> desired) {
        bindings.put(key, new Binding<>(option, Boolean::parseBoolean, desired));
        return this;
    }

    public VanillaOverrides number(String key, Function<Options, OptionInstance<Double>> option, Supplier<Double> desired) {
        bindings.put(key, new Binding<>(option, Double::parseDouble, desired));
        return this;
    }

    /** Writes every desired value; returns whether an option changed (then options.txt should be saved). */
    public boolean apply(Options options) {
        boolean changed = false;
        for (Map.Entry<String, Binding<?>> e : bindings.entrySet()) {
            changed |= apply(options, e.getKey(), e.getValue());
        }
        return changed;
    }

    private <T> boolean apply(Options options, String key, Binding<T> binding) {
        OptionInstance<T> option = binding.option().apply(options);
        T current = option.get();
        T desired = binding.desired().get();
        if (desired == null) {
            return false;
        }
        ledger.remember(key, String.valueOf(current));
        boolean changed = !Objects.equals(current, desired);
        if (changed) {
            option.set(desired);
        }
        // OptionInstance.set may refuse an invalid value; record what is really there.
        ledger.written(key, String.valueOf(option.get()));
        return changed;
    }

    /** Puts back the user's values (unless they changed an option by hand meanwhile); returns whether one changed. */
    public boolean restore(Options options) {
        boolean changed = false;
        for (String key : ledger.keys()) {
            Binding<?> binding = bindings.get(key);
            if (binding != null) {
                changed |= restore(options, key, binding);
            }
        }
        ledger.clear();
        return changed;
    }

    private <T> boolean restore(Options options, String key, Binding<T> binding) {
        OptionInstance<T> option = binding.option().apply(options);
        String value = ledger.restoreValue(key, String.valueOf(option.get()));
        if (value == null) {
            return false;
        }
        T parsed;
        try {
            parsed = binding.parse().apply(value);
        } catch (RuntimeException e) {
            return false;
        }
        if (Objects.equals(option.get(), parsed)) {
            return false;
        }
        option.set(parsed);
        return true;
    }
}
