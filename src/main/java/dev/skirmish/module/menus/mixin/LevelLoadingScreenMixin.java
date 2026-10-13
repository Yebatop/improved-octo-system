package dev.skirmish.module.menus.mixin;

import dev.skirmish.module.menus.LoadingBackdrop;
import dev.skirmish.module.menus.TransitionsModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** «Loading & Transitions» on the terrain loading screen: Skirmish backdrop, no chunk grid, a rounded accent bar. */
@Mixin(LevelLoadingScreen.class)
abstract class LevelLoadingScreenMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void skirmish$background(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (TransitionsModule.loadingScreens()) {
            LoadingBackdrop.draw(graphics);
            ci.cancel();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;renderChunks(Lnet/minecraft/client/gui/GuiGraphics;IIIILnet/minecraft/server/level/progress/ChunkLoadStatusView;)V"))
    private void skirmish$chunks(GuiGraphics graphics, int x, int y, int size, int spacing, net.minecraft.server.level.progress.ChunkLoadStatusView view) {
        if (!TransitionsModule.loadingScreens()) {
            LevelLoadingScreen.renderChunks(graphics, x, y, size, spacing, view);
        }
    }

    @Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
    private void skirmish$bar(GuiGraphics graphics, int x, int y, int w, int h, float progress, CallbackInfo ci) {
        if (TransitionsModule.loadingScreens()) {
            LoadingBackdrop.bar(graphics, x, y, w, h, progress);
            ci.cancel();
        }
    }
}
