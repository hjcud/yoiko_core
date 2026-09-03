package com.yoiko.core.rank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.cosmetic.CosmeticManager;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

public final class RankManager {
    private static final String RANK_ICON_TEXTURE_PREFIX = "yoiko_core:textures/gui/rank/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path RANK_FILE = FMLPaths.CONFIGDIR.get().resolve(YoikoServerCore.MODID).resolve("ranks.json");
    private static final ResourceLocation RANK_ICON_FONT = YoikoServerCore.id("rank_icons");
    private static final Map<String, RankData> RANKS = new LinkedHashMap<>();

    static {
        reload();
    }

    private RankManager() {
    }

    public static void init() {
        reload();
    }

    public static void reload() {
        ensureFile();
        try (Reader reader = Files.newBufferedReader(RANK_FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            Map<String, RankData> loaded = new LinkedHashMap<>();
            JsonArray ranks = root == null ? null : root.getAsJsonArray("ranks");
            if (ranks != null) {
                for (JsonElement element : ranks) {
                    if (element.isJsonObject()) {
                        RankData rank = readRank(element.getAsJsonObject());
                        if (rank != null) {
                            loaded.put(rank.id(), rank);
                        }
                    }
                }
            }
            for (RankData rank : defaultRanks()) {
                loaded.put(rank.id(), rank);
            }
            if (loaded.isEmpty()) {
                throw new IllegalStateException("No ranks were loaded.");
            }
            RANKS.clear();
            RANKS.putAll(loaded);
        } catch (Exception exception) {
            YoikoServerCore.LOGGER.warn("Failed to load Yoiko rank config. Using defaults.", exception);
            RANKS.clear();
            for (RankData rank : defaultRanks()) {
                register(rank);
            }
            writeDefaultFile();
        }
    }

    public static Collection<String> ids() {
        return RANKS.keySet();
    }

    public static RankData get(String id) {
        return RANKS.get(id);
    }

    public static boolean grant(ServerPlayer player, String rankId) {
        RankData rank = get(rankId);
        if (rank == null) {
            return false;
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        data.ownedRanks.add(rankId);
        if (data.activeRank.isBlank() || !data.ownedRanks.contains(data.activeRank)) {
            data.activeRank = rankId;
        }
        for (String cosmetic : rank.grantCosmetics()) {
            CosmeticManager.grant(player, cosmetic, false);
        }
        ServerYoikoSavedData.get(player.server).markDirty(player);
        RankDisplayManager.refresh(player);
        return true;
    }

    public static boolean revoke(ServerPlayer player, String rankId) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        boolean removed = data.ownedRanks.remove(rankId);
        if (rankId.equals(data.activeRank)) {
            data.activeRank = data.ownedRanks.stream().findFirst().orElse("");
        }
        ServerYoikoSavedData.get(player.server).markDirty(player);
        RankDisplayManager.refresh(player);
        return removed;
    }

    public static boolean setActive(ServerPlayer player, String rankId) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        if (!data.ownedRanks.contains(rankId)) {
            return false;
        }
        data.activeRank = rankId;
        ServerYoikoSavedData.get(player.server).markDirty(player);
        RankDisplayManager.refresh(player);
        return true;
    }

    /** Unequips the active rank without revoking any owned ranks. */
    public static boolean unequip(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        if (data.activeRank.isBlank()) {
            return false;
        }
        data.activeRank = "";
        ServerYoikoSavedData.get(player.server).markDirty(player);
        RankDisplayManager.refresh(player);
        return true;
    }

    public static Component prefixFor(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        RankData rank = get(data.activeRank);
        return rank == null ? Component.empty() : decoratedPrefix(rank);
    }

    public static Component tabPrefixFor(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        RankData rank = get(data.activeRank);
        return rank == null ? Component.empty() : decoratedPrefix(rank);
    }

    private static RankData readRank(JsonObject object) {
        String id = string(object, "id", "");
        if (id.isBlank() || isDisabledDummy(id)) {
            return null;
        }
        String iconTexture = string(
                object,
                "iconTexture",
                RANK_ICON_TEXTURE_PREFIX + id + ".png"
        );
        return new RankData(
                id,
                string(object, "displayName", id),
                string(object, "acquisitionPath", defaultAcquisitionPath(id)),
                integer(object, "priority", 0),
                color(string(object, "color", "white")),
                iconTexture,
                string(object, "nameplateIcon", id),
                string(object, "iconGlyph", iconGlyph(id)),
                string(object, "chatPrefix", ""),
                string(object, "tabPrefix", string(object, "chatPrefix", "")),
                stringList(object, "grantCosmetics"),
                bool(object, "allowUserSelect", true)
        );
    }

    private static void ensureFile() {
        try {
            Files.createDirectories(RANK_FILE.getParent());
            if (Files.notExists(RANK_FILE)) {
                writeDefaultFile();
            }
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Failed to create rank config file.", exception);
        }
    }

    private static void writeDefaultFile() {
        JsonObject root = new JsonObject();
        JsonArray ranks = new JsonArray();
        ranks.add(rank("supporter", defaultName("supporter"), defaultAcquisitionPath("supporter"), 50, "aqua", "", "", List.of(), true));
        ranks.add(rank("artist", defaultName("artist"), defaultAcquisitionPath("artist"), 60, "light_purple", "", "", List.of(), true));
        ranks.add(rank("pokemon_champion_bronze", defaultName("pokemon_champion_bronze"), defaultAcquisitionPath("pokemon_champion_bronze"), 100, "gold", "", "", List.of(), true));
        ranks.add(rank("pokemon_champion_silver", defaultName("pokemon_champion_silver"), defaultAcquisitionPath("pokemon_champion_silver"), 120, "gray", "", "", List.of(), true));
        ranks.add(rank("pokemon_champion_gold", defaultName("pokemon_champion_gold"), defaultAcquisitionPath("pokemon_champion_gold"), 140, "yellow", "", "", List.of(), true));
        ranks.add(rank("pokemon_champion_platinum", defaultName("pokemon_champion_platinum"), defaultAcquisitionPath("pokemon_champion_platinum"), 160, "white", "", "", List.of(), true));
        ranks.add(rank("pokemon_champion_diamond", defaultName("pokemon_champion_diamond"), defaultAcquisitionPath("pokemon_champion_diamond"), 180, "aqua", "", "", List.of(), true));
        ranks.add(rank("title_blue_miracle_friend", defaultName("title_blue_miracle_friend"), defaultAcquisitionPath("title_blue_miracle_friend"), 230, "blue", "", "", List.of(), true));
        ranks.add(rank("title_mirror_hunter", defaultName("title_mirror_hunter"), defaultAcquisitionPath("title_mirror_hunter"), 240, "aqua", "", "", List.of(), true));
        ranks.add(rank("title_radiant_tracker", defaultName("title_radiant_tracker"), defaultAcquisitionPath("title_radiant_tracker"), 250, "aqua", "", "", List.of(), true));
        ranks.add(rank("title_crown_breaker", defaultName("title_crown_breaker"), defaultAcquisitionPath("title_crown_breaker"), 260, "gold", "", "", List.of(), true));
        ranks.add(rank("title_myth_awakener", defaultName("title_myth_awakener"), defaultAcquisitionPath("title_myth_awakener"), 270, "light_purple", "", "", List.of(), true));
        ranks.add(rank("title_master_appraiser", defaultName("title_master_appraiser"), defaultAcquisitionPath("title_master_appraiser"), 280, "yellow", "", "", List.of(), true));
        ranks.add(rank("title_radiant_heir", defaultName("title_radiant_heir"), defaultAcquisitionPath("title_radiant_heir"), 290, "white", "", "", List.of(), true));
        ranks.add(rank("title_golden_shell_king", defaultName("title_golden_shell_king"), defaultAcquisitionPath("title_golden_shell_king"), 300, "gold", "", "", List.of(), true));
        root.add("ranks", ranks);

        try {
            Files.createDirectories(RANK_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(RANK_FILE)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Failed to write default ranks.", exception);
        }
    }

    private static JsonObject rank(String id, String displayName, String acquisitionPath, int priority, String color, String chatPrefix, String tabPrefix, List<String> cosmetics, boolean allowUserSelect) {
        JsonObject rank = new JsonObject();
        rank.addProperty("id", id);
        rank.addProperty("displayName", displayName);
        rank.addProperty("acquisitionPath", acquisitionPath);
        rank.addProperty("priority", priority);
        rank.addProperty("color", color);
        rank.addProperty("iconTexture", RANK_ICON_TEXTURE_PREFIX + id + ".png");
        rank.addProperty("iconGlyph", iconGlyph(id));
        rank.addProperty("chatPrefix", chatPrefix);
        rank.addProperty("tabPrefix", tabPrefix);
        rank.addProperty("nameplateIcon", id);
        JsonArray grantCosmetics = new JsonArray();
        for (String cosmetic : cosmetics) {
            grantCosmetics.add(cosmetic);
        }
        rank.add("grantCosmetics", grantCosmetics);
        rank.addProperty("allowUserSelect", allowUserSelect);
        return rank;
    }

    private static List<RankData> defaultRanks() {
        return List.of(
                defaultRank("supporter", defaultName("supporter"), defaultAcquisitionPath("supporter"), 50, ChatFormatting.AQUA, "", "", List.of(), true),
                defaultRank("artist", defaultName("artist"), defaultAcquisitionPath("artist"), 60, ChatFormatting.LIGHT_PURPLE, "", "", List.of(), true),
                defaultRank("pokemon_champion_bronze", defaultName("pokemon_champion_bronze"), defaultAcquisitionPath("pokemon_champion_bronze"), 100, ChatFormatting.GOLD, "", "", List.of(), true),
                defaultRank("pokemon_champion_silver", defaultName("pokemon_champion_silver"), defaultAcquisitionPath("pokemon_champion_silver"), 120, ChatFormatting.GRAY, "", "", List.of(), true),
                defaultRank("pokemon_champion_gold", defaultName("pokemon_champion_gold"), defaultAcquisitionPath("pokemon_champion_gold"), 140, ChatFormatting.YELLOW, "", "", List.of(), true),
                defaultRank("pokemon_champion_platinum", defaultName("pokemon_champion_platinum"), defaultAcquisitionPath("pokemon_champion_platinum"), 160, ChatFormatting.WHITE, "", "", List.of(), true),
                defaultRank("pokemon_champion_diamond", defaultName("pokemon_champion_diamond"), defaultAcquisitionPath("pokemon_champion_diamond"), 180, ChatFormatting.AQUA, "", "", List.of(), true),
                defaultRank("title_blue_miracle_friend", defaultName("title_blue_miracle_friend"), defaultAcquisitionPath("title_blue_miracle_friend"), 230, ChatFormatting.BLUE, "", "", List.of(), true),
                defaultRank("title_mirror_hunter", defaultName("title_mirror_hunter"), defaultAcquisitionPath("title_mirror_hunter"), 240, ChatFormatting.AQUA, "", "", List.of(), true),
                defaultRank("title_radiant_tracker", defaultName("title_radiant_tracker"), defaultAcquisitionPath("title_radiant_tracker"), 250, ChatFormatting.AQUA, "", "", List.of(), true),
                defaultRank("title_crown_breaker", defaultName("title_crown_breaker"), defaultAcquisitionPath("title_crown_breaker"), 260, ChatFormatting.GOLD, "", "", List.of(), true),
                defaultRank("title_myth_awakener", defaultName("title_myth_awakener"), defaultAcquisitionPath("title_myth_awakener"), 270, ChatFormatting.LIGHT_PURPLE, "", "", List.of(), true),
                defaultRank("title_master_appraiser", defaultName("title_master_appraiser"), defaultAcquisitionPath("title_master_appraiser"), 280, ChatFormatting.YELLOW, "", "", List.of(), true),
                defaultRank("title_radiant_heir", defaultName("title_radiant_heir"), defaultAcquisitionPath("title_radiant_heir"), 290, ChatFormatting.WHITE, "", "", List.of(), true),
                defaultRank("title_golden_shell_king", defaultName("title_golden_shell_king"), defaultAcquisitionPath("title_golden_shell_king"), 300, ChatFormatting.GOLD, "", "", List.of(), true)
        );
    }

    private static RankData defaultRank(String id, String displayName, String acquisitionPath, int priority, ChatFormatting color, String chatPrefix, String tabPrefix, List<String> cosmetics, boolean allowUserSelect) {
        return new RankData(id, displayName, acquisitionPath, priority, color, RANK_ICON_TEXTURE_PREFIX + id + ".png", id, iconGlyph(id), chatPrefix, tabPrefix, cosmetics, allowUserSelect);
    }

    private static Component decoratedPrefix(RankData rank) {
        MutableComponent component = Component.empty();
        if (!rank.iconGlyph().isBlank()) {
            component.append(Component.literal(rank.iconGlyph()).withStyle(style -> style.withFont(RANK_ICON_FONT).withColor(0xFFFFFF)));
            component.append(Component.literal(" "));
        }
        return component;
    }

    private static String iconGlyph(String id) {
        return switch (id) {
            case "supporter" -> "\uE100";
            case "artist" -> "\uE101";
            case "pokemon_champion_bronze" -> "\uE102";
            case "pokemon_champion_silver" -> "\uE103";
            case "pokemon_champion_gold" -> "\uE104";
            case "pokemon_champion_platinum" -> "\uE105";
            case "pokemon_champion_diamond" -> "\uE106";
            case "title_radiant_tracker" -> "\uE107";
            case "title_mirror_hunter" -> "\uE108";
            case "title_crown_breaker" -> "\uE109";
            case "title_myth_awakener" -> "\uE10A";
            case "title_radiant_heir" -> "\uE10B";
            case "title_golden_shell_king" -> "\uE10C";
            case "title_blue_miracle_friend" -> "\uE10D";
            case "title_master_appraiser" -> "\uE10E";
            default -> "";
        };
    }

    private static String defaultName(String id) {
        return "yoiko_core.rank." + id + ".name";
    }

    private static String defaultAcquisitionPath(String id) {
        return switch (id) {
            case "supporter", "artist", "pokemon_champion_bronze", "pokemon_champion_silver",
                    "pokemon_champion_gold", "pokemon_champion_platinum", "pokemon_champion_diamond",
                    "title_radiant_tracker", "title_mirror_hunter", "title_crown_breaker",
                    "title_myth_awakener", "title_radiant_heir", "title_golden_shell_king",
                    "title_blue_miracle_friend", "title_master_appraiser" ->
                    "yoiko_core.rank." + id + ".acquisition";
            default -> "yoiko_core.acquisition.admin";
        };
    }

    private static boolean isDisabledDummy(String id) {
        return id != null && id.startsWith("dummy_");
    }

    private static void register(RankData rank) {
        RANKS.put(rank.id(), rank);
    }

    private static ChatFormatting color(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('#', ' ').trim();
        ChatFormatting formatting = ChatFormatting.getByName(normalized);
        if (formatting != null && formatting.isColor()) {
            return formatting;
        }
        return switch (normalized) {
            case "55ffff", "00ffff", "cyan" -> ChatFormatting.AQUA;
            case "ff55ff", "d36bff", "purple" -> ChatFormatting.LIGHT_PURPLE;
            case "ffd700", "ffaa00" -> ChatFormatting.GOLD;
            case "ffff55", "ffff00" -> ChatFormatting.YELLOW;
            case "ff5555", "ff0000" -> ChatFormatting.RED;
            case "5555ff", "0000ff" -> ChatFormatting.BLUE;
            default -> ChatFormatting.WHITE;
        };
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsBoolean() : fallback;
    }

    private static List<String> stringList(JsonObject object, String key) {
        List<String> values = new ArrayList<>();
        JsonArray array = object.getAsJsonArray(key);
        if (array != null) {
            for (JsonElement element : array) {
                values.add(element.getAsString());
            }
        }
        return values;
    }
}
