package dev.skirmish.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.Locale;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final Class<E> type;
    private final java.util.Map<E, String> valueFeatures = new java.util.HashMap<>();

    public EnumSetting(String id, E defaultValue) {
        super(id, defaultValue);
        this.type = defaultValue.getDeclaringClass();
    }

    public List<E> values() {
        return List.of(type.getEnumConstants());
    }

    /** Ties one value to a Feature Control id: while blocked the value is hidden and never returned by {@link #get()}. */
    public EnumSetting<E> valueFeature(E value, String featureId) {
        valueFeatures.put(value, featureId);
        FeatureGate.declare(featureId);
        return this;
    }

    public boolean isValueBlocked(E value) {
        return FeatureGate.isBlocked(valueFeatures.get(value));
    }

    /** Values that may be shown and chosen right now. */
    public List<E> visibleValues() {
        return values().stream().filter(v -> !isValueBlocked(v)).toList();
    }

    /** The saved value, or the default (then the first allowed value) while the saved one is blocked. */
    @Override
    public E get() {
        E value = super.get();
        if (!isValueBlocked(value)) {
            return value;
        }
        if (!isValueBlocked(defaultValue())) {
            return defaultValue();
        }
        List<E> allowed = visibleValues();
        return allowed.isEmpty() ? value : allowed.getFirst();
    }

    public void cycle() {
        E[] constants = type.getEnumConstants();
        set(constants[(get().ordinal() + 1) % constants.length]);
    }

    /** Translation key of a single value: {@code <setting key>.<value in lower case>}. */
    public String valueTranslationKey(E value) {
        return translationKey() + "." + value.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(super.get().name());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) {
            return;
        }
        String name = json.getAsString();
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name)) {
                load(constant);
                return;
            }
        }
    }
}
