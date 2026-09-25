package dev.skirmish.module.lag.mixin;

import dev.skirmish.module.lag.LagMeterModule;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only tap on incoming packets of the client's connection (network thread): stores the arrival time and, for
 * world time updates, the server's game time. The packet itself is not touched.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void skirmish$onPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (((Connection) (Object) this).getReceiving() == PacketFlow.CLIENTBOUND) {
            LagMeterModule.onPacket(packet);
        }
    }
}
