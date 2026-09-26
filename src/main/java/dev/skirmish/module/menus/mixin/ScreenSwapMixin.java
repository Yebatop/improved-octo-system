package dev.skirmish.module.menus.mixin;

import dev.skirmish.module.menus.MainMenuModule;
import dev.skirmish.module.menus.PauseMenuModule;
import dev.skirmish.module.menus.SkirmishPauseScreen;
import dev.skirmish.module.menus.SkirmishTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps vanilla's title screen and pause menu for Skirmish's when those modules are on, at the start of setScreen (a
 * null screen with no world, which vanilla turns into its title screen, is swapped too). F3+Esc (pause without
 * menu) keeps vanilla.
 */
@Mixin(Minecraft.class)
abstract class ScreenSwapMixin {
    @ModifyVariable(method = "setScreen", argsOnly = true, at = @At("HEAD"))
    private Screen skirmish$swapScreen(Screen screen) {
        // A null screen with no world becomes vanilla's title screen further down: take its place here.
        if ((screen instanceof TitleScreen || screen == null && ((Minecraft) (Object) this).level == null) && MainMenuModule.active()) {
            return new SkirmishTitleScreen();
        }
        if (screen instanceof PauseScreen pause && pause.showsPauseMenu() && PauseMenuModule.active()) {
            return new SkirmishPauseScreen();
        }
        return screen;
    }
}
