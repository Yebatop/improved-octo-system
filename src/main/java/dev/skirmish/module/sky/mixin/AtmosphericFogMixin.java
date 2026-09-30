package dev.skirmish.module.sky.mixin;

import dev.skirmish.module.sky.CustomSkyModule;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom Sky: the air fog takes the new sky's horizon colour, so distant land fades into it without a seam. */
@Mixin(AtmosphericFogEnvironment.class)
abstract class AtmosphericFogMixin {
    @Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
    private void skirmish$tintFog(ClientLevel level, Camera camera, int renderDistance, float partial, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CustomSkyModule.fogColor(cir.getReturnValueI()));
    }
}
