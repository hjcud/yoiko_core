package com.yoiko.core.relic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.advancement.YoikoAdvancementManager;
import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.newspaper.WeeklyNewspaperManager;
import com.yoiko.core.network.RelicGachaResultPayload;
import com.yoiko.core.registry.YoikoItems;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RelicManager {
    private static final int RELIC_CONFIG_SCHEMA_VERSION = 6;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path RELIC_FILE = FMLPaths.CONFIGDIR.get().resolve(YoikoServerCore.MODID).resolve("relics.json");
    private static final Random RANDOM = new Random();
    private static final Map<String, RelicData> RELICS = new LinkedHashMap<>();
    private static final EnumMap<RelicRarity, Integer> RARITY_WEIGHTS = new EnumMap<>(RelicRarity.class);
    private static final Map<Integer, UpgradeRate> UPGRADE_RATES = new LinkedHashMap<>();
    private static final Map<UUID, EffectSnapshot> EFFECT_SNAPSHOTS = new LinkedHashMap<>();
    private static final String CROWN_HIGH_AIR_STEP_EFFECT = "radiant_high_air_step_count";
    private static final String CROWN_DOUBLE_AIR_STEP_EFFECT = "radiant_double_air_step_count";
    private static final Set<String> CROWN_EXCLUSIVE_PRIMARY_RELIC_IDS = Set.of(
            "radiant_sky_step", "radiant_double_sky_step");
    private static final Map<RelicRarity, Double> SECONDARY_EFFECT_CHANCES = Map.of(
            RelicRarity.COMMON, 0.0D,
            RelicRarity.UNCOMMON, 20.0D,
            RelicRarity.RARE, 35.0D,
            RelicRarity.EPIC, 50.0D,
            RelicRarity.LEGENDARY, 70.0D,
            RelicRarity.MYSTIC, 100.0D,
            RelicRarity.RADIANT, 100.0D
    );
    static {
        reload();
    }

    private RelicManager() {
    }

    public static void init() {
        reload();
    }

    public static void reload() {
        EFFECT_SNAPSHOTS.clear();
        ensureFile();
        try (Reader reader = Files.newBufferedReader(RELIC_FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                throw new IllegalStateException("Relic config root is empty.");
            }
            int schemaVersion = readSchemaVersion(root);
            Map<String, RelicData> loaded = new LinkedHashMap<>();
            JsonArray relics = root.getAsJsonArray("relics");
            if (relics != null) {
                for (JsonElement element : relics) {
                    if (element.isJsonObject()) {
                        RelicData relic = readRelic(element.getAsJsonObject());
                        if (relic != null && relic.enabled()) {
                            if (loaded.putIfAbsent(relic.id(), relic) != null) {
                                throw new IllegalStateException("Duplicate relic id: " + relic.id());
                            }
                        }
                    }
                }
            }
            if (loaded.isEmpty()) {
                throw new IllegalStateException("No relics were loaded.");
            }
            EnumMap<RelicRarity, Integer> loadedWeights =
                    readRarityWeights(root.getAsJsonObject("rarityWeights"));
            Map<Integer, UpgradeRate> loadedUpgradeRates =
                    readUpgradeRates(root.getAsJsonObject("upgradeRates"));

            RELICS.clear();
            RELICS.putAll(loaded);
            RARITY_WEIGHTS.clear();
            RARITY_WEIGHTS.putAll(loadedWeights);
            UPGRADE_RATES.clear();
            UPGRADE_RATES.putAll(loadedUpgradeRates);
            logLoadedPools(schemaVersion);
        } catch (Exception exception) {
            activateBuiltInDefaults();
            Path quarantined = quarantineInvalidConfig();
            if (quarantined != null && writeDefaultFile()) {
                YoikoServerCore.LOGGER.error(
                        "Failed to load Yoiko relic config. Moved it to {} and created fresh schemaVersion {} defaults.",
                        quarantined,
                        RELIC_CONFIG_SCHEMA_VERSION,
                        exception);
            } else if (quarantined == null) {
                YoikoServerCore.LOGGER.error(
                        "Failed to load Yoiko relic config and could not preserve it before replacement; "
                                + "the source file was left unchanged and built-in defaults are active for this run.",
                        exception);
            } else {
                YoikoServerCore.LOGGER.error(
                        "Failed to load Yoiko relic config. It was moved to {}, but a new default file could not be written; "
                                + "built-in defaults are active for this run.",
                        quarantined,
                        exception);
            }
        }
    }

    private static int readSchemaVersion(JsonObject root) {
        int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
        if (version != RELIC_CONFIG_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported relic config schemaVersion: " + version);
        }
        return version;
    }

    private static void activateBuiltInDefaults() {
        RELICS.clear();
        for (RelicData relic : defaultRelics()) {
            RELICS.put(relic.id(), relic);
        }
        RARITY_WEIGHTS.clear();
        RARITY_WEIGHTS.putAll(defaultRarityWeights());
        UPGRADE_RATES.clear();
        UPGRADE_RATES.putAll(defaultUpgradeRates());
    }

    /** Keeps one diagnostic copy before a destructive replacement without running a rotating backup system. */
    private static Path quarantineInvalidConfig() {
        if (Files.notExists(RELIC_FILE)) {
            return null;
        }
        Path quarantine = RELIC_FILE.resolveSibling("relics.invalid.json");
        try {
            try {
                Files.move(RELIC_FILE, quarantine,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(RELIC_FILE, quarantine, StandardCopyOption.REPLACE_EXISTING);
            }
            return quarantine;
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Could not move invalid relic config to {}.", quarantine, exception);
            return null;
        }
    }

    public static void disableRelicsWithInvalidRuntimeHandlers() {
        Set<String> invalidEffects = RelicEffectHandlerRegistry.invalidRuntimeEffectKeys();
        if (invalidEffects.isEmpty()) {
            return;
        }
        int previousSize = RELICS.size();
        RELICS.entrySet().removeIf(entry -> invalidEffects.contains(entry.getValue().effect()));
        int disabled = previousSize - RELICS.size();
        if (disabled > 0) {
            YoikoServerCore.LOGGER.error(
                    "Disabled {} relic definitions because their runtime handlers are unavailable: {}",
                    disabled, String.join(", ", invalidEffects));
        }
        if (RELICS.isEmpty()) {
            YoikoServerCore.LOGGER.error(
                    "No usable relic definitions remain after runtime handler validation. Relic ticket rolls will be rejected.");
        }
    }

    private static void logLoadedPools(int schemaVersion) {
        int totalWeight = RARITY_WEIGHTS.values().stream().mapToInt(Integer::intValue).sum();
        YoikoServerCore.LOGGER.info(
                "Loaded {} relic definitions (schemaVersion={}, total rarity weight={}).",
                RELICS.size(), schemaVersion, totalWeight);
        for (RelicRarity rarity : RelicRarity.values()) {
            int weight = RARITY_WEIGHTS.getOrDefault(rarity, 0);
            int eligible = eligibleRelics(rarity).size();
            double chance = totalWeight <= 0 ? 0.0D : weight * 100.0D / totalWeight;
            if (weight > 0 && eligible == 0) {
                YoikoServerCore.LOGGER.error(
                        "Relic pool {} has weight {} ({}) but no eligible relics.",
                        rarity, weight, formatRate(chance));
            } else {
                YoikoServerCore.LOGGER.info(
                        "Relic pool {}: weight={}, chance={}, eligible={}",
                        rarity, weight, formatRate(chance), eligible);
            }
        }
    }

    public static Collection<String> ids() {
        return RELICS.keySet().stream()
                .filter(id -> !CROWN_EXCLUSIVE_PRIMARY_RELIC_IDS.contains(id))
                .toList();
    }

    public static RelicData get(String id) {
        return RELICS.get(id);
    }

    public static List<RelicData> dexRelics() {
        return RELICS.values().stream()
                .filter(relic -> !CROWN_EXCLUSIVE_PRIMARY_RELIC_IDS.contains(relic.id()))
                .filter(relic -> RelicEffectRegistry.isRuntimeAvailable(relic.effect()))
                .toList();
    }

    public static double relicRatePercent(String relicId) {
        return relicRatePercent(relicId, RelicAppraisalCategory.ALL);
    }

    public static double relicRatePercent(String relicId, RelicAppraisalCategory appraisalCategory) {
        RelicData relic = RELICS.get(relicId);
        if (relic == null || RELICS.isEmpty()) {
            return 0.0D;
        }
        RelicAppraisalCategory category = appraisalCategory == null
                ? RelicAppraisalCategory.ALL : appraisalCategory;
        double rate = 0.0D;
        for (RelicRarity rarity : relic.allowedRarities()) {
            List<RelicData> eligible = eligibleRelics(rarity);
            List<RelicData> pool = category == RelicAppraisalCategory.ALL || rarity == RelicRarity.RADIANT
                    ? eligible
                    : eligible.stream().filter(candidate -> category.matches(candidate.effect())).toList();
            if (!pool.isEmpty() && pool.stream().anyMatch(candidate -> candidate.id().equals(relicId))) {
                rate += rarityRatePercent(rarity) / pool.size();
            }
        }
        return rate;
    }

    public static double rarityRatePercent(RelicRarity rarity) {
        int total = RARITY_WEIGHTS.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return 0.0D;
        }
        return RARITY_WEIGHTS.getOrDefault(rarity, 0) * 100.0D / total;
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

    public static String displayNameKey(String relicId, String fallback) {
        if (fallback != null && fallback.startsWith("yoiko_core.")) {
            return fallback;
        }
        return switch (relicId) {
            case "pokemon_friendship_bonus", "pokemon_exp_bonus", "catch_rate_bonus", "shiny_luck",
                    "player_exp_bonus", "storage_expand", "movement_speed_bonus", "max_health_bonus",
                    "block_interaction_range_bonus",
                    "critical_strike_chance", "critical_strike_damage", "knockback_resistance_bonus",
                    "armor_toughness_bonus", "luck_bonus", "hunter_hourglass", "riposte_medal",
                    "guardian_pulse", "victory_ribbon", "hawk_bowstring", "purification_bell",
                    "survival_anklet", "stalker_fang", "purification_ribbon",
                    "first_moonlight", "grudge_nail", "charge_spur", "frost_needle",
                    "victory_drum", "restoration_score", "guardian_hourglass",
                    "harvest_seal", "golden_carrot_charm", "waystone_return_charm", "crystal_compass",
                    "common_spawn_bonus", "rare_spawn_bonus", "ultra_rare_spawn_bonus",
                    "totemless_revive_chance", "mounted_pokemon_speed_bonus",
                    "capture_rewind",
                    "radiant_mercy", "radiant_resonance_crown", "radiant_weakest_echo",
                    "radiant_chromatic_contract", "radiant_satiation_crystal", "radiant_lava_cocoon",
                    "radiant_sky_step", "radiant_double_sky_step", "radiant_combo_constellation" ->
                    "yoiko_core.relic." + relicId + ".name";
            default -> fallback;
        };
    }

    public static double effectBonus(ServerPlayer player, String effectId) {
        RelicEffectDefinition definition = RelicEffectRegistry.get(effectId);
        if (definition == null) {
            YoikoServerCore.LOGGER.error("Unknown relic effect key requested at runtime: {}", effectId);
            return 0.0D;
        }
        return effectSnapshot(player).bonuses().getOrDefault(effectId, 0.0D);
    }

    public static boolean hasEquippedEffect(ServerPlayer player, String effectId) {
        return effectSnapshot(player).equippedEffects().contains(effectId);
    }

    public static void invalidateEffectSnapshot(ServerPlayer player) {
        if (player != null) {
            EFFECT_SNAPSHOTS.remove(player.getUUID());
            RelicRuntimeManager.refreshPlayerAttributes(player);
        }
    }

    public static void clearEffectSnapshot(UUID playerUuid) {
        if (playerUuid != null) {
            EFFECT_SNAPSHOTS.remove(playerUuid);
        }
    }

    private static EffectSnapshot effectSnapshot(ServerPlayer player) {
        return EFFECT_SNAPSHOTS.computeIfAbsent(player.getUUID(), ignored -> buildEffectSnapshot(player));
    }

    private static EffectSnapshot buildEffectSnapshot(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        Map<String, EquippedEffectWinner> winners = resolveEquippedEffects(data, Set.of());
        Map<String, Double> bonuses = new LinkedHashMap<>();
        for (Map.Entry<String, EquippedEffectWinner> entry : winners.entrySet()) {
            bonuses.put(entry.getKey(), entry.getValue().value());
        }
        return new EffectSnapshot(Map.copyOf(bonuses), Set.copyOf(winners.keySet()));
    }

    public static RelicRarity equippedRarity(ServerPlayer player, String effectId) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        EquippedEffectWinner winner = resolveEquippedEffects(data, Set.of()).get(effectId);
        PlayerYoikoData.RelicInstance instance = winner == null ? null : find(data, winner.uuid());
        return instance == null ? null : instance.rarity;
    }

    private static Map<String, EquippedEffectWinner> resolveEquippedEffects(
            PlayerYoikoData data,
            Set<String> excludedUuids
    ) {
        Map<String, Integer> effectiveLevels = effectiveEquippedLevels(data, excludedUuids);
        Map<String, EquippedEffectWinner> winners = new LinkedHashMap<>();
        for (int equippedSlot = 0; equippedSlot < data.equippedRelics.size(); equippedSlot++) {
            String uuid = data.equippedRelics.get(equippedSlot);
            if (uuid.isBlank() || excludedUuids.contains(uuid)) {
                continue;
            }
            PlayerYoikoData.RelicInstance instance = find(data, uuid);
            RelicData relic = instance == null ? null : RELICS.get(instance.relicId);
            if (instance == null || relic == null) {
                continue;
            }
            int effectiveLevel = effectiveLevels.getOrDefault(uuid, instance.level);
            considerEffectWinner(
                    winners,
                    relic.effect(),
                    relic.value(instance.rarity, effectiveLevel),
                    uuid,
                    equippedSlot,
                    0
            );
            considerEffectWinner(
                    winners,
                    instance.secondaryEffect,
                    secondaryEffectValue(instance, effectiveLevel),
                    uuid,
                    equippedSlot,
                    1
            );
        }
        return winners;
    }

    private static void considerEffectWinner(
            Map<String, EquippedEffectWinner> winners,
            String effect,
            double value,
            String uuid,
            int equippedSlot,
            int effectSlot
    ) {
        if (effect == null || effect.isBlank()
                || !RelicEffectRegistry.contains(effect)
                || !RelicEffectRegistry.isRuntimeAvailable(effect)) {
            return;
        }
        EquippedEffectWinner current = winners.get(effect);
        if (current == null || Double.compare(value, current.value()) > 0) {
            winners.put(effect, new EquippedEffectWinner(uuid, equippedSlot, effectSlot, value));
        }
    }

    public static boolean grant(ServerPlayer player, String relicId, RelicRarity rarity) {
        return grant(player, relicId, rarity, "random");
    }

    /** Grants a random enabled relic of the requested rarity with a guaranteed random secondary effect. */
    public static String grantRandom(ServerPlayer player, RelicRarity rarity) {
        if (player == null || rarity == null) {
            return "";
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        if (data.ownedRelics.size() >= PlayerYoikoData.MAX_OWNED_RELICS) {
            return "";
        }
        List<RelicData> eligible = eligibleRelics(rarity);
        if (eligible.isEmpty()) {
            return "";
        }
        RelicData relic = eligible.get(RANDOM.nextInt(eligible.size()));
        return grant(player, relic.id(), rarity, "random") ? relic.id() : "";
    }

    public static boolean grant(
            ServerPlayer player,
            String relicId,
            RelicRarity rarity,
            String secondaryEffectOption
    ) {
        RelicData relic = RELICS.get(relicId);
        if (relic == null || !relic.allows(rarity)) {
            return false;
        }
        String secondaryEffect = selectSecondaryEffect(relic, rarity, secondaryEffectOption);
        if (secondaryEffect == null) {
            return false;
        }
        addRelic(player, relicId, rarity, secondaryEffect);
        return true;
    }

    public static List<String> secondaryEffectIds() {
        return RELICS.values().stream()
                .filter(relic -> relic.enabled() && relic.equipGroup() == RelicEquipGroup.STANDARD)
                .map(RelicData::effect)
                .filter(effect -> !effect.isBlank() && RelicEffectRegistry.isRuntimeAvailable(effect))
                .distinct()
                .toList();
    }

    public static List<String> secondaryEffectIds(String relicId) {
        RelicData relic = RELICS.get(relicId);
        return relic == null ? List.of() : secondaryEffectCandidates(relic);
    }

    public static double secondaryEffectChance(RelicRarity rarity) {
        return SECONDARY_EFFECT_CHANCES.getOrDefault(rarity, 0.0D);
    }

    public static boolean isValidSecondaryEffectOption(String relicId, String option) {
        RelicData relic = RELICS.get(relicId);
        if (relic == null) {
            return false;
        }
        String normalized = option == null || option.isBlank()
                ? "auto"
                : option.trim().toLowerCase(Locale.ROOT);
        return "auto".equals(normalized)
                || "none".equals(normalized)
                || "random".equals(normalized)
                || isValidSecondaryEffect(relic, normalized);
    }

    public static boolean rollFromTicket(ServerPlayer player, ItemStack ticketStack) {
        return rollFromTicketResult(player, ticketStack).success();
    }

    public static RollResult rollFromInventory(ServerPlayer player) {
        ItemStack ticketStack = findStack(player, YoikoItems.RELIC_GACHA_TICKET.get());
        if (ticketStack.isEmpty() && !player.getAbilities().instabuild) {
            player.sendSystemMessage(Component.translatable("message.yoiko_core.relic_gacha.no_ticket").withStyle(ChatFormatting.RED));
            return new RollResult(false, "", "message.yoiko_core.relic_gacha.no_ticket");
        }
        return rollFromTicketResult(player, ticketStack);
    }

    public static RollResult rollFromTicketResult(ServerPlayer player, ItemStack ticketStack) {
        PendingRoll roll = beginTicketRoll(player, ticketStack);
        if (roll == null) {
            return new RollResult(false, "", "");
        }
        return completeTicketRoll(player, roll);
    }

    public static double applyEffect(ServerPlayer player, String effectId, double baseValue) {
        RelicEffectDefinition definition = RelicEffectRegistry.get(effectId);
        if (definition == null) {
            YoikoServerCore.LOGGER.error("Attempted to apply unknown relic effect '{}'", effectId);
            return baseValue;
        }
        return definition.apply(baseValue, effectBonus(player, effectId));
    }

    public static PendingRoll beginTicketRoll(ServerPlayer player, ItemStack ticketStack) {
        return beginTicketRoll(player, ticketStack, RelicAppraisalCategory.ALL);
    }

    public static PendingRoll beginTicketRoll(ServerPlayer player, ItemStack ticketStack,
                                              RelicAppraisalCategory appraisalCategory) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        if (data.ownedRelics.size() >= PlayerYoikoData.MAX_OWNED_RELICS) {
            player.sendSystemMessage(Component.translatable("message.yoiko_core.relic_gacha.full").withStyle(ChatFormatting.RED));
            return null;
        }
        if (RELICS.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.yoiko_core.relic_gacha.empty_pool").withStyle(ChatFormatting.RED));
            return null;
        }
        if (ticketStack.isEmpty() && !player.getAbilities().instabuild) {
            player.sendSystemMessage(Component.translatable("message.yoiko_core.relic_gacha.no_ticket").withStyle(ChatFormatting.RED));
            return null;
        }

        RelicRarity rarity = randomRarity();
        if (rarity == null) {
            YoikoServerCore.LOGGER.error("Rejected relic roll because the configured rarity weights are invalid.");
            player.sendSystemMessage(Component.translatable(
                    "message.yoiko_core.relic_gacha.empty_pool").withStyle(ChatFormatting.RED));
            return null;
        }
        List<RelicData> eligible = eligibleRelics(rarity);
        if (eligible.isEmpty()) {
            YoikoServerCore.LOGGER.error(
                    "Rejected relic roll because rarity {} has a positive weight but no eligible relics.", rarity);
            player.sendSystemMessage(Component.translatable(
                    "message.yoiko_core.relic_gacha.empty_rarity_pool",
                    Component.translatable(rarityTranslationKey(rarity))).withStyle(ChatFormatting.RED));
            return null;
        }
        RelicAppraisalCategory category = appraisalCategory == null
                ? RelicAppraisalCategory.ALL : appraisalCategory;
        boolean focused = category != RelicAppraisalCategory.ALL;
        List<RelicData> focusedPool = focused && rarity != RelicRarity.RADIANT
                ? eligible.stream().filter(candidate -> category.matches(candidate.effect())).toList()
                : eligible;
        if (focusedPool.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "message.yoiko_core.relic_gacha.empty_category_pool",
                    Component.translatable(category.translationKey()),
                    Component.translatable(rarityTranslationKey(rarity))
            ).withStyle(ChatFormatting.RED));
            return null;
        }
        RelicData relic = focusedPool.get(RANDOM.nextInt(focusedPool.size()));
        if (!player.getAbilities().instabuild) {
            ticketStack.shrink(1);
        }
        return new PendingRoll(relic.id(), rarity, category, focused, RelicRollSource.STANDARD);
    }

    /** Prepares the event-exclusive Crown Rabbit roll without selecting below Epic rarity. */
    public static PendingRoll beginCrownTicketRoll(ServerPlayer player, ItemStack ticketStack) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        if (data.ownedRelics.size() >= PlayerYoikoData.MAX_OWNED_RELICS) {
            player.sendSystemMessage(Component.translatable(
                    "message.yoiko_core.relic_gacha.full").withStyle(ChatFormatting.RED));
            return null;
        }
        if (ticketStack.isEmpty() && !player.getAbilities().instabuild) {
            player.sendSystemMessage(Component.translatable(
                    "message.yoiko_core.crown_relic.no_ticket").withStyle(ChatFormatting.RED));
            return null;
        }
        RelicRarity rarity = randomCrownRarity();
        if (rarity == null) {
            YoikoServerCore.LOGGER.error(
                    "Rejected Crown sealed relic because every Crown rarity weight is zero.");
            player.sendSystemMessage(Component.translatable(
                    "message.yoiko_core.crown_relic.empty_pool").withStyle(ChatFormatting.RED));
            return null;
        }
        List<RelicData> eligible = eligibleRelics(rarity);
        if (eligible.isEmpty()) {
            YoikoServerCore.LOGGER.error(
                    "Rejected Crown sealed relic because {} has no eligible relic definitions.", rarity);
            player.sendSystemMessage(Component.translatable(
                    "message.yoiko_core.crown_relic.empty_pool").withStyle(ChatFormatting.RED));
            return null;
        }
        RelicData relic = eligible.get(RANDOM.nextInt(eligible.size()));
        if (!player.getAbilities().instabuild) {
            ticketStack.shrink(1);
        }
        return new PendingRoll(relic.id(), rarity, RelicAppraisalCategory.ALL, false,
                RelicRollSource.CROWN_RABBIT);
    }

    public static RollResult completeTicketRoll(ServerPlayer player, PendingRoll roll) {
        return completeTicketRoll(player, roll, false);
    }

    public static RollResult completeTicketRoll(ServerPlayer player, PendingRoll roll, boolean animatedReveal) {
        CommittedRoll committed = commitTicketRoll(player, roll);
        if (committed == null) {
            return new RollResult(false, "", "");
        }
        return animatedReveal
                ? revealCommittedTicketRoll(player, committed)
                : presentCommittedTicketRoll(player, committed);
    }

    /**
     * Commits the rolled relic to persistent player data before any world-space reveal begins.
     * The animation is presentation only: disconnecting or stopping the server cannot leave a
     * consumed ticket represented solely by an in-memory pending roll.
     */
    public static CommittedRoll commitTicketRoll(ServerPlayer player, PendingRoll roll) {
        RelicData relic = RELICS.get(roll.relicId());
        if (relic == null || !relic.enabled() || !relic.allows(roll.rarity())) {
            refundRelicTicket(player, roll);
            player.sendSystemMessage(Component.translatable("message.yoiko_core.relic_gacha.empty_pool").withStyle(ChatFormatting.RED));
            return null;
        }

        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        if (data.ownedRelics.size() >= PlayerYoikoData.MAX_OWNED_RELICS) {
            refundRelicTicket(player, roll);
            player.sendSystemMessage(Component.translatable("message.yoiko_core.relic_gacha.full").withStyle(ChatFormatting.RED));
            return null;
        }

        String secondaryEffect = roll.source() == RelicRollSource.CROWN_RABBIT
                ? crownJumpEffect(roll.rarity())
                : roll.categoryFocused() && roll.rarity() == RelicRarity.RADIANT
                        ? randomSecondaryEffect(relic, roll.category())
                        : selectSecondaryEffect(relic, roll.rarity(), "auto");
        if (roll.categoryFocused() && roll.rarity() == RelicRarity.RADIANT && secondaryEffect.isBlank()) {
            refundRelicTicket(player, roll);
            player.sendSystemMessage(Component.translatable(
                    "message.yoiko_core.relic_gacha.empty_category_pool",
                    Component.translatable(roll.category().translationKey()),
                    Component.translatable(rarityTranslationKey(roll.rarity()))
            ).withStyle(ChatFormatting.RED));
            return null;
        }
        PlayerYoikoData.RelicInstance instance = addRelic(
                player, relic.id(), roll.rarity(), secondaryEffect,
                roll.source() == RelicRollSource.CROWN_RABBIT);
        YoikoAdvancementManager.recordRelicAppraisal(player, roll, instance);
        Component relicName = Component.translatable(displayNameKey(relic.id(), relic.displayName())).withStyle(roll.rarity().getColor());
        Component rarityName = Component.translatable(rarityTranslationKey(roll.rarity())).withStyle(roll.rarity().getColor());
        Component message = Component.translatable("message.yoiko_core.relic_gacha.result", relicName, rarityName, instance.level)
                .withStyle(ChatFormatting.GOLD);
        if (instance.crownRabbitRelic) {
            message = Component.translatable("message.yoiko_core.crown_relic.result",
                    relicName, rarityName,
                    Component.translatable("yoiko_core.effect_short." + secondaryEffect))
                    .withStyle(ChatFormatting.GOLD);
        }
        if (roll.rarity() == RelicRarity.RADIANT) {
            WeeklyNewspaperManager.recordRadiantRelic(player);
        }
        RollResult result = new RollResult(true, instance.uuid.toString(), message.getString());
        RelicGachaResultPayload payload = new RelicGachaResultPayload(
                instance.uuid,
                displayNameKey(relic.id(), relic.displayName()),
                instance.rarity,
                instance.level,
                relic.effect(),
                relic.value(instance.rarity, instance.level),
                instance.secondaryEffect,
                secondaryEffectValue(instance, instance.level)
        );
        return new CommittedRoll(result, payload, roll.rarity(), relicName, rarityName, message);
    }

    /** Shows the dedicated result card. Only the separately configured rare server broadcast uses chat. */
    public static RollResult revealCommittedTicketRoll(ServerPlayer player, CommittedRoll committed) {
        RelicGachaAnnouncements.announce(
                player,
                committed.rarity(),
                committed.relicName(),
                committed.rarityName(),
                committed.message(),
                false
        );
        PacketDistributor.sendToPlayer(player, committed.payload());
        return committed.result();
    }

    /** Announces a committed rare result when a reveal is interrupted, without adding personal result chat. */
    public static void announceCommittedTicketRoll(ServerPlayer player, CommittedRoll committed) {
        RelicGachaAnnouncements.announce(
                player,
                committed.rarity(),
                committed.relicName(),
                committed.rarityName(),
                committed.message(),
                false
        );
    }

    private static RollResult presentCommittedTicketRoll(ServerPlayer player, CommittedRoll committed) {
        RelicGachaAnnouncements.announce(
                player,
                committed.rarity(),
                committed.relicName(),
                committed.rarityName(),
                committed.message(),
                true
        );
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.8F, 1.25F);
        return committed.result();
    }

    public static boolean remove(ServerPlayer player, UUID relicUuid) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        PlayerYoikoData.RelicInstance relic = find(data, relicUuid);
        if (relic == null) {
            return false;
        }
        StorageReductionBlock block = storageReductionBlock(data, relic.uuid.toString());
        if (block != null) {
            sendStorageReductionBlocked(player, block);
            return false;
        }
        boolean removed = data.ownedRelics.remove(relic);
        if (!removed) {
            return false;
        }
        data.pruneEquippedRelics();
        clampStoragePage(data);
        data.captureActiveRelicPreset();
        ServerYoikoSavedData.get(player.server).markDirty(player);
        invalidateEffectSnapshot(player);
        return true;
    }

    public static EquipResult equip(ServerPlayer player, UUID relicUuid) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        PlayerYoikoData.RelicInstance relic = find(data, relicUuid);
        if (relic == null) {
            return new EquipResult(false, "", message("yoiko_core.message.relic.equip_not_found"));
        }
        RelicData relicData = RELICS.get(relic.relicId);
        if (relicData == null || !relicData.allows(relic.rarity)) {
            return new EquipResult(false, relic.uuid.toString(), message("yoiko_core.message.relic.definition_missing"));
        }
        String uuid = relic.uuid.toString();
        int equipped = data.equippedRelics.indexOf(uuid);
        if (equipped >= 0) {
            return new EquipResult(false, uuid, message("yoiko_core.message.relic.already_equipped", equipped + 1));
        }
        if (equippedGroupConflict(data, relicData, Set.of())) {
            return new EquipResult(false, uuid, message("yoiko_core.message.relic.special_conflict"));
        }
        int empty = data.equippedRelics.indexOf("");
        if (empty < 0) {
            return new EquipResult(false, uuid, message("yoiko_core.message.relic.no_empty_slot"));
        }
        data.equippedRelics.set(empty, uuid);
        data.captureActiveRelicPreset();
        ServerYoikoSavedData.get(player.server).markDirty(player);
        invalidateEffectSnapshot(player);
        playRelicEquipSound(player);
        YoikoAdvancementManager.recordEquip(player);
        return new EquipResult(true, uuid, message("yoiko_core.message.relic.equipped", empty + 1));
    }

    public static EquipResult equipToSlot(ServerPlayer player, UUID relicUuid, int slot) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        if (slot < 0 || slot >= PlayerYoikoData.RELIC_EQUIP_SLOT_COUNT) {
            return new EquipResult(false, "", message("yoiko_core.message.relic.invalid_slot"));
        }
        PlayerYoikoData.RelicInstance relic = find(data, relicUuid);
        if (relic == null) {
            return new EquipResult(false, "", message("yoiko_core.message.relic.equip_not_found"));
        }
        RelicData relicData = RELICS.get(relic.relicId);
        if (relicData == null || !relicData.allows(relic.rarity)) {
            return new EquipResult(false, relic.uuid.toString(), message("yoiko_core.message.relic.definition_missing"));
        }

        String uuid = relic.uuid.toString();
        int currentSlot = data.equippedRelics.indexOf(uuid);
        if (currentSlot == slot) {
            return new EquipResult(false, uuid, message("yoiko_core.message.relic.already_in_slot"));
        }

        String replacedUuid = data.equippedRelics.get(slot);
        if (!replacedUuid.isBlank() && !replacedUuid.equals(uuid)) {
            StorageReductionBlock block = storageReductionBlock(data, replacedUuid);
            if (block != null) {
                return new EquipResult(false, uuid, storageReductionMessage(block));
            }
        }

        Set<String> ignoredUuids = new LinkedHashSet<>();
        ignoredUuids.add(uuid);
        if (!replacedUuid.isBlank()) {
            ignoredUuids.add(replacedUuid);
        }
        if (equippedGroupConflict(data, relicData, ignoredUuids)) {
            return new EquipResult(false, uuid, message("yoiko_core.message.relic.special_conflict"));
        }

        if (currentSlot >= 0) {
            data.equippedRelics.set(currentSlot, "");
        }
        data.equippedRelics.set(slot, uuid);
        data.captureActiveRelicPreset();
        clampStoragePage(data);
        ServerYoikoSavedData.get(player.server).markDirty(player);
        invalidateEffectSnapshot(player);
        playRelicEquipSound(player);
        YoikoAdvancementManager.recordEquip(player);
        return new EquipResult(true, uuid, message("yoiko_core.message.relic.equipped", slot + 1));
    }

    private static void playRelicEquipSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.85F, 1.05F);
    }

    public static EquipResult unequip(ServerPlayer player, UUID relicUuid) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        String uuid = relicUuid.toString();
        int equipped = data.equippedRelics.indexOf(uuid);
        if (equipped < 0) {
            return new EquipResult(false, uuid, message("yoiko_core.message.relic.not_equipped"));
        }
        StorageReductionBlock block = storageReductionBlock(data, uuid);
        if (block != null) {
            return new EquipResult(false, uuid, storageReductionMessage(block));
        }
        data.equippedRelics.set(equipped, "");
        data.captureActiveRelicPreset();
        clampStoragePage(data);
        ServerYoikoSavedData.get(player.server).markDirty(player);
        invalidateEffectSnapshot(player);
        return new EquipResult(true, uuid, message("yoiko_core.message.relic.unequipped", equipped + 1));
    }

    public static EquipResult unequipToStorageSlot(ServerPlayer player, UUID relicUuid, int targetStorageSlot) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        String uuid = relicUuid.toString();
        int equipped = data.equippedRelics.indexOf(uuid);
        if (equipped < 0) {
            return new EquipResult(false, uuid, message("yoiko_core.message.relic.not_equipped"));
        }
        if (targetStorageSlot < 0 || targetStorageSlot >= PlayerYoikoData.MAX_OWNED_RELICS) {
            return new EquipResult(false, uuid, message("yoiko_core.message.relic.invalid_storage_slot"));
        }
        StorageReductionBlock block = storageReductionBlock(data, uuid);
        if (block != null) {
            return new EquipResult(false, uuid, storageReductionMessage(block));
        }

        PlayerYoikoData.RelicInstance unequippedRelic = find(data, relicUuid);
        if (unequippedRelic == null) {
            return new EquipResult(false, uuid, message("yoiko_core.message.relic.equip_not_found"));
        }
        PlayerYoikoData.RelicInstance occupant = null;
        for (PlayerYoikoData.RelicInstance relic : data.ownedRelics) {
            if (!relic.uuid.equals(relicUuid)
                    && !data.equippedRelics.contains(relic.uuid.toString())
                    && relic.storageSlot == targetStorageSlot) {
                occupant = relic;
                break;
            }
        }
        int displacedStorageSlot = -1;
        if (occupant != null) {
            displacedStorageSlot = firstFreeRelicStorageSlot(data, relicUuid);
            if (displacedStorageSlot < 0) {
                return new EquipResult(false, uuid, message("yoiko_core.message.relic.no_empty_storage_slot"));
            }
        }

        data.equippedRelics.set(equipped, "");
        data.captureActiveRelicPreset();
        unequippedRelic.storageSlot = targetStorageSlot;
        if (occupant != null) {
            occupant.storageSlot = displacedStorageSlot;
        }
        clampStoragePage(data);
        ServerYoikoSavedData.get(player.server).markDirty(player);
        invalidateEffectSnapshot(player);
        return new EquipResult(true, uuid, message("yoiko_core.message.relic.unequipped", equipped + 1));
    }

    public static EquipResult unequipSlot(ServerPlayer player, int slot) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        if (slot < 0 || slot >= PlayerYoikoData.RELIC_EQUIP_SLOT_COUNT) {
            return new EquipResult(false, "", message("yoiko_core.message.relic.invalid_slot"));
        }
        String uuid = data.equippedRelics.get(slot);
        if (uuid.isBlank()) {
            return new EquipResult(false, "", message("yoiko_core.message.relic.empty_slot"));
        }
        StorageReductionBlock block = storageReductionBlock(data, uuid);
        if (block != null) {
            return new EquipResult(false, uuid, storageReductionMessage(block));
        }
        data.equippedRelics.set(slot, "");
        data.captureActiveRelicPreset();
        clampStoragePage(data);
        ServerYoikoSavedData.get(player.server).markDirty(player);
        invalidateEffectSnapshot(player);
        return new EquipResult(true, uuid, message("yoiko_core.message.relic.unequipped", slot + 1));
    }

    public static EquipResult selectChromaticContractTarget(ServerPlayer player, UUID relicUuid) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        PlayerYoikoData.RelicInstance contract =
                equippedInstanceForEffect(data, "chromatic_contract_level_shift");
        if (contract == null) {
            return new EquipResult(
                    false, relicUuid == null ? "" : relicUuid.toString(),
                    message("yoiko_core.message.relic.contract_not_equipped")
            );
        }
        PlayerYoikoData.RelicInstance target = relicUuid == null ? null : find(data, relicUuid);
        RelicData targetData = target == null ? null : RELICS.get(target.relicId);
        String targetUuid = target == null ? "" : target.uuid.toString();
        if (target == null || targetData == null
                || targetData.equipGroup() != RelicEquipGroup.STANDARD
                || !data.equippedRelics.contains(targetUuid)) {
            return new EquipResult(
                    false, targetUuid,
                    message("yoiko_core.message.relic.contract_invalid_target")
            );
        }

        int currentSlots = activeStorageSlots(data, "");
        String previousTarget = data.radiantContractTargetUuid;
        data.radiantContractTargetUuid = targetUuid;
        int nextSlots = activeStorageSlots(data, "");
        StorageReductionBlock block = storageReductionBlock(data, currentSlots, nextSlots);
        if (block != null) {
            data.radiantContractTargetUuid = previousTarget;
            return new EquipResult(false, targetUuid, storageReductionMessage(block));
        }

        clampStoragePage(data);
        data.captureActiveRelicPreset();
        ServerYoikoSavedData.get(player.server).markDirty(player);
        invalidateEffectSnapshot(player);
        return new EquipResult(
                true, targetUuid,
                message("yoiko_core.message.relic.contract_target_selected")
        );
    }

    public static DismantleResult dismantle(ServerPlayer player, UUID relicUuid) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        PlayerYoikoData.RelicInstance relic = find(data, relicUuid);
        if (relic == null) {
            return new DismantleResult(false, "", message("yoiko_core.message.relic.dismantle_not_found"));
        }
        if (data.equippedRelics.contains(relic.uuid.toString())) {
            return new DismantleResult(false, relic.uuid.toString(), message("yoiko_core.message.relic.dismantle_equipped"));
        }
        if (relic.locked) {
            return new DismantleResult(false, relic.uuid.toString(), message("yoiko_core.message.relic.dismantle_locked"));
        }
        int scrap = scrapValue(relic.rarity);
        data.ownedRelics.remove(relic);
        data.relicScrap += scrap;
        data.pruneEquippedRelics();
        ServerYoikoSavedData.get(player.server).markDirty(player);
        String message = message("yoiko_core.message.relic.dismantled", scrap);
        player.sendSystemMessage(messageComponent(message).withStyle(ChatFormatting.YELLOW));
        player.level().playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.7F, 1.2F);
        YoikoAdvancementManager.recordDismantle(player, 1);
        return new DismantleResult(true, "", message);
    }

    public static boolean toggleLock(ServerPlayer player, UUID relicUuid) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        PlayerYoikoData.RelicInstance relic = find(data, relicUuid);
        if (relic == null) {
            return false;
        }
        relic.locked = !relic.locked;
        ServerYoikoSavedData.get(player.server).markDirty(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.PLAYERS, 0.45F, relic.locked ? 1.15F : 1.45F);
        return true;
    }

    public static boolean upgrade(ServerPlayer player, UUID relicUuid, boolean useProtection) {
        UpgradeResult result = upgradeWithResult(player, relicUuid, useProtection);
        sendUpgradeResultFeedback(player, result);
        return result.processed();
    }

    public static UpgradeResult upgradeWithResult(ServerPlayer player, UUID relicUuid, boolean useProtection) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        PlayerYoikoData.RelicInstance relic = find(data, relicUuid);
        if (relic == null) {
            String message = message("yoiko_core.message.relic.upgrade_not_found");
            player.sendSystemMessage(messageComponent(message).withStyle(ChatFormatting.RED));
            return new UpgradeResult(false, "", message, UpgradeOutcome.REJECTED);
        }
        if (relic.locked) {
            String message = message("yoiko_core.message.relic.upgrade_locked");
            player.sendSystemMessage(messageComponent(message).withStyle(ChatFormatting.RED));
            return new UpgradeResult(false, relic.uuid.toString(), message, UpgradeOutcome.REJECTED);
        }
        if (!isRelicUpgradeable(relic)) {
            String message = message("yoiko_core.message.relic.radiant_not_upgradeable");
            player.sendSystemMessage(messageComponent(message).withStyle(ChatFormatting.AQUA));
            return new UpgradeResult(false, relic.uuid.toString(), message, UpgradeOutcome.REJECTED);
        }
        if (data.equippedRelics.contains(relic.uuid.toString())) {
            String message = message("yoiko_core.message.relic.upgrade_equipped");
            player.sendSystemMessage(messageComponent(message).withStyle(ChatFormatting.RED));
            return new UpgradeResult(false, relic.uuid.toString(), message, UpgradeOutcome.REJECTED);
        }
        if (relic.level >= 10) {
            String message = message("yoiko_core.message.relic.max_upgrade");
            player.sendSystemMessage(messageComponent(message).withStyle(ChatFormatting.YELLOW));
            return new UpgradeResult(false, relic.uuid.toString(), message, UpgradeOutcome.REJECTED);
        }
        UpgradeRate rate = upgradeRate(relic.level);
        boolean protectionApplies = useProtection && (rate.downgrade() > 0.0D || rate.destroy() > 0.0D);
        boolean creative = player.getAbilities().instabuild;
        if (protectionApplies && !creative
                && data.relicScrap < PlayerYoikoData.RELIC_PROTECTION_SCRAP_COST) {
            String message = message("yoiko_core.message.relic.no_protection_scrap",
                    PlayerYoikoData.RELIC_PROTECTION_SCRAP_COST);
            player.sendSystemMessage(messageComponent(message).withStyle(ChatFormatting.RED));
            return new UpgradeResult(false, relic.uuid.toString(), message, UpgradeOutcome.REJECTED);
        }
        if (!creative && data.relicUpgradeCrystals <= 0) {
            String message = message("yoiko_core.message.relic.no_upgrade_crystal");
            player.sendSystemMessage(messageComponent(message).withStyle(ChatFormatting.RED));
            return new UpgradeResult(false, relic.uuid.toString(), message, UpgradeOutcome.REJECTED);
        }

        if (!creative) {
            data.relicUpgradeCrystals--;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7F, 1.0F);
        boolean scrapProtected = protectionApplies;
        if (protectionApplies) {
            if (!creative) {
                data.relicScrap -= PlayerYoikoData.RELIC_PROTECTION_SCRAP_COST;
            }
        }
        double roll = RANDOM.nextDouble() * 100.0D;
        if (roll < rate.greatSuccess()) {
            relic.level = Math.min(10, relic.level + 2);
            ServerYoikoSavedData.get(player.server).markDirty(player);
            String message = message("yoiko_core.message.relic.upgrade_great_success", relic.level);
            YoikoAdvancementManager.recordUpgrade(player, relic.level);
            return new UpgradeResult(true, relic.uuid.toString(), message, UpgradeOutcome.GREAT_SUCCESS);
        }
        double successThreshold = rate.greatSuccess() + rate.success();
        if (roll < successThreshold) {
            relic.level++;
            ServerYoikoSavedData.get(player.server).markDirty(player);
            String message = message("yoiko_core.message.relic.upgrade_success", relic.level);
            YoikoAdvancementManager.recordUpgrade(player, relic.level);
            return new UpgradeResult(true, relic.uuid.toString(), message, UpgradeOutcome.SUCCESS);
        }

        boolean protectedAttempt = scrapProtected;
        double downgradeThreshold = successThreshold + rate.downgrade();
        double destroyThreshold = downgradeThreshold + rate.destroy();
        boolean downgradeOutcome = roll < downgradeThreshold;
        boolean destroyOutcome = !downgradeOutcome && roll < destroyThreshold;
        if (!protectedAttempt && downgradeOutcome && relic.level > 0) {
            relic.level--;
            ServerYoikoSavedData.get(player.server).markDirty(player);
            String message = message("yoiko_core.message.relic.upgrade_downgraded", relic.level);
            return new UpgradeResult(true, relic.uuid.toString(), message, UpgradeOutcome.DOWNGRADE);
        }
        if (!protectedAttempt && destroyOutcome) {
            data.ownedRelics.remove(relic);
            data.pruneEquippedRelics();
            ServerYoikoSavedData.get(player.server).markDirty(player);
            String message = message("yoiko_core.message.relic.upgrade_destroyed");
            return new UpgradeResult(true, "", message, UpgradeOutcome.DESTROY);
        }

        ServerYoikoSavedData.get(player.server).markDirty(player);
        boolean preventedHarm = downgradeOutcome || destroyOutcome;
        String message = scrapProtected && preventedHarm
                ? message("yoiko_core.message.relic.upgrade_protection_applied")
                : message("yoiko_core.message.relic.upgrade_unchanged");
        return new UpgradeResult(true, relic.uuid.toString(), message, UpgradeOutcome.UNCHANGED);
    }

    public static void sendUpgradeResultFeedback(ServerPlayer player, UpgradeResult result) {
        if (player == null || result == null || !result.processed()) {
            return;
        }
        ChatFormatting color = switch (result.outcome()) {
            case GREAT_SUCCESS -> ChatFormatting.AQUA;
            case SUCCESS -> ChatFormatting.GREEN;
            case DOWNGRADE, UNCHANGED -> ChatFormatting.YELLOW;
            case DESTROY -> ChatFormatting.RED;
            case REJECTED -> ChatFormatting.GRAY;
        };
        player.sendSystemMessage(messageComponent(result.message()).withStyle(color));
        playUpgradeResultSound(player, result.outcome());
    }

    private static void playUpgradeResultSound(ServerPlayer player, UpgradeOutcome outcome) {
        if (player == null || outcome == null || outcome == UpgradeOutcome.REJECTED) {
            return;
        }
        switch (outcome) {
            case GREAT_SUCCESS -> player.level().playSound(null, player.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, 1.65F);
            case SUCCESS -> player.level().playSound(null, player.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
            case DOWNGRADE -> player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ANVIL_BREAK, SoundSource.PLAYERS, 0.7F, 1.15F);
            case DESTROY -> player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ANVIL_BREAK, SoundSource.PLAYERS, 0.8F, 0.9F);
            case UNCHANGED -> player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.25F);
            case REJECTED -> {
            }
        }
    }

    public static BatchDismantleResult dismantleBatch(ServerPlayer player, Collection<UUID> relicUuids) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        Set<UUID> requested = new LinkedHashSet<>(relicUuids);
        int dismantled = 0;
        int skipped = 0;
        int scrap = 0;
        for (UUID uuid : requested) {
            PlayerYoikoData.RelicInstance relic = find(data, uuid);
            if (relic == null || relic.locked || data.equippedRelics.contains(relic.uuid.toString())) {
                skipped++;
                continue;
            }
            data.ownedRelics.remove(relic);
            scrap += scrapValue(relic.rarity);
            dismantled++;
        }
        if (dismantled > 0) {
            data.relicScrap = Math.max(0, data.relicScrap + scrap);
            data.pruneEquippedRelics();
            ServerYoikoSavedData.get(player.server).markDirty(player);
            player.level().playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
            YoikoAdvancementManager.recordDismantle(player, dismantled);
        }
        String message = message("yoiko_core.message.relic.batch_dismantled", dismantled, scrap, skipped);
        player.sendSystemMessage(messageComponent(message).withStyle(
                dismantled > 0 ? ChatFormatting.YELLOW : ChatFormatting.RED));
        return new BatchDismantleResult(dismantled, scrap, skipped, message);
    }

    public static List<RelicView> views(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        List<RelicView> views = new ArrayList<>();
        Map<String, Integer> effectiveLevels = effectiveEquippedLevels(data);
        Map<String, EquippedEffectWinner> effectWinners = resolveEquippedEffects(data, Set.of());
        for (PlayerYoikoData.RelicInstance instance : data.ownedRelics) {
            RelicData relic = RELICS.get(instance.relicId);
            boolean primaryUpgradeable = isPrimaryEffectUpgradeable(instance);
            boolean secondaryUpgradeable = isSecondaryEffectUpgradeable(instance);
            boolean upgradeable = primaryUpgradeable || secondaryUpgradeable;
            double baseValue = relic == null ? 0.0D : relic.baseValue(instance.rarity);
            double upgradeBonus = relic == null ? 0.0D : relic.upgradeBonus(instance.rarity);
            int effectiveLevel = effectiveLevels.getOrDefault(instance.uuid.toString(), instance.level);
            double value = baseValue + upgradeBonus * effectiveLevel;
            double secondaryValue = secondaryEffectValue(instance, effectiveLevel);
            int nextEffectiveLevel = effectiveLevel;
            if (upgradeable && instance.level < 10) {
                int nextStoredLevel = instance.level + 1;
                nextEffectiveLevel = data.equippedRelics.contains(instance.uuid.toString())
                        ? effectiveEquippedLevels(
                                data,
                                Set.of(),
                                Map.of(instance.uuid.toString(), nextStoredLevel)
                        ).getOrDefault(instance.uuid.toString(), nextStoredLevel)
                        : nextStoredLevel;
            }
            double nextValue = baseValue + upgradeBonus * nextEffectiveLevel;
            double secondaryNextValue = secondaryEffectValue(instance, nextEffectiveLevel);
            UpgradeRate rate = upgradeable ? upgradeRate(instance.level) : new UpgradeRate(0.0D, 0.0D, 0.0D, 0.0D);
            String uuid = instance.uuid.toString();
            int equippedSlot = equippedSlot(data, uuid);
            EquippedEffectWinner primaryWinner = relic == null ? null : effectWinners.get(relic.effect());
            EquippedEffectWinner secondaryWinner = effectWinners.get(instance.secondaryEffect);
            boolean primarySuppressed = equippedSlot >= 0 && relic != null && !relic.effect().isBlank()
                    && !isEffectWinner(primaryWinner, uuid, 0);
            boolean secondarySuppressed = equippedSlot >= 0 && !instance.secondaryEffect.isBlank()
                    && !isEffectWinner(secondaryWinner, uuid, 1);
            views.add(new RelicView(
                    uuid,
                    instance.relicId,
                    relic == null ? instance.relicId : relic.displayName(),
                    relic == null ? "" : relic.effect(),
                    instance.secondaryEffect,
                    instance.rarity.name(),
                    instance.level,
                    effectiveLevel,
                    value,
                    nextValue,
                    secondaryValue,
                    secondaryNextValue,
                    rate.greatSuccess(),
                    rate.success(),
                    rate.downgrade(),
                    rate.destroy(),
                    equippedSlot,
                    scrapValue(instance.rarity),
                    instance.locked,
                    upgradeable,
                    primaryUpgradeable,
                    secondaryUpgradeable,
                    instance.storageSlot,
                    primarySuppressed,
                    secondarySuppressed
            ));
        }
        return views;
    }

    private static boolean isEffectWinner(EquippedEffectWinner winner, String uuid, int effectSlot) {
        return winner != null && winner.uuid().equals(uuid) && winner.effectSlot() == effectSlot;
    }

    public static List<String> equippedSlots(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        return List.copyOf(data.equippedRelics);
    }

    public static int activeRelicPreset(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        return data.activeRelicPreset;
    }

    public static List<Integer> relicPresetFilledCounts(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        data.ensureRelicPresets();
        return data.relicPresets.stream()
                .map(preset -> (int) preset.stream().filter(uuid -> uuid != null && !uuid.isBlank()).count())
                .toList();
    }

    /** Validates the complete target set before replacing any equipped slot. */
    public static EquipResult switchRelicPreset(ServerPlayer player, int presetIndex) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        if (presetIndex < 0 || presetIndex >= PlayerYoikoData.RELIC_PRESET_COUNT) {
            return new EquipResult(false, "", message("yoiko_core.message.relic.preset_invalid"));
        }
        if (presetIndex == data.activeRelicPreset) {
            return new EquipResult(false, "", message("yoiko_core.message.relic.preset_already_active", presetIndex + 1));
        }

        data.captureActiveRelicPreset();
        List<String> current = new ArrayList<>(data.equippedRelics);
        String currentContractTarget = data.radiantContractTargetUuid;
        List<String> target = new ArrayList<>(data.relicPresets.get(presetIndex));
        String targetContract = data.relicPresetContractTargets.get(presetIndex);
        if (!validPresetLoadout(data, target)) {
            return new EquipResult(false, "", message("yoiko_core.message.relic.preset_invalid_loadout"));
        }

        int currentStorageSlots = activeStorageSlots(data, "");
        data.equippedRelics = target;
        data.radiantContractTargetUuid = targetContract;
        clearInvalidChromaticContractTarget(data);
        int nextStorageSlots = activeStorageSlots(data, "");
        StorageReductionBlock block = storageReductionBlock(data, currentStorageSlots, nextStorageSlots);
        if (block != null) {
            data.equippedRelics = current;
            data.radiantContractTargetUuid = currentContractTarget;
            return new EquipResult(false, "", storageReductionMessage(block));
        }

        data.activeRelicPreset = presetIndex;
        normalizeRelicStorageSlots(data);
        clampStoragePage(data);
        data.captureActiveRelicPreset();
        ServerYoikoSavedData.get(player.server).markDirty(player);
        invalidateEffectSnapshot(player);
        playRelicEquipSound(player);
        YoikoAdvancementManager.recordEquip(player);
        return new EquipResult(true, "", message("yoiko_core.message.relic.preset_switched", presetIndex + 1));
    }

    public static int specialEquippedCount(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        int count = 0;
        for (String uuid : data.equippedRelics) {
            PlayerYoikoData.RelicInstance instance = find(data, uuid);
            RelicData relic = instance == null ? null : RELICS.get(instance.relicId);
            if (relic != null && relic.equipGroup() == RelicEquipGroup.SPECIAL) {
                count++;
            }
        }
        return count;
    }

    public static String chromaticContractTargetUuid(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        return data.radiantContractTargetUuid;
    }

    public static int scrapCount(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return 999;
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        return Math.max(0, data.relicScrap);
    }

    public static void addScrap(ServerPlayer player, int count) {
        if (count <= 0) {
            return;
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        data.relicScrap = Math.max(0, data.relicScrap + count);
        ServerYoikoSavedData.get(player.server).markDirty(player);
    }

    public static int upgradeCrystalCount(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return 999;
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        return Math.max(0, data.relicUpgradeCrystals);
    }

    public static void addUpgradeCrystals(ServerPlayer player, int count) {
        if (count <= 0) {
            return;
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        data.relicUpgradeCrystals = Math.max(0, data.relicUpgradeCrystals + count);
        ServerYoikoSavedData.get(player.server).markDirty(player);
    }

    public static boolean absorbInternalMaterial(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(YoikoItems.RELIC_UPGRADE_CRYSTAL.get())) {
            addUpgradeCrystals(player, stack.getCount());
            return true;
        }
        if (stack.is(YoikoItems.RELIC_SCRAP.get())) {
            addScrap(player, stack.getCount());
            return true;
        }
        return false;
    }

    public static boolean isInternalMaterial(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.is(YoikoItems.RELIC_UPGRADE_CRYSTAL.get())
                || stack.is(YoikoItems.RELIC_SCRAP.get()));
    }

    public static int countItem(ServerPlayer player, Item item) {
        if (player.getAbilities().instabuild) {
            return 999;
        }
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static UpgradeRate upgradeRate(int level) {
        return UPGRADE_RATES.getOrDefault(level, new UpgradeRate(0.0D, 100.0D, 0.0D, 0.0D));
    }

    private static void refundRelicTicket(ServerPlayer player, PendingRoll roll) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack stack = roll.source() == RelicRollSource.CROWN_RABBIT
                ? YoikoItems.CROWN_SEALED_RELIC.toStack()
                : new ItemStack(YoikoItems.relicGachaTicket(roll.category()));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.getInventory().setChanged();
    }

    private static String rarityTranslationKey(RelicRarity rarity) {
        return "yoiko_core.rarity." + rarity.name().toLowerCase(Locale.ROOT);
    }

    private static PlayerYoikoData.RelicInstance addRelic(
            ServerPlayer player,
            String relicId,
            RelicRarity rarity,
            String secondaryEffect
    ) {
        return addRelic(player, relicId, rarity, secondaryEffect, false);
    }

    private static PlayerYoikoData.RelicInstance addRelic(
            ServerPlayer player,
            String relicId,
            RelicRarity rarity,
            String secondaryEffect,
            boolean crownRabbitRelic
    ) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        PlayerYoikoData.RelicInstance instance = new PlayerYoikoData.RelicInstance(relicId, rarity);
        instance.secondaryEffect = secondaryEffect == null ? "" : secondaryEffect;
        instance.secondaryEffectInitialized = true;
        instance.crownRabbitRelic = crownRabbitRelic;
        instance.storageSlot = firstFreeRelicStorageSlot(data, null);
        data.ownedRelics.add(instance);
        ServerYoikoSavedData.get(player.server).markDirty(player);
        return instance;
    }

    /**
     * Returns null only for an invalid forced option. An empty string is a valid empty secondary
     * slot. "auto" uses the rarity chance, while "random" guarantees one compatible effect.
     */
    private static String selectSecondaryEffect(
            RelicData primaryRelic,
            RelicRarity rarity,
            String option
    ) {
        String normalized = option == null || option.isBlank()
                ? "auto"
                : option.trim().toLowerCase(Locale.ROOT);
        if ("none".equals(normalized)) {
            return "";
        }
        if ("auto".equals(normalized)) {
            double chance = secondaryEffectChance(rarity);
            if (chance <= 0.0D || RANDOM.nextDouble() * 100.0D >= chance) {
                return "";
            }
            return randomSecondaryEffect(primaryRelic);
        }
        if ("random".equals(normalized)) {
            return randomSecondaryEffect(primaryRelic);
        }
        return isValidSecondaryEffect(primaryRelic, normalized) ? normalized : null;
    }

    private static String randomSecondaryEffect(RelicData primaryRelic) {
        List<String> candidates = secondaryEffectCandidates(primaryRelic);
        return candidates.isEmpty() ? "" : candidates.get(RANDOM.nextInt(candidates.size()));
    }

    private static String randomSecondaryEffect(
            RelicData primaryRelic, RelicAppraisalCategory appraisalCategory) {
        RelicAppraisalCategory category = appraisalCategory == null
                ? RelicAppraisalCategory.ALL : appraisalCategory;
        List<String> candidates = secondaryEffectCandidates(primaryRelic).stream()
                .filter(category::matches)
                .toList();
        return candidates.isEmpty() ? "" : candidates.get(RANDOM.nextInt(candidates.size()));
    }

    private static List<String> secondaryEffectCandidates(RelicData primaryRelic) {
        String primaryEffect = primaryRelic == null ? "" : primaryRelic.effect();
        return secondaryEffectIds().stream()
                .filter(effect -> !effect.equals(primaryEffect))
                .toList();
    }

    private static boolean isValidSecondaryEffect(RelicData primaryRelic, String effect) {
        return !effect.isBlank() && secondaryEffectCandidates(primaryRelic).contains(effect);
    }

    private static RelicData secondaryEffectProfile(String effect) {
        if (effect == null || effect.isBlank()) {
            return null;
        }
        for (RelicData relic : RELICS.values()) {
            if (relic.enabled()
                    && relic.equipGroup() == RelicEquipGroup.STANDARD
                    && effect.equals(relic.effect())) {
                return relic;
            }
        }
        return null;
    }

    private static double secondaryEffectValue(
            PlayerYoikoData.RelicInstance instance,
            int effectiveLevel
    ) {
        if (CROWN_HIGH_AIR_STEP_EFFECT.equals(instance.secondaryEffect)) {
            return 1.0D;
        }
        if (CROWN_DOUBLE_AIR_STEP_EFFECT.equals(instance.secondaryEffect)) {
            return 2.0D;
        }
        RelicData profile = secondaryEffectProfile(instance.secondaryEffect);
        if (profile == null) {
            return 0.0D;
        }
        RelicRarity valueRarity = secondaryEffectValueRarity(instance.rarity);
        return profile.value(valueRarity, effectiveLevel);
    }

    private static RelicRarity secondaryEffectValueRarity(RelicRarity hostRarity) {
        return switch (hostRarity) {
            case COMMON -> RelicRarity.COMMON;
            case UNCOMMON -> RelicRarity.COMMON;
            case RARE -> RelicRarity.UNCOMMON;
            case EPIC -> RelicRarity.RARE;
            case LEGENDARY -> RelicRarity.EPIC;
            case MYSTIC -> RelicRarity.LEGENDARY;
            case RADIANT -> RelicRarity.MYSTIC;
        };
    }

    private static void prepareRelics(ServerPlayer player, PlayerYoikoData data) {
        data.pruneEquippedRelics();
        boolean secondaryEffectsInitialized = false;
        for (PlayerYoikoData.RelicInstance instance : data.ownedRelics) {
            if (!instance.secondaryEffectInitialized) {
                RelicData relic = RELICS.get(instance.relicId);
                instance.secondaryEffect = relic == null
                        ? ""
                        : selectSecondaryEffect(relic, instance.rarity, "auto");
                instance.secondaryEffectInitialized = true;
                secondaryEffectsInitialized = true;
            }
        }
        boolean groupOverflowRemoved = pruneExcessEquipGroups(data);
        if (groupOverflowRemoved) {
            data.captureActiveRelicPreset();
        }
        boolean contractTargetCleared = clearInvalidChromaticContractTarget(data);
        if (contractTargetCleared) {
            data.captureActiveRelicPreset();
        }
        boolean storageSlotsNormalized = normalizeRelicStorageSlots(data);
        if (secondaryEffectsInitialized || groupOverflowRemoved || contractTargetCleared || storageSlotsNormalized) {
            ServerYoikoSavedData.get(player.server).markDirty(player);
            invalidateEffectSnapshot(player);
        }
    }

    public static boolean moveStoredRelic(ServerPlayer player, UUID relicUuid, int targetSlot) {
        if (relicUuid == null || targetSlot < 0 || targetSlot >= PlayerYoikoData.MAX_OWNED_RELICS) {
            return false;
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        prepareRelics(player, data);
        PlayerYoikoData.RelicInstance moved = find(data, relicUuid);
        if (moved == null || data.equippedRelics.contains(moved.uuid.toString())) {
            return false;
        }
        int sourceSlot = moved.storageSlot;
        if (sourceSlot == targetSlot) {
            return false;
        }
        PlayerYoikoData.RelicInstance occupant = null;
        for (PlayerYoikoData.RelicInstance relic : data.ownedRelics) {
            if (!relic.uuid.equals(moved.uuid)
                    && !data.equippedRelics.contains(relic.uuid.toString())
                    && relic.storageSlot == targetSlot) {
                occupant = relic;
                break;
            }
        }
        moved.storageSlot = targetSlot;
        if (occupant != null) {
            occupant.storageSlot = sourceSlot;
        }
        ServerYoikoSavedData.get(player.server).markDirty(player);
        return true;
    }

    private static boolean normalizeRelicStorageSlots(PlayerYoikoData data) {
        boolean[] used = new boolean[PlayerYoikoData.MAX_OWNED_RELICS];
        boolean changed = false;
        Set<String> equipped = new LinkedHashSet<>(data.equippedRelics);
        for (PlayerYoikoData.RelicInstance relic : data.ownedRelics) {
            if (equipped.contains(relic.uuid.toString())) {
                if (relic.storageSlot != -1) {
                    relic.storageSlot = -1;
                    changed = true;
                }
                continue;
            }
            if (relic.storageSlot < 0
                    || relic.storageSlot >= PlayerYoikoData.MAX_OWNED_RELICS
                    || used[relic.storageSlot]) {
                if (relic.storageSlot != -1) {
                    relic.storageSlot = -1;
                    changed = true;
                }
                continue;
            }
            used[relic.storageSlot] = true;
        }
        for (PlayerYoikoData.RelicInstance relic : data.ownedRelics) {
            if (equipped.contains(relic.uuid.toString()) || relic.storageSlot >= 0) {
                continue;
            }
            int slot = firstFreeRelicStorageSlot(used);
            if (slot < 0) {
                break;
            }
            relic.storageSlot = slot;
            used[slot] = true;
            changed = true;
        }
        return changed;
    }

    private static int firstFreeRelicStorageSlot(PlayerYoikoData data, UUID ignoredUuid) {
        boolean[] used = new boolean[PlayerYoikoData.MAX_OWNED_RELICS];
        Set<String> equipped = new LinkedHashSet<>(data.equippedRelics);
        for (PlayerYoikoData.RelicInstance relic : data.ownedRelics) {
            if ((ignoredUuid == null || !ignoredUuid.equals(relic.uuid))
                    && !equipped.contains(relic.uuid.toString())
                    && relic.storageSlot >= 0
                    && relic.storageSlot < used.length) {
                used[relic.storageSlot] = true;
            }
        }
        return firstFreeRelicStorageSlot(used);
    }

    private static int firstFreeRelicStorageSlot(boolean[] used) {
        for (int slot = 0; slot < used.length; slot++) {
            if (!used[slot]) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean clearInvalidChromaticContractTarget(PlayerYoikoData data) {
        if (data.radiantContractTargetUuid.isBlank()) {
            return false;
        }
        PlayerYoikoData.RelicInstance contract =
                equippedInstanceForEffect(data, "chromatic_contract_level_shift");
        PlayerYoikoData.RelicInstance target = find(data, data.radiantContractTargetUuid);
        RelicData targetData = target == null ? null : RELICS.get(target.relicId);
        if (contract != null && targetData != null
                && targetData.equipGroup() == RelicEquipGroup.STANDARD
                && data.equippedRelics.contains(data.radiantContractTargetUuid)) {
            return false;
        }
        data.radiantContractTargetUuid = "";
        return true;
    }

    private static List<RelicData> eligibleRelics(RelicRarity rarity) {
        return RELICS.values().stream()
                .filter(relic -> relic.enabled() && relic.allows(rarity))
                .filter(relic -> !CROWN_EXCLUSIVE_PRIMARY_RELIC_IDS.contains(relic.id()))
                .filter(relic -> RelicEffectRegistry.isRuntimeAvailable(relic.effect()))
                .toList();
    }

    private static RelicRarity randomCrownRarity() {
        int epic = YoikoCommonConfig.TREASURE_RABBIT_CROWN_RELIC_EPIC_WEIGHT.get();
        int legendary = YoikoCommonConfig.TREASURE_RABBIT_CROWN_RELIC_LEGENDARY_WEIGHT.get();
        int mystic = YoikoCommonConfig.TREASURE_RABBIT_CROWN_RELIC_MYSTIC_WEIGHT.get();
        int radiant = YoikoCommonConfig.TREASURE_RABBIT_CROWN_RELIC_RADIANT_WEIGHT.get();
        int total = epic + legendary + mystic + radiant;
        if (total <= 0) {
            return null;
        }
        int value = RANDOM.nextInt(total);
        if ((value -= epic) < 0) return RelicRarity.EPIC;
        if ((value -= legendary) < 0) return RelicRarity.LEGENDARY;
        if ((value -= mystic) < 0) return RelicRarity.MYSTIC;
        return RelicRarity.RADIANT;
    }

    private static String crownJumpEffect(RelicRarity rarity) {
        return rarity == RelicRarity.RADIANT
                ? CROWN_DOUBLE_AIR_STEP_EFFECT : CROWN_HIGH_AIR_STEP_EFFECT;
    }

    private static boolean isCrownJumpEffect(String effect) {
        return CROWN_HIGH_AIR_STEP_EFFECT.equals(effect)
                || CROWN_DOUBLE_AIR_STEP_EFFECT.equals(effect);
    }

    private static boolean isPrimaryEffectUpgradeable(PlayerYoikoData.RelicInstance relic) {
        if (relic.rarity == RelicRarity.RADIANT) {
            return false;
        }
        RelicData profile = RELICS.get(relic.relicId);
        return profile != null && profile.upgradeBonus(relic.rarity) > 0.0D;
    }

    private static boolean isSecondaryEffectUpgradeable(PlayerYoikoData.RelicInstance relic) {
        if (relic.rarity == RelicRarity.RADIANT
                || relic.secondaryEffect.isBlank()
                || isCrownJumpEffect(relic.secondaryEffect)) {
            return false;
        }
        RelicData profile = secondaryEffectProfile(relic.secondaryEffect);
        return profile != null
                && profile.upgradeBonus(secondaryEffectValueRarity(relic.rarity)) > 0.0D;
    }

    private static boolean isRelicUpgradeable(PlayerYoikoData.RelicInstance relic) {
        return isPrimaryEffectUpgradeable(relic) || isSecondaryEffectUpgradeable(relic);
    }

    private static RelicRarity randomRarity() {
        int total = RARITY_WEIGHTS.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return null;
        }
        int value = RANDOM.nextInt(total);
        for (Map.Entry<RelicRarity, Integer> entry : RARITY_WEIGHTS.entrySet()) {
            value -= entry.getValue();
            if (value < 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static PlayerYoikoData.RelicInstance find(PlayerYoikoData data, UUID relicUuid) {
        return find(data, relicUuid.toString());
    }

    private static PlayerYoikoData.RelicInstance find(PlayerYoikoData data, String relicUuid) {
        for (PlayerYoikoData.RelicInstance relic : data.ownedRelics) {
            if (relic.uuid.toString().equals(relicUuid)) {
                return relic;
            }
        }
        return null;
    }

    private static int equippedSlot(PlayerYoikoData data, String uuid) {
        for (int i = 0; i < data.equippedRelics.size(); i++) {
            if (uuid.equals(data.equippedRelics.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean equippedGroupConflict(PlayerYoikoData data, RelicData newRelic, Set<String> ignoredUuids) {
        int equipped = 0;
        for (String uuid : data.equippedRelics) {
            if (uuid.isBlank() || ignoredUuids.contains(uuid)) {
                continue;
            }
            PlayerYoikoData.RelicInstance instance = find(data, uuid);
            RelicData relic = instance == null ? null : RELICS.get(instance.relicId);
            if (relic != null && relic.equipGroup() == newRelic.equipGroup()) {
                equipped++;
            }
        }
        return equipped >= newRelic.equipGroup().maxEquipped();
    }

    private static boolean validPresetLoadout(PlayerYoikoData data, List<String> slots) {
        if (slots.size() != PlayerYoikoData.RELIC_EQUIP_SLOT_COUNT) {
            return false;
        }
        Set<String> used = new LinkedHashSet<>();
        EnumMap<RelicEquipGroup, Integer> groups = new EnumMap<>(RelicEquipGroup.class);
        for (String uuid : slots) {
            if (uuid == null || uuid.isBlank()) {
                continue;
            }
            if (!used.add(uuid)) {
                return false;
            }
            PlayerYoikoData.RelicInstance instance = find(data, uuid);
            RelicData relic = instance == null ? null : RELICS.get(instance.relicId);
            if (relic == null || !relic.allows(instance.rarity)) {
                return false;
            }
            int count = groups.merge(relic.equipGroup(), 1, Integer::sum);
            if (count > relic.equipGroup().maxEquipped()) {
                return false;
            }
        }
        return true;
    }

    private static boolean pruneExcessEquipGroups(PlayerYoikoData data) {
        EnumMap<RelicEquipGroup, Integer> counts = new EnumMap<>(RelicEquipGroup.class);
        boolean changed = false;
        for (int slot = 0; slot < data.equippedRelics.size(); slot++) {
            String uuid = data.equippedRelics.get(slot);
            PlayerYoikoData.RelicInstance instance = uuid.isBlank() ? null : find(data, uuid);
            RelicData relic = instance == null ? null : RELICS.get(instance.relicId);
            if (relic == null) {
                continue;
            }
            int count = counts.merge(relic.equipGroup(), 1, Integer::sum);
            if (count > relic.equipGroup().maxEquipped()) {
                data.equippedRelics.set(slot, "");
                changed = true;
            }
        }
        return changed;
    }

    private static PlayerYoikoData.RelicInstance equippedInstanceForEffect(PlayerYoikoData data, String effectId) {
        for (String uuid : data.equippedRelics) {
            PlayerYoikoData.RelicInstance instance = find(data, uuid);
            RelicData relic = instance == null ? null : RELICS.get(instance.relicId);
            if (relic != null && effectId.equals(relic.effect())) {
                return instance;
            }
        }
        return null;
    }

    private static Map<String, Integer> effectiveEquippedLevels(PlayerYoikoData data) {
        return effectiveEquippedLevels(data, Set.of(), Map.of());
    }

    private static Map<String, Integer> effectiveEquippedLevels(
            PlayerYoikoData data, Set<String> excludedUuids) {
        return effectiveEquippedLevels(data, excludedUuids, Map.of());
    }

    private static Map<String, Integer> effectiveEquippedLevels(
            PlayerYoikoData data,
            Set<String> excludedUuids,
            Map<String, Integer> storedLevelOverrides
    ) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        int standardMinimum = Integer.MAX_VALUE;
        String weakestUuid = "";
        int levelModifierSlot = -1;
        String levelModifierEffect = "";
        for (int slot = 0; slot < data.equippedRelics.size(); slot++) {
            String uuid = data.equippedRelics.get(slot);
            if (uuid.isBlank() || excludedUuids.contains(uuid)) {
                continue;
            }
            PlayerYoikoData.RelicInstance instance = find(data, uuid);
            RelicData relic = instance == null ? null : RELICS.get(instance.relicId);
            if (relic == null) {
                continue;
            }
            if (isRelicLevelModifierEffect(relic.effect())) {
                levelModifierSlot = slot;
                levelModifierEffect = relic.effect();
                break;
            }
        }
        boolean chromaticTargetConnected = false;
        for (int slot = 0; slot < data.equippedRelics.size(); slot++) {
            String uuid = data.equippedRelics.get(slot);
            if (uuid.isBlank() || excludedUuids.contains(uuid)) {
                continue;
            }
            PlayerYoikoData.RelicInstance instance = find(data, uuid);
            RelicData relic = instance == null ? null : RELICS.get(instance.relicId);
            if (relic == null || relic.equipGroup() != RelicEquipGroup.STANDARD
                    || !isConnectedLevelModifierTarget(
                    data.equippedRelics, levelModifierSlot, slot, excludedUuids)) {
                continue;
            }
            chromaticTargetConnected |= uuid.equals(data.radiantContractTargetUuid);
            int storedLevel = storedLevelOverrides.getOrDefault(uuid, instance.level);
            if (storedLevel < standardMinimum) {
                standardMinimum = storedLevel;
                weakestUuid = uuid;
            }
        }
        for (int slot = 0; slot < data.equippedRelics.size(); slot++) {
            String uuid = data.equippedRelics.get(slot);
            if (uuid.isBlank() || excludedUuids.contains(uuid)) {
                continue;
            }
            PlayerYoikoData.RelicInstance instance = find(data, uuid);
            if (instance == null) {
                continue;
            }
            RelicData relic = RELICS.get(instance.relicId);
            int level = storedLevelOverrides.getOrDefault(uuid, instance.level);
            if (relic != null && relic.equipGroup() == RelicEquipGroup.STANDARD
                    && isConnectedLevelModifierTarget(
                    data.equippedRelics, levelModifierSlot, slot, excludedUuids)) {
                if ("equipped_relic_level_bonus".equals(levelModifierEffect)) {
                    level++;
                } else if ("weakest_relic_level_bonus".equals(levelModifierEffect)
                        && uuid.equals(weakestUuid)) {
                    level += 2;
                } else if ("chromatic_contract_level_shift".equals(levelModifierEffect)
                        && chromaticTargetConnected) {
                    level += uuid.equals(data.radiantContractTargetUuid) ? 3 : -1;
                }
            }
            levels.put(instance.uuid.toString(), Math.max(0, Math.min(10, level)));
        }
        return levels;
    }

    private static boolean isRelicLevelModifierEffect(String effect) {
        return "equipped_relic_level_bonus".equals(effect)
                || "weakest_relic_level_bonus".equals(effect)
                || "chromatic_contract_level_shift".equals(effect);
    }

    /**
     * A level modifier travels through the equipped row until the first empty (or preview-excluded)
     * slot. This keeps the runtime calculation identical to the visual connection chain.
     */
    private static boolean isConnectedLevelModifierTarget(
            List<String> equippedSlots,
            int sourceSlot,
            int targetSlot,
            Set<String> excludedUuids
    ) {
        if (sourceSlot < 0 || targetSlot < 0 || sourceSlot == targetSlot
                || sourceSlot >= equippedSlots.size() || targetSlot >= equippedSlots.size()) {
            return false;
        }
        int step = targetSlot > sourceSlot ? 1 : -1;
        for (int slot = sourceSlot + step; ; slot += step) {
            String uuid = equippedSlots.get(slot);
            if (uuid.isBlank() || excludedUuids.contains(uuid)) {
                return false;
            }
            if (slot == targetSlot) {
                return true;
            }
        }
    }

    private static StorageReductionBlock storageReductionBlock(PlayerYoikoData data, String removedUuid) {
        PlayerYoikoData.RelicInstance removed = find(data, removedUuid);
        RelicData removedData = removed == null ? null : RELICS.get(removed.relicId);
        if (removed == null || removedData == null || !data.equippedRelics.contains(removedUuid)) {
            return null;
        }
        int currentSlots = activeStorageSlots(data, "");
        int nextSlots = activeStorageSlots(data, removedUuid);
        return storageReductionBlock(data, currentSlots, nextSlots);
    }

    private static StorageReductionBlock storageReductionBlock(
            PlayerYoikoData data, int currentSlots, int nextSlots) {
        if (nextSlots >= currentSlots) {
            return null;
        }
        int maxCheckedSlot = Math.min(currentSlots, data.yoikoStorage.size());
        for (int slot = Math.max(0, nextSlots); slot < maxCheckedSlot; slot++) {
            if (!data.yoikoStorage.get(slot).isEmpty()) {
                return new StorageReductionBlock(slot + 1, nextSlots, currentSlots);
            }
        }
        return null;
    }

    private static int activeStorageSlots(PlayerYoikoData data, String excludedUuid) {
        Set<String> excluded = excludedUuid == null || excludedUuid.isBlank()
                ? Set.of()
                : Set.of(excludedUuid);
        EquippedEffectWinner winner = resolveEquippedEffects(data, excluded).get("yoiko_bag_slots");
        double best = winner == null ? 0.0D : winner.value();
        return PlayerYoikoData.clampYoikoStorageSlots(PlayerYoikoData.MIN_YOIKO_STORAGE_SLOTS + (int) Math.floor(best));
    }

    private static void clampStoragePage(PlayerYoikoData data) {
        data.yoikoStoragePage = PlayerYoikoData.clampYoikoStoragePage(data.yoikoStoragePage, activeStorageSlots(data, ""));
    }

    private static void sendStorageReductionBlocked(ServerPlayer player, StorageReductionBlock block) {
        player.sendSystemMessage(messageComponent(storageReductionMessage(block)).withStyle(ChatFormatting.RED));
    }

    private static String storageReductionMessage(StorageReductionBlock block) {
        int reduced = block.currentSlots() - block.nextSlots();
        return message("yoiko_core.message.relic.storage_reduction_blocked", reduced, block.firstOccupiedSlot());
    }

    private static String message(String key, Object... args) {
        StringBuilder builder = new StringBuilder(key);
        for (Object arg : args) {
            builder.append('|').append(arg);
        }
        return builder.toString();
    }

    private static net.minecraft.network.chat.MutableComponent messageComponent(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length == 1) {
            return Component.translatable(value);
        }
        Object[] args = new Object[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return Component.translatable(parts[0], args);
    }

    private static int scrapValue(RelicRarity rarity) {
        return switch (rarity) {
            case UNCOMMON -> 8;
            case RARE -> 12;
            case EPIC -> 25;
            case LEGENDARY -> 50;
            case MYSTIC -> 100;
            case RADIANT -> 250;
            default -> 5;
        };
    }

    private static boolean consume(ServerPlayer player, Item item, int count) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        if (!hasItem(player, item, count)) {
            return false;
        }
        int remaining = count;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                int used = Math.min(remaining, stack.getCount());
                stack.shrink(used);
                remaining -= used;
                if (remaining <= 0) {
                    player.getInventory().setChanged();
                    return true;
                }
            }
        }
        player.getInventory().setChanged();
        return false;
    }

    private static boolean hasItem(ServerPlayer player, Item item, int count) {
        return countItem(player, item) >= count;
    }

    private static ItemStack findStack(ServerPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static RelicData readRelic(JsonObject object) {
        String id = string(object, "id", "");
        if (id.isBlank()) {
            return null;
        }
        String effect = string(object, "effect", "");
        if (effect.isBlank()) {
            YoikoServerCore.LOGGER.error("Ignoring relic '{}' because its canonical effect key is blank.", id);
            return null;
        }
        RelicEffectDefinition definition = RelicEffectRegistry.get(effect);
        if (definition == null) {
            YoikoServerCore.LOGGER.error("Ignoring relic '{}' because effect key '{}' is not registered.", id, effect);
            return null;
        }
        String handlerError = RelicEffectHandlerRegistry.validationError(definition);
        if (!handlerError.isBlank()) {
            YoikoServerCore.LOGGER.error(
                    "Ignoring relic '{}' because effect '{}' has no usable runtime handler: {}",
                    id, effect, handlerError);
            return null;
        }
        Set<RelicRarity> allowedRarities = readAllowedRarities(object, definition.allowedRarities());
        if (allowedRarities.isEmpty()) {
            YoikoServerCore.LOGGER.error("Ignoring relic '{}' because allowedRarities is empty or invalid.", id);
            return null;
        }
        if (!definition.allowedRarities().containsAll(allowedRarities)) {
            YoikoServerCore.LOGGER.error(
                    "Ignoring relic '{}' because effect '{}' only allows rarities {}, but the relic declares {}.",
                    id, effect, definition.allowedRarities(), allowedRarities);
            return null;
        }
        String groupName = string(object, "equipGroup",
                allowedRarities.contains(RelicRarity.RADIANT) ? RelicEquipGroup.SPECIAL.name()
                        : RelicEquipGroup.STANDARD.name());
        RelicEquipGroup equipGroup = RelicEquipGroup.parse(groupName).orElse(null);
        if (equipGroup == null) {
            YoikoServerCore.LOGGER.error("Ignoring relic '{}' because equipGroup '{}' is unknown.", id, groupName);
            return null;
        }
        if (allowedRarities.contains(RelicRarity.RADIANT) && equipGroup != RelicEquipGroup.SPECIAL) {
            YoikoServerCore.LOGGER.error("Ignoring radiant relic '{}' because it is not in the SPECIAL equip group.", id);
            return null;
        }
        JsonObject baseObject = object.getAsJsonObject("values");
        JsonObject upgradeObject = object.getAsJsonObject("upgradeBonuses");
        EnumMap<RelicRarity, Double> baseValues = readRarityValues(id, "values", baseObject, allowedRarities);
        EnumMap<RelicRarity, Double> perLevel = readRarityValues(
                id, "upgradeBonuses", upgradeObject, allowedRarities);
        if (baseValues == null || perLevel == null) {
            YoikoServerCore.LOGGER.error(
                    "Ignoring relic '{}' because values and upgradeBonuses must exactly match allowedRarities {}.",
                    id, allowedRarities);
            return null;
        }
        for (RelicRarity rarity : allowedRarities) {
            double base = baseValues.getOrDefault(rarity, -1.0D);
            double upgrade = perLevel.getOrDefault(rarity, -1.0D);
            double maximum = base + upgrade * 10.0D;
            if (!Double.isFinite(base) || !Double.isFinite(upgrade) || base < 0.0D || upgrade < 0.0D
                    || maximum > definition.maximumValue()) {
                YoikoServerCore.LOGGER.error(
                        "Ignoring relic '{}' because {} values are invalid (base={}, perLevel={}, +10={}, max={}).",
                        id, rarity, base, upgrade, maximum, definition.maximumValue());
                return null;
            }
            if (rarity == RelicRarity.RADIANT && upgrade != 0.0D) {
                YoikoServerCore.LOGGER.error(
                        "Ignoring radiant relic '{}' because radiant upgradeBonuses must be 0.", id);
                return null;
            }
        }
        return new RelicData(
                id,
                string(object, "displayName", id),
                effect,
                allowedRarities,
                equipGroup,
                baseValues,
                perLevel,
                bool(object, "enabled", true)
        );
    }

    private static Set<RelicRarity> readAllowedRarities(
            JsonObject object, Set<RelicRarity> effectAllowedRarities) {
        JsonArray array = object.getAsJsonArray("allowedRarities");
        if (array == null) {
            return effectAllowedRarities;
        }
        EnumSet<RelicRarity> result = EnumSet.noneOf(RelicRarity.class);
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                return Set.of();
            }
            RelicRarity rarity = RelicRarity.parse(element.getAsString()).orElse(null);
            if (rarity == null) {
                YoikoServerCore.LOGGER.error("Unknown relic rarity in allowedRarities: {}", element);
                return Set.of();
            }
            result.add(rarity);
        }
        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }

    private static EnumMap<RelicRarity, Double> readRarityValues(
            String relicId, String field, JsonObject object, Set<RelicRarity> allowedRarities) {
        if (object == null) {
            return null;
        }
        EnumMap<RelicRarity, Double> values = new EnumMap<>(RelicRarity.class);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            RelicRarity rarity = RelicRarity.parse(entry.getKey()).orElse(null);
            if (rarity == null) {
                YoikoServerCore.LOGGER.error(
                        "Relic '{}' has unknown rarity key '{}' in {}.", relicId, entry.getKey(), field);
                return null;
            }
            if (!allowedRarities.contains(rarity) || !entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                return null;
            }
            values.put(rarity, entry.getValue().getAsDouble());
        }
        return values.keySet().equals(allowedRarities) ? values : null;
    }

    private static void ensureFile() {
        try {
            Files.createDirectories(RELIC_FILE.getParent());
            if (Files.notExists(RELIC_FILE)) {
                writeDefaultFile();
            }
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Failed to create relic config file.", exception);
        }
    }

    private static boolean writeDefaultFile() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", RELIC_CONFIG_SCHEMA_VERSION);
        JsonArray relics = new JsonArray();
        for (RelicData relic : defaultRelics()) {
            relics.add(relic(relic));
        }
        root.add("relics", relics);

        JsonObject rarityWeights = new JsonObject();
        for (Map.Entry<RelicRarity, Integer> entry : defaultRarityWeights().entrySet()) {
            rarityWeights.addProperty(entry.getKey().name(), entry.getValue());
        }
        root.add("rarityWeights", rarityWeights);

        root.add("upgradeRates", defaultUpgradeRatesJson());

        try {
            Files.createDirectories(RELIC_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(RELIC_FILE)) {
                GSON.toJson(root, writer);
            }
            return true;
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Failed to write default relics.", exception);
            return false;
        }
    }

    private static List<RelicData> defaultRelics() {
        return List.of(
                relic("pokemon_friendship_bonus", "yoiko_core.relic.pokemon_friendship_bonus.name", "pokemon_friendship_gain_bonus",
                        rarityValues(5, 7, 10, 15, 22, 32),
                        rarityValues(0.5D, 0.7D, 1.0D, 1.4D, 2.0D, 2.8D)),
                relic("pokemon_exp_bonus", "yoiko_core.relic.pokemon_exp_bonus.name", "pokemon_exp_multiplier_bonus",
                        rarityValues(2, 3, 4.5D, 6.5D, 9.5D, 14),
                        rarityValues(0.2D, 0.3D, 0.45D, 0.65D, 0.9D, 1.3D)),
                relic("catch_rate_bonus", "yoiko_core.relic.catch_rate_bonus.name", "pokemon_catch_rate_bonus",
                        rarityValues(1, 1.5D, 2.3D, 3.5D, 5.2D, 8),
                        rarityValues(0.1D, 0.15D, 0.23D, 0.35D, 0.52D, 0.8D)),
                relic("shiny_luck", "yoiko_core.relic.shiny_luck.name", "natural_shiny_chance_bonus",
                        rarityValues(1, 1.5D, 2.5D, 4, 6, 10),
                        rarityValues(0.1D, 0.15D, 0.25D, 0.4D, 0.6D, 1)),
                relic("player_exp_bonus", "yoiko_core.relic.player_exp_bonus.name", "player_exp_multiplier_bonus",
                        rarityValues(3, 4.5D, 7, 10.5D, 16, 24),
                        rarityValues(0.3D, 0.45D, 0.7D, 1.05D, 1.6D, 2.4D)),
                relic("storage_expand", "yoiko_core.relic.storage_expand.name", "yoiko_bag_slots",
                        rarityValues(3, 6, 12, 24, 42, 69),
                        rarityValues(1, 2, 3, 5, 8, 12)),
                relic("movement_speed_bonus", "yoiko_core.relic.movement_speed_bonus.name", "player_movement_speed_bonus",
                        rarityValues(2, 3, 4.5D, 6.5D, 9, 12.5D),
                        rarityValues(0.2D, 0.3D, 0.45D, 0.65D, 0.9D, 1.25D)),
                relic("max_health_bonus", "yoiko_core.relic.max_health_bonus.name", "player_max_health_bonus",
                        rarityValues(1, 1.5D, 2.3D, 3.5D, 5.2D, 8),
                        rarityValues(0.1D, 0.15D, 0.23D, 0.35D, 0.52D, 0.8D)),
                relic("block_interaction_range_bonus", "yoiko_core.relic.block_interaction_range_bonus.name", "block_interaction_range_bonus",
                        rarityValues(0.5D, 0.75D, 1.0D, 1.25D, 1.5D, 1.75D),
                        rarityValues(0.05D, 0.075D, 0.1D, 0.125D, 0.15D, 0.175D)),
                relic("critical_strike_chance", "yoiko_core.relic.critical_strike_chance.name", "critical_strike_chance_bonus",
                        rarityValues(0.75D, 1.1D, 1.6D, 2.4D, 3.5D, 5.0D),
                        rarityValues(0.075D, 0.11D, 0.16D, 0.24D, 0.35D, 0.5D)),
                relic("critical_strike_damage", "yoiko_core.relic.critical_strike_damage.name", "critical_strike_damage_bonus",
                        rarityValues(2.5D, 3.5D, 5.0D, 7.5D, 11.0D, 15.0D),
                        rarityValues(0.25D, 0.35D, 0.5D, 0.75D, 1.1D, 1.5D)),
                relic("hunter_hourglass", "yoiko_core.relic.hunter_hourglass.name", "low_health_damage_bonus",
                        rarityValues(1.0D, 1.5D, 2.3D, 3.5D, 5.2D, 10.0D),
                        rarityValues(0.1D, 0.15D, 0.23D, 0.35D, 0.52D, 1.0D)),
                relic("riposte_medal", "yoiko_core.relic.riposte_medal.name", "riposte_damage_bonus",
                        rarityValues(2.5D, 3.5D, 5.0D, 7.5D, 11.0D, 25.0D),
                        rarityValues(0.25D, 0.35D, 0.5D, 0.75D, 1.1D, 2.5D)),
                relic("guardian_pulse", "yoiko_core.relic.guardian_pulse.name", "heavy_hit_damage_reduction",
                        rarityValues(1.25D, 1.75D, 2.5D, 3.75D, 5.5D, 12.5D),
                        rarityValues(0.125D, 0.175D, 0.25D, 0.375D, 0.55D, 1.25D)),
                relic("hawk_bowstring", "yoiko_core.relic.hawk_bowstring.name", "ranged_distance_damage_bonus",
                        rarityValues(1.0D, 1.5D, 2.3D, 3.5D, 5.2D, 10.0D),
                        rarityValues(0.1D, 0.15D, 0.23D, 0.35D, 0.52D, 1.0D)),
                relic("purification_bell", "yoiko_core.relic.purification_bell.name", "harmful_effect_duration_reduction",
                        rarityValues(2.5D, 4.0D, 6.5D, 10.0D, 17.5D, 32.5D),
                        rarityValues(0.25D, 0.4D, 0.65D, 1.0D, 1.75D, 3.25D)),
                relic("survival_anklet", "yoiko_core.relic.survival_anklet.name", "low_health_escape_speed_bonus",
                        rarityValues(2.0D, 3.0D, 4.5D, 6.8D, 10.0D, 15.0D),
                        rarityValues(0.2D, 0.3D, 0.45D, 0.68D, 1.0D, 1.5D)),
                relic("stalker_fang", "yoiko_core.relic.stalker_fang.name", "rear_attack_damage_bonus",
                        rarityValues(0.75D, 1.1D, 1.6D, 2.4D, 3.5D, 7.5D),
                        rarityValues(0.075D, 0.11D, 0.16D, 0.24D, 0.35D, 0.75D)),
                relic("first_moonlight", "yoiko_core.relic.first_moonlight.name", "full_health_first_strike_bonus",
                        rarityValues(0.9D, 1.3D, 2.0D, 3.0D, 4.5D, 9.0D),
                        rarityValues(0.09D, 0.13D, 0.2D, 0.3D, 0.45D, 0.9D)),
                relic("grudge_nail", "yoiko_core.relic.grudge_nail.name", "marked_attacker_damage_bonus",
                        rarityValues(0.75D, 1.1D, 1.6D, 2.4D, 3.5D, 7.5D),
                        rarityValues(0.075D, 0.11D, 0.16D, 0.24D, 0.35D, 0.75D)),
                relic("charge_spur", "yoiko_core.relic.charge_spur.name", "sprinting_knockback_bonus",
                        rarityValues(2.0D, 3.0D, 4.5D, 6.8D, 10.0D, 20.0D),
                        rarityValues(0.2D, 0.3D, 0.45D, 0.68D, 1.0D, 2.0D)),
                relic("frost_needle", "yoiko_core.relic.frost_needle.name", "frost_slow_chance",
                        rarityValues(1.0D, 1.5D, 2.3D, 3.5D, 6.0D, 12.5D),
                        rarityValues(0.1D, 0.15D, 0.23D, 0.35D, 0.6D, 1.25D)),
                relic("victory_drum", "yoiko_core.relic.victory_drum.name", "kill_attack_speed_bonus",
                        rarityValues(0.6D, 0.9D, 1.4D, 2.1D, 3.2D, 6.0D),
                        rarityValues(0.06D, 0.09D, 0.14D, 0.21D, 0.32D, 0.6D)),
                relic("guardian_hourglass", "yoiko_core.relic.guardian_hourglass.name", "out_of_combat_damage_absorption",
                        rarityValues(1.0D, 1.5D, 2.3D, 3.5D, 5.2D, 10.0D),
                        rarityValues(0.1D, 0.15D, 0.23D, 0.35D, 0.52D, 1.0D)),
                relic("waystone_return_charm", "yoiko_core.relic.waystone_return_charm.name", "waystone_travel_discount",
                        rarityValues(1.5D, 2.5D, 4.0D, 6.0D, 9.0D, 12.5D),
                        rarityValues(0.15D, 0.25D, 0.4D, 0.6D, 0.9D, 1.25D)),
                relic("crystal_compass", "yoiko_core.relic.crystal_compass.name", "mega_shard_find_bonus",
                        rarityValues(1.5D, 2.5D, 4.0D, 6.0D, 9.0D, 12.5D),
                        rarityValues(0.15D, 0.25D, 0.4D, 0.6D, 0.9D, 1.25D)),
                relic("knockback_resistance_bonus", "yoiko_core.relic.knockback_resistance_bonus.name", "player_knockback_resistance_bonus",
                        rarityValues(1.5D, 2.2D, 3.2D, 4.6D, 6.8D, 10.0D),
                        rarityValues(0.15D, 0.22D, 0.32D, 0.46D, 0.68D, 1.0D)),
                relic("armor_toughness_bonus", "yoiko_core.relic.armor_toughness_bonus.name", "player_armor_toughness_bonus",
                        rarityValues(0.25D, 0.35D, 0.5D, 0.75D, 1.1D, 2.0D),
                        rarityValues(0.025D, 0.035D, 0.05D, 0.075D, 0.11D, 0.2D)),
                relic("luck_bonus", "yoiko_core.relic.luck_bonus.name", "player_luck_bonus",
                        rarityValues(0.05D, 0.08D, 0.12D, 0.18D, 0.28D, 0.5D),
                        rarityValues(0.005D, 0.008D, 0.012D, 0.018D, 0.028D, 0.05D)),
                relic("common_spawn_bonus", "yoiko_core.relic.common_spawn_bonus.name", "pokemon_common_spawn_weight_bonus",
                        rarityValues(2, 3, 4.5D, 6.8D, 10, 15),
                        rarityValues(0.2D, 0.3D, 0.45D, 0.68D, 1, 1.5D)),
                relic("rare_spawn_bonus", "yoiko_core.relic.rare_spawn_bonus.name", "pokemon_rare_spawn_weight_bonus",
                        rarityValues(1, 1.5D, 2.3D, 3.5D, 5.2D, 8),
                        rarityValues(0.1D, 0.15D, 0.23D, 0.35D, 0.52D, 0.8D)),
                relic("ultra_rare_spawn_bonus", "yoiko_core.relic.ultra_rare_spawn_bonus.name", "pokemon_ultra_rare_spawn_weight_bonus",
                        rarityValues(0.5D, 0.75D, 1.1D, 1.7D, 2.6D, 4),
                        rarityValues(0.05D, 0.075D, 0.11D, 0.17D, 0.26D, 0.4D)),
                relic("totemless_revive_chance", "yoiko_core.relic.totemless_revive_chance.name", "totemless_revive_chance",
                        rarityValues(1, 1.5D, 2.3D, 3.5D, 5.2D, 8),
                        rarityValues(0.1D, 0.15D, 0.23D, 0.35D, 0.52D, 0.8D)),
                relic("mounted_pokemon_speed_bonus", "yoiko_core.relic.mounted_pokemon_speed_bonus.name", "mounted_pokemon_speed_bonus",
                        rarityValues(2, 3, 4.5D, 6.8D, 10, 15),
                        rarityValues(0.2D, 0.3D, 0.45D, 0.68D, 1, 1.5D)),
                relic("capture_rewind", "yoiko_core.relic.capture_rewind.name", "failed_pokeball_return_chance",
                        rarityValues(2, 3, 4.5D, 6.8D, 10, 15),
                        rarityValues(0.2D, 0.3D, 0.45D, 0.68D, 1, 1.5D)),
                relic("victory_ribbon", "yoiko_core.relic.victory_ribbon.name", "battle_victory_heal_percent",
                        rarityValues(0.75D, 1.0D, 1.5D, 2.0D, 3.5D, 6.0D),
                        rarityValues(0.075D, 0.1D, 0.15D, 0.2D, 0.35D, 0.6D)),
                relic("purification_ribbon", "yoiko_core.relic.purification_ribbon.name", "battle_victory_status_cure_chance",
                        rarityValues(3.0D, 5.0D, 8.0D, 12.0D, 20.0D, 40.0D),
                        rarityValues(0.3D, 0.5D, 0.8D, 1.2D, 2.0D, 4.0D)),
                relic("restoration_score", "yoiko_core.relic.restoration_score.name", "battle_victory_move_pp_restore_chance",
                        rarityValues(2.0D, 3.5D, 6.0D, 10.0D, 17.5D, 25.0D),
                        rarityValues(0.2D, 0.35D, 0.6D, 1.0D, 1.75D, 2.5D)),
                relic("harvest_seal", "yoiko_core.relic.harvest_seal.name", "quality_food_grade_upgrade_chance",
                        rarityValues(1.0D, 1.5D, 2.3D, 3.5D, 5.2D, 10.0D),
                        rarityValues(0.1D, 0.15D, 0.23D, 0.35D, 0.52D, 1.0D)),
                relic("golden_carrot_charm", "yoiko_core.relic.golden_carrot_charm.name", "treasure_rabbit_spawn_chance_bonus",
                        rarityValues(1.25D, 2.0D, 3.2D, 5.0D, 7.5D, 12.5D),
                        rarityValues(0.125D, 0.2D, 0.32D, 0.5D, 0.75D, 1.25D)),
                relic("radiant_satiation_crystal", "yoiko_core.relic.radiant_satiation_crystal.name", "satiation_absorption_conversion",
                        rarityValues(5, 7.5D, 10, 12.5D, 17.5D, 25),
                        rarityValues(0.5D, 0.75D, 1, 1.25D, 1.75D, 2.5D)),
                radiantRelic("radiant_mercy", "yoiko_core.relic.radiant_mercy.name", "mercy_hp_floor", 100),
                radiantRelic("radiant_resonance_crown", "yoiko_core.relic.radiant_resonance_crown.name", "equipped_relic_level_bonus", 1),
                radiantRelic("radiant_weakest_echo", "yoiko_core.relic.radiant_weakest_echo.name", "weakest_relic_level_bonus", 2),
                radiantRelic("radiant_chromatic_contract", "yoiko_core.relic.radiant_chromatic_contract.name",
                        "chromatic_contract_level_shift", 3),
                radiantRelic("radiant_lava_cocoon", "yoiko_core.relic.radiant_lava_cocoon.name", "fire_cocoon_duration", 5),
                radiantRelic("radiant_combo_constellation", "yoiko_core.relic.radiant_combo_constellation.name",
                        "combo_strike_damage_bonus", 40)
        );
    }

    private static JsonObject defaultUpgradeRatesJson() {
        JsonObject upgradeRates = new JsonObject();
        for (Map.Entry<Integer, UpgradeRate> entry : defaultUpgradeRates().entrySet()) {
            JsonObject rate = new JsonObject();
            rate.addProperty("greatSuccess", entry.getValue().greatSuccess());
            rate.addProperty("success", entry.getValue().success());
            rate.addProperty("downgrade", entry.getValue().downgrade());
            rate.addProperty("destroy", entry.getValue().destroy());
            upgradeRates.add(Integer.toString(entry.getKey()), rate);
        }
        return upgradeRates;
    }

    private static RelicData relic(String id, String displayName, String effect,
                                   Map<RelicRarity, Double> values,
                                   Map<RelicRarity, Double> upgradeBonuses) {
        return new RelicData(id, displayName, effect,
                Set.copyOf(EnumSet.range(RelicRarity.COMMON, RelicRarity.MYSTIC)),
                RelicEquipGroup.STANDARD, values, upgradeBonuses, true);
    }

    private static RelicData radiantRelic(String id, String displayName, String effect, double value) {
        EnumMap<RelicRarity, Double> values = new EnumMap<>(RelicRarity.class);
        values.put(RelicRarity.RADIANT, value);
        EnumMap<RelicRarity, Double> upgrades = new EnumMap<>(RelicRarity.class);
        upgrades.put(RelicRarity.RADIANT, 0.0D);
        return new RelicData(id, displayName, effect, Set.of(RelicRarity.RADIANT),
                RelicEquipGroup.SPECIAL, values, upgrades, true);
    }

    private static EnumMap<RelicRarity, Double> rarityValues(double common, double uncommon, double rare,
                                                             double epic, double legendary, double mystic) {
        EnumMap<RelicRarity, Double> values = new EnumMap<>(RelicRarity.class);
        values.put(RelicRarity.COMMON, common);
        values.put(RelicRarity.UNCOMMON, uncommon);
        values.put(RelicRarity.RARE, rare);
        values.put(RelicRarity.EPIC, epic);
        values.put(RelicRarity.LEGENDARY, legendary);
        values.put(RelicRarity.MYSTIC, mystic);
        return values;
    }

    private static JsonObject relic(RelicData data) {
        JsonObject relic = new JsonObject();
        relic.addProperty("id", data.id());
        relic.addProperty("displayName", data.displayName());
        relic.addProperty("effect", data.effect());
        JsonArray allowedRarities = new JsonArray();
        for (RelicRarity rarity : data.allowedRarities()) {
            allowedRarities.add(rarity.name());
        }
        relic.add("allowedRarities", allowedRarities);
        relic.addProperty("equipGroup", data.equipGroup().name());
        JsonObject values = new JsonObject();
        for (Map.Entry<RelicRarity, Double> entry : data.values().entrySet()) {
            values.addProperty(entry.getKey().name(), entry.getValue());
        }
        relic.add("values", values);
        JsonObject upgradeBonuses = new JsonObject();
        for (Map.Entry<RelicRarity, Double> entry : data.upgradeBonuses().entrySet()) {
            upgradeBonuses.addProperty(entry.getKey().name(), entry.getValue());
        }
        relic.add("upgradeBonuses", upgradeBonuses);
        relic.addProperty("enabled", data.enabled());
        return relic;
    }

    private static EnumMap<RelicRarity, Integer> readRarityWeights(JsonObject object) {
        EnumMap<RelicRarity, Integer> weights = defaultRarityWeights();
        if (object != null) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                RelicRarity rarity = RelicRarity.parse(entry.getKey()).orElseThrow(() ->
                        new IllegalArgumentException("Unknown relic rarity weight: " + entry.getKey()));
                int weight = entry.getValue().getAsInt();
                if (weight < 0) {
                    throw new IllegalArgumentException("Negative relic rarity weight: " + entry.getKey());
                }
                weights.put(rarity, weight);
            }
        }
        if (weights.values().stream().mapToInt(Integer::intValue).sum() <= 0) {
            throw new IllegalArgumentException("At least one relic rarity weight must be positive.");
        }
        return weights;
    }

    private static Map<Integer, UpgradeRate> readUpgradeRates(JsonObject object) {
        Map<Integer, UpgradeRate> rates = defaultUpgradeRates();
        if (object != null) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    JsonObject rate = entry.getValue().getAsJsonObject();
                    int level = Integer.parseInt(entry.getKey());
                    rates.put(level, new UpgradeRate(
                            decimal(rate, "greatSuccess", 0.0D),
                            decimal(rate, "success", 100.0D),
                            decimal(rate, "downgrade", 0.0D),
                            decimal(rate, "destroy", 0.0D)
                    ));
                }
            }
        }
        return rates;
    }

    private static EnumMap<RelicRarity, Integer> defaultRarityWeights() {
        EnumMap<RelicRarity, Integer> weights = new EnumMap<>(RelicRarity.class);
        weights.put(RelicRarity.COMMON, 6000);
        weights.put(RelicRarity.UNCOMMON, 2500);
        weights.put(RelicRarity.RARE, 1000);
        weights.put(RelicRarity.EPIC, 400);
        weights.put(RelicRarity.LEGENDARY, 90);
        weights.put(RelicRarity.MYSTIC, 10);
        weights.put(RelicRarity.RADIANT, 1);
        return weights;
    }

    private static Map<Integer, UpgradeRate> defaultUpgradeRates() {
        Map<Integer, UpgradeRate> rates = new LinkedHashMap<>();
        rates.put(0, new UpgradeRate(10.0D, 90.0D, 0.0D, 0.0D));
        rates.put(1, new UpgradeRate(9.0D, 81.0D, 0.0D, 0.0D));
        rates.put(2, new UpgradeRate(8.0D, 72.0D, 0.0D, 0.0D));
        rates.put(3, new UpgradeRate(7.0D, 63.0D, 5.0D, 0.0D));
        rates.put(4, new UpgradeRate(6.0D, 54.0D, 10.0D, 0.0D));
        rates.put(5, new UpgradeRate(5.0D, 45.0D, 15.0D, 1.0D));
        rates.put(6, new UpgradeRate(4.0D, 36.0D, 20.0D, 2.0D));
        rates.put(7, new UpgradeRate(3.0D, 27.0D, 25.0D, 3.0D));
        rates.put(8, new UpgradeRate(2.0D, 18.0D, 30.0D, 5.0D));
        rates.put(9, new UpgradeRate(0.0D, 10.0D, 40.0D, 10.0D));
        return rates;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static double decimal(JsonObject object, String key, double fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsBoolean() : fallback;
    }

    private record StorageReductionBlock(int firstOccupiedSlot, int nextSlots, int currentSlots) {
    }

    private record EffectSnapshot(Map<String, Double> bonuses, Set<String> equippedEffects) {
    }

    public record PendingRoll(String relicId, RelicRarity rarity,
                              RelicAppraisalCategory category, boolean categoryFocused,
                              RelicRollSource source) {
    }

    public record CommittedRoll(
            RollResult result,
            RelicGachaResultPayload payload,
            RelicRarity rarity,
            Component relicName,
            Component rarityName,
            Component message
    ) {
    }

    public enum RelicRollSource {
        STANDARD,
        CROWN_RABBIT
    }

    public record RollResult(boolean success, String selectedUuid, String message) {
    }

    public enum UpgradeOutcome {
        REJECTED,
        GREAT_SUCCESS,
        SUCCESS,
        DOWNGRADE,
        DESTROY,
        UNCHANGED
    }

    public record UpgradeResult(
            boolean processed,
            String selectedUuid,
            String message,
            UpgradeOutcome outcome
    ) {
    }

    public record EquipResult(boolean processed, String selectedUuid, String message) {
    }

    public record DismantleResult(boolean processed, String selectedUuid, String message) {
    }

    public record BatchDismantleResult(int dismantled, int scrap, int skipped, String message) {
    }

    public record RelicView(
            String uuid,
            String relicId,
            String displayName,
            String effect,
            String secondaryEffect,
            String rarity,
            int level,
            int effectiveLevel,
            double value,
            double nextValue,
            double secondaryValue,
            double secondaryNextValue,
            double greatSuccess,
            double success,
            double downgrade,
            double destroy,
            int equippedSlot,
            int scrapValue,
            boolean locked,
            boolean upgradeable,
            boolean primaryUpgradeable,
            boolean secondaryUpgradeable,
            int storageSlot,
            boolean primaryEffectSuppressed,
            boolean secondaryEffectSuppressed
    ) {
    }

    private record EquippedEffectWinner(String uuid, int equippedSlot, int effectSlot, double value) {
    }

    public record UpgradeRate(double greatSuccess, double success, double downgrade, double destroy) {
        public UpgradeRate {
            if (!Double.isFinite(greatSuccess) || !Double.isFinite(success)
                    || !Double.isFinite(downgrade) || !Double.isFinite(destroy)
                    || greatSuccess < 0.0D || success < 0.0D || downgrade < 0.0D || destroy < 0.0D
                    || greatSuccess + success + downgrade + destroy > 100.000001D) {
                throw new IllegalArgumentException("Invalid relic upgrade outcome rates.");
            }
        }

        public double failure() {
            return Math.max(0.0D, 100.0D - greatSuccess - success - downgrade - destroy);
        }
    }

}
