package com.yoiko.core.treasure;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.event.IncidentCategory;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Parse-friendly operational logs for balancing natural treasure-rabbit encounters. */
public final class TreasureRabbitStatistics {
    private TreasureRabbitStatistics() {
    }

    public static void spawned(TreasureRabbitEntity rabbit, ServerPlayer target,
                               int eligiblePlayers, double effectiveChance, int misses) {
        log("SPAWNED", rabbit, target,
                "eligiblePlayers=" + eligiblePlayers
                        + " effectiveChance=" + String.format(Locale.ROOT, "%.8f", effectiveChance)
                        + " misses=" + misses);
    }

    public static void spawnedByBreakingNews(TreasureRabbitEntity rabbit, ServerPlayer target,
                                             long runId, int swarmSize) {
        log("SPAWNED", rabbit, target,
                "source=breaking_news runId=" + runId + " swarmSize=" + swarmSize);
    }

    public static void spawnedByCommand(TreasureRabbitEntity rabbit, ServerPlayer target) {
        log("SPAWNED", rabbit, target, "source=admin_command");
    }

    public static void discovered(TreasureRabbitEntity rabbit, ServerPlayer player) {
        log("DISCOVERED", rabbit, player, "distance="
                + String.format(Locale.ROOT, "%.2f", rabbit.distanceTo(player)));
    }

    public static void hit(TreasureRabbitEntity rabbit, ServerPlayer player, int hits, int required) {
        log("HIT", rabbit, player, "hits=" + hits + " required=" + required);
    }

    public static void caught(TreasureRabbitEntity rabbit, ServerPlayer player) {
        UUID targetId = rabbit.encounterTargetId();
        log("CAUGHT", rabbit, player, "hits=" + rabbit.countedHits()
                + " targetMatched=" + (targetId != null && targetId.equals(player.getUUID())));
    }

    public static void escaped(TreasureRabbitEntity rabbit, @Nullable ServerPlayer target, String reason) {
        log("ESCAPED", rabbit, target, "hits=" + rabbit.countedHits() + " reason=" + reason);
    }

    public static void recovered(TreasureRabbitEntity rabbit, @Nullable ServerPlayer pursuer,
                                 int attempt, String mode, boolean pathRetried) {
        log("RECOVERED", rabbit, pursuer, "attempt=" + attempt
                + " mode=" + mode + " pathRetried=" + pathRetried);
    }

    private static void log(String event, TreasureRabbitEntity rabbit,
                            @Nullable ServerPlayer player, String details) {
        String playerName = player == null ? "-" : player.getGameProfile().getName();
        String playerId = player == null ? "-" : player.getUUID().toString();
        UUID targetId = rabbit.encounterTargetId();
        ServerPlayer onlineTarget = targetId == null || !(rabbit.level() instanceof ServerLevel level)
                ? null : level.getServer().getPlayerList().getPlayer(targetId);
        String targetName = onlineTarget == null ? "-" : onlineTarget.getGameProfile().getName();
        String targetUuid = targetId == null ? "-" : targetId.toString();
        IncidentCategory category = rabbit.incidentCategory();
        YoikoServerCore.LOGGER.info(
                "[TreasureRabbitStats] event={} incidentCategory={} variant={} natural={} rabbit={} player={} playerUuid={} target={} targetUuid={} dimension={} x={} y={} z={} {}",
                event, category == null ? "none" : category.id(), rabbit.treasureVariant().id(),
                rabbit.isNaturalEncounter(), rabbit.getUUID(),
                playerName, playerId, targetName, targetUuid, rabbit.level().dimension().location(),
                rabbit.blockPosition().getX(), rabbit.blockPosition().getY(), rabbit.blockPosition().getZ(), details);
    }
}
