package com.yoiko.core.client.event;

import com.yoiko.core.network.BreakingNewsZonePayload;

/** Client-only cache used by optional map integrations. */
public final class ClientBreakingNewsZone {
    private static volatile BreakingNewsZonePayload current = hidden();
    private static volatile long revision;

    private ClientBreakingNewsZone() {
    }

    public static synchronized void update(BreakingNewsZonePayload payload) {
        BreakingNewsZonePayload next = payload == null ? hidden() : payload;
        if (!next.equals(current)) {
            current = next;
            revision++;
        }
    }

    public static BreakingNewsZonePayload current() {
        return current;
    }

    public static long revision() {
        return revision;
    }

    public static void clear() {
        update(null);
    }

    private static BreakingNewsZonePayload hidden() {
        return new BreakingNewsZonePayload(false, 0L, "", "", 0, 0, 0, "");
    }
}
