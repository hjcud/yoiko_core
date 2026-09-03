package com.yoiko.core.client.screen;

import com.yoiko.core.network.MenuActionPayload;
import com.yoiko.core.network.MenuSessionPayload;
import com.yoiko.core.network.RelicBatchDismantlePayload;
import com.yoiko.core.network.TurtleMenuActionPayload;
import java.util.List;
import java.util.UUID;

public final class ClientMenuSession {
    private static UUID sessionId = new UUID(0L, 0L);
    private static String scope = "";
    private static long nonce;

    private ClientMenuSession() {
    }

    public static void update(MenuSessionPayload payload) {
        if (!sessionId.equals(payload.sessionId())) {
            nonce = 0L;
        }
        sessionId = payload.sessionId();
        scope = payload.scope();
    }

    public static MenuActionPayload action(String action) {
        return new MenuActionPayload(action, sessionId, ++nonce);
    }

    public static RelicBatchDismantlePayload relicBatchDismantle(List<UUID> relicIds) {
        return new RelicBatchDismantlePayload(relicIds, sessionId, ++nonce);
    }

    public static TurtleMenuActionPayload turtleAction(
            String action, UUID turtleId, String value, long expectedRevision) {
        return new TurtleMenuActionPayload(
                action, turtleId, value, expectedRevision, sessionId, ++nonce);
    }

    public static String scope() {
        return scope;
    }
}
