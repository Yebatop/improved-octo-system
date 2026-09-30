package dev.skirmish.module.zoom.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.skirmish.module.zoom.ZoomModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouse input while zooming. turnPlayer: the local copy of the sensitivity option (its only
 * {@code Double.doubleValue()} call) is scaled so the view turns slower while zoomed; the option is not written.
 * onScroll: with no screen open and the zoom key held, the wheel changes the zoom factor instead of the hotbar
 * slot. Nothing else is read or changed.
 */
@Mixin(MouseHandler.class)
abstract class ZoomMouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyExpressionValue(method = "turnPlayer", at = @At(value = "INVOKE", target = "Ljava/lang/Double;doubleValue()D", ordinal = 0))
    private double skirmish$zoomSensitivity(double sensitivity) {
        return ZoomModule.sensitivity(sensitivity);
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void skirmish$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == minecraft.getWindow().handle() && minecraft.screen == null && minecraft.getOverlay() == null
                && minecraft.player != null && ZoomModule.scroll(vertical)) {
            ci.cancel();
        }
    }
}
