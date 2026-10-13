package dev.skirmish.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String id, boolean defaultValue) {
        super(id, defaultValue);
    }

    /** Off while the setting's feature is blocked; the saved value is kept for when it is unblocked. */
    @Override
    public Boolean get() {
        return !isBlocked() && super.get();
    }

    public void toggle() {
        set(!get());
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(super.get());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
            load(json.getAsBoolean());
        }
    }
}
