package dev.skirmish.module.sky.mixin;

import dev.skirmish.module.sky.CustomSkyModule;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Sky: skips the vanilla overworld sky (disc, sunset glow, sun, moon, stars, dark disc) while it replaces it. */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Inject(method = {"renderSkyDisc", "renderSunriseAndSunset", "renderSunMoonAndStars", "renderDarkDisc"}, at = @At("HEAD"), cancellable = true)
    private void skirmish$replaceSky(CallbackInfo ci) {
        if (CustomSkyModule.replacesVanillaSky()) {
            ci.cancel();
        }
    }
}
