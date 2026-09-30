package dev.skirmish.module.playerfx.mixin;

import dev.skirmish.module.playerfx.PlayerFxModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player FX «Плащ»: my own model (in the world and in the Studio) wears the chosen cape; only I see it. */
@Mixin(AvatarRenderer.class)
abstract class CapeMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void skirmish$cape(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (avatar != Minecraft.getInstance().player) {
            return;
        }
        PlayerFxModule m = PlayerFxModule.instance();
        Identifier texture = m == null ? null : m.capeTexture();
        if (texture == null) {
            return;
        }
        ClientAsset.Texture cape = new ClientAsset.ResourceTexture(texture, texture);
        PlayerSkin skin = state.skin;
        state.skin = new PlayerSkin(skin.body(), cape, m.capeElytra.get() ? cape : skin.elytra(), skin.model(), skin.secure());
        state.showCape = true;
    }
}
