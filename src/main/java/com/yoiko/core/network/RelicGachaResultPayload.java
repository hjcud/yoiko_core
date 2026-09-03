package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.relic.RelicRarity;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-authoritative relic appraisal result shown only to the ticket owner. */
public record RelicGachaResultPayload(
        UUID relicUuid,
        String displayName,
        RelicRarity rarity,
        int level,
        String primaryEffect,
        double primaryValue,
        String secondaryEffect,
        double secondaryValue
) implements CustomPacketPayload {
    public static final Type<RelicGachaResultPayload> TYPE =
            new Type<>(YoikoServerCore.id("relic_gacha_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RelicGachaResultPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RelicGachaResultPayload::write, RelicGachaResultPayload::new);

    private RelicGachaResultPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUUID(),
                buffer.readUtf(128),
                readRarity(buffer),
                buffer.readVarInt(),
                buffer.readUtf(128),
                buffer.readDouble(),
                buffer.readUtf(128),
                buffer.readDouble()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(relicUuid);
        buffer.writeUtf(displayName, 128);
        buffer.writeVarInt(rarity.ordinal());
        buffer.writeVarInt(level);
        buffer.writeUtf(primaryEffect, 128);
        buffer.writeDouble(primaryValue);
        buffer.writeUtf(secondaryEffect, 128);
        buffer.writeDouble(secondaryValue);
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
