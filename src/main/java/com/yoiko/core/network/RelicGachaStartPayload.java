package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.relic.RelicRarity;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RelicGachaStartPayload(
        UUID sessionId,
        UUID ownerId,
        double x,
        double groundY,
        double z,
        double forwardX,
        double forwardZ,
        double sideX,
        double sideZ,
        float facingYaw,
        RelicRarity rarity
) implements CustomPacketPayload {
    public static final Type<RelicGachaStartPayload> TYPE =
            new Type<>(YoikoServerCore.id("relic_gacha_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RelicGachaStartPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RelicGachaStartPayload::write, RelicGachaStartPayload::new);

    private RelicGachaStartPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUUID(), buffer.readUUID(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), readRarity(buffer)
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeUUID(ownerId);
        buffer.writeDouble(x);
        buffer.writeDouble(groundY);
        buffer.writeDouble(z);
        buffer.writeDouble(forwardX);
        buffer.writeDouble(forwardZ);
        buffer.writeDouble(sideX);
        buffer.writeDouble(sideZ);
        buffer.writeFloat(facingYaw);
        buffer.writeVarInt(rarity.ordinal());
    }

    private static RelicRarity readRarity(RegistryFriendlyByteBuf buffer) {
        RelicRarity[] values = RelicRarity.values();
        int ordinal = buffer.readVarInt();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : RelicRarity.COMMON;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
