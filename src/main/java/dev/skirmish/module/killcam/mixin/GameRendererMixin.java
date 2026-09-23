package dev.skirmish.module.killcam.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skirmish.module.killcam.KillCamHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** During a replay the camera entity is still the dead local player: drop its death/hurt tilt, view bob and death FOV zoom. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void skirmish$killcamNoHurtTilt(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (KillCamHooks.active()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void skirmish$killcamNoBob(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (KillCamHooks.active()) {
            ci.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void skirmish$killcamFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (KillCamHooks.active()) {
            cir.setReturnValue(useFovSetting ? (float) Minecraft.getInstance().options.fov().get().intValue() : 70.0F);
        }
    }
}
