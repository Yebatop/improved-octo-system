package dev.skirmish.module.zoom.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skirmish.module.zoom.ZoomModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional: no first-person hands in front of the zoomed view. Render only. */
@Mixin(ItemInHandRenderer.class)
abstract class ZoomItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void skirmish$zoomHideHands(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light,
                                       CallbackInfo ci) {
        if (ZoomModule.hidesHand()) {
            ci.cancel();
        }
    }
}
