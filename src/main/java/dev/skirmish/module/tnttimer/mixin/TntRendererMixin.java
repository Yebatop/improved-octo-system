package dev.skirmish.module.tnttimer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skirmish.module.tnttimer.TntRenderHooks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render-only: after vanilla extracts a primed TNT's render state, the fuse label is attached to it; after vanilla
 * submits the TNT, the label (and the optional ring) is submitted in the same pose. Vanilla drawing is untouched.
 */
@Mixin(TntRenderer.class)
public abstract class TntRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/PrimedTnt;Lnet/minecraft/client/renderer/entity/state/TntRenderState;F)V",
            at = @At("TAIL"))
    private void skirmish$tntTimerExtract(PrimedTnt tnt, TntRenderState state, float partialTick, CallbackInfo ci) {
        TntRenderHooks.extract(tnt, state, partialTick);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/TntRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("TAIL"))
    private void skirmish$tntTimerSubmit(TntRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                         CameraRenderState camera, CallbackInfo ci) {
        TntRenderHooks.submit(state, poseStack, collector, camera);
    }
}
