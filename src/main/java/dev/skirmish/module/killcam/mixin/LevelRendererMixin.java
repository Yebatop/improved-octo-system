package dev.skirmish.module.killcam.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.skirmish.module.killcam.KillCamHooks;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * extractVisibleEntities skips entities whose render section is not compiled (chunk not loaded). A saved replay can
 * be watched away from where it was recorded, so the replay's own fake players are drawn there anyway. Only the
 * mod's client-side fakes are affected; real entities keep vanilla's rule.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiledAndVisible(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean skirmish$killcamFakesAnywhere(boolean visible, @Local Entity entity) {
        return visible || KillCamHooks.showAnywhere(entity);
    }
}
