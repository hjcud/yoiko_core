package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GachaSelectionRevealPayload(
        UUID sessionId,
        int selectedIndex,
        String species,
        String rarity,
        boolean shiny,
        int level,
        int revealTicks
) implements CustomPacketPayload {
    public static final Type<GachaSelectionRevealPayload> TYPE =
            new Type<>(YoikoServerCore.id("gacha_selection_reveal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GachaSelectionRevealPayload> STREAM_CODEC =
            CustomPacketPayload.codec(GachaSelectionRevealPayload::write, GachaSelectionRevealPayload::new);

    private GachaSelectionRevealPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readUtf(128),
                buffer.readUtf(32),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeVarInt(selectedIndex);
        buffer.writeUtf(species, 128);
        buffer.writeUtf(rarity, 32);
        buffer.writeBoolean(shiny);
        buffer.writeVarInt(level);
        buffer.writeVarInt(revealTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
