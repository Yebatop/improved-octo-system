package dev.skirmish.hud;

import dev.skirmish.module.Module;
import dev.skirmish.setting.ActionSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.ui.Theme;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/** Core module «Интерфейс»: accent color of the UI kit and the HUD layout editor. */
public final class InterfaceModule extends Module {
    public static final String ID = "interface";

    /** Values match the {@code accents} keys of theme.json. */
    public enum Accent {
        VIOLET, BLUE, PINK, GREEN
    }

    final EnumSetting<Accent> accent = add(new EnumSetting<>("accent", Accent.VIOLET));
    final ActionSetting editHud = add(new ActionSetting("edit_hud", () -> {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new HudEditScreen(mc.screen));
    }));
    final ActionSetting resetHud = add(new ActionSetting("reset_hud", () -> Hud.get().resetAll()));

    public InterfaceModule() {
        super(ID, true);
        accent.onChange(value -> Theme.get().setAccent(value.name().toLowerCase(Locale.ROOT)));
    }

    @Override
    public void onInitialize() {
        Theme.get().setAccent(accent.get().name().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean canToggle() {
        return false;
    }

    @Override
    public int menuOrder() {
        return 20;
    }
}
