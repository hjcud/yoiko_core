package com.yoiko.core.gacha;

import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.advancement.YoikoAdvancementManager;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.newspaper.WeeklyNewspaperManager;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public final class GachaManager {
    private static final Random RANDOM = new Random();

    private GachaManager() {
    }

    public static boolean rollFromTicket(ServerPlayer player, GachaType type, ItemStack ticketStack) {
        if (ticketStack.isEmpty()) {
            return false;
        }

        return GachaAnimationManager.start(player, type, ticketStack);
    }

    public static void completeTicketRoll(ServerPlayer player, GachaResult result) {
        grantResult(player, result);
        recordRollStats(player, result);
    }

    public static void recordRollStats(ServerPlayer player, GachaResult result) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        data.gachaStats.totalRolls++;
        if (result.rarity() == GachaRarity.LEGENDARY) {
            data.gachaStats.legendaryRolls++;
        }
        if (result.shiny()) {
            data.gachaStats.shinyRolls++;
        }
        updatePityCounters(data.gachaStats, result);
        savedData.addGachaLog(player, result.species(), result.rarity().name(), result.shiny(), result.level(), result.pityForced());
        WeeklyNewspaperManager.recordPokemonGacha(player, result.shiny());
        YoikoAdvancementManager.recordPokemonGacha(player, result);
    }

    public static GachaResult roll(GachaType type) {
        return roll(null, type);
    }

    public static GachaResult roll(ServerPlayer player, GachaType type) {
        String configurationError = configurationError(type);
        if (!configurationError.isBlank()) {
            throw new IllegalStateException(configurationError);
        }
        GachaRarity rarity = rollRarity(player, type);
        GachaRarity pityRarity = applyPity(player, rarity);
        boolean pityForced = pityRarity != rarity;
        rarity = pityRarity;
        String species = GachaPoolScanner.getPool().randomSpecies(rarity, RANDOM);
        boolean shiny = type == GachaType.SHINY_ALL || RANDOM.nextDouble() < shinyChance(player, type);
        int level = levelFor(rarity);
        return new GachaResult(species, rarity, shiny, level, pityForced);
    }

    public static String configurationError(GachaType type) {
        EnumMap<GachaRarity, Integer> configuredWeights = weights(null, type);
        int total = 0;
        for (Map.Entry<GachaRarity, Integer> entry : configuredWeights.entrySet()) {
            int weight = Math.max(0, entry.getValue());
            total += weight;
            if (weight > 0 && GachaPoolScanner.getPool().isEmpty(entry.getKey())) {
                return "positive weight with empty " + entry.getKey().name() + " pool";
            }
        }
        return total <= 0 ? "all rarity weights are zero" : "";
    }

    public static void grantResult(ServerPlayer player, GachaResult result) {
        if (!CobblemonPokemonFactory.giveDirect(player, result)) {
            String command = CobblemonPokemonFactory.buildGiveCommand(player, result);
            CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput().withPermission(4);
            player.server.getCommands().performPrefixedCommand(source, command);
        }

        Component shiny = result.shiny()
                ? Component.translatable("yoiko_core.gacha.shiny_prefix").withStyle(ChatFormatting.YELLOW)
                : Component.empty();
        Component pity = result.pityForced()
                ? Component.translatable("yoiko_core.gacha.pity_suffix").withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.empty();
        Component message = Component.translatable(
                "yoiko_core.message.gacha.result",
                player.getName(),
                shiny,
                result.rarity().getTranslatedName().withStyle(result.rarity().getColor()),
                PokemonNameFormatter.localizedName(result.species()).withStyle(result.rarity().getColor()),
                pity
        ).withStyle(ChatFormatting.WHITE);

        if (result.rarity() == GachaRarity.LEGENDARY || result.shiny()) {
            player.server.getPlayerList().broadcastSystemMessage(message, false);
        } else {
            player.sendSystemMessage(message);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    public static List<Component> describeRecentLogComponents(MinecraftServer server, int limit) {
        ListTag logs = ServerYoikoSavedData.get(server).gachaLogs();
        if (logs.isEmpty()) {
            return List.of(Component.translatable("yoiko_core.command.gacha.history.empty"));
        }
        int count = Math.min(Math.max(1, limit), Math.min(10, logs.size()));
        List<Component> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CompoundTag log = logs.getCompound(i);
            Component line = Component.literal((i + 1) + ". " + log.getString("playerName") + " - ")
                    .append(log.getBoolean("pityForced") ? Component.translatable("yoiko_core.gacha.pity_prefix").withStyle(ChatFormatting.LIGHT_PURPLE) : Component.empty())
                    .append(log.getBoolean("shiny") ? Component.translatable("yoiko_core.gacha.shiny_prefix").withStyle(ChatFormatting.YELLOW) : Component.empty())
                    .append(GachaRarity.fromString(log.getString("rarity")).getTranslatedName().withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" "))
                    .append(PokemonNameFormatter.localizedName(log.getString("species")).withStyle(ChatFormatting.WHITE))
                    .append(Component.translatable("yoiko_core.gacha.level_suffix", log.getInt("level")).withStyle(ChatFormatting.GRAY));
            lines.add(line);
        }
        return lines;
    }

    public static double rarityRatePercent(ServerPlayer player, GachaType type, GachaRarity rarity) {
        EnumMap<GachaRarity, Integer> weights = weights(player, type);
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        return percent(weights.getOrDefault(rarity, 0), total);
    }

    public static double speciesRatePercent(ServerPlayer player, GachaType type, GachaRarity rarity) {
        int poolSize = GachaPoolScanner.getPool().get(rarity).size();
        if (poolSize <= 0) {
            return 0.0D;
        }
        return rarityRatePercent(player, type, rarity) / poolSize;
    }

    public static String formatRate(double percent) {
        if (percent <= 0.0D) {
            return "0%";
        }
        if (percent < 0.01D) {
            return String.format(Locale.ROOT, "%.4f%%", percent);
        }
        return String.format(Locale.ROOT, "%.2f%%", percent);
    }

    public static Component describePityComponent(ServerPlayer player) {
        if (!YoikoCommonConfig.GACHA_PITY_ENABLED.get()) {
            return Component.translatable("yoiko_core.command.gacha.pity_disabled");
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        int mythicalRemaining = Math.max(0,
                YoikoCommonConfig.GACHA_MYTHICAL_PITY_COUNT.get() - data.gachaStats.rollsSinceMythical);
        int legendaryRemaining = Math.max(0,
                YoikoCommonConfig.GACHA_LEGENDARY_PITY_COUNT.get() - data.gachaStats.rollsSinceLegendary);
        return Component.translatable("yoiko_core.command.gacha.pity_status",
                mythicalRemaining, legendaryRemaining,
                data.gachaStats.rollsSinceMythical, data.gachaStats.rollsSinceLegendary);
    }

    private static GachaRarity rollRarity(ServerPlayer player, GachaType type) {
        EnumMap<GachaRarity, Integer> weights = weights(player, type);
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return GachaRarity.COMMON;
        }
        int value = RANDOM.nextInt(total);
        for (Map.Entry<GachaRarity, Integer> entry : weights.entrySet()) {
            value -= entry.getValue();
            if (value < 0) {
                return entry.getKey();
            }
        }
        return GachaRarity.COMMON;
    }

    private static GachaRarity applyPity(ServerPlayer player, GachaRarity rolledRarity) {
        if (player == null || !YoikoCommonConfig.GACHA_PITY_ENABLED.get()) {
            return rolledRarity;
        }
        PlayerYoikoData.GachaStats stats = ServerYoikoSavedData.get(player.server).getOrCreate(player).gachaStats;
        int legendaryThreshold = Math.max(1, YoikoCommonConfig.GACHA_LEGENDARY_PITY_COUNT.get());
        int mythicalThreshold = Math.max(1, YoikoCommonConfig.GACHA_MYTHICAL_PITY_COUNT.get());
        if (stats.rollsSinceLegendary >= legendaryThreshold - 1 && rolledRarity != GachaRarity.LEGENDARY) {
            return GachaRarity.LEGENDARY;
        }
        if (stats.rollsSinceMythical >= mythicalThreshold - 1
                && rolledRarity != GachaRarity.MYTHICAL
                && rolledRarity != GachaRarity.LEGENDARY) {
            return GachaRarity.MYTHICAL;
        }
        return rolledRarity;
    }

    private static void updatePityCounters(PlayerYoikoData.GachaStats stats, GachaResult result) {
        if (!YoikoCommonConfig.GACHA_PITY_ENABLED.get()) {
            return;
        }
        boolean resetOnHit = YoikoCommonConfig.GACHA_PITY_RESET_ON_HIT.get() || result.pityForced();
        if (result.rarity() == GachaRarity.LEGENDARY && resetOnHit) {
            stats.rollsSinceLegendary = 0;
            stats.rollsSinceMythical = 0;
            return;
        }

        stats.rollsSinceLegendary++;
        if ((result.rarity() == GachaRarity.MYTHICAL || result.rarity() == GachaRarity.LEGENDARY) && resetOnHit) {
            stats.rollsSinceMythical = 0;
        } else {
            stats.rollsSinceMythical++;
        }
    }

    private static EnumMap<GachaRarity, Integer> weights(ServerPlayer player, GachaType type) {
        EnumMap<GachaRarity, Integer> weights = new EnumMap<>(GachaRarity.class);
        switch (type) {
            case LEGENDARY -> {
                weights.put(GachaRarity.SUB_LEGENDARY, YoikoCommonConfig.LEGENDARY_SUB_LEGENDARY_WEIGHT.get());
                weights.put(GachaRarity.MYTHICAL, YoikoCommonConfig.LEGENDARY_MYTHICAL_WEIGHT.get());
                weights.put(GachaRarity.LEGENDARY, YoikoCommonConfig.LEGENDARY_LEGENDARY_WEIGHT.get());
            }
            case SHINY_ALL -> {
                weights.put(GachaRarity.COMMON, YoikoCommonConfig.SHINY_ALL_COMMON_WEIGHT.get());
                weights.put(GachaRarity.SUB_LEGENDARY, YoikoCommonConfig.SHINY_ALL_SUB_LEGENDARY_WEIGHT.get());
                weights.put(GachaRarity.MYTHICAL, YoikoCommonConfig.SHINY_ALL_MYTHICAL_WEIGHT.get());
                weights.put(GachaRarity.LEGENDARY, YoikoCommonConfig.SHINY_ALL_LEGENDARY_WEIGHT.get());
            }
            default -> {
                weights.put(GachaRarity.COMMON, YoikoCommonConfig.ALL_COMMON_WEIGHT.get());
                weights.put(GachaRarity.SUB_LEGENDARY, YoikoCommonConfig.ALL_SUB_LEGENDARY_WEIGHT.get());
                weights.put(GachaRarity.MYTHICAL, YoikoCommonConfig.ALL_MYTHICAL_WEIGHT.get());
                weights.put(GachaRarity.LEGENDARY, YoikoCommonConfig.ALL_LEGENDARY_WEIGHT.get());
            }
        }
        return weights;
    }

    private static double shinyChance(ServerPlayer player, GachaType type) {
        return switch (type) {
            case LEGENDARY -> YoikoCommonConfig.LEGENDARY_SHINY_CHANCE.get();
            case SHINY_ALL -> 1.0D;
            default -> YoikoCommonConfig.ALL_SHINY_CHANCE.get();
        };
    }

    private static double percent(int weight, int total) {
        return total == 0 ? 0.0D : (weight * 100.0D / total);
    }

    private static int levelFor(GachaRarity rarity) {
        int min;
        int max;
        switch (rarity) {
            case SUB_LEGENDARY -> {
                min = YoikoCommonConfig.SUB_LEGENDARY_LEVEL_MIN.get();
                max = YoikoCommonConfig.SUB_LEGENDARY_LEVEL_MAX.get();
            }
            case MYTHICAL -> {
                min = YoikoCommonConfig.MYTHICAL_LEVEL_MIN.get();
                max = YoikoCommonConfig.MYTHICAL_LEVEL_MAX.get();
            }
            case LEGENDARY -> {
                min = YoikoCommonConfig.LEGENDARY_LEVEL_MIN.get();
                max = YoikoCommonConfig.LEGENDARY_LEVEL_MAX.get();
            }
            default -> {
                min = YoikoCommonConfig.COMMON_LEVEL_MIN.get();
                max = YoikoCommonConfig.COMMON_LEVEL_MAX.get();
            }
        }
        if (max < min) {
            max = min;
        }
        return min + RANDOM.nextInt(max - min + 1);
    }
}
