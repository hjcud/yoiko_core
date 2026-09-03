package com.yoiko.core.event;

import net.minecraft.network.chat.Component;

/** Audience scope for Yoiko incidents. */
public enum IncidentCategory {
    PERSONAL("personal"),
    SERVER("server");

    private final String id;

    IncidentCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("yoiko_core.incident.category." + id);
    }
}
