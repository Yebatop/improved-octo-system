package dev.skirmish.module.autosprint;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.KeySetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Sprint whenever you move forward, as if the sprint key were held: {@code mixin.KeyboardInputMixin} reports the
 * sprint key as down while the module is on. Vanilla still decides whether sprinting can start (forward only, enough
 * food, not sneaking, not using an item, not blind), so nothing else changes. Feature Control id {@code auto_sprint}.
 */
public final class AutoSprintModule extends Module {
    public static final String ID = "auto_sprint";
    private static @Nullable AutoSprintModule instance;

    final KeySetting key = add(new KeySetting("key", "key.skirmish.auto_sprint.toggle"));

    public AutoSprintModule() {
        super(ID, true);
        instance = this;
    }

    @Override
    public Category category() {
        return Category.UTILITY;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        // The toggle key works while the module is off too, so it is polled here instead of in tick().
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (SkirmishKeys.AUTO_SPRINT_TOGGLE.consumeClick()) {
                if (isBlocked() || mc.player == null) {
                    continue;
                }
                toggle();
                mc.player.displayClientMessage(Component.translatable(isEnabled()
                        ? "skirmish.auto_sprint.toggled_on" : "skirmish.auto_sprint.toggled_off"), true);
            }
        });
    }

    /** Called from the KeyboardInput mixin with the real sprint key state. */
    public static boolean sprintKey(boolean down) {
        AutoSprintModule module = instance;
        return down || module != null && module.isEnabled();
    }
}
