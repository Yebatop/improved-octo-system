package dev.skirmish.module.hwtimers.mixin;

import dev.skirmish.module.hwtimers.ItemTimersModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only: explosions the server announces (centre, power, broken block count) for the raid-block timer. TAIL runs
 * only on the client thread (the network-thread call returns early from {@code ensureRunningOnSameThread}).
 */
@Mixin(ClientPacketListener.class)
public abstract class ExplosionMixin {
    @Inject(method = "handleExplosion", at = @At("TAIL"))
    private void skirmish$hwTimersExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        ItemTimersModule module = ItemTimersModule.instance();
        if (module != null) {
            module.onExplosion(packet.center(), packet.radius(), packet.blockCount());
        }
    }
}
