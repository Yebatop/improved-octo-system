package dev.skirmish.module.zoom.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.skirmish.module.zoom.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code getFov(Camera, float, boolean)}: with {@code useFovSetting} true it is the world FOV (projection,
 * culling, projectPointToScreen); with false it is the hand's fixed 70°, which stays unzoomed. Render only.
 */
@Mixin(GameRenderer.class)
abstract class ZoomGameRendererMixin {
    @Shadow
    public abstract boolean isPanoramicMode();

    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private float skirmish$zoom(float fov, Camera camera, float partialTick, boolean useFovSetting) {
        if (!useFovSetting || isPanoramicMode()) {
            return fov;
        }
        return ZoomModule.fov(fov, partialTick);
    }
}
