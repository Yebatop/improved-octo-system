package dev.skirmish.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/**
 * Shows a vanilla key binding (by its translation name, e.g. {@code key.skirmish.killcam.replay}) as a row in the
 * module's settings. The binding itself stays a standard KeyMapping stored in options.txt, so it is not persisted
 * here and remains editable in Options → Controls.
 */
public class KeySetting extends Setting<Boolean> {
    private final String mappingName;

    public KeySetting(String id, String mappingName) {
        super(id, Boolean.FALSE);
        this.mappingName = mappingName;
    }

    public String mappingName() {
        return mappingName;
    }

    @Override
    public boolean isPersisted() {
        return false;
    }

    @Override
    public JsonElement toJson() {
        return JsonNull.INSTANCE;
    }

    @Override
    public void fromJson(JsonElement json) {
    }
}
