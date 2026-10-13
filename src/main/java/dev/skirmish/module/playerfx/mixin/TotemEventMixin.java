package dev.skirmish.module.playerfx.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import dev.skirmish.module.playerfx.PlayerFxModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Player FX's totem: when its own effect replaces vanilla's, the vanilla totem particles, sound and (if chosen) the
 * big totem in the middle of the screen are skipped for that pop. Only what this client draws and plays changes.
 */
@Mixin(ClientPacketListener.class)
abstract class TotemEventMixin {
    @WrapWithCondition(method = "handleEntityEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"))
    private boolean skirmish$totemParticles(ParticleEngine engine, Entity target, ParticleOptions options, int lifetime) {
        PlayerFxModule m = PlayerFxModule.instance();
        return m == null || !m.replacesTotemParticles(target);
    }

    @WrapWithCondition(method = "handleEntityEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private boolean skirmish$totemSound(ClientLevel level, double x, double y, double z, SoundEvent sound, SoundSource source,
                                        float volume, float pitch, boolean delayed, @Local Entity entity) {
        PlayerFxModule m = PlayerFxModule.instance();
        return m == null || !m.replacesTotemSound(entity);
    }

    @WrapWithCondition(method = "handleEntityEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;displayItemActivation(Lnet/minecraft/world/item/ItemStack;)V"))
    private boolean skirmish$totemItem(GameRenderer renderer, ItemStack stack) {
        PlayerFxModule m = PlayerFxModule.instance();
        return m == null || !m.hidesTotemItem();
    }
}
