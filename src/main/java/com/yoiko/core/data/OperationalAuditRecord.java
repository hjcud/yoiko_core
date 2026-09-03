package com.yoiko.core.data;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record OperationalAuditRecord(
        UUID id,
        String category,
        String action,
        UUID playerUuid,
        String playerName,
        String detail,
        long createdAt
) {
    public OperationalAuditRecord {
        id = id == null ? UUID.randomUUID() : id;
        category = category == null ? "" : category;
        action = action == null ? "" : action;
        playerName = playerName == null ? "" : playerName;
        detail = detail == null ? "" : detail;
        createdAt = Math.max(0L, createdAt);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("category", category);
        tag.putString("action", action);
        if (playerUuid != null) {
            tag.putUUID("playerUuid", playerUuid);
        }
        tag.putString("playerName", playerName);
        tag.putString("detail", detail);
        tag.putLong("createdAt", createdAt);
        return tag;
    }

    public static OperationalAuditRecord load(CompoundTag tag) {
        return new OperationalAuditRecord(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                tag.getString("category"),
                tag.getString("action"),
                tag.hasUUID("playerUuid") ? tag.getUUID("playerUuid") : null,
                tag.getString("playerName"),
                tag.getString("detail"),
                tag.getLong("createdAt")
        );
    }
}
