package com.yoiko.core.gacha;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.labels.CobblemonPokemonLabels;
import com.cobblemon.mod.common.pokemon.Species;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.config.YoikoCommonConfig;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

public final class GachaPoolScanner {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve(YoikoServerCore.MODID);
    private static final Path POOL_CACHE = CONFIG_DIR.resolve("gacha_pool_cache.json");

    private static GachaPoolData cache = new GachaPoolData();

    private GachaPoolScanner() {
    }

    public static void init() {
        ensureFiles();
        if (!rescanFromCobblemon()) {
            load();
        }
    }

    public static GachaPoolData getPool() {
        return cache;
    }

    public static void rescan() {
        ensureFiles();
        if (!rescanFromCobblemon()) {
            writeDefaultPool();
            load();
        }
    }

    public static void load() {
        ensureFiles();
        GachaPoolData next = new GachaPoolData();
        try (Reader reader = Files.newBufferedReader(POOL_CACHE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            JsonObject pools = root.has("pools") ? root.getAsJsonObject("pools") : new JsonObject();
            for (GachaRarity rarity : GachaRarity.values()) {
                JsonArray array = pools.has(rarity.configKey())
                        ? pools.getAsJsonArray(rarity.configKey())
                        : new JsonArray();
                for (int i = 0; i < array.size(); i++) {
                    next.get(rarity).add(array.get(i).getAsString());
                }
            }
            cache = next;
        } catch (Exception exception) {
            YoikoServerCore.LOGGER.warn("Failed to load Yoiko gacha pool cache. Recreating defaults.", exception);
            writeDefaultPool();
            cache = defaultPool();
        }
    }

    public static Component describeSummaryComponent() {
        int total = cache.pools().values().stream().mapToInt(List::size).sum();
        return Component.translatable("yoiko_core.command.dex.summary", total,
                cache.get(GachaRarity.COMMON).size(), cache.get(GachaRarity.SUB_LEGENDARY).size(),
                cache.get(GachaRarity.MYTHICAL).size(), cache.get(GachaRarity.LEGENDARY).size());
    }

    public static List<String> list(GachaRarity rarity, int limit) {
        return limited(cache.get(rarity), limit);
    }

    public static List<String> search(String query, int limit) {
        String normalizedQuery = normalize(query);
        List<String> results = new ArrayList<>();
        for (List<String> pool : cache.pools().values()) {
            for (String species : pool) {
                if (normalize(species).contains(normalizedQuery) || normalize(pathOnly(species)).contains(normalizedQuery)) {
                    results.add(species);
                }
            }
        }
        results.sort(Comparator.naturalOrder());
        return limited(results, limit);
    }

    public static Component describeSpeciesComponent(String species) {
        GachaRarity rarity = rarityOf(species);
        if (rarity == null) {
            return Component.translatable("yoiko_core.command.dex.species_missing", species);
        }
        String canonical = canonicalSpecies(species);
        Component source = Component.translatable("yoiko_core.command.dex.source_tags");
        return Component.translatable("yoiko_core.command.dex.species_info", canonical,
                rarity.getTranslatedName(), source);
    }

    public static GachaRarity rarityOf(String species) {
        for (GachaRarity rarity : GachaRarity.values()) {
            for (String entry : cache.get(rarity)) {
                if (matchesSpecies(entry, species)) {
                    return rarity;
                }
            }
        }
        return null;
    }

    private static void ensureFiles() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (Files.notExists(POOL_CACHE)) {
                writeDefaultPool();
            }
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Failed to create Yoiko config files.", exception);
        }
    }

    private static void writeDefaultPool() {
        save(defaultPool());
    }

    private static boolean rescanFromCobblemon() {
        try {
            GachaPoolData data = new GachaPoolData();
            for (Species species : PokemonSpecies.getImplemented()) {
                if (species == null || !species.getImplemented()) {
                    continue;
                }
                String id = species.getResourceIdentifier().toString();
                data.get(classifyByLabels(species)).add(id);
            }
            int total = data.pools().values().stream().mapToInt(List::size).sum();
            if (total == 0) {
                return false;
            }
            for (List<String> pool : data.pools().values()) {
                pool.sort(Comparator.naturalOrder());
            }
            cache = data;
            save(data);
            YoikoServerCore.LOGGER.info("Scanned {} Cobblemon species into label-based Yoiko gacha pools.", total);
            return true;
        } catch (Throwable throwable) {
            YoikoServerCore.LOGGER.warn("Unable to scan Cobblemon species registry. Using configured fallback pool.", throwable);
            return false;
        }
    }

    private static GachaRarity classifyByLabels(Species species) {
        boolean legendary = hasLabel(species, CobblemonPokemonLabels.LEGENDARY);
        boolean restricted = hasLabel(species, CobblemonPokemonLabels.RESTRICTED);
        if (legendary && restricted) {
            return GachaRarity.LEGENDARY;
        }
        if (hasLabel(species, CobblemonPokemonLabels.MYTHICAL)) {
            return GachaRarity.MYTHICAL;
        }
        if (legendary) {
            return GachaRarity.SUB_LEGENDARY;
        }
        return GachaRarity.COMMON;
    }

    private static boolean hasLabel(Species species, String expected) {
        for (String label : species.getLabels()) {
            if (label.equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('-', '_').replace(" ", "_");
    }

    private static GachaPoolData defaultPool() {
        GachaPoolData data = new GachaPoolData();
        List<? extends String> commonPool = YoikoCommonConfig.DEFAULT_COMMON_POOL.get();
        data.get(GachaRarity.COMMON).addAll(commonPool);
        return data;
    }

    private static void save(GachaPoolData data) {
        JsonObject root = new JsonObject();
        root.addProperty("generatedAt", OffsetDateTime.now().toString());
        JsonObject pools = new JsonObject();
        for (GachaRarity rarity : GachaRarity.values()) {
            JsonArray array = new JsonArray();
            for (String species : data.get(rarity)) {
                array.add(species);
            }
            pools.add(rarity.configKey(), array);
        }
        root.add("pools", pools);
        writeJson(POOL_CACHE, root);
    }

    private static String canonicalSpecies(String species) {
        String existing = findSpecies(species);
        return existing == null ? species : existing;
    }

    private static String findSpecies(String species) {
        for (List<String> pool : cache.pools().values()) {
            for (String entry : pool) {
                if (matchesSpecies(entry, species)) {
                    return entry;
                }
            }
        }
        return null;
    }

    private static boolean matchesSpecies(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return normalizedLeft.equals(normalizedRight)
                || normalize(pathOnly(left)).equals(normalizedRight)
                || normalizedLeft.equals(normalize(pathOnly(right)));
    }

    private static String pathOnly(String species) {
        int separator = species.indexOf(':');
        return separator >= 0 ? species.substring(separator + 1) : species;
    }

    private static List<String> limited(List<String> source, int limit) {
        int size = Math.min(Math.max(1, limit), source.size());
        return new ArrayList<>(source.subList(0, size));
    }

    private static void writeJson(Path path, JsonObject root) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Failed to write {}", path, exception);
        }
    }

}
