package com.yoiko.core.profile;

import com.yoiko.core.cosmetic.CosmeticData;
import com.yoiko.core.cosmetic.CosmeticEquipSlot;
import com.yoiko.core.cosmetic.CosmeticManager;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoAuditSavedData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.menu.MenuSessionManager;
import com.yoiko.core.network.OpenProfilePayload;
import com.yoiko.core.rank.RankData;
import com.yoiko.core.rank.RankManager;
import com.yoiko.core.relic.RelicRarity;
import com.yoiko.core.turtle.TurtlePlayerProgress;
import com.yoiko.core.turtle.TurtleRacingSavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PublicProfileService {
    private static final String RANK_PREFIX = "rank:";

    private PublicProfileService() {
    }

    public static boolean setSharing(ServerPlayer player, boolean enabled) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        if (data.publicProfileEnabled == enabled) {
            return false;
        }
        data.publicProfileEnabled = enabled;
        ServerYoikoSavedData.get(player.server).markDirty(player);
        ServerYoikoAuditSavedData.get(player.server).addOperational(
                "PROFILE", "SHARING_CHANGED", player.getUUID(), player.getGameProfile().getName(),
                "enabled=" + enabled);
        return true;
    }

    public static boolean canView(ServerPlayer viewer, ServerPlayer target) {
        return viewer.getUUID().equals(target.getUUID())
                || ServerYoikoSavedData.get(target.server).getOrCreate(target).publicProfileEnabled;
    }

    public static boolean handleAction(ServerPlayer viewer, String action) {
        if ("profile_open".equals(action)) {
            return open(viewer, viewer.getUUID());
        }
        if (action.startsWith("profile_select|")) {
            try {
                return open(viewer, UUID.fromString(action.substring("profile_select|".length())));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        if (action.startsWith("profile_sharing|")) {
            String requested = action.substring("profile_sharing|".length());
            if (!"true".equals(requested) && !"false".equals(requested)) {
                return false;
            }
            boolean enabled = Boolean.parseBoolean(requested);
            setSharing(viewer, enabled);
            return open(viewer, viewer.getUUID());
        }
        return false;
    }

    public static boolean open(ServerPlayer viewer, UUID requestedPlayerId) {
        ServerPlayer target = viewer.server.getPlayerList().getPlayer(requestedPlayerId);
        if (target == null || !canView(viewer, target)) {
            return false;
        }
        if (viewer.containerMenu != viewer.inventoryMenu) {
            viewer.closeContainer();
        }
        MenuSessionManager.open(viewer, "profile");
        List<OpenProfilePayload.PlayerChoice> choices = viewer.server.getPlayerList().getPlayers().stream()
                .filter(candidate -> canView(viewer, candidate))
                .sorted(Comparator
                        .comparing((ServerPlayer candidate) -> !candidate.getUUID().equals(viewer.getUUID()))
                        .thenComparing(candidate -> candidate.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER))
                .map(candidate -> new OpenProfilePayload.PlayerChoice(
                        candidate.getUUID(), candidate.getGameProfile().getName()))
                .toList();
        PacketDistributor.sendToPlayer(viewer, new OpenProfilePayload(profile(viewer, target), choices));
        return true;
    }

    public static List<Component> lines(ServerPlayer target) {
        PlayerYoikoData data = ServerYoikoSavedData.get(target.server).getOrCreate(target);
        TurtlePlayerProgress turtle = TurtleRacingSavedData.get(target.server)
                .getOrCreatePlayer(target.getUUID());
        RankData rank = RankManager.get(data.activeRank);
        AchievementSummary achievements = achievementSummary(target);
        String rankName = rank == null ? Component.translatable("yoiko_core.profile.none").getString()
                : Component.translatable(rank.displayName()).getString();

        List<String> cosmetics = new ArrayList<>();
        for (CosmeticEquipSlot slot : CosmeticEquipSlot.values()) {
            String id = data.equippedCosmetics.getOrDefault(slot, "");
            CosmeticData cosmetic = CosmeticManager.get(id);
            if (cosmetic != null) {
                cosmetics.add(Component.translatable(cosmetic.displayName()).getString());
            }
        }
        String cosmeticNames = cosmetics.isEmpty()
                ? Component.translatable("yoiko_core.profile.none").getString()
                : String.join(", ", cosmetics);

        return List.of(
                Component.translatable("yoiko_core.profile.header", target.getGameProfile().getName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.translatable("yoiko_core.profile.rank", rankName).withStyle(ChatFormatting.WHITE),
                Component.translatable("yoiko_core.profile.cosmetics", cosmeticNames).withStyle(ChatFormatting.WHITE),
                Component.translatable("yoiko_core.profile.activity", data.dailyStreak,
                        data.marketplaceSales.size()).withStyle(ChatFormatting.WHITE),
                Component.translatable("yoiko_core.profile.achievements",
                        achievements.completed(), achievements.total())
                        .withStyle(ChatFormatting.WHITE),
                Component.translatable("yoiko_core.profile.relics", data.ownedRelics.size(),
                        count(data, "relic_unsealed")).withStyle(ChatFormatting.WHITE),
                Component.translatable("yoiko_core.profile.rabbits", count(data, "rabbit_caught"))
                        .withStyle(ChatFormatting.WHITE),
                Component.translatable("yoiko_core.profile.gacha", data.gachaStats.totalRolls,
                        data.gachaStats.shinyRolls).withStyle(ChatFormatting.WHITE),
                Component.translatable("yoiko_core.profile.turtles", turtle.turtleIds().size(),
                        turtle.officialFinishes(), turtle.hasGoldenShell() ? 1 : 0).withStyle(ChatFormatting.WHITE)
        );
    }

    private static OpenProfilePayload.Profile profile(ServerPlayer viewer, ServerPlayer target) {
        PlayerYoikoData data = ServerYoikoSavedData.get(target.server).getOrCreate(target);
        TurtlePlayerProgress turtle = TurtleRacingSavedData.get(target.server).getOrCreatePlayer(target.getUUID());
        RankData activeRank = RankManager.get(data.activeRank);
        AchievementSummary achievements = achievementSummary(target);
        RelicRarity highestRarity = data.ownedRelics.stream()
                .map(relic -> relic.rarity)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(null);
        int rankColor = activeRank == null || activeRank.color().getColor() == null
                ? 0xFFFFFF : activeRank.color().getColor();
        return new OpenProfilePayload.Profile(
                target.getUUID(),
                target.getGameProfile().getName(),
                viewer.getUUID().equals(target.getUUID()),
                data.publicProfileEnabled,
                activeRank == null ? "" : activeRank.id(),
                activeRank == null ? "" : activeRank.displayName(),
                rankColor,
                showcaseTitles(data),
                data.equippedCosmetics.getOrDefault(CosmeticEquipSlot.HEAD, ""),
                data.equippedCosmetics.getOrDefault(CosmeticEquipSlot.CHEST, ""),
                data.equippedCosmetics.getOrDefault(CosmeticEquipSlot.FEET, ""),
                achievements.completed(), achievements.total(),
                Math.max(0, data.dailyStreak), data.marketplaceSales.size(),
                data.ownedRelics.size(), count(data, "relic_unsealed"),
                highestRarity == null ? "" : highestRarity.name().toLowerCase(Locale.ROOT),
                count(data, "rabbit_caught"), count(data, "radiant_rabbit") > 0,
                count(data, "mirror_rabbit") > 0, count(data, "crown_rabbit") > 0,
                Math.max(0, data.gachaStats.totalRolls), Math.max(0, data.gachaStats.shinyRolls),
                Math.max(0, data.gachaStats.legendaryRolls),
                turtle.turtleIds().size(), turtle.officialFinishes(), turtle.officialWins(),
                turtle.timeTrialRecordCount(), turtle.hasGoldenShell()
        );
    }

    private static List<OpenProfilePayload.ShowcaseTitle> showcaseTitles(PlayerYoikoData data) {
        Set<String> ordered = new LinkedHashSet<>();
        for (String favorite : data.favoriteCosmetics) {
            if (favorite.startsWith(RANK_PREFIX)) {
                ordered.add(favorite.substring(RANK_PREFIX.length()));
            }
        }
        if (!data.activeRank.isBlank()) ordered.add(data.activeRank);
        data.ownedRanks.stream()
                .sorted(Comparator.comparingInt((String id) -> {
                    RankData rank = RankManager.get(id);
                    return rank == null ? Integer.MIN_VALUE : rank.priority();
                }).reversed())
                .forEach(ordered::add);

        List<OpenProfilePayload.ShowcaseTitle> result = new ArrayList<>();
        for (String id : ordered) {
            RankData rank = RankManager.get(id);
            if (rank == null || !data.ownedRanks.contains(id)) continue;
            Integer color = rank.color().getColor();
            result.add(new OpenProfilePayload.ShowcaseTitle(
                    rank.id(), rank.displayName(), color == null ? 0xFFFFFF : color));
            if (result.size() >= 3) break;
        }
        return List.copyOf(result);
    }

    private static AchievementSummary achievementSummary(ServerPlayer player) {
        int total = 0;
        int completed = 0;
        for (AdvancementHolder holder : player.server.getAdvancements().getAllAdvancements()) {
            ResourceLocation id = holder.id();
            boolean yoiko = "yoiko_core".equals(id.getNamespace())
                    && (id.getPath().startsWith("yoiko/") || id.getPath().startsWith("turtle/"));
            if (!yoiko || "yoiko/root".equals(id.getPath())) continue;
            total++;
            if (player.getAdvancements().getOrStartProgress(holder).isDone()) completed++;
        }
        return new AchievementSummary(completed, total);
    }

    private static int count(PlayerYoikoData data, String key) {
        return Math.max(0, data.achievementCounters.getOrDefault(key, 0));
    }

    private record AchievementSummary(int completed, int total) {
    }
}
