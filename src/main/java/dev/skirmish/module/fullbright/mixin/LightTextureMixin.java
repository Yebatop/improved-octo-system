package dev.skirmish.module.fullbright.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.skirmish.module.fullbright.FullbrightModule;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * updateLightTexture reads {@code options.darknessEffectScale().get().floatValue()} (ordinal 0) and then
 * {@code options.gamma().get().floatValue()} (ordinal 1). Only the local copy of the gamma read is changed; the
 * option itself (clamped to 0..1 by vanilla) is never written.
 */
@Mixin(LightTexture.class)
abstract class LightTextureMixin {
    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
    private float skirmish$fullbright(float gamma, @Local(argsOnly = true) float partialTick) {
        return FullbrightModule.gamma(gamma, partialTick);
    }
}
