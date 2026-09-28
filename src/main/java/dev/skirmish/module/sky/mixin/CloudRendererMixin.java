package dev.skirmish.module.sky.mixin;

import dev.skirmish.module.sky.CustomSkyModule;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Sky: no vanilla clouds over the new sky (only on this screen; the game option is untouched). */
@Mixin(CloudRenderer.class)
abstract class CloudRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void skirmish$hideClouds(CallbackInfo ci) {
        if (CustomSkyModule.hidesClouds()) {
            ci.cancel();
        }
    }
}
