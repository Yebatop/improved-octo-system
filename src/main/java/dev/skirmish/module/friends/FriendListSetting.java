package dev.skirmish.module.friends;

import com.google.gson.JsonElement;
import dev.skirmish.setting.Setting;

/**
 * The friend list as a module setting, so it is saved to config.json with the rest of the module. It has no menu
 * control (edited with the command and the key) and is not cleared by «reset module»: it is data, not an option.
 */
public final class FriendListSetting extends Setting<FriendList> {
    public FriendListSetting(String id) {
        super(id, FriendList.EMPTY);
        visibleWhen(() -> false);
    }

    /** Resetting the module's options keeps the friends. */
    @Override
    public void reset() {
    }

    /** Removes everyone (explicit command only). */
    public void clear() {
        set(FriendList.EMPTY);
    }

    @Override
    public JsonElement toJson() {
        return get().toJson();
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonArray()) {
            load(FriendList.fromJson(json));
        }
    }
}
