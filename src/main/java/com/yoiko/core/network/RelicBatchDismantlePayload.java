package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Typed request for dismantling multiple relics without encoding UUIDs into an action string. */
public record RelicBatchDismantlePayload(
        List<UUID> relicIds,
        UUID sessionId,
        long nonce
) implements CustomPacketPayload {
    private static final int MAX_RELICS = 45;
    public static final Type<RelicBatchDismantlePayload> TYPE =
            new Type<>(YoikoServerCore.id("relic_batch_dismantle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RelicBatchDismantlePayload> STREAM_CODEC =
            CustomPacketPayload.codec(RelicBatchDismantlePayload::write, RelicBatchDismantlePayload::new);

    private RelicBatchDismantlePayload(RegistryFriendlyByteBuf buffer) {
        this(readIds(buffer), buffer.readUUID(), buffer.readVarLong());
    }

    public RelicBatchDismantlePayload {
        relicIds = List.copyOf(relicIds == null ? List.of() : relicIds.subList(0, Math.min(relicIds.size(), MAX_RELICS)));
        sessionId = sessionId == null ? new UUID(0L, 0L) : sessionId;
        nonce = Math.max(0L, nonce);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(relicIds.size());
        relicIds.forEach(buffer::writeUUID);
        buffer.writeUUID(sessionId);
        buffer.writeVarLong(nonce);
    }

    private static List<UUID> readIds(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_RELICS) {
            throw new IllegalArgumentException("Invalid relic batch size: " + size);
        }
        List<UUID> ids = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            ids.add(buffer.readUUID());
        }
        return ids;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
