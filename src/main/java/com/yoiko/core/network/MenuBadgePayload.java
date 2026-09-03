package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MenuBadgePayload(int mailCount, int unreadMailCount, int mailCapacity,
                               int unseenMarketSales, boolean showIntro)
        implements CustomPacketPayload {
    public static final Type<MenuBadgePayload> TYPE = new Type<>(YoikoServerCore.id("menu_badges"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuBadgePayload> STREAM_CODEC =
            CustomPacketPayload.codec(MenuBadgePayload::write, MenuBadgePayload::new);

    private MenuBadgePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(mailCount);
        buffer.writeVarInt(unreadMailCount);
        buffer.writeVarInt(mailCapacity);
        buffer.writeVarInt(unseenMarketSales);
        buffer.writeBoolean(showIntro);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
