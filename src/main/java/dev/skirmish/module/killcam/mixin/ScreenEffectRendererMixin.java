package dev.skirmish.module.killcam.mixin;

import dev.skirmish.module.killcam.KillCamHooks;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fire / in-wall / underwater overlays and the totem animation belong to the dead player, not to the replay camera. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderScreenEffect", at = @At("HEAD"), cancellable = true)
    private void skirmish$killcamNoOverlay(boolean sleeping, float partialTick, SubmitNodeCollector collector, CallbackInfo ci) {
        if (KillCamHooks.active()) {
            ci.cancel();
        }
    }
}
