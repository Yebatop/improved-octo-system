package dev.skirmish.mixin;

import dev.skirmish.combat.CombatTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only taps for the combat tracker. TAIL runs only on the client thread: on the network thread
 * {@code PacketUtils.ensureRunningOnSameThread} re-schedules the packet and aborts the method first.
 * Module mixin configs must not inject into these handlers; use {@code CombatListener}.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void skirmish$onDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        Entity victim = level.getEntity(packet.entityId());
        if (victim != null) {
            CombatTracker.get().onDamagePacket(victim, packet.getSource(level));
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void skirmish$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Entity entity = packet.getEntity(level);
        if (entity != null) {
            CombatTracker.get().onEntityEvent(entity, packet.getEventId());
        }
    }

    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void skirmish$onAnimate(ClientboundAnimatePacket packet, CallbackInfo ci) {
        Entity entity = level.getEntity(packet.getId());
        if (entity != null) {
            CombatTracker.get().onAnimate(entity, packet.getAction());
        }
    }

    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void skirmish$onSetEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        Entity entity = level.getEntity(packet.id());
        if (entity != null) {
            CombatTracker.get().onEntityData(entity);
        }
    }

    @Inject(method = "handlePlayerCombatKill", at = @At("TAIL"))
    private void skirmish$onPlayerCombatKill(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && packet.playerId() == mc.player.getId()) {
            CombatTracker.get().onOwnDeath(packet.message());
        }
    }
}
