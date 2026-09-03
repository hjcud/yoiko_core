package com.yoiko.core.menu;

import com.yoiko.core.cosmetic.CosmeticData;
import com.yoiko.core.cosmetic.CosmeticEquipSlot;
import com.yoiko.core.cosmetic.CosmeticManager;
import com.yoiko.core.cosmetic.CosmeticType;
import com.yoiko.core.cosmetic.ParticleCategory;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.economy.CurrencyManager;
import com.yoiko.core.economy.CurrencyType;
import com.yoiko.core.economy.EconomyManager;
import com.yoiko.core.network.OpenCosmeticPayload;
import com.yoiko.core.network.CosmeticMenuCatalogPayload;
import com.yoiko.core.rank.RankData;
import com.yoiko.core.rank.RankManager;
import com.yoiko.core.reward.RewardManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class YoikoCosmeticMenu {
    private static final String RANK_PREFIX = "rank:";
    private static final int PAGE_SIZE = 90;

    private YoikoCosmeticMenu() {
    }

    public static void syncCatalog(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, catalogPayload());
    }

    public static void syncCatalogToAll(MinecraftServer server) {
        CosmeticMenuCatalogPayload payload = catalogPayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static boolean handleAction(ServerPlayer player, String action) {
        String[] parts = action.split("\\|", 3);
        switch (parts[0]) {
            case "cosmetic_open" -> open(player, part(parts, 1), "");
            case "cosmetic_select" -> open(player, part(parts, 1), "");
            case "cosmetic_rank_equip" -> equipRank(player, part(parts, 1));
            case "cosmetic_equip" -> equip(player, part(parts, 1));
            case "cosmetic_exchange" -> exchange(player, part(parts, 1));
            case "cosmetic_favorite" -> toggleFavorite(player, part(parts, 1));
            case "cosmetic_favorites_only" -> toggleFavoritesOnly(player);
            case "cosmetic_unequip" -> unequip(player, part(parts, 1));
            case "cosmetic_unequip_type" -> unequipType(player, part(parts, 1));
            case "cosmetic_clear" -> clear(player);
            case "cosmetic_view" -> updateView(player, part(parts, 1), part(parts, 2));
            default -> {
                return false;
            }
        }
        return true;
    }

    public static void open(ServerPlayer player) {
        MenuSessionManager.open(player, "cosmetic");
        open(player, "", "");
    }

    private static void updateView(ServerPlayer player, String category, String pageValue) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        data.cosmeticCategory = PlayerYoikoData.sanitizeCosmeticCategory(category);
        try {
            data.cosmeticPage = Math.max(0, Integer.parseInt(pageValue));
        } catch (NumberFormatException ignored) {
            data.cosmeticPage = 0;
        }
        savedData.markDirty(player);
        open(player, "", "");
    }

    private static void equip(ServerPlayer player, String cosmeticId) {
        if (isRankEntry(cosmeticId)) {
            equipRank(player, cosmeticId);
            return;
        }
        CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
        if (cosmetic == null) {
            open(player, "", "yoiko_core.message.cosmetic.not_found");
            return;
        }
        boolean success = CosmeticManager.equip(player, cosmeticId);
        open(player, cosmeticId, success ? "yoiko_core.message.cosmetic.equipped" : "yoiko_core.message.cosmetic.cannot_equip");
    }

    private static void unequip(ServerPlayer player, String cosmeticId) {
        if (isRankEntry(cosmeticId)) {
            String rankId = stripRankPrefix(cosmeticId);
            boolean success = rankId.equals(ServerYoikoSavedData.get(player.server)
                    .getOrCreate(player).activeRank) && RankManager.unequip(player);
            open(player, cosmeticId, success
                    ? "yoiko_core.message.cosmetic.rank_unequipped"
                    : "yoiko_core.message.cosmetic.rank_not_equipped");
            return;
        }
        CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
        if (cosmetic == null) {
            open(player, "", "yoiko_core.message.cosmetic.not_found");
            return;
        }
        CosmeticEquipSlot slot = CosmeticEquipSlot.forCosmetic(cosmetic);
        if (slot != null) {
            CosmeticManager.unequip(player, slot);
        }
        open(player, cosmeticId, "yoiko_core.message.cosmetic.unequipped");
    }

    private static void exchange(ServerPlayer player, String cosmeticId) {
        CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        if (cosmetic == null || !RewardManager.isGemExchangeEligibleCosmetic(cosmeticId)) {
            open(player, cosmeticId, "yoiko_core.message.cosmetic.exchange_unavailable");
            return;
        }
        if (data.ownedCosmetics.contains(cosmeticId)) {
            open(player, cosmeticId, "yoiko_core.message.cosmetic.already_owned");
            return;
        }
        long price = EconomyManager.cosmeticGemPrice(cosmeticId, cosmetic.rarity().name());
        if (price <= 0L || !CurrencyManager.take(player, CurrencyType.GEM, price)) {
            open(player, cosmeticId, "yoiko_core.message.market.not_enough_gems");
            return;
        }
        if (!CosmeticManager.grant(player, cosmeticId, false)) {
            CurrencyManager.add(player, CurrencyType.GEM, price);
            open(player, cosmeticId, "yoiko_core.message.cosmetic.exchange_failed");
            return;
        }
        EconomyManager.recordEconomy(player, "GEM_SPENT", price,
                "source=cosmetic_exchange;cosmetic=" + cosmeticId);
        player.sendSystemMessage(Component.translatable(
                "yoiko_core.message.cosmetic.exchanged",
                cosmeticDisplayName(cosmetic),
                price
        ).withStyle(ChatFormatting.AQUA));
        open(player, cosmeticId, "yoiko_core.message.cosmetic.exchange_success");
    }

    private static void equipRank(ServerPlayer player, String entryId) {
        String rankId = stripRankPrefix(entryId);
        RankData rank = RankManager.get(rankId);
        if (rank == null) {
            open(player, "", "yoiko_core.message.cosmetic.rank_not_found");
            return;
        }
        if (!rank.allowUserSelect()) {
            open(player, RANK_PREFIX + rankId, "yoiko_core.message.cosmetic.rank_cannot_select");
            return;
        }
        boolean success = RankManager.setActive(player, rankId);
        open(player, RANK_PREFIX + rankId, success ? "yoiko_core.message.cosmetic.rank_equipped" : "yoiko_core.message.cosmetic.rank_not_owned");
    }

    private static void unequipType(ServerPlayer player, String typeName) {
        CosmeticEquipSlot slot = CosmeticEquipSlot.fromString(typeName);
        if (slot != null) {
            CosmeticManager.unequip(player, slot);
            open(player, "", "yoiko_core.message.cosmetic.slot_cleared");
        }
    }

    private static void clear(ServerPlayer player) {
        CosmeticManager.clearEquipment(player);
        open(player, "", "yoiko_core.message.cosmetic.cleared_all");
    }

    private static void toggleFavorite(ServerPlayer player, String cosmeticId) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        if (CosmeticManager.get(cosmeticId) == null && !isRankEntry(cosmeticId)) {
            open(player, "", "yoiko_core.message.cosmetic.not_found");
            return;
        }
        if (!data.favoriteCosmetics.remove(cosmeticId)) {
            data.favoriteCosmetics.add(cosmeticId);
        }
        savedData.markDirty(player);
        open(player, cosmeticId, "");
    }

    private static void toggleFavoritesOnly(ServerPlayer player) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        data.cosmeticFavoritesOnly = !data.cosmeticFavoritesOnly;
        data.cosmeticPage = 0;
        savedData.markDirty(player);
        open(player, "", "");
    }

    private static void open(ServerPlayer player, String requestedId, String message) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        if (!requestedId.isBlank()) {
            data.cosmeticCategory = requestedId.startsWith(RANK_PREFIX) ? "RANK" : categoryOf(requestedId);
        }
        if (!PlayerYoikoData.MENU_TAB_COSMETIC.equals(data.lastMenuTab)) {
            data.lastMenuTab = PlayerYoikoData.MENU_TAB_COSMETIC;
            savedData.markDirty(player);
        }
        List<OpenCosmeticPayload.EquippedSlot> slots = new ArrayList<>();
        slots.add(new OpenCosmeticPayload.EquippedSlot("HEAD", data.equippedCosmetics.getOrDefault(CosmeticEquipSlot.HEAD, "")));
        slots.add(new OpenCosmeticPayload.EquippedSlot("CHEST", data.equippedCosmetics.getOrDefault(CosmeticEquipSlot.CHEST, "")));
        slots.add(new OpenCosmeticPayload.EquippedSlot("FEET", data.equippedCosmetics.getOrDefault(CosmeticEquipSlot.FEET, "")));
        slots.add(new OpenCosmeticPayload.EquippedSlot("RANK", data.activeRank.isBlank() ? "" : RANK_PREFIX + data.activeRank));

        List<OpenCosmeticPayload.Entry> entries = new ArrayList<>();
        for (String cosmeticId : CosmeticManager.ids()) {
            CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
            if (cosmetic == null) {
                continue;
            }
            boolean owned = data.ownedCosmetics.contains(cosmeticId);
            entries.add(OpenCosmeticPayload.Entry.state(cosmetic.id(), owned,
                    isEquipped(data, cosmetic), data.favoriteCosmetics.contains(cosmeticId)));
        }

        for (String rankId : RankManager.ids()) {
            RankData rank = RankManager.get(rankId);
            if (rank == null) {
                continue;
            }
            entries.add(OpenCosmeticPayload.Entry.state(RANK_PREFIX + rank.id(),
                    data.ownedRanks.contains(rank.id()), rank.id().equals(data.activeRank),
                    data.favoriteCosmetics.contains(RANK_PREFIX + rank.id())));
        }

        List<OpenCosmeticPayload.Entry> equippedEntries = new ArrayList<>();
        for (OpenCosmeticPayload.EquippedSlot slot : slots) {
            if (slot.cosmeticId().isBlank()) {
                continue;
            }
            entries.stream().filter(entry -> entry.id().equals(slot.cosmeticId())).findFirst()
                    .ifPresent(entry -> {
                        if (equippedEntries.stream().noneMatch(existing -> existing.id().equals(entry.id()))) {
                            equippedEntries.add(entry);
                        }
                    });
        }
        savedData.markDirty(player);
        PacketDistributor.sendToPlayer(player, new OpenCosmeticPayload(
                requestedId, message, data.cosmeticCategory, data.cosmeticPage,
                1, data.cosmeticFavoritesOnly, data.gems, slots, equippedEntries, entries));
    }

    private static CosmeticMenuCatalogPayload catalogPayload() {
        List<CosmeticMenuCatalogPayload.Entry> entries = new ArrayList<>();
        for (String cosmeticId : CosmeticManager.ids()) {
            CosmeticData cosmetic = CosmeticManager.get(cosmeticId);
            if (cosmetic == null) {
                continue;
            }
            entries.add(new CosmeticMenuCatalogPayload.Entry(
                    cosmetic.id(), cosmeticDisplayName(cosmetic), cosmetic.type().name(),
                    cosmetic.particleCategory().name(), cosmetic.requiredRank(), acquisitionPath(cosmetic),
                    cosmetic.creator(), cosmetic.rarity().name(), 0xFFFFFF, exchangePrice(cosmetic),
                    cosmetic.modelData().modelId(), cosmetic.modelData().anchor().name(),
                    cosmetic.modelData().primaryColor(), cosmetic.modelData().accentColor(),
                    1000 - rarityOrder(cosmetic.id())));
        }
        for (String rankId : RankManager.ids()) {
            RankData rank = RankManager.get(rankId);
            if (rank == null) {
                continue;
            }
            Integer color = rank.color().getColor();
            entries.add(new CosmeticMenuCatalogPayload.Entry(
                    RANK_PREFIX + rank.id(), rankDisplayName(rank), "RANK", ParticleCategory.NONE.name(),
                    "", acquisitionPath(rank), "Yoiko", "", color == null ? 0xFFFFFF : color,
                    0L, "", "NONE", 0xFFFFFFFF, 0xFFFFFFFF, rank.priority()));
        }
        return new CosmeticMenuCatalogPayload(CosmeticManager.particleCatalogRevision(), entries);
    }

    private static long exchangePrice(CosmeticData cosmetic) {
        if (cosmetic == null || !RewardManager.isGemExchangeEligibleCosmetic(cosmetic.id())) {
            return 0L;
        }
        return EconomyManager.cosmeticGemPrice(cosmetic.id(), cosmetic.rarity().name());
    }

    private static int rarityOrder(String id) {
        CosmeticData cosmetic = CosmeticManager.get(id);
        return cosmetic != null && "MYTHIC".equals(cosmetic.rarity().name()) ? 0 : 1;
    }

    private static String categoryOf(String id) {
        CosmeticData cosmetic = CosmeticManager.get(id);
        CosmeticEquipSlot slot = CosmeticEquipSlot.forCosmetic(cosmetic);
        return slot == null ? "HEAD" : slot.name();
    }

    private static String acquisitionPath(CosmeticData cosmetic) {
        if (CosmeticManager.isAdminOnlyCosmetic(cosmetic.id())) {
            return "yoiko_core.cosmetic.acquisition.admin_only";
        }
        if (cosmetic.requiredRank().isBlank()) {
            return cosmetic.type() == CosmeticType.PARTICLE
                    ? "yoiko_core.cosmetic.acquisition.daily_bonus"
                    : "yoiko_core.cosmetic.acquisition.event_admin";
        }
        if ("supporter".equals(cosmetic.requiredRank())) {
            return "yoiko_core.cosmetic.acquisition.rank.supporter";
        }
        RankData rank = RankManager.get(cosmetic.requiredRank());
        String rankName = rank == null ? cosmetic.requiredRank() : rank.displayName();
        return "yoiko_core.cosmetic.acquisition.rank_required|" + rankName;
    }

    private static String acquisitionPath(RankData rank) {
        String key = rankAcquisitionKey(rank.id());
        if (!key.isBlank()) {
            return key;
        }
        return rank.acquisitionPath().isBlank() ? "yoiko_core.acquisition.admin" : rank.acquisitionPath();
    }

    private static boolean isEquipped(PlayerYoikoData data, CosmeticData cosmetic) {
        CosmeticEquipSlot slot = CosmeticEquipSlot.forCosmetic(cosmetic);
        return slot != null && cosmetic.id().equals(data.equippedCosmetics.getOrDefault(slot, ""));
    }

    private static boolean isRankEntry(String entryId) {
        return entryId != null && entryId.startsWith(RANK_PREFIX);
    }

    private static String stripRankPrefix(String entryId) {
        return isRankEntry(entryId) ? entryId.substring(RANK_PREFIX.length()) : entryId;
    }

    private static String cosmeticDisplayName(CosmeticData cosmetic) {
        if (cosmetic.type() == CosmeticType.PARTICLE) {
            return "yoiko_core.cosmetic." + cosmetic.id() + ".name";
        }
        return switch (cosmetic.id()) {
            case "supporter_crown", "sample_head_crystal_crown", "sample_chest_crystal_badge",
                    "witch_hat", "red_beret", "cat_ear_headband", "sylveon_headpiece",
                    "shiny_sylveon_headpiece", "jolteon_headpiece", "shiny_jolteon_headpiece",
                    "vaporeon_headpiece", "shiny_vaporeon_headpiece", "ribbon_brooch", "short_cape",
                    "crossed_swords", "back_greatsword", "mechanical_backpack", "small_model_wings" ->
                    "yoiko_core.cosmetic." + cosmetic.id() + ".name";
            default -> cosmetic.displayName();
        };
    }

    private static String rankDisplayName(RankData rank) {
        String key = rankNameKey(rank.id());
        return key.isBlank() ? rank.displayName() : key;
    }

    private static String rankNameKey(String rankId) {
        return switch (rankId) {
            case "supporter", "artist", "pokemon_champion_bronze", "pokemon_champion_silver",
                    "pokemon_champion_gold", "pokemon_champion_platinum", "pokemon_champion_diamond",
                    "title_radiant_tracker", "title_mirror_hunter", "title_crown_breaker",
                    "title_myth_awakener", "title_radiant_heir", "title_golden_shell_king",
                    "title_blue_miracle_friend", "title_master_appraiser" -> "yoiko_core.rank." + rankId + ".name";
            default -> "";
        };
    }

    private static String rankAcquisitionKey(String rankId) {
        return switch (rankId) {
            case "supporter", "artist", "pokemon_champion_bronze", "pokemon_champion_silver",
                    "pokemon_champion_gold", "pokemon_champion_platinum", "pokemon_champion_diamond",
                    "title_radiant_tracker", "title_mirror_hunter", "title_crown_breaker",
                    "title_myth_awakener", "title_radiant_heir", "title_golden_shell_king",
                    "title_blue_miracle_friend", "title_master_appraiser" -> "yoiko_core.rank." + rankId + ".acquisition";
            default -> "";
        };
    }

    private static String part(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index] : "";
    }
}
