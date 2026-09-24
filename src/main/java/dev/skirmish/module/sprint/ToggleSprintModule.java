package dev.skirmish.module.sprint;

import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.util.OptionLedger;
import dev.skirmish.util.VanillaOverrides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Switches on vanilla's own "Sprint: Toggle" option (Options → Controls) while enabled and puts the user's value
 * back when disabled. The player still presses the sprint key once to start sprinting; nothing is pressed or sent
 * by the mod. The indicator only reads the vanilla sprint key's toggled state.
 */
public final class ToggleSprintModule extends Module {
    public static final String ID = "toggle_sprint";

    final BoolSetting indicator = add(new BoolSetting("indicator", true));
    /** The user's own "Sprint: Toggle" value while the module overrides it; hidden from the menu. */
    private final OptionLedger.Store saved = add(new OptionLedger.Store("saved_vanilla"));

    private final VanillaOverrides overrides;
    private boolean dirty;

    public ToggleSprintModule() {
        super(ID, false);
        overrides = new VanillaOverrides(new OptionLedger(saved)).bool("toggleSprint", Options::toggleSprint, () -> true);
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
        Hud.get().register(new SprintIndicator(this));
    }

    @Override
    protected void onEnable() {
        dirty = true;
    }

    @Override
    protected void onDisable() {
        dirty = false;
        Options options = Minecraft.getInstance().options;
        if (options != null && overrides.restore(options)) {
            options.save();
            log("restored the user's Sprint: Toggle option");
        }
    }

    @Override
    public void tick() {
        Options options = Minecraft.getInstance().options;
        if (dirty && options != null) {
            dirty = false;
            if (overrides.apply(options)) {
                options.save();
                log("vanilla Sprint: Toggle switched on");
            }
        }
    }

    /** Vanilla toggle mode is on and the sprint key is toggled on. */
    boolean sprintToggledOn() {
        Options options = Minecraft.getInstance().options;
        return options != null && options.toggleSprint().get() && options.keySprint.isDown();
    }
}
