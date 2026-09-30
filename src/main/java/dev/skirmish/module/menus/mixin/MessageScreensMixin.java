package dev.skirmish.module.menus.mixin;

import dev.skirmish.module.menus.LoadingBackdrop;
import dev.skirmish.module.menus.TransitionsModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** «Loading & Transitions» on message screens («Сохранение мира…», «Выход с сервера…»). */
@Mixin(GenericMessageScreen.class)
abstract class MessageScreensMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void skirmish$background(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (TransitionsModule.loadingScreens()) {
            LoadingBackdrop.draw(graphics);
            ci.cancel();
        }
    }
}
