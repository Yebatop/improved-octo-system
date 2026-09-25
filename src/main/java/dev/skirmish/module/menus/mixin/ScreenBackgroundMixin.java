package dev.skirmish.module.menus.mixin;

import dev.skirmish.module.menus.LoadingBackdrop;
import dev.skirmish.module.menus.TransitionsModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** «Loading & Transitions» on the connecting, progress and disconnected screens (which use Screen's background). */
@Mixin(Screen.class)
abstract class ScreenBackgroundMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void skirmish$background(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Object self = this;
        if ((self instanceof ConnectScreen || self instanceof ProgressScreen || self instanceof DisconnectedScreen)
                && TransitionsModule.loadingScreens()) {
            LoadingBackdrop.draw(graphics);
            ci.cancel();
        }
    }
}
