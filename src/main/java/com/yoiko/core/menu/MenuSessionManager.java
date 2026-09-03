package com.yoiko.core.menu;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.data.ServerYoikoAuditSavedData;
import com.yoiko.core.network.MenuActionPayload;
import com.yoiko.core.network.MenuSessionPayload;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MenuSessionManager {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, ArrayDeque<Long>> REQUESTS = new HashMap<>();
    private static final Map<UUID, Long> LAST_WARNING = new HashMap<>();

    private MenuSessionManager() {
    }

    public static void open(ServerPlayer player, String scope) {
        String sanitizedScope = sanitizeScope(scope);
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            session = new Session(UUID.randomUUID(), sanitizedScope, 0L);
            SESSIONS.put(player.getUUID(), session);
        } else {
            session.scope = sanitizedScope;
        }
        PacketDistributor.sendToPlayer(player, new MenuSessionPayload(session.id, session.scope));
    }

    public static boolean authenticate(ServerPlayer player, MenuActionPayload payload) {
        String action = payload.action();
        if (!allowRequest(player)) {
            warnRateLimited(player);
            return false;
        }
        Session session = SESSIONS.get(player.getUUID());
        String actionScope = actionScope(action);
        boolean navigation = action.endsWith("_open") || "menu_open".equals(action);
        if (session == null || payload.sessionId() == null
                || !session.id.equals(payload.sessionId())
                || payload.nonce() <= session.lastAcceptedNonce
                || (!navigation && !session.scope.equals(actionScope))) {
            auditReject(player, payload, session);
            player.sendSystemMessage(Component.translatable("yoiko_core.message.menu.invalid_session")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        session.lastAcceptedNonce = payload.nonce();
        if (navigation) {
            session.scope = actionScope;
        }
        return true;
    }

    public static boolean allowOpenRequest(ServerPlayer player) {
        if (allowRequest(player)) {
            return true;
        }
        warnRateLimited(player);
        return false;
    }

    public static void rejectUnknown(ServerPlayer player, MenuActionPayload payload) {
        if (!allowRequest(player)) {
            warnRateLimited(player);
            return;
        }
        String action = payload.action() == null ? "" : payload.action();
        String safeAction = action.substring(0, Math.min(64, action.length()));
        ServerYoikoAuditSavedData.get(player.server).addOperational(
                "MENU", "REJECTED_UNKNOWN_ACTION", player.getUUID(), player.getGameProfile().getName(),
                "action=" + safeAction + ";session=" + payload.sessionId() + ";nonce=" + payload.nonce());
        YoikoServerCore.LOGGER.warn("[MenuSecurity] rejected unknown action from {}: {}",
                player.getGameProfile().getName(), safeAction);
        player.sendSystemMessage(Component.translatable("yoiko_core.message.menu.unknown_action", safeAction)
                .withStyle(ChatFormatting.YELLOW));
    }

    public static void clear(UUID playerId) {
        SESSIONS.remove(playerId);
        REQUESTS.remove(playerId);
        LAST_WARNING.remove(playerId);
    }

    private static boolean allowRequest(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long window = YoikoCommonConfig.MENU_REQUEST_WINDOW_MS.get();
        ArrayDeque<Long> requests = REQUESTS.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        while (!requests.isEmpty() && now - requests.peekFirst() >= window) {
            requests.removeFirst();
        }
        if (requests.size() >= YoikoCommonConfig.MENU_REQUEST_LIMIT.get()) {
            return false;
        }
        requests.addLast(now);
        return true;
    }

    private static void warnRateLimited(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long last = LAST_WARNING.getOrDefault(player.getUUID(), 0L);
        if (now - last < YoikoCommonConfig.MENU_RATE_WARNING_COOLDOWN_MS.get()) {
            return;
        }
        LAST_WARNING.put(player.getUUID(), now);
        player.sendSystemMessage(Component.translatable("yoiko_core.message.menu.too_many_requests")
                .withStyle(ChatFormatting.YELLOW));
    }

    private static void auditReject(ServerPlayer player, MenuActionPayload payload, Session session) {
        String action = payload.action();
        String detail = "scope=" + actionScope(action) + ";action=" + action.substring(0, Math.min(64, action.length()))
                + ";session=" + payload.sessionId() + ";nonce=" + payload.nonce()
                + ";expected=" + (session == null ? "none" : session.id)
                + ";expectedScope=" + (session == null ? "none" : session.scope)
                + ";lastNonce=" + (session == null ? -1L : session.lastAcceptedNonce);
        ServerYoikoAuditSavedData.get(player.server).addOperational(
                "MENU", "REJECTED_SESSION", player.getUUID(), player.getGameProfile().getName(), detail);
        YoikoServerCore.LOGGER.warn("[MenuSecurity] rejected action from {}: {}",
                player.getGameProfile().getName(), detail);
    }

    private static String actionScope(String action) {
        if (action.startsWith("relic_dex_")) {
            return "relicdex";
        }
        int separator = action.indexOf('_');
        return separator <= 0 ? "" : sanitizeScope(action.substring(0, separator));
    }

    private static String sanitizeScope(String scope) {
        return switch (scope == null ? "" : scope) {
            case "storage", "mailbox", "relic", "cosmetic", "market", "dex", "relicdex", "turtle", "profile" -> scope;
            default -> "menu";
        };
    }

    private static final class Session {
        private final UUID id;
        private String scope;
        private long lastAcceptedNonce;

        private Session(UUID id, String scope, long lastAcceptedNonce) {
            this.id = id;
            this.scope = scope;
            this.lastAcceptedNonce = lastAcceptedNonce;
        }
    }
}
