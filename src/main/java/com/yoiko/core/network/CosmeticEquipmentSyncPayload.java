package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.cosmetic.CosmeticAnchor;
import com.yoiko.core.cosmetic.CosmeticData;
import com.yoiko.core.cosmetic.CosmeticModelData;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CosmeticEquipmentSyncPayload(
        UUID playerUuid,
        ModelEntry head,
        ModelEntry chest
) implements CustomPacketPayload {
    public static final Type<CosmeticEquipmentSyncPayload> TYPE =
            new Type<>(YoikoServerCore.id("cosmetic_equipment_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CosmeticEquipmentSyncPayload> STREAM_CODEC =
            CustomPacketPayload.codec(CosmeticEquipmentSyncPayload::write, CosmeticEquipmentSyncPayload::new);

    private CosmeticEquipmentSyncPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), ModelEntry.read(buffer), ModelEntry.read(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(playerUuid);
        head.write(buffer);
        chest.write(buffer);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record ModelEntry(
            String cosmeticId,
            String modelId,
            CosmeticAnchor anchor,
            int primaryColor,
            int accentColor
    ) {
        public static final ModelEntry EMPTY =
                new ModelEntry("", "", CosmeticAnchor.NONE, 0xFFFFFFFF, 0xFFFFFFFF);

        public ModelEntry {
            cosmeticId = cosmeticId == null ? "" : cosmeticId;
            modelId = modelId == null ? "" : modelId;
            anchor = anchor == null ? CosmeticAnchor.NONE : anchor;
        }

        public static ModelEntry from(CosmeticData cosmetic) {
            if (cosmetic == null || !cosmetic.modelData().present()) {
                return EMPTY;
            }
            CosmeticModelData model = cosmetic.modelData();
            return new ModelEntry(
                    cosmetic.id(),
                    model.modelId(),
                    model.anchor(),
                    model.primaryColor(),
                    model.accentColor()
            );
        }

        public boolean present() {
            return !cosmeticId.isBlank() && !modelId.isBlank() && anchor != CosmeticAnchor.NONE;
        }

        private static ModelEntry read(RegistryFriendlyByteBuf buffer) {
            String cosmeticId = buffer.readUtf(128);
            String modelId = buffer.readUtf(128);
            CosmeticAnchor anchor = CosmeticAnchor.fromString(buffer.readUtf(32));
            int primaryColor = buffer.readInt();
            int accentColor = buffer.readInt();
            return new ModelEntry(cosmeticId, modelId, anchor, primaryColor, accentColor);
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(cosmeticId, 128);
            buffer.writeUtf(modelId, 128);
            buffer.writeUtf(anchor.name(), 32);
            buffer.writeInt(primaryColor);
            buffer.writeInt(accentColor);
        }
    }
}
