package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.gacha.GachaRarity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GachaSelectionStartPayload(
        UUID sessionId,
        UUID ownerId,
        List<BallEntry> balls,
        int fallTicks,
        int staggerTicks,
        int timeoutTicks,
        int revealTicks,
        double fallHeight,
        double selectionDistance,
        int particleDensity
) implements CustomPacketPayload {
    public static final Type<GachaSelectionStartPayload> TYPE =
            new Type<>(YoikoServerCore.id("gacha_selection_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GachaSelectionStartPayload> STREAM_CODEC =
            CustomPacketPayload.codec(GachaSelectionStartPayload::write, GachaSelectionStartPayload::new);

    public GachaSelectionStartPayload {
        balls = List.copyOf(balls);
        if (balls.size() != 3) {
            throw new IllegalArgumentException("A gacha selection must contain exactly three balls");
        }
    }

    private GachaSelectionStartPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUUID(),
                buffer.readUUID(),
                readBalls(buffer),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeUUID(ownerId);
        buffer.writeVarInt(balls.size());
        for (BallEntry ball : balls) {
            buffer.writeDouble(ball.x());
            buffer.writeDouble(ball.y());
            buffer.writeDouble(ball.z());
            buffer.writeVarInt(ball.appearanceRarity().ordinal());
            buffer.writeBoolean(ball.shiny());
            buffer.writeFloat(ball.facingYaw());
        }
        buffer.writeVarInt(fallTicks);
        buffer.writeVarInt(staggerTicks);
        buffer.writeVarInt(timeoutTicks);
        buffer.writeVarInt(revealTicks);
        buffer.writeDouble(fallHeight);
        buffer.writeDouble(selectionDistance);
        buffer.writeVarInt(particleDensity);
    }

    private static List<BallEntry> readBalls(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size != 3) {
            throw new IllegalArgumentException("Invalid gacha ball count: " + size);
        }
        List<BallEntry> balls = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            balls.add(new BallEntry(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    readAppearanceRarity(buffer),
                    buffer.readBoolean(),
                    buffer.readFloat()
            ));
        }
        return balls;
    }

    private static GachaRarity readAppearanceRarity(RegistryFriendlyByteBuf buffer) {
        int id = buffer.readVarInt();
        GachaRarity[] values = GachaRarity.values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Invalid gacha ball appearance rarity: " + id);
        }
        return values[id];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record BallEntry(double x, double y, double z, GachaRarity appearanceRarity, boolean shiny, float facingYaw) {
    }
}
