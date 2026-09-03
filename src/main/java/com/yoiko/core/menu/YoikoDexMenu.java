package com.yoiko.core.menu;

import com.yoiko.core.gacha.GachaManager;
import com.yoiko.core.gacha.GachaPoolScanner;
import com.yoiko.core.gacha.GachaRarity;
import com.yoiko.core.gacha.GachaType;
import com.yoiko.core.network.OpenDexPayload;
import com.yoiko.core.data.PlayerYoikoData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

public final class YoikoDexMenu {
    private static final int PAGE_SIZE = 24;

    private YoikoDexMenu() {
    }

    public static boolean handleAction(ServerPlayer player, String action) {
        if (!action.startsWith("dex_")) {
            return false;
        }
        String[] parts = action.split("\\|", 7);
        switch (parts[0]) {
            case "dex_rarity" -> send(player, typePart(parts, 1), rarityPart(parts, 2), 0, "", part(parts, 3));
            case "dex_page" -> {
                send(player, typePart(parts, 1), rarityPart(parts, 2), intPart(parts, 3, 0),
                        part(parts, 4), part(parts, 5));
                playDexPageSound(player);
            }
            case "dex_search" -> send(player, typePart(parts, 1), rarityPart(parts, 2), 0, "", part(parts, 3));
            default -> player.sendSystemMessage(Component.translatable("yoiko_core.message.menu.unknown_action", action)
                    .withStyle(ChatFormatting.YELLOW));
        }
        return true;
    }

    public static void open(ServerPlayer player) {
        open(player, GachaType.ALL);
    }

    public static void open(ServerPlayer player, GachaType type) {
        MenuSessionManager.open(player, "dex");
        send(player, type, firstRarity(player, type), 0, "", "");
        playDexPageSound(player);
    }

    private static void playDexPageSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.65F, 1.1F);
    }

    private static void send(ServerPlayer player, GachaType type, GachaRarity rarity, int page,
                             String selectedSpecies, String requestedSearch) {
        List<String> all = new ArrayList<>(GachaPoolScanner.getPool().get(rarity));
        String search = PlayerYoikoData.sanitizeSearch(requestedSearch);
        if (!search.isBlank()) {
            String needle = search.toLowerCase(java.util.Locale.ROOT);
            all.removeIf(species -> !species.toLowerCase(java.util.Locale.ROOT).contains(needle));
        }
        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = Math.min(safePage * PAGE_SIZE, all.size());
        int to = Math.min(from + PAGE_SIZE, all.size());
        List<String> entries = new ArrayList<>(all.subList(from, to));
        String selected = selectedSpecies;
        if ((selected.isBlank() || !contains(all, selected)) && !entries.isEmpty()) {
            selected = entries.get(0);
        }
        GachaRarity selectedRarity = selected.isBlank() ? rarity : GachaPoolScanner.rarityOf(selected);
        if (selectedRarity == null) {
            selectedRarity = rarity;
        }
        String speciesRate = selected.isBlank()
                ? "-"
                : GachaManager.formatRate(GachaManager.speciesRatePercent(player, type, selectedRarity));
        String rarityRate = GachaManager.formatRate(GachaManager.rarityRatePercent(player, type, selectedRarity));
        PacketDistributor.sendToPlayer(player, new OpenDexPayload(
                ticketTitleKey(type),
                type.getId(),
                rarity.configKey(),
                safePage,
                totalPages,
                entries,
                selected,
                speciesRate,
                rarityRate,
                search,
                player.hasPermissions(2)
        ));
    }

    private static String ticketTitleKey(GachaType type) {
        return switch (type) {
            case LEGENDARY -> "item.yoiko_core.legendary_pokemon_gacha_ticket";
            case SHINY_ALL -> "item.yoiko_core.shiny_all_pokemon_gacha_ticket";
            case ALL -> "item.yoiko_core.all_pokemon_gacha_ticket";
        };
    }

    private static boolean contains(List<String> species, String target) {
        return species.stream().anyMatch(entry -> entry.equals(target));
    }

    private static GachaRarity firstRarity(ServerPlayer player, GachaType type) {
        for (GachaRarity rarity : GachaRarity.values()) {
            if (GachaManager.rarityRatePercent(player, type, rarity) > 0.0D
                    && !GachaPoolScanner.getPool().isEmpty(rarity)) {
                return rarity;
            }
        }
        return GachaRarity.COMMON;
    }

    private static GachaType typePart(String[] parts, int index) {
        return GachaType.fromString(part(parts, index));
    }

    private static GachaRarity rarityPart(String[] parts, int index) {
        return GachaRarity.fromString(part(parts, index));
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
