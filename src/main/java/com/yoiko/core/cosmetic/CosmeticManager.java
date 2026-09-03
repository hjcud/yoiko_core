package com.yoiko.core.cosmetic;

import com.yoiko.core.advancement.YoikoAdvancementManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

public final class CosmeticManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path COSMETIC_FILE = FMLPaths.CONFIGDIR.get().resolve(YoikoServerCore.MODID).resolve("cosmetics.json");
    private static final Map<String, CosmeticData> COSMETICS = new LinkedHashMap<>();
    /** Built-in particle rarity is curated by visual complexity; every other built-in effect is legendary. */
    private static final Set<String> MYTHIC_PARTICLE_COSMETICS = Set.of(
            "sakura_shower_trail", "golden_afterglow_trail", "melody_trail", "arcana_trail",
            "emerald_glow_trail", "stardust_steps_trail", "meteor_shower_trail", "prism_shards_trail",
            "mint_circuit_trail", "koi_stream_trail", "card_trick_trail",
            "crescent_moon_ring", "deep_crown_ring", "devil_horns_ring", "marionette_stage_aura",
            "kaleidoscope_ring", "libra_crown_ring", "lotus_mandala_ring",
            "elemental_trinity_orbit", "aurora_veil_aura", "celestial_armillary_ring",
            "card_dealer_orbit", "mobius_ribbon_orbit", "puzzle_cube_orbit",
            "azure_dragon_wings", "end_wings", "peacock_fan_wings", "cathedral_wings",
            "mechanical_iris_wings", "grand_ribbon_wings",
            "ominous_rite_aura", "sculk_pulse_aura", "lucky_dice_aura",
            "trial_flame_companion", "jellyfish_companion", "candle_companion",
            "galaxy_whale_companion", "butterfly_companion"
    );
    private static final Set<String> MIMISYAVRC_COSMETICS = Set.of(
            "sylveon_headpiece", "shiny_sylveon_headpiece",
            "jolteon_headpiece", "shiny_jolteon_headpiece",
            "vaporeon_headpiece", "shiny_vaporeon_headpiece"
    );
    private static final Map<String, ParticleCategory> BUILT_IN_PARTICLE_CATEGORIES = builtInParticleCategories();
    private static final Map<String, CosmeticModelData> BUILT_IN_MODELS = builtInModels();
    private static int particleCatalogRevision;

    static {
        reload();
    }

    private CosmeticManager() {
    }

    public static void init() {
        reload();
    }

    public static void reload() {
        ensureFile();
        try (Reader reader = Files.newBufferedReader(COSMETIC_FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            Map<String, CosmeticData> loaded = new LinkedHashMap<>();
            JsonArray cosmetics = root == null ? null : root.getAsJsonArray("cosmetics");
            if (cosmetics != null) {
                for (JsonElement element : cosmetics) {
                    if (element.isJsonObject()) {
                        CosmeticData cosmetic = readCosmetic(element.getAsJsonObject());
                        if (cosmetic != null && validate(cosmetic) && !isDisabledCosmetic(cosmetic.id())) {
                            loaded.put(cosmetic.id(), cosmetic);
                        }
                    }
                }
            }
            for (CosmeticData cosmetic : defaultCosmetics()) {
                if (!isDisabledCosmetic(cosmetic.id())) {
                    loaded.putIfAbsent(cosmetic.id(), cosmetic);
                }
            }
            if (loaded.isEmpty()) {
                throw new IllegalStateException("No cosmetics were loaded.");
            }
            COSMETICS.clear();
            COSMETICS.putAll(loaded);
        } catch (Exception exception) {
            YoikoServerCore.LOGGER.warn("Failed to load Yoiko cosmetic config. Using defaults.", exception);
            COSMETICS.clear();
            for (CosmeticData cosmetic : defaultCosmetics()) {
                register(cosmetic);
            }
            writeDefaultFile();
        }
        particleCatalogRevision = particleCatalogRevision == Integer.MAX_VALUE
                ? 1
                : particleCatalogRevision + 1;
    }

    public static Collection<String> ids() {
        return COSMETICS.keySet();
    }

    public static CosmeticData get(String id) {
        return COSMETICS.get(id);
    }

    public static int particleCatalogRevision() {
        return particleCatalogRevision;
    }

    public static List<CosmeticData> particleCatalog() {
        return COSMETICS.values().stream()
                .filter(cosmetic -> cosmetic.type() == CosmeticType.PARTICLE)
                .toList();
    }

    public static boolean isAdminOnlyCosmetic(String id) {
        return id != null && (id.equals("supporter_light_aura")
                || id.equals("champion_gold_glory_aura")
                || id.equals("champion_bronze_glory_aura")
                || id.equals("champion_silver_glory_aura")
                || id.equals("champion_platinum_glory_aura")
                || id.equals("champion_diamond_glory_aura"));
    }

    public static boolean grant(ServerPlayer player, String cosmeticId, boolean notify) {
        if (!COSMETICS.containsKey(cosmeticId)) {
            return false;
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        data.ownedCosmetics.add(cosmeticId);
        ServerYoikoSavedData.get(player.server).markDirty(player);
        YoikoAdvancementManager.recordCosmeticOwnership(player);
        if (notify) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.cosmetic.granted", cosmeticId));
        }
        return true;
    }

    public static boolean revoke(ServerPlayer player, String cosmeticId) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        boolean removed = data.ownedCosmetics.remove(cosmeticId);
        boolean equipmentChanged = false;
        for (CosmeticEquipSlot slot : CosmeticEquipSlot.values()) {
            if (cosmeticId.equals(data.equippedCosmetics.get(slot))) {
                data.equippedCosmetics.put(slot, "");
                equipmentChanged = true;
            }
        }
        ServerYoikoSavedData.get(player.server).markDirty(player);
        if (equipmentChanged) {
            ParticleCosmeticDisplayManager.sync(player);
            CosmeticEquipmentDisplayManager.sync(player);
        }
        return removed;
    }

    public static boolean equip(ServerPlayer player, String cosmeticId) {
        CosmeticData cosmetic = get(cosmeticId);
        if (cosmetic == null) {
            return false;
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        if (!data.ownedCosmetics.contains(cosmeticId)) {
            return false;
        }
        if (!cosmetic.requiredRank().isBlank() && !data.ownedRanks.contains(cosmetic.requiredRank())) {
            return false;
        }
        CosmeticEquipSlot slot = CosmeticEquipSlot.forCosmetic(cosmetic);
        if (slot == null) {
            return false;
        }
        data.equippedCosmetics.put(slot, cosmeticId);
        ServerYoikoSavedData.get(player.server).markDirty(player);
        ParticleCosmeticDisplayManager.sync(player);
        CosmeticEquipmentDisplayManager.sync(player);
        return true;
    }

    public static void unequip(ServerPlayer player, CosmeticEquipSlot slot) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        data.equippedCosmetics.put(slot, "");
        ServerYoikoSavedData.get(player.server).markDirty(player);
        ParticleCosmeticDisplayManager.sync(player);
        CosmeticEquipmentDisplayManager.sync(player);
    }

    public static void clearEquipment(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        for (CosmeticEquipSlot slot : CosmeticEquipSlot.values()) {
            data.equippedCosmetics.put(slot, "");
        }
        ServerYoikoSavedData.get(player.server).markDirty(player);
        ParticleCosmeticDisplayManager.sync(player);
        CosmeticEquipmentDisplayManager.sync(player);
    }

    private static CosmeticData readCosmetic(JsonObject object) {
        String id = string(object, "id", "");
        if (id.isBlank() || isDisabledCosmetic(id)) {
            return null;
        }
        return new CosmeticData(
                id,
                string(object, "displayName", id),
                CosmeticType.fromString(string(object, "type", "HEAD")),
                string(object, "particle", ""),
                integer(object, "intervalTicks", 5),
                integer(object, "count", 1),
                decimal(object, "offsetY", 0.1D),
                canonicalRequiredRank(id, string(object, "requiredRank", "")),
                canonicalCreator(id, string(object, "creator", "Yoiko")),
                canonicalRarity(id, object.has("rarity")
                        ? CosmeticRarity.fromString(string(object, "rarity", "MYTHIC"))
                        : CosmeticRarity.MYTHIC),
                canonicalParticleCategory(id,
                        ParticleCategory.fromString(string(object, "particleCategory", "NONE"))),
                canonicalModelData(id, new CosmeticModelData(
                        string(object, "modelId", ""),
                        CosmeticAnchor.fromString(string(object, "anchor", "NONE")),
                        color(object, "primaryColor", 0xFFFFFFFF),
                        color(object, "accentColor", 0xFFFFFFFF)
                ))
        );
    }

    private static void ensureFile() {
        try {
            Files.createDirectories(COSMETIC_FILE.getParent());
            if (Files.notExists(COSMETIC_FILE)) {
                writeDefaultFile();
            }
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Failed to create cosmetic config file.", exception);
        }
    }

    private static void writeDefaultFile() {
        JsonObject root = new JsonObject();
        JsonArray cosmetics = new JsonArray();
        for (CosmeticData data : defaultCosmetics()) {
            if (!isDisabledCosmetic(data.id())) {
                cosmetics.add(cosmetic(data, false));
            }
        }
        root.add("cosmetics", cosmetics);

        try {
            Files.createDirectories(COSMETIC_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(COSMETIC_FILE)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Failed to write default cosmetics.", exception);
        }
    }

    private static JsonObject cosmetic(CosmeticData data, boolean hidden) {
        JsonObject cosmetic = new JsonObject();
        cosmetic.addProperty("id", data.id());
        cosmetic.addProperty("displayName", data.displayName());
        cosmetic.addProperty("type", data.type().name());
        cosmetic.addProperty("particle", data.particle());
        cosmetic.addProperty("intervalTicks", data.intervalTicks());
        cosmetic.addProperty("count", data.count());
        cosmetic.addProperty("offsetY", data.offsetY());
        cosmetic.addProperty("requiredRank", data.requiredRank());
        cosmetic.addProperty("creator", data.creator());
        cosmetic.addProperty("rarity", data.rarity().name());
        cosmetic.addProperty("particleCategory", data.particleCategory().name());
        cosmetic.addProperty("modelId", data.modelData().modelId());
        cosmetic.addProperty("anchor", data.modelData().anchor().name());
        cosmetic.addProperty("primaryColor", colorString(data.modelData().primaryColor()));
        cosmetic.addProperty("accentColor", colorString(data.modelData().accentColor()));
        cosmetic.addProperty("hidden", hidden);
        return cosmetic;
    }

    private static List<CosmeticData> defaultCosmetics() {
        return List.of(
                rabbitCrown(),
                defaultCrown(),
                sampleHead(),
                sampleChestBadge(),
                modelCosmetic("witch_hat", "yoiko_core.cosmetic.witch_hat.name", CosmeticType.HEAD, "witch_hat", CosmeticAnchor.HEAD,
                        0xFF4E3470, 0xFFC28BFF, CosmeticRarity.LEGENDARY),
                modelCosmetic("red_beret", "yoiko_core.cosmetic.red_beret.name", CosmeticType.HEAD, "red_beret", CosmeticAnchor.HEAD,
                        0xFFB83B4B, 0xFF63212C, CosmeticRarity.LEGENDARY),
                modelCosmetic("cat_ear_headband", "yoiko_core.cosmetic.cat_ear_headband.name", CosmeticType.HEAD, "cat_ear_headband", CosmeticAnchor.HEAD,
                        0xFFF0A9C4, 0xFFFFE8F1, CosmeticRarity.LEGENDARY),
                modelCosmetic("sylveon_headpiece", "yoiko_core.cosmetic.sylveon_headpiece.name", CosmeticType.HEAD, "sylveon_headpiece", CosmeticAnchor.HEAD,
                        0xFFFFFFFF, 0xFFFFFFFF, CosmeticRarity.LEGENDARY),
                modelCosmetic("shiny_sylveon_headpiece", "yoiko_core.cosmetic.shiny_sylveon_headpiece.name", CosmeticType.HEAD, "sylveon_headpiece_shiny", CosmeticAnchor.HEAD,
                        0xFFFFFFFF, 0xFFFFFFFF, CosmeticRarity.MYTHIC),
                modelCosmetic("jolteon_headpiece", "yoiko_core.cosmetic.jolteon_headpiece.name", CosmeticType.HEAD, "jolteon_headpiece", CosmeticAnchor.HEAD,
                        0xFFFFFFFF, 0xFFFFFFFF, CosmeticRarity.LEGENDARY),
                modelCosmetic("shiny_jolteon_headpiece", "yoiko_core.cosmetic.shiny_jolteon_headpiece.name", CosmeticType.HEAD, "jolteon_headpiece_shiny", CosmeticAnchor.HEAD,
                        0xFFFFFFFF, 0xFFFFFFFF, CosmeticRarity.MYTHIC),
                modelCosmetic("vaporeon_headpiece", "yoiko_core.cosmetic.vaporeon_headpiece.name", CosmeticType.HEAD, "vaporeon_headpiece", CosmeticAnchor.HEAD,
                        0xFFFFFFFF, 0xFFFFFFFF, CosmeticRarity.LEGENDARY),
                modelCosmetic("shiny_vaporeon_headpiece", "yoiko_core.cosmetic.shiny_vaporeon_headpiece.name", CosmeticType.HEAD, "vaporeon_headpiece_shiny", CosmeticAnchor.HEAD,
                        0xFFFFFFFF, 0xFFFFFFFF, CosmeticRarity.MYTHIC),
                modelCosmetic("ribbon_brooch", "yoiko_core.cosmetic.ribbon_brooch.name", CosmeticType.CHEST, "ribbon_brooch", CosmeticAnchor.CHEST_FRONT,
                        0xFFC83C55, 0xFFFFD267, CosmeticRarity.LEGENDARY),
                modelCosmetic("short_cape", "yoiko_core.cosmetic.short_cape.name", CosmeticType.CHEST, "short_cape", CosmeticAnchor.CHEST_BACK,
                        0xFF3977B9, 0xFFFFD267, CosmeticRarity.LEGENDARY),
                modelCosmetic("crossed_swords", "yoiko_core.cosmetic.crossed_swords.name", CosmeticType.CHEST, "crossed_swords", CosmeticAnchor.CHEST_BACK,
                        0xFFC9D5DE, 0xFF795033, CosmeticRarity.MYTHIC),
                modelCosmetic("back_greatsword", "yoiko_core.cosmetic.back_greatsword.name", CosmeticType.CHEST, "back_greatsword", CosmeticAnchor.CHEST_BACK,
                        0xFFB8C9D6, 0xFFE2B84F, CosmeticRarity.MYTHIC),
                modelCosmetic("mechanical_backpack", "yoiko_core.cosmetic.mechanical_backpack.name", CosmeticType.CHEST, "mechanical_backpack", CosmeticAnchor.CHEST_BACK,
                        0xFF9A7040, 0xFF5AC8D8, CosmeticRarity.MYTHIC),
                modelCosmetic("small_model_wings", "yoiko_core.cosmetic.small_model_wings.name", CosmeticType.CHEST, "small_model_wings", CosmeticAnchor.CHEST_BACK,
                        0xFFEAF7FF, 0xFF74D7E8, CosmeticRarity.MYTHIC),
                aura("champion_bronze_glory_aura", "Bronze Glory", "minecraft:totem_of_undying", 4, 4, 1.0D, CosmeticRarity.LEGENDARY),
                aura("champion_silver_glory_aura", "Silver Glory", "minecraft:totem_of_undying", 4, 4, 1.0D, CosmeticRarity.LEGENDARY),
                aura("champion_platinum_glory_aura", "Platinum Glory", "minecraft:totem_of_undying", 4, 4, 1.0D, CosmeticRarity.LEGENDARY),
                aura("champion_diamond_glory_aura", "Diamond Glory", "minecraft:totem_of_undying", 4, 4, 1.0D, CosmeticRarity.LEGENDARY),
                aura("starfield_aura", "Starfield", "minecraft:end_rod", 4, 5, 1.0D, CosmeticRarity.MYTHIC),
                trail("aqua_drops_trail", "Aqua Drops", "minecraft:splash", 2, 2, 0.12D, CosmeticRarity.LEGENDARY),
                trail("sakura_shower_trail", "Sakura Shower", "minecraft:cherry_leaves", 2, 3, 0.12D, CosmeticRarity.MYTHIC),
                trail("rainbow_spark_trail", "Rainbow Spark", "minecraft:firework", 3, 2, 0.12D, CosmeticRarity.LEGENDARY),
                trail("black_haze_trail", "Black Haze", "minecraft:smoke", 2, 2, 0.12D, CosmeticRarity.LEGENDARY),
                trail("golden_afterglow_trail", "Golden Afterglow", "minecraft:firework", 3, 2, 0.12D, CosmeticRarity.LEGENDARY),
                ring("angel_halo_ring", "Angel Halo", "minecraft:end_rod", 2, 8, 2.05D, CosmeticRarity.LEGENDARY),
                ring("crescent_moon_ring", "Crescent Moon", "minecraft:end_rod", 3, 8, 2.05D, CosmeticRarity.MYTHIC),
                ring("star_crown_ring", "Star Crown", "minecraft:firework", 4, 5, 2.08D, CosmeticRarity.LEGENDARY),
                orbit("arcane_runes_orbit", "Arcane Runes", "minecraft:enchant", 4, 3, 1.05D, CosmeticRarity.LEGENDARY),
                orbit("elemental_trinity_orbit", "Elemental Trinity", "minecraft:happy_villager", 4, 3, 1.05D, CosmeticRarity.MYTHIC),
                wings("angel_wings", "Angel Wings", "minecraft:end_rod", 2, 12, 1.22D),
                wings("shadow_wings", "Shadow Wings", "minecraft:smoke", 2, 12, 1.22D),
                wings("fairy_wings", "Fairy Wings", "minecraft:witch", 2, 12, 1.22D),
                wings("azure_dragon_wings", "Azure Dragon Wings", "minecraft:electric_spark", 2, 12, 1.22D),
                aura("soft_sparkle_aura", "Soft Sparkle", "minecraft:glow", 3, 2, 1.0D),
                aura("supporter_light_aura", "Supporter Light", "minecraft:end_rod", 4, 3, 1.0D, CosmeticRarity.LEGENDARY),
                aura("champion_gold_glory_aura", "Golden Glory", "minecraft:totem_of_undying", 4, 4, 1.0D, CosmeticRarity.LEGENDARY),
                aura("rose_glow_aura", "Rose Glow", "minecraft:firework", 3, 5, 1.0D, CosmeticRarity.LEGENDARY),
                aura("pixel_glitch_aura", "Pixel Glitch", "minecraft:scrape", 4, 3, 1.0D),
                trail("melody_trail", "Melody", "minecraft:note", 4, 1, 0.45D, CosmeticRarity.MYTHIC),
                trail("soulflame_trail", "Soulflame", "minecraft:soul_fire_flame", 2, 2, 0.12D, CosmeticRarity.LEGENDARY),
                trail("arcana_trail", "Arcana", "minecraft:enchant", 2, 3, 0.35D, CosmeticRarity.MYTHIC),
                trail("venom_mist_trail", "Venom Mist", "minecraft:witch", 4, 2, 0.35D, CosmeticRarity.LEGENDARY),
                trail("emerald_glow_trail", "Emerald Glow", "minecraft:happy_villager", 4, 1, 0.35D, CosmeticRarity.LEGENDARY),
                ring("soulfire_halo_ring", "Soulfire Halo", "minecraft:soul_fire_flame", 4, 5, 2.05D, CosmeticRarity.LEGENDARY),
                ring("portal_ring", "Portal", "minecraft:reverse_portal", 4, 6, 2.05D, CosmeticRarity.LEGENDARY),
                ring("flower_crown_ring", "Flower Crown", "minecraft:cherry_leaves", 4, 6, 2.05D, CosmeticRarity.LEGENDARY),
                orbit("wandering_souls_orbit", "Wandering Souls", "minecraft:soul", 5, 3, 1.05D, CosmeticRarity.LEGENDARY),
                orbit("end_echo_orbit", "End Echo", "minecraft:dragon_breath", 5, 3, 1.05D, CosmeticRarity.LEGENDARY),
                orbit("emerald_arc_orbit", "Emerald Arc", "minecraft:happy_villager", 5, 3, 1.05D, CosmeticRarity.LEGENDARY),
                wings("flame_wings", "Flame Wings", "minecraft:flame", 2, 14, 1.22D),
                wings("soulfire_wings", "Soulfire Wings", "minecraft:soul_fire_flame", 2, 14, 1.22D),
                wings("end_wings", "End Wings", "minecraft:dragon_breath", 3, 12, 1.22D),
                ring("rain_cloud_ring", "Rain Cloud", "minecraft:cloud", 4, 4, 1.0D, CosmeticRarity.LEGENDARY),
                aura("heartlight_aura", "Heartlight", "minecraft:heart", 8, 2, 1.0D),
                aura("dimensional_rift_aura", "Dimensional Rift", "minecraft:reverse_portal", 4, 4, 1.0D),
                aura("runic_glow_aura", "Runic Glow", "minecraft:enchant", 4, 4, 1.0D),
                companion("azure_wisp_companion", "Azure Wisp", "minecraft:soul_fire_flame", 2, 1, 1.25D),
                companion("fairy_light_companion", "Fairy Light", "minecraft:glow", 2, 1, 1.25D),
                companion("fire_wisp_companion", "Fire Wisp", "minecraft:small_flame", 2, 1, 1.25D),
                companion("aqua_wisp_companion", "Aqua Wisp", "minecraft:bubble", 2, 1, 1.25D),
                companion("forest_wisp_companion", "Forest Wisp", "minecraft:happy_villager", 3, 1, 1.25D),
                trail("stardust_steps_trail", "Stardust Steps", "minecraft:end_rod", 1, 1, 0.10D, CosmeticRarity.LEGENDARY),
                trail("meteor_shower_trail", "Meteor Shower", "minecraft:end_rod", 1, 1, 0.10D, CosmeticRarity.MYTHIC),
                trail("ember_steps_trail", "Ember Steps", "minecraft:small_flame", 1, 1, 0.10D, CosmeticRarity.LEGENDARY),
                trail("frost_trace_trail", "Frost Trace", "minecraft:snowflake", 1, 1, 0.06D, CosmeticRarity.LEGENDARY),
                trail("wind_trace_trail", "Wind Trace", "minecraft:small_gust", 1, 1, 0.10D, CosmeticRarity.LEGENDARY),
                trail("sandstorm_trail", "Sandstorm", "minecraft:dust_plume", 1, 1, 0.06D, CosmeticRarity.LEGENDARY),
                ring("thorn_crown_ring", "Crown of Thorns", "minecraft:happy_villager", 4, 12, 2.08D, CosmeticRarity.LEGENDARY),
                ring("crystal_diadem_ring", "Crystal Tiara", "minecraft:end_rod", 4, 15, 2.08D, CosmeticRarity.LEGENDARY),
                ring("ember_crown_ring", "Ember Crown", "minecraft:small_flame", 4, 10, 2.08D, CosmeticRarity.LEGENDARY),
                ring("deep_crown_ring", "Deep Crown", "minecraft:sculk_charge_pop", 5, 9, 2.08D, CosmeticRarity.MYTHIC),
                orbit("twin_moons_orbit", "Twin Moons", "minecraft:end_rod", 4, 4, 1.05D, CosmeticRarity.LEGENDARY),
                orbit("crystal_satellites_orbit", "Crystal Satellites", "minecraft:end_rod", 4, 6, 1.05D, CosmeticRarity.LEGENDARY),
                orbit("void_satellite_orbit", "Void Satellite", "minecraft:reverse_portal", 3, 7, 1.05D, CosmeticRarity.LEGENDARY),
                orbit("firefly_swarm_orbit", "Firefly Swarm", "minecraft:glow", 4, 7, 1.05D, CosmeticRarity.LEGENDARY),
                aura("arcane_pulse_aura", "Arcane Pulse", "minecraft:witch", 6, 8, 0.35D, CosmeticRarity.LEGENDARY),
                aura("snow_blossom_aura", "Snow Blossom", "minecraft:snowflake", 5, 6, 0.35D, CosmeticRarity.LEGENDARY),
                aura("ominous_rite_aura", "Ominous Rite", "minecraft:ominous_spawning", 12, 5, 0.20D, CosmeticRarity.MYTHIC),
                companion("cloud_wisp_companion", "Cloud Wisp", "minecraft:cloud", 3, 1, 1.25D, CosmeticRarity.LEGENDARY),
                companion("void_eye_companion", "Void Eye", "minecraft:sculk_soul", 3, 1, 1.25D, CosmeticRarity.LEGENDARY),
                ring("zephyr_ring", "Zephyr Ring", "minecraft:small_gust", 4, 10, 2.08D, CosmeticRarity.LEGENDARY),
                aura("sculk_pulse_aura", "Sculk Pulse", "minecraft:sculk_charge_pop", 8, 8, 0.35D, CosmeticRarity.MYTHIC),
                companion("trial_flame_companion", "Trial Flame", "minecraft:small_flame", 3, 1, 1.25D, CosmeticRarity.MYTHIC),
                companion("firefly_wisp_companion", "Firefly Wisp", "minecraft:wax_on", 3, 1, 1.25D, CosmeticRarity.LEGENDARY),
                orbit("aurora_veil_aura", "Aurora Veil", "minecraft:end_rod", 4, 6, 0.75D, CosmeticRarity.MYTHIC),

                trail("prism_shards_trail", "Prism Shards", "minecraft:end_rod", 1, 1, 0.14D, CosmeticRarity.LEGENDARY),
                trail("soda_pop_trail", "Soda Pop", "minecraft:bubble_pop", 1, 1, 0.18D, CosmeticRarity.LEGENDARY),
                trail("mint_circuit_trail", "Mint Circuit", "minecraft:electric_spark", 1, 1, 0.08D, CosmeticRarity.LEGENDARY),
                trail("lightning_stitch_trail", "Lightning Stitch", "minecraft:electric_spark", 1, 1, 0.16D, CosmeticRarity.LEGENDARY),
                trail("candy_pixel_trail", "Candy Pixels", "minecraft:glow", 1, 1, 0.16D, CosmeticRarity.LEGENDARY),
                trail("koi_stream_trail", "Koi Stream", "minecraft:splash", 1, 1, 0.20D, CosmeticRarity.MYTHIC),
                trail("card_trick_trail", "Card Trick", "minecraft:enchant", 1, 1, 0.18D, CosmeticRarity.MYTHIC),

                ring("compass_ring", "Compass Ring", "minecraft:wax_on", 4, 12, 2.07D, CosmeticRarity.LEGENDARY),
                ring("pearl_tide_ring", "Pearl Tide", "minecraft:end_rod", 4, 8, 2.07D, CosmeticRarity.LEGENDARY),
                ring("ribbon_knot_ring", "Ribbon Knot", "minecraft:cherry_leaves", 2, 12, 2.07D, CosmeticRarity.LEGENDARY),
                ring("newbie_sprout_ring", "Newbie", "minecraft:happy_villager", 2, 10, 2.07D, CosmeticRarity.LEGENDARY),
                ring("clover_crest_ring", "Clover Crest", "minecraft:happy_villager", 4, 12, 2.07D, CosmeticRarity.LEGENDARY),
                ring("honeydrop_ring", "Honeydrop Ring", "minecraft:wax_on", 4, 12, 2.07D, CosmeticRarity.LEGENDARY),
                ring("wave_meter_ring", "Wave Meter", "minecraft:note", 4, 12, 2.07D, CosmeticRarity.LEGENDARY),
                ring("devil_horns_ring", "Devil Horns", "minecraft:witch", 2, 16, 2.07D, CosmeticRarity.MYTHIC),
                ring("marionette_stage_aura", "Marionette Stage", "minecraft:glow", 2, 22, 2.07D, CosmeticRarity.MYTHIC),
                ring("kaleidoscope_ring", "Kaleidoscope Crown", "minecraft:glow", 5, 18, 2.08D, CosmeticRarity.MYTHIC),
                ring("libra_crown_ring", "Libra Crown", "minecraft:wax_on", 5, 16, 2.08D, CosmeticRarity.MYTHIC),
                ring("lotus_mandala_ring", "Lotus Mandala", "minecraft:cherry_leaves", 5, 16, 2.08D, CosmeticRarity.MYTHIC),

                orbit("celestial_armillary_ring", "Celestial Armillary", "minecraft:end_rod", 4, 18, 1.04D, CosmeticRarity.MYTHIC),
                orbit("seal_charms_orbit", "Seal Charms", "minecraft:enchant", 4, 12, 1.04D, CosmeticRarity.LEGENDARY),
                orbit("glass_baubles_orbit", "Glass Baubles", "minecraft:end_rod", 4, 12, 1.04D, CosmeticRarity.LEGENDARY),
                orbit("crayon_bits_orbit", "Crayon Bits", "minecraft:glow", 4, 12, 1.04D, CosmeticRarity.LEGENDARY),
                orbit("card_dealer_orbit", "Card Dealer's Hand", "minecraft:soul", 2, 16, 1.04D, CosmeticRarity.LEGENDARY),
                orbit("mobius_ribbon_orbit", "Mobius Ribbon", "minecraft:end_rod", 4, 20, 1.04D, CosmeticRarity.MYTHIC),
                orbit("puzzle_cube_orbit", "Puzzle Cube", "minecraft:glow", 5, 24, 1.04D, CosmeticRarity.MYTHIC),

                wings("peacock_fan_wings", "Peacock Fan Wings", "minecraft:glow", 2, 12, 1.22D, CosmeticRarity.MYTHIC),
                wings("cathedral_wings", "Cathedral Wings", "minecraft:end_rod", 2, 12, 1.22D, CosmeticRarity.MYTHIC),
                wings("mechanical_iris_wings", "Mechanical Iris Wings", "minecraft:wax_on", 2, 12, 1.22D, CosmeticRarity.MYTHIC),
                wings("grand_ribbon_wings", "Grand Ribbon Wings", "minecraft:cherry_leaves", 2, 12, 1.22D, CosmeticRarity.MYTHIC),

                aura("soaplight_aura", "Soaplight", "minecraft:bubble_pop", 4, 8, 0.44D, CosmeticRarity.LEGENDARY),
                aura("lucky_dice_aura", "Lucky Dice", "minecraft:crit", 5, 10, 0.44D, CosmeticRarity.LEGENDARY),

                companion("paper_crane_companion", "Paper Crane", "minecraft:white_ash", 2, 10, 1.25D, CosmeticRarity.LEGENDARY),
                companion("jellyfish_companion", "Jellyfish", "minecraft:bubble_pop", 2, 10, 1.25D, CosmeticRarity.LEGENDARY),
                companion("candle_companion", "Candle Spirit", "minecraft:small_flame", 2, 10, 1.25D, CosmeticRarity.LEGENDARY),
                companion("galaxy_whale_companion", "Galaxy Whale", "minecraft:end_rod", 3, 16, 1.25D, CosmeticRarity.MYTHIC),
                companion("butterfly_companion", "Butterfly Spirit", "minecraft:glow", 3, 16, 1.25D, CosmeticRarity.MYTHIC)
        );
    }

    private static Map<String, ParticleCategory> builtInParticleCategories() {
        Map<String, ParticleCategory> categories = new LinkedHashMap<>();
        for (CosmeticData cosmetic : defaultCosmetics()) {
            if (cosmetic.type() == CosmeticType.PARTICLE) {
                categories.put(cosmetic.id(), cosmetic.particleCategory());
            }
        }
        return Map.copyOf(categories);
    }

    private static Map<String, CosmeticModelData> builtInModels() {
        Map<String, CosmeticModelData> models = new LinkedHashMap<>();
        for (CosmeticData cosmetic : defaultCosmetics()) {
            if (cosmetic.modelData().present()) {
                models.put(cosmetic.id(), cosmetic.modelData());
            }
        }
        return Map.copyOf(models);
    }

    private static ParticleCategory canonicalParticleCategory(String id, ParticleCategory configured) {
        return BUILT_IN_PARTICLE_CATEGORIES.getOrDefault(id, configured);
    }

    private static CosmeticModelData canonicalModelData(String id, CosmeticModelData configured) {
        return BUILT_IN_MODELS.getOrDefault(id, configured);
    }

    private static CosmeticData defaultCrown() {
        return modelCosmetic("supporter_crown", "yoiko_core.cosmetic.supporter_crown.name", CosmeticType.HEAD,
                "supporter_crown", CosmeticAnchor.HEAD, 0xFFFFD84D, 0xFFC98C2E,
                CosmeticRarity.MYTHIC, "supporter");
    }

    private static CosmeticData rabbitCrown() {
        return modelCosmetic(RabbitCrownStyle.COSMETIC_ID,
                "yoiko_core.cosmetic.rabbit_crown.name", CosmeticType.HEAD,
                RabbitCrownStyle.MODEL_ID, CosmeticAnchor.HEAD,
                RabbitCrownStyle.GOLD_COLOR, RabbitCrownStyle.JEWEL_COLOR,
                CosmeticRarity.MYTHIC);
    }

    private static CosmeticData sampleHead() {
        return modelCosmetic("sample_head_crystal_crown", "yoiko_core.cosmetic.sample_head_crystal_crown.name", CosmeticType.HEAD,
                "crystal_crown", CosmeticAnchor.HEAD, 0xFF55DDF2, 0xFFE9FCFF,
                CosmeticRarity.MYTHIC);
    }

    private static CosmeticData sampleChestBadge() {
        return modelCosmetic("sample_chest_crystal_badge", "yoiko_core.cosmetic.sample_chest_crystal_badge.name", CosmeticType.CHEST,
                "crystal_badge", CosmeticAnchor.CHEST_FRONT, 0xFF55DDF2, 0xFFE9FCFF,
                CosmeticRarity.LEGENDARY);
    }

    private static CosmeticData modelCosmetic(String id, String displayName, CosmeticType type, String modelId,
                                              CosmeticAnchor anchor, int primaryColor, int accentColor,
                                              CosmeticRarity rarity) {
        return modelCosmetic(id, displayName, type, modelId, anchor, primaryColor, accentColor, rarity, "");
    }

    private static CosmeticData modelCosmetic(String id, String displayName, CosmeticType type, String modelId,
                                              CosmeticAnchor anchor, int primaryColor, int accentColor,
                                              CosmeticRarity rarity, String requiredRank) {
        return new CosmeticData(id, displayName, type, "", 0, 0, 0.0D, requiredRank,
                canonicalCreator(id, "Yoiko"),
                rarity, ParticleCategory.NONE,
                new CosmeticModelData(modelId, anchor, primaryColor, accentColor));
    }

    private static CosmeticData particle(String id, String displayName, String particle, int intervalTicks, int count, double offsetY) {
        throw new IllegalStateException("Particle cosmetics must declare a particle category: " + id);
    }

    private static CosmeticData particle(String id, String displayName, String particle, int intervalTicks, int count,
                                         double offsetY, CosmeticRarity rarity) {
        throw new IllegalStateException("Particle cosmetics must declare a particle category: " + id);
    }

    private static CosmeticData particle(ParticleCategory category, String id, String displayName, String particle,
                                         int intervalTicks, int count, double offsetY, CosmeticRarity ignoredRarity) {
        return new CosmeticData(id, displayName, CosmeticType.PARTICLE, particle, intervalTicks, count, offsetY,
                canonicalRequiredRank(id, ""), "Yoiko", builtInParticleRarity(id), category,
                CosmeticModelData.NONE);
    }

    private static CosmeticData particle(String id, String displayName, String particle, int intervalTicks, int count, double offsetY, String requiredRank) {
        throw new IllegalStateException("Particle cosmetics must declare a particle category: " + id);
    }

    private static CosmeticData trail(String id, String name, String particle, int interval, int count, double y) {
        return particle(ParticleCategory.TRAIL, id, name, particle, interval, count, y, CosmeticRarity.MYTHIC);
    }

    private static CosmeticData trail(String id, String name, String particle, int interval, int count, double y, CosmeticRarity rarity) {
        return particle(ParticleCategory.TRAIL, id, name, particle, interval, count, y, rarity);
    }

    private static CosmeticData ring(String id, String name, String particle, int interval, int count, double y, CosmeticRarity rarity) {
        return particle(ParticleCategory.RING, id, name, particle, interval, count, y, rarity);
    }

    private static CosmeticData orbit(String id, String name, String particle, int interval, int count, double y, CosmeticRarity rarity) {
        return particle(ParticleCategory.ORBIT, id, name, particle, interval, count, y, rarity);
    }

    private static CosmeticData wings(String id, String name, String particle, int interval, int count, double y) {
        return particle(ParticleCategory.WINGS, id, name, particle, interval, count, y, CosmeticRarity.MYTHIC);
    }

    private static CosmeticData wings(String id, String name, String particle, int interval, int count, double y,
                                      CosmeticRarity rarity) {
        return particle(ParticleCategory.WINGS, id, name, particle, interval, count, y, rarity);
    }

    private static CosmeticData aura(String id, String name, String particle, int interval, int count, double y) {
        return particle(ParticleCategory.AURA, id, name, particle, interval, count, y, CosmeticRarity.MYTHIC);
    }

    private static CosmeticData aura(String id, String name, String particle, int interval, int count, double y, CosmeticRarity rarity) {
        return particle(ParticleCategory.AURA, id, name, particle, interval, count, y, rarity);
    }

    private static CosmeticData companion(String id, String name, String particle, int interval, int count, double y) {
        return particle(ParticleCategory.COMPANION, id, name, particle, interval, count, y, CosmeticRarity.MYTHIC);
    }

    private static CosmeticData companion(String id, String name, String particle, int interval, int count, double y, CosmeticRarity rarity) {
        return particle(ParticleCategory.COMPANION, id, name, particle, interval, count, y, rarity);
    }

    private static boolean isDisabledCosmetic(String id) {
        return id != null && (id.startsWith("dummy_")
                || id.startsWith("jump_burst")
                || id.equals("aqua_wings")
                || id.equals("pokeball_orbit")
                || id.equals("pokeball_spark_trail")
                || id.equals("starlight_trail")
                || id.equals("crystal_dust_trail")
                || id.equals("warped_spore_trail")
                || id.equals("wax_spark_trail")
                || id.equals("ash_trail")
                || id.equals("portal_trail")
                || id.equals("heart_trail")
                || id.equals("note_ring")
                || id.equals("heart_ring")
                || id.equals("wax_orbit")
                || id.equals("crimson_warped_orbit")
                || id.equals("blossom_wings")
                || id.equals("mythic_mist")
                || id.equals("ash_mist")
                || id.equals("spore_aura")
                || id.equals("ember_trail")
                || id.equals("snowflake_trail")
                || id.equals("enchant_trail")
                || id.equals("breeze_leaf_trail")
                || id.equals("honey_drop_trail")
                || id.equals("sun_halo_ring")
                || id.equals("constellation_halo_ring")
                || id.equals("resonance_node_orbit")
                || id.equals("nautilus_current_orbit")
                || id.equals("obsidian_tear_orbit")
                || id.equals("frost_particle_wings")
                || id.equals("cobweb_wings")
                || id.equals("nectar_wings")
                || id.equals("blade_arc_wings")
                || id.equals("forest_spore_aura")
                || id.equals("thunder_flash_aura")
                || id.equals("sonic_pulse_aura")
                || id.equals("ink_bloom_aura")
                || id.equals("eggshell_sprite_companion")
                || id.equals("verdigris_halo_ring")
                || id.equals("fury_crown_ring")
                || id.equals("clockwork_orbit")
                || id.equals("solar_prominence_aura")
                || id.equals("dawn_mist_aura")
                || id.equals("star_wisp_companion")
                || id.equals("golden_wisp_companion")
                || id.equals("void_wisp_companion")
                || id.equals("slime_wisp_companion")
                || id.equals("paper_flight_trail")
                || id.equals("sonic_flight_trail")
                || id.equals("dragon_brush_trail")
                || id.equals("brass_keys_orbit")
                || id.equals("magnetic_poles_orbit")
                || id.equals("double_helix_orbit")
                || id.equals("stained_gate_orbit")
                || id.equals("origami_wings")
                || id.equals("kite_wings")
                || id.equals("soundwave_wings")
                || id.equals("brass_lattice_wings")
                || id.equals("lotus_wings")
                || id.equals("snapshot_aura")
                || id.equals("polarity_aura")
                || id.equals("hourglass_aura")
                || id.equals("royal_chess_aura")
                || id.equals("radiant_cathedral_aura")
                || id.equals("compass_companion")
                || id.equals("teacup_companion")
                || id.equals("clockwork_bird_companion")
                || id.equals("fox_mask_companion"));
    }

    private static String canonicalRequiredRank(String id, String configured) {
        return isAdminOnlyCosmetic(id) ? "" : configured;
    }

    private static String canonicalCreator(String id, String configured) {
        return MIMISYAVRC_COSMETICS.contains(id) ? "@mimisyaVRC" : configured;
    }

    private static CosmeticRarity canonicalRarity(String id, CosmeticRarity configured) {
        return BUILT_IN_PARTICLE_CATEGORIES.containsKey(id) ? builtInParticleRarity(id) : configured;
    }

    private static CosmeticRarity builtInParticleRarity(String id) {
        return MYTHIC_PARTICLE_COSMETICS.contains(id)
                ? CosmeticRarity.MYTHIC
                : CosmeticRarity.LEGENDARY;
    }

    private static void register(CosmeticData cosmetic) {
        if (validate(cosmetic)) {
            COSMETICS.put(cosmetic.id(), cosmetic);
        }
    }

    private static boolean validate(CosmeticData cosmetic) {
        if (cosmetic.id().isBlank() || cosmetic.id().length() > 128) {
            YoikoServerCore.LOGGER.error("Cosmetic ID must contain 1-128 characters; entry ignored.");
            return false;
        }
        if (cosmetic.type() == CosmeticType.PARTICLE && cosmetic.particleCategory() == ParticleCategory.NONE) {
            YoikoServerCore.LOGGER.error("Particle cosmetic '{}' must declare a non-NONE particleCategory; entry ignored.", cosmetic.id());
            return false;
        }
        if (cosmetic.type() == CosmeticType.PARTICLE
                && (cosmetic.particle().length() > 128
                || cosmetic.intervalTicks() < 1 || cosmetic.intervalTicks() > 1_200
                || cosmetic.count() < 0 || cosmetic.count() > 256
                || !Double.isFinite(cosmetic.offsetY()) || Math.abs(cosmetic.offsetY()) > 16.0D)) {
            YoikoServerCore.LOGGER.error(
                    "Particle cosmetic '{}' has unsafe render parameters; entry ignored.", cosmetic.id()
            );
            return false;
        }
        if (cosmetic.type() != CosmeticType.PARTICLE && cosmetic.particleCategory() != ParticleCategory.NONE) {
            YoikoServerCore.LOGGER.error("Non-particle cosmetic '{}' must use particleCategory NONE; entry ignored.", cosmetic.id());
            return false;
        }
        if (cosmetic.type() == CosmeticType.PARTICLE && cosmetic.modelData().present()) {
            YoikoServerCore.LOGGER.error("Particle cosmetic '{}' cannot declare a player model; entry ignored.", cosmetic.id());
            return false;
        }
        if (CosmeticEquipSlot.forCosmetic(cosmetic) == null) {
            YoikoServerCore.LOGGER.error("Cosmetic '{}' cannot be assigned to a physical equipment slot; entry ignored.", cosmetic.id());
            return false;
        }
        if (cosmetic.modelData().present()) {
            boolean validAnchor = cosmetic.type() == CosmeticType.HEAD
                    ? cosmetic.modelData().anchor() == CosmeticAnchor.HEAD
                            || cosmetic.modelData().anchor() == CosmeticAnchor.FACE
                    : cosmetic.type() == CosmeticType.CHEST
                            && cosmetic.modelData().anchor() != CosmeticAnchor.HEAD
                            && cosmetic.modelData().anchor() != CosmeticAnchor.FACE
                            && cosmetic.modelData().anchor() != CosmeticAnchor.NONE;
            if (!validAnchor) {
                YoikoServerCore.LOGGER.error(
                        "Cosmetic '{}' has incompatible type {} and model anchor {}; entry ignored.",
                        cosmetic.id(), cosmetic.type(), cosmetic.modelData().anchor()
                );
                return false;
            }
        }
        return true;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
    }

    private static double decimal(JsonObject object, String key, double fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsDouble() : fallback;
    }

    private static int color(JsonObject object, String key, int fallback) {
        String value = string(object, key, "");
        if (value.isBlank()) {
            return fallback;
        }
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        try {
            long parsed = Long.parseLong(normalized, 16);
            if (normalized.length() <= 6) {
                parsed |= 0xFF000000L;
            }
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String colorString(int color) {
        return String.format("#%08X", color);
    }
}
