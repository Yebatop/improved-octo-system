package dev.skirmish.mixin;

import dev.skirmish.ui.widget.ScreenWidgets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws UI-kit widgets put on vanilla screens right before the deferred tooltip, so item tooltips stay on top
 * (Fabric's afterRender runs after the tooltip). Render-only.
 */
@Mixin(Screen.class)
abstract class ScreenMixin {
    @Inject(method = "renderWithTooltipAndSubtitles",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderDeferredElements()V"))
    private void skirmish$beforeTooltip(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ScreenWidgets.renderAttached((Screen) (Object) this, graphics);
    }
}
