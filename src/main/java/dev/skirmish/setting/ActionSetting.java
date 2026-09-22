package dev.skirmish.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A button in the module's settings (e.g. "open folder"). Not persisted. */
public class ActionSetting extends Setting<Boolean> {
    private final Runnable action;

    public ActionSetting(String id, Runnable action) {
        super(id, Boolean.FALSE);
        this.action = action;
    }

    public void run() {
        action.run();
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
