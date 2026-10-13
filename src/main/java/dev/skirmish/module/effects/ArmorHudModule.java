package dev.skirmish.module.effects;

import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;

/**
 * Compact durability of the local player's own armor and hand items (never anyone else's). Read and render only.
 */
public final class ArmorHudModule extends Module {
    public static final String ID = "armor_hud";

    enum Mode {
        PERCENT, REMAINING
    }

    final EnumSetting<Mode> mode = add(new EnumSetting<>("mode", Mode.PERCENT));
    final BoolSetting showHands = add(new BoolSetting("show_hands", true));
    final NumberSetting warnPercent = add(new NumberSetting("warn_percent", 15, 5, 50, 5).unit("%"));

    public ArmorHudModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.COMBAT;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        Hud.get().register(new ArmorHud(this));
    }
}
