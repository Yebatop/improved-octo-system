package dev.skirmish.module.enemycd.mixin;

import dev.skirmish.module.enemycd.EnemyCooldownsModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only: your own item cooldowns as the server sets them (to learn how long each lasts on this server) and
 * sounds played at a position (a shield knocked out). TAIL runs on the client thread after the packet is handled.
 */
@Mixin(ClientPacketListener.class)
abstract class CooldownPacketMixin {
    @Inject(method = "handleItemCooldown", at = @At("TAIL"))
    private void skirmish$learnCooldown(ClientboundCooldownPacket packet, CallbackInfo ci) {
        EnemyCooldownsModule module = EnemyCooldownsModule.instance();
        if (module != null && packet.duration() > 0) {
            module.onOwnCooldown(packet.cooldownGroup().toString(), packet.duration());
        }
    }

    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void skirmish$sound(ClientboundSoundPacket packet, CallbackInfo ci) {
        EnemyCooldownsModule module = EnemyCooldownsModule.instance();
        if (module != null) {
            module.onSound(packet.getSound(), packet.getX(), packet.getY(), packet.getZ());
        }
    }
}
