package com.yoiko.core.menu;

import com.yoiko.core.network.OpenRelicDexPayload;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.relic.RelicData;
import com.yoiko.core.relic.RelicManager;
import com.yoiko.core.relic.RelicRarity;
import com.yoiko.core.relic.RelicAppraisalCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

public final class YoikoRelicDexMenu {
    private static final int PAGE_SIZE = 36;
    private static final Map<UUID, RelicAppraisalCategory> ACTIVE_CATEGORIES = new ConcurrentHashMap<>();

    private YoikoRelicDexMenu() {
    }

    public static boolean handleAction(ServerPlayer player, String action) {
        if (!action.startsWith("relic_dex_")) {
            return false;
        }
        String[] parts = action.split("\\|", 6);
        switch (parts[0]) {
            case "relic_dex_rarity" -> send(player, rarityPart(parts, 1), 0, "", part(parts, 2));
            case "relic_dex_page" -> {
                send(player, rarityPart(parts, 1), intPart(parts, 2, 0), part(parts, 3), part(parts, 4));
                playDexPageSound(player);
            }
            case "relic_dex_search" -> send(player, rarityPart(parts, 1), 0, "", part(parts, 2));
            default -> player.sendSystemMessage(Component.translatable("yoiko_core.message.relic_dex.unknown_action", action)
                    .withStyle(ChatFormatting.YELLOW));
        }
        return true;
    }

    public static void open(ServerPlayer player) {
        open(player, RelicAppraisalCategory.ALL);
    }

    public static void open(ServerPlayer player, RelicAppraisalCategory appraisalCategory) {
        RelicAppraisalCategory category = appraisalCategory == null
                ? RelicAppraisalCategory.ALL : appraisalCategory;
        ACTIVE_CATEGORIES.put(player.getUUID(), category);
        MenuSessionManager.open(player, "relicdex");
        send(player, firstRarity(), 0, "", "");
        playDexPageSound(player);
    }

    private static void playDexPageSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.65F, 1.1F);
    }

    private static void send(ServerPlayer player, RelicRarity rarity, int page,
                             String selectedRelicId, String requestedSearch) {
        if (rarity == null) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.relic_dex.unknown_rarity").withStyle(ChatFormatting.RED));
            return;
        }
        String search = PlayerYoikoData.sanitizeSearch(requestedSearch);
        String needle = search.toLowerCase(java.util.Locale.ROOT);
        RelicAppraisalCategory category = ACTIVE_CATEGORIES.getOrDefault(
                player.getUUID(), RelicAppraisalCategory.ALL);
        List<RelicData> all = RelicManager.dexRelics().stream()
                .filter(relic -> relic.allows(rarity))
                .filter(relic -> category == RelicAppraisalCategory.ALL
                        || rarity == RelicRarity.RADIANT
                        || category.matches(relic.effect()))
                .filter(relic -> search.isBlank()
                        || relic.id().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || relic.displayName().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || relic.effect().toLowerCase(java.util.Locale.ROOT).contains(needle))
                .toList();
        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = Math.min(safePage * PAGE_SIZE, all.size());
        int to = Math.min(from + PAGE_SIZE, all.size());

        List<OpenRelicDexPayload.Entry> entries = new ArrayList<>();
        for (RelicData relic : all.subList(from, to)) {
            entries.add(entry(relic, rarity, category));
        }

        RelicData selected = selectedRelicId.isBlank() ? null : find(all, selectedRelicId);
        if (selected == null && !entries.isEmpty()) {
            selected = find(all, entries.get(0).relicId());
        }

        PacketDistributor.sendToPlayer(player, new OpenRelicDexPayload(
                "yoiko_core.screen.relic_dex",
                category.id(),
                rarity.name(),
                safePage,
                totalPages,
                search,
                entries,
                selected == null ? OpenRelicDexPayload.Entry.empty() : entry(selected, rarity, category)
        ));
    }

    private static OpenRelicDexPayload.Entry entry(
            RelicData relic, RelicRarity rarity, RelicAppraisalCategory appraisalCategory) {
        return new OpenRelicDexPayload.Entry(
                relic.id(),
                RelicManager.displayNameKey(relic.id(), relic.displayName()),
                relic.effect(),
                rarity.name(),
                RelicManager.formatRate(RelicManager.relicRatePercent(relic.id(), appraisalCategory)),
                RelicManager.formatRate(RelicManager.rarityRatePercent(rarity)),
                relic.baseValue(rarity),
                relic.upgradeBonus(rarity)
        );
    }

    private static RelicData find(List<RelicData> relics, String relicId) {
        for (RelicData relic : relics) {
            if (relic.id().equals(relicId)) {
                return relic;
            }
        }
        return null;
    }

    private static RelicRarity firstRarity() {
        for (RelicRarity rarity : RelicRarity.values()) {
            if (RelicManager.rarityRatePercent(rarity) > 0.0D) {
                return rarity;
            }
        }
        return RelicRarity.COMMON;
    }

    private static RelicRarity rarityPart(String[] parts, int index) {
        return RelicRarity.parse(part(parts, index)).orElse(null);
    }

    private static int intPart(String[] parts, int index, int fallback) {
        try {
            return Integer.parseInt(part(parts, index));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String part(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index] : "";
    }
}
