package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client acknowledgement that the player closed a turtle hatch result. */
public record TurtleHatchConfirmPayload(UUID turtleId) implements CustomPacketPayload {
    public static final Type<TurtleHatchConfirmPayload> TYPE =
            new Type<>(YoikoServerCore.id("turtle_hatch_confirm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TurtleHatchConfirmPayload> STREAM_CODEC =
            CustomPacketPayload.codec(TurtleHatchConfirmPayload::write, TurtleHatchConfirmPayload::new);

    private TurtleHatchConfirmPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(turtleId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
