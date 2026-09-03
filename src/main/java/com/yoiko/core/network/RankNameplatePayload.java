package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RankNameplatePayload(UUID playerUuid, String rankId, String iconTexture, int color) implements CustomPacketPayload {
    public static final Type<RankNameplatePayload> TYPE = new Type<>(YoikoServerCore.id("rank_nameplate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RankNameplatePayload> STREAM_CODEC =
            CustomPacketPayload.codec(RankNameplatePayload::write, RankNameplatePayload::new);

    private RankNameplatePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(128), buffer.readUtf(512), buffer.readInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(playerUuid);
        buffer.writeUtf(rankId, 128);
        buffer.writeUtf(iconTexture, 512);
        buffer.writeInt(color);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
