package dev.skirmish.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.Locale;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final Class<E> type;

    public EnumSetting(String id, E defaultValue) {
        super(id, defaultValue);
        this.type = defaultValue.getDeclaringClass();
    }

    public List<E> values() {
        return List.of(type.getEnumConstants());
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
        return new JsonPrimitive(get().name());
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
