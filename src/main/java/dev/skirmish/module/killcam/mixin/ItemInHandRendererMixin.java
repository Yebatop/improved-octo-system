package dev.skirmish.module.killcam.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skirmish.module.killcam.KillCamHooks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The dead player's first-person hands would float in front of the replay camera. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void skirmish$killcamNoHands(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light,
                                        CallbackInfo ci) {
        if (KillCamHooks.active()) {
            ci.cancel();
        }
    }
}
