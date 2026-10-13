package dev.skirmish.module.effects;

import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * The local player's active effects as a HUD list: icon, name with level and time left (effects the server marks
 * as hidden stay hidden). Optionally hides vanilla's top-right effect icons, through Fabric's HUD registry.
 * Read and render only.
 */
public final class EffectHudModule extends Module {
    public static final String ID = "effect_hud";

    final BoolSetting hideVanilla = add(new BoolSetting("hide_vanilla", false));
    final BoolSetting showAmbient = add(new BoolSetting("show_ambient", true));
    final NumberSetting warnSeconds = add(new NumberSetting("warn_seconds", 10, 0, 60, 1).unit(" s"));

    public EffectHudModule() {
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
        Hud.get().register(new EffectHud(this));
        HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, vanilla -> (HudElement) (graphics, delta) -> {
            if (!(isEnabled() && hideVanilla.get())) {
                vanilla.render(graphics, delta);
            }
        });
    }
}
