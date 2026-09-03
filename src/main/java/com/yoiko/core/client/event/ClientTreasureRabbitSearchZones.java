package com.yoiko.core.client.event;

import com.yoiko.core.network.TreasureRabbitSearchZonePayload;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client-only cache of player-private Treasure Rabbit search areas. */
public final class ClientTreasureRabbitSearchZones {
    private static volatile Snapshot current = Snapshot.empty();
    private static volatile long revision;

    private ClientTreasureRabbitSearchZones() {
    }

    public static synchronized void update(TreasureRabbitSearchZonePayload payload) {
        if (payload == null || payload.rabbitId() == null) {
            return;
        }
        Snapshot snapshot = current;
        TreasureRabbitSearchZonePayload previous = snapshot.byId().get(payload.rabbitId());
        boolean show = payload.visible() && payload.radius() > 0;
        if ((show && payload.equals(previous)) || (!show && previous == null)) {
            return;
        }
        Map<UUID, TreasureRabbitSearchZonePayload> updated = new LinkedHashMap<>(snapshot.byId());
        if (show) {
            updated.put(payload.rabbitId(), payload);
        } else {
            updated.remove(payload.rabbitId());
        }
        current = new Snapshot(Collections.unmodifiableMap(updated), List.copyOf(updated.values()));
        revision++;
    }

    public static List<TreasureRabbitSearchZonePayload> current() {
        return current.zones();
    }

    public static long revision() {
        return revision;
    }

    public static synchronized void clear() {
        if (!current.zones().isEmpty()) {
            current = Snapshot.empty();
            revision++;
        }
    }

    private record Snapshot(Map<UUID, TreasureRabbitSearchZonePayload> byId,
                            List<TreasureRabbitSearchZonePayload> zones) {
        private static Snapshot empty() {
            return new Snapshot(Map.of(), List.of());
        }
    }
}
