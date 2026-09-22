package dev.skirmish.setting;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A single persisted module option. Pure Java (no Minecraft classes) so it can be unit tested.
 * The GUI builds widgets from the concrete subclasses; the config manager (de)serializes them via JSON.
 */
public abstract class Setting<T> {
    private final String id;
    private final T defaultValue;
    private T value;
    private final List<Consumer<T>> listeners = new ArrayList<>();
    private BooleanSupplier visible = () -> true;
    private String translationKey;
    private Runnable saveHook = () -> {};

    protected Setting(String id, T defaultValue) {
        this.id = Objects.requireNonNull(id);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.translationKey = id;
    }

    public final String id() {
        return id;
    }

    public T get() {
        return value;
    }

    public T defaultValue() {
        return defaultValue;
    }

    /** Sets the value (sanitized), notifies listeners and schedules a config save when it changed. */
    public void set(T newValue) {
        T sanitized = sanitize(newValue);
        if (Objects.equals(value, sanitized)) {
            return;
        }
        value = sanitized;
        for (Consumer<T> listener : listeners) {
            listener.accept(sanitized);
        }
        saveHook.run();
    }

    /** Assigns a value loaded from disk: sanitized, listeners notified, no save scheduled. */
    protected void load(T loaded) {
        T sanitized = sanitize(loaded);
        if (Objects.equals(value, sanitized)) {
            return;
        }
        value = sanitized;
        for (Consumer<T> listener : listeners) {
            listener.accept(sanitized);
        }
    }

    public void reset() {
        set(defaultValue);
    }

    protected T sanitize(T candidate) {
        return candidate == null ? defaultValue : candidate;
    }

    public Setting<T> onChange(Consumer<T> listener) {
        listeners.add(listener);
        return this;
    }

    public Setting<T> visibleWhen(BooleanSupplier condition) {
        this.visible = condition;
        return this;
    }

    public boolean isVisible() {
        return visible.getAsBoolean();
    }

    /** Whether the value is written to config.json. */
    public boolean isPersisted() {
        return true;
    }

    public String translationKey() {
        return translationKey;
    }

    /** Called by {@code Module.add}: gives the setting its translation key and the config save hook. */
    public void bind(String moduleId, Runnable saveHook) {
        this.translationKey = "skirmish.module." + moduleId + ".setting." + id;
        this.saveHook = saveHook;
    }

    public abstract JsonElement toJson();

    /** Reads a value written by {@link #toJson()}; invalid input keeps the current value. */
    public abstract void fromJson(JsonElement json);
}
