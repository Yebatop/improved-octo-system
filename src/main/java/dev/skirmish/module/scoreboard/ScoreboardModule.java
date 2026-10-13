package dev.skirmish.module.scoreboard;

import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * «Scoreboard»: the server's sidebar (HolyWorld's board) in a Skirmish panel instead of vanilla's grey box: a title
 * strip with an accent underline, «Label: value» lines as two columns with an icon (balance, clan, ping, kills…) in
 * the mod's font, blank lines turned into thin dividers and the red score numbers hidden. It is a HUD element, so
 * it can be moved and scaled in the HUD editor. The lines keep the server's own text and colours; lines with
 * resource-pack glyphs keep the game font so the glyphs still show. Render-only; Feature Control id
 * {@code scoreboard}; off, vanilla draws the sidebar as usual.
 */
public final class ScoreboardModule extends Module {
    public static final String ID = "scoreboard";

    /** Two columns in the mod's font, or the server's lines as they are in the game font. */
    public enum Look {
        MODERN, CLASSIC
    }

    final EnumSetting<Look> look = add(new EnumSetting<>("look", Look.MODERN));
    final BoolSetting title = add(new BoolSetting("title", true));
    final BoolSetting numbers = add(new BoolSetting("numbers", false));
    final BoolSetting dividers = add(new BoolSetting("dividers", true));
    final BoolSetting shadow = add(new BoolSetting("shadow", true));
    final NumberSetting opacity = add(new NumberSetting("opacity", 85, 0, 100, 5).unit("%"));

    public ScoreboardModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.INTERFACE;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        Hud.get().register(new ScoreboardHud(this));
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> (HudElement) (graphics, delta) -> {
            if (!isEnabled()) {
                vanilla.render(graphics, delta);
            }
        });
    }
}
