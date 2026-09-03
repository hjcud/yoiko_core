package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RelicGachaEndPayload(UUID sessionId) implements CustomPacketPayload {
    public static final Type<RelicGachaEndPayload> TYPE = new Type<>(YoikoServerCore.id("relic_gacha_end"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RelicGachaEndPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RelicGachaEndPayload::write, RelicGachaEndPayload::new);

    private RelicGachaEndPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
