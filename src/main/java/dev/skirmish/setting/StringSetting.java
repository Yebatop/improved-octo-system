package dev.skirmish.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Free text; with {@link #password()} the menu masks the value. */
public class StringSetting extends Setting<String> {
    private final int maxLength;
    private final boolean password;

    public StringSetting(String id, String defaultValue, int maxLength, boolean password) {
        super(id, defaultValue);
        this.maxLength = maxLength;
        this.password = password;
    }

    public StringSetting(String id, String defaultValue) {
        this(id, defaultValue, 256, false);
    }

    public int maxLength() {
        return maxLength;
    }

    public boolean password() {
        return password;
    }

    @Override
    protected String sanitize(String candidate) {
        if (candidate == null) {
            return defaultValue();
        }
        return candidate.length() > maxLength ? candidate.substring(0, maxLength) : candidate;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            load(json.getAsString());
        }
    }
}
