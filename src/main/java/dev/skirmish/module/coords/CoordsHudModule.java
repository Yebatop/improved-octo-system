package dev.skirmish.module.coords;

import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;

/**
 * Coordinates HUD: position, facing and the matching Nether / Overworld coordinates. The «Стример» option hides
 * every coordinate (only the facing stays) for recording and streaming. Read and render only.
 */
public final class CoordsHudModule extends Module {
    public static final String ID = "coords_hud";

    final BoolSetting showFacing = add(new BoolSetting("show_facing", true));
    final BoolSetting showConverted = add(new BoolSetting("show_converted", true));
    final BoolSetting streamer = add(new BoolSetting("streamer", false));

    public CoordsHudModule() {
        super(ID, false);
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        Hud.get().register(new CoordsHud(this));
    }
}
