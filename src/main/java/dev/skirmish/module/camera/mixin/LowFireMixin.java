package dev.skirmish.module.camera.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skirmish.module.camera.LowFireModule;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code ScreenEffectRenderer.renderFire(PoseStack, MultiBufferSource, TextureAtlasSprite)} (static): the fire quads
 * are moved down by pushing a translated pose at HEAD and popping it at TAIL, and the alpha of their vertices
 * (vanilla 0.9, the 4th argument of every {@code setColor(FFFF)} call) is scaled. Render only.
 */
@Mixin(ScreenEffectRenderer.class)
abstract class LowFireMixin {
    @Unique
    private static boolean skirmish$pushed;

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void skirmish$lowFireBegin(PoseStack poseStack, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        skirmish$pushed = false;
        if (LowFireModule.hidden()) {
            ci.cancel();
            return;
        }
        float offset = LowFireModule.offset();
        if (offset > 0f) {
            poseStack.pushPose();
            poseStack.translate(0.0F, -offset, 0.0F);
            skirmish$pushed = true;
        }
    }

    @Inject(method = "renderFire", at = @At("TAIL"))
    private static void skirmish$lowFireEnd(PoseStack poseStack, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (skirmish$pushed) {
            skirmish$pushed = false;
            poseStack.popPose();
        }
    }

    @ModifyArg(method = "renderFire", index = 3, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static float skirmish$lowFireAlpha(float alpha) {
        return alpha * LowFireModule.alpha();
    }
}
