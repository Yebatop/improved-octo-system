package dev.skirmish.module.killcam.mixin;

import dev.skirmish.module.killcam.KillCamHooks;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Camera.setup runs once per frame (GameRenderer.updateCamera); the replay camera replaces its result. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void skirmish$killcamCamera(Level level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        double[] pose = KillCamHooks.frame(partialTick);
        if (pose != null) {
            setRotation((float) pose[3], (float) pose[4]);
            setPosition(new Vec3(pose[0], pose[1], pose[2]));
        }
    }
}
