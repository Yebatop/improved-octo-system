package dev.skirmish.module.playerfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skirmish.module.playerfx.PlayerFxModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player FX «Предмет в руке»: moves and scales the held item in first person right before it is drawn. */
@Mixin(ItemInHandRenderer.class)
abstract class HeldItemMixin {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    private void skirmish$heldItem(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing,
                                   ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        PlayerFxModule m = PlayerFxModule.instance();
        float[] t = m == null ? null : m.handTransform();
        if (t == null) {
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        pose.translate(t[0] * side, t[1], t[2]);
        pose.scale(t[3], t[3], t[3]);
    }
}
