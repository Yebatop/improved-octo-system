package dev.skirmish.holyworld;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Raw body of a {@code liteapi:feature-control} plugin message: UTF-8 JSON with no length prefix, as in HolyWorld's
 * protocol docs and official Fabric example.
 */
public record FeatureControlPayload(byte[] body) implements CustomPacketPayload {
    public static final Type<FeatureControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("liteapi", "feature-control"));
    public static final StreamCodec<FriendlyByteBuf, FeatureControlPayload> CODEC = CustomPacketPayload.codec(
            (payload, buf) -> buf.writeBytes(payload.body()),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new FeatureControlPayload(bytes);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
