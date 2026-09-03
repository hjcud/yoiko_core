package com.yoiko.core.newspaper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.event.BreakingNewsSavedData;
import com.yoiko.core.mail.MailboxManager;
import com.yoiko.core.reward.RewardItemSerializer;
import com.yoiko.core.reward.YoikoResetClock;
import com.yoiko.core.treasure.TreasureRabbitVariant;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

/** Builds a brief, event-focused weekly issue from persisted server incidents. */
public final class WeeklyNewspaperManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get()
            .resolve(YoikoServerCore.MODID).resolve("weekly_newspaper.json");
    private static final Map<String, List<String>> TEMPLATES = new LinkedHashMap<>();

    private static boolean enabled = true;
    private static boolean autoPublish = true;
    private static List<ItemStack> rewards = List.of();
    private static long lastTick = Long.MIN_VALUE;

    private WeeklyNewspaperManager() {
    }

    public static void init() {
        reload();
    }

    public static void reload() {
        ensureFile();
        Map<String, List<String>> previousTemplates = Map.copyOf(TEMPLATES);
        List<ItemStack> previousRewards = copyRewards(rewards);
        boolean previousEnabled = enabled;
        boolean previousAutoPublish = autoPublish;
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            Config config = GSON.fromJson(reader, Config.class);
            loadValidated(config == null ? defaultConfig() : config);
        } catch (Exception exception) {
            YoikoServerCore.LOGGER.warn("Failed to load Yoiko weekly newspaper config; keeping the last known-good templates.", exception);
            if (previousTemplates.isEmpty()) {
                loadValidated(defaultConfig());
            } else {
                TEMPLATES.clear();
                TEMPLATES.putAll(previousTemplates);
                rewards = previousRewards;
                enabled = previousEnabled;
                autoPublish = previousAutoPublish;
            }
        }
    }

    public static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (lastTick != Long.MIN_VALUE && tick >= lastTick && tick - lastTick < 1_200L) {
            return;
        }
        lastTick = tick;
        ensureWeek(server, true);
    }

    public static void onPlayerLogin(ServerPlayer player) {
        ensureWeek(player.server, true);
        WeeklyNewspaperSavedData.get(player.server)
                .touchPlayer(player.getUUID(), player.getGameProfile().getName());
    }

    public static void recordRadiantRelic(ServerPlayer player) {
        if (!enabled) {
            return;
        }
        ensureWeek(player.server, true).recordRadiantRelic(
                player.getUUID(), player.getGameProfile().getName());
    }

    public static void recordPokemonGacha(ServerPlayer player, boolean shiny) {
        if (!enabled || !shiny) {
            return;
        }
        ensureWeek(player.server, true).recordShinyGacha(
                player.getUUID(), player.getGameProfile().getName());
    }

    public static void recordTreasureRabbit(ServerPlayer player, TreasureRabbitVariant variant) {
        if (!enabled) {
            return;
        }
        ensureWeek(player.server, true).recordTreasureRabbit(
                player.getUUID(), player.getGameProfile().getName(),
                variant);
    }

    public static void recordMarketSale(MinecraftServer server, UUID sellerId,
                                        String sellerName, long revenue) {
        if (!enabled || sellerId == null || revenue <= 0L) {
            return;
        }
        ensureWeek(server, true).recordMarketSale(sellerId, sellerName, revenue);
    }

    public static void recordBreakingNews(MinecraftServer server,
                                          BreakingNewsSavedData.CompletedIncident incident) {
        if (incident == null) {
            return;
        }
        ensureWeek(server, true).recordBreakingNews(new WeeklyNewspaperSavedData.BreakingNewsReport(
                incident.type(), incident.startedAt(), incident.endedAt(), incident.biomeId(),
                incident.directionId(), incident.distanceId(), incident.targetSpecies(), incident.goal(), incident.spawned(),
                incident.primaryCount(), incident.secondaryCount(), incident.tertiaryCount(),
                incident.rewardCount(), incident.participantCount()));
    }

    public static Issue preview(MinecraftServer server) {
        WeeklyNewspaperSavedData data = ensureWeek(server, true);
        touchOnlinePlayers(server, data);
        return buildIssue(data.snapshot());
    }

    /** Sends a deliberately manual edition from current counters without resetting the week. */
    public static PublicationResult sendCurrent(MinecraftServer server) {
        WeeklyNewspaperSavedData data = ensureWeek(server, true);
        touchOnlinePlayers(server, data);
        WeeklyNewspaperSavedData.Snapshot snapshot = data.snapshot();
        String issueId = "weekly_newspaper:manual:" + snapshot.weekKey() + ":" + UUID.randomUUID();
        return publish(server, snapshot, issueId);
    }

    public static Status status(MinecraftServer server) {
        WeeklyNewspaperSavedData data = ensureWeek(server, true);
        WeeklyNewspaperSavedData.Snapshot snapshot = data.snapshot();
        Totals totals = totals(snapshot);
        return new Status(enabled, autoPublish, snapshot.weekKey(), snapshot.knownPlayers().size(),
                totals.radiantRelics, totals.shinyGacha, totals.goldenRabbits,
                totals.radiantRabbits, totals.mirrorRabbits, totals.crownRabbits,
                totals.marketSales, totals.marketRevenue,
                data.lastAutoPublishedWeekKey());
    }

    private static WeeklyNewspaperSavedData ensureWeek(MinecraftServer server, boolean allowAutoPublish) {
        WeeklyNewspaperSavedData data = WeeklyNewspaperSavedData.get(server);
        LocalDate weekStart = weekStart(System.currentTimeMillis());
        String weekKey = weekStart.toString();
        if (data.currentWeekKey().isBlank()) {
            data.beginWeek(weekKey, weekStartMillis(weekStart));
            return data;
        }
        if (weekKey.equals(data.currentWeekKey())) {
            return data;
        }

        touchOnlinePlayers(server, data);
        WeeklyNewspaperSavedData.Snapshot previous = data.snapshot();
        if (allowAutoPublish && enabled && autoPublish
                && !previous.weekKey().isBlank()
                && !previous.weekKey().equals(data.lastAutoPublishedWeekKey())) {
            publish(server, previous, "weekly_newspaper:auto:" + previous.weekKey());
            data.setLastAutoPublishedWeekKey(previous.weekKey());
        }
        data.beginWeek(weekKey, weekStartMillis(weekStart));
        return data;
    }

    private static void touchOnlinePlayers(MinecraftServer server, WeeklyNewspaperSavedData data) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            data.touchPlayer(player.getUUID(), player.getGameProfile().getName());
        }
    }

    private static PublicationResult publish(MinecraftServer server,
                                             WeeklyNewspaperSavedData.Snapshot snapshot,
                                             String issueId) {
        Issue issue = buildIssue(snapshot);
        int delivered = 0;
        List<ItemStack> issueRewards = copyRewards(rewards);
        if (!issueRewards.isEmpty()) {
            for (Map.Entry<UUID, String> recipient : snapshot.knownPlayers().entrySet()) {
                if (MailboxManager.sendSystemMail(server, recipient.getKey(), "weekly_reward", issueId,
                        "yoiko_core.mail.newspaper.reward.title",
                        "yoiko_core.mail.newspaper.reward.message", copyRewards(issueRewards))) {
                    delivered++;
                }
            }
        }
        WeeklyNewspaperSavedData.get(server).archiveIssue(new WeeklyNewspaperSavedData.ArchivedIssue(
                issueId, snapshot.weekKey(), System.currentTimeMillis(), issue.title(), issue.message()));
        YoikoServerCore.LOGGER.info(
                "[WeeklyNewspaper] issue={} week={} recipients={} delivered={} radiantRelics={} shinyGacha={} rabbits={} marketSales={}",
                issueId, snapshot.weekKey(), snapshot.knownPlayers().size(), delivered,
                issue.totals().radiantRelics(), issue.totals().shinyGacha(),
                issue.totals().goldenRabbits() + issue.totals().radiantRabbits()
                        + issue.totals().mirrorRabbits() + issue.totals().crownRabbits(),
                issue.totals().marketSales());
        return new PublicationResult(snapshot.weekKey(), issueId,
                snapshot.knownPlayers().size(), delivered);
    }

    private static Issue buildIssue(WeeklyNewspaperSavedData.Snapshot snapshot) {
        LocalDate start = parseWeek(snapshot.weekKey());
        LocalDate end = start.plusDays(6L);
        List<WeeklyNewspaperSavedData.BreakingNewsReport> incidents = sortedIncidents(snapshot);
        List<RenderedLine> lines = new ArrayList<>();
        if (incidents.isEmpty()) {
            lines.add(render("yoiko_core.newspaper.empty.headline"));
            lines.add(render("yoiko_core.newspaper.empty.body"));
        } else {
            incidents.stream().limit(4L).forEach(report -> {
                lines.add(breakingNewsHeadline(report));
                lines.add(breakingNewsArticle(report));
            });
        }

        String message = lines.stream().map(RenderedLine::encoded)
                .collect(java.util.stream.Collectors.joining("\n"));
        String title = encoded("yoiko_core.mail.newspaper.title", start.toString(), end.toString());
        return new Issue(title, message, lines.stream().map(RenderedLine::component).toList(),
                totals(snapshot), incidents.stream().limit(8L)
                        .map(WeeklyNewspaperManager::incidentStory).toList());
    }

    private static List<WeeklyNewspaperSavedData.BreakingNewsReport> sortedIncidents(
            WeeklyNewspaperSavedData.Snapshot snapshot) {
        return snapshot.breakingNews().stream()
                .sorted(Comparator.comparingLong(
                        WeeklyNewspaperSavedData.BreakingNewsReport::endedAt).reversed())
                .toList();
    }

    private static Story incidentStory(WeeklyNewspaperSavedData.BreakingNewsReport report) {
        RenderedLine headline = breakingNewsHeadline(report);
        RenderedLine article = breakingNewsArticle(report);
        long timestamp = report.endedAt() > 0L ? report.endedAt() : report.startedAt();
        String date = YoikoResetClock.dailyPeriodDate(Math.max(0L, timestamp))
                .format(DateTimeFormatter.ofPattern("MM.dd", Locale.ROOT));
        return new Story(report.type(), headline.encoded(), article.encoded(), date,
                "breaking_" + sanitize(report.type()));
    }

    private static RenderedLine breakingNewsHeadline(WeeklyNewspaperSavedData.BreakingNewsReport report) {
        if ("treasure_rabbit_swarm".equals(report.type())) {
            return render("yoiko_core.newspaper.headline.rabbits");
        }
        if ("fishing_festival".equals(report.type())) {
            return render("yoiko_core.newspaper.headline.fishing");
        }
        if ("pokemon_capture_goal".equals(report.type())) {
            return render("yoiko_core.newspaper.headline.capture", speciesName(report.targetSpecies()));
        }
        if ("mass_outbreak".equals(report.type())) {
            return render("yoiko_core.newspaper.headline.outbreak", speciesName(report.targetSpecies()));
        }
        return render("yoiko_core.newspaper.headline.unknown");
    }

    private static RenderedLine breakingNewsArticle(WeeklyNewspaperSavedData.BreakingNewsReport report) {
        if ("treasure_rabbit_swarm".equals(report.type())) {
            return render("yoiko_core.newspaper.breaking.rabbits",
                    report.spawned(), report.primaryCount(), report.tertiaryCount(),
                    report.participantCount());
        }
        if ("fishing_festival".equals(report.type())) {
            return render("yoiko_core.newspaper.breaking.fishing",
                    report.primaryCount(), report.secondaryCount(), report.tertiaryCount(),
                    report.rewardCount(), report.participantCount());
        }
        if ("pokemon_capture_goal".equals(report.type())) {
            return render("yoiko_core.newspaper.breaking.capture",
                    speciesName(report.targetSpecies()), report.primaryCount(), report.goal(), report.participantCount());
        }
        if ("mass_outbreak".equals(report.type())) {
            return render("yoiko_core.newspaper.breaking.outbreak",
                    speciesName(report.targetSpecies()), report.primaryCount(), report.tertiaryCount(), report.secondaryCount());
        }
        return render("yoiko_core.newspaper.breaking.unknown");
    }

    private static Component speciesName(String speciesId) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(speciesId);
        com.cobblemon.mod.common.pokemon.Species species = id == null ? null
                : com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByIdentifier(id);
        return species == null ? Component.literal(speciesId) : species.getTranslatedName();
    }

    private static RenderedLine radiantArticle(WeeklyNewspaperSavedData.Snapshot snapshot) {
        List<PlayerCount> values = counts(snapshot, WeeklyNewspaperSavedData.PlayerStats::radiantRelics);
        int total = values.stream().mapToInt(PlayerCount::count).sum();
        if (values.isEmpty()) {
            return render(select("radiant.none", snapshot.weekKey()));
        }
        if (values.size() == 1 && total == 1) {
            return render(select("radiant.single", snapshot.weekKey()), values.get(0).name());
        }
        if (values.size() == 1) {
            return render(select("radiant.repeat", snapshot.weekKey()), values.get(0).name(), total);
        }
        int best = values.get(0).count();
        List<PlayerCount> leaders = values.stream().filter(value -> value.count() == best).toList();
        if (leaders.size() == 1) {
            return render(select("radiant.leader", snapshot.weekKey()), leaders.get(0).name(), best,
                    total, values.size());
        }
        return render(select("radiant.tie", snapshot.weekKey()), joinedNames(leaders),
                leaders.size(), best, total);
    }

    private static RenderedLine shinyArticle(WeeklyNewspaperSavedData.Snapshot snapshot) {
        List<PlayerCount> values = counts(snapshot, WeeklyNewspaperSavedData.PlayerStats::shinyGacha);
        int total = values.stream().mapToInt(PlayerCount::count).sum();
        if (values.isEmpty()) {
            return render(select("shiny.none", snapshot.weekKey()));
        }
        if (values.size() == 1 && total == 1) {
            return render(select("shiny.single", snapshot.weekKey()), values.get(0).name());
        }
        int best = values.get(0).count();
        List<PlayerCount> leaders = values.stream().filter(value -> value.count() == best).toList();
        if (leaders.size() == 1) {
            return render(select("shiny.leader", snapshot.weekKey()), leaders.get(0).name(), best,
                    total, values.size());
        }
        return render(select("shiny.tie", snapshot.weekKey()), joinedNames(leaders),
                leaders.size(), best, total);
    }

    private static RenderedLine rabbitArticle(WeeklyNewspaperSavedData.Snapshot snapshot) {
        List<PlayerCount> values = counts(snapshot, WeeklyNewspaperSavedData.PlayerStats::totalRabbits);
        Totals totals = totals(snapshot);
        int total = totals.goldenRabbits + totals.radiantRabbits
                + totals.mirrorRabbits + totals.crownRabbits;
        if (values.isEmpty()) {
            return render(select("rabbit.none", snapshot.weekKey()));
        }
        if (total == 1) {
            String key = totals.crownRabbits == 1 ? "rabbit.single_crown"
                    : totals.mirrorRabbits == 1 ? "rabbit.single_mirror"
                    : totals.radiantRabbits == 1 ? "rabbit.single_radiant" : "rabbit.single_golden";
            return render(select(key, snapshot.weekKey()), values.get(0).name());
        }
        int best = values.get(0).count();
        List<PlayerCount> leaders = values.stream().filter(value -> value.count() == best).toList();
        if (leaders.size() == 1) {
            return render(select("rabbit.leader", snapshot.weekKey()), leaders.get(0).name(), best,
                    total, totals.radiantRabbits, totals.mirrorRabbits, totals.crownRabbits);
        }
        return render(select("rabbit.tie", snapshot.weekKey()), joinedNames(leaders),
                leaders.size(), best, total, totals.radiantRabbits,
                totals.mirrorRabbits, totals.crownRabbits);
    }

    private static RenderedLine marketArticle(WeeklyNewspaperSavedData.Snapshot snapshot) {
        List<PlayerCount> values = counts(snapshot, WeeklyNewspaperSavedData.PlayerStats::marketSales);
        Totals totals = totals(snapshot);
        if (values.isEmpty()) {
            return render(select("market.none", snapshot.weekKey()));
        }
        if (values.size() == 1) {
            WeeklyNewspaperSavedData.PlayerStats stats = statsByName(snapshot, values.get(0).name());
            return render(select("market.single", snapshot.weekKey()), values.get(0).name(),
                    stats.marketSales(), stats.marketRevenue());
        }
        int best = values.get(0).count();
        List<PlayerCount> leaders = values.stream().filter(value -> value.count() == best).toList();
        if (leaders.size() == 1) {
            WeeklyNewspaperSavedData.PlayerStats stats = statsByName(snapshot, leaders.get(0).name());
            return render(select("market.leader", snapshot.weekKey()), leaders.get(0).name(), best,
                    stats.marketRevenue(), totals.marketSales, totals.marketRevenue);
        }
        return render(select("market.tie", snapshot.weekKey()), joinedNames(leaders),
                leaders.size(), best, totals.marketSales, totals.marketRevenue);
    }

    private static WeeklyNewspaperSavedData.PlayerStats statsByName(
            WeeklyNewspaperSavedData.Snapshot snapshot, String name) {
        return snapshot.playerStats().values().stream()
                .filter(stats -> safeName(stats.name()).equals(name))
                .findFirst()
                .orElse(new WeeklyNewspaperSavedData.PlayerStats(name, 0, 0, 0, 0, 0, 0, 0, 0L));
    }

    private static List<PlayerCount> counts(WeeklyNewspaperSavedData.Snapshot snapshot,
                                            ToIntFunction<WeeklyNewspaperSavedData.PlayerStats> extractor) {
        return snapshot.playerStats().values().stream()
                .map(stats -> new PlayerCount(safeName(stats.name()), Math.max(0, extractor.applyAsInt(stats))))
                .filter(value -> value.count() > 0)
                .sorted(Comparator.comparingInt(PlayerCount::count).reversed()
                        .thenComparing(PlayerCount::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static Totals totals(WeeklyNewspaperSavedData.Snapshot snapshot) {
        int radiantRelics = sum(snapshot, WeeklyNewspaperSavedData.PlayerStats::radiantRelics);
        int shinyGacha = sum(snapshot, WeeklyNewspaperSavedData.PlayerStats::shinyGacha);
        int goldenRabbits = sum(snapshot, WeeklyNewspaperSavedData.PlayerStats::goldenRabbits);
        int radiantRabbits = sum(snapshot, WeeklyNewspaperSavedData.PlayerStats::radiantRabbits);
        int mirrorRabbits = sum(snapshot, WeeklyNewspaperSavedData.PlayerStats::mirrorRabbits);
        int crownRabbits = sum(snapshot, WeeklyNewspaperSavedData.PlayerStats::crownRabbits);
        int marketSales = sum(snapshot, WeeklyNewspaperSavedData.PlayerStats::marketSales);
        long marketRevenue = snapshot.playerStats().values().stream()
                .mapToLong(WeeklyNewspaperSavedData.PlayerStats::marketRevenue)
                .reduce(0L, WeeklyNewspaperManager::saturatingAdd);
        return new Totals(radiantRelics, shinyGacha, goldenRabbits,
                radiantRabbits, mirrorRabbits, crownRabbits, marketSales, marketRevenue);
    }

    private static int sum(WeeklyNewspaperSavedData.Snapshot snapshot,
                           ToIntFunction<WeeklyNewspaperSavedData.PlayerStats> extractor) {
        long total = snapshot.playerStats().values().stream()
                .mapToLong(stats -> Math.max(0, extractor.applyAsInt(stats))).sum();
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static String joinedNames(List<PlayerCount> values) {
        String visible = values.stream().limit(3L).map(PlayerCount::name)
                .collect(java.util.stream.Collectors.joining(", "));
        int hidden = Math.max(0, values.size() - 3);
        return hidden == 0 ? visible : visible + " +" + hidden;
    }

    private static String safeName(String value) {
        return value == null || value.isBlank() ? "?" : sanitize(value);
    }

    private static String select(String caseId, String weekKey) {
        List<String> candidates = TEMPLATES.get(caseId);
        if (candidates == null || candidates.isEmpty()) {
            return "yoiko_core.newspaper.template.missing";
        }
        int index = Math.floorMod(Objects.hash(caseId, weekKey), candidates.size());
        return candidates.get(index);
    }

    private static RenderedLine render(String template, Object... arguments) {
        String[] safeArguments = java.util.Arrays.stream(arguments)
                .map(WeeklyNewspaperManager::encodedArgument)
                .toArray(String[]::new);
        if (template != null && template.startsWith("yoiko_core.")) {
            return new RenderedLine(encoded(template, (Object[]) safeArguments),
                    Component.translatable(template, arguments));
        }
        String literal = template == null ? "" : template;
        for (int index = 0; index < safeArguments.length; index++) {
            literal = literal.replace("{" + index + "}", safeArguments[index]);
        }
        return new RenderedLine(literal, Component.literal(literal));
    }

    private static String encodedArgument(Object argument) {
        if (argument instanceof Component component) {
            if (component.getContents() instanceof TranslatableContents translatable
                    && component.getSiblings().isEmpty()) {
                return "@" + translatable.getKey();
            }
            return sanitize(component.getString());
        }
        return sanitize(String.valueOf(argument));
    }

    private static String encoded(String key, Object... arguments) {
        StringBuilder builder = new StringBuilder(key);
        for (Object argument : arguments) {
            builder.append('|').append(sanitize(String.valueOf(argument)));
        }
        return builder.toString();
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    private static LocalDate weekStart(long millis) {
        return YoikoResetClock.dailyPeriodDate(millis)
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }

    private static long weekStartMillis(LocalDate date) {
        ZoneId zone = YoikoResetClock.zone();
        return ZonedDateTime.of(date.atTime(com.yoiko.core.config.YoikoCommonConfig.DAILY_RESET_HOUR.get(), 0), zone)
                .toInstant().toEpochMilli();
    }

    private static LocalDate parseWeek(String weekKey) {
        try {
            return LocalDate.parse(weekKey);
        } catch (Exception ignored) {
            return weekStart(System.currentTimeMillis());
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static List<ItemStack> copyRewards(List<ItemStack> source) {
        return source == null ? List.of() : source.stream().map(ItemStack::copy).toList();
    }

    private static void loadValidated(Config config) {
        Config defaults = defaultConfig();
        Map<String, List<String>> loadedTemplates = new LinkedHashMap<>(defaults.templates);
        if (config.templates != null) {
            config.templates.forEach((key, values) -> {
                if (key == null || key.isBlank() || values == null) {
                    return;
                }
                List<String> usable = values.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .limit(20L)
                        .toList();
                if (!usable.isEmpty()) {
                    loadedTemplates.put(key, usable);
                }
            });
        }
        JsonArray rewardArray = new JsonArray();
        if (config.rewards != null) {
            config.rewards.forEach(rewardArray::add);
        }
        List<ItemStack> loadedRewards = RewardItemSerializer.listFromJson(rewardArray);
        if (loadedRewards.size() > 12) {
            throw new IllegalArgumentException("Weekly newspaper rewards may use at most 12 item stacks.");
        }
        enabled = config.enabled;
        autoPublish = config.autoPublish;
        rewards = loadedRewards;
        TEMPLATES.clear();
        TEMPLATES.putAll(loadedTemplates);
    }

    private static void ensureFile() {
        if (Files.exists(CONFIG_FILE)) {
            return;
        }
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(defaultConfig(), writer);
            }
        } catch (Exception exception) {
            YoikoServerCore.LOGGER.warn("Failed to create Yoiko weekly newspaper config.", exception);
        }
    }

    private static Config defaultConfig() {
        Config config = new Config();
        config.rewards = List.of(reward("yoiko_core:relic_scrap", 10),
                reward("yoiko_core:all_pokemon_gacha_ticket", 1));
        Map<String, List<String>> templates = new LinkedHashMap<>();
        templates.put("lead", List.of(
                "yoiko_core.newspaper.lead.1", "yoiko_core.newspaper.lead.2"));
        templates.put("closing", List.of(
                "yoiko_core.newspaper.closing.1", "yoiko_core.newspaper.closing.2"));
        templates.put("radiant.none", List.of(
                "yoiko_core.newspaper.radiant.none.1", "yoiko_core.newspaper.radiant.none.2"));
        templates.put("radiant.single", List.of(
                "yoiko_core.newspaper.radiant.single.1", "yoiko_core.newspaper.radiant.single.2"));
        templates.put("radiant.repeat", List.of(
                "yoiko_core.newspaper.radiant.repeat.1", "yoiko_core.newspaper.radiant.repeat.2"));
        templates.put("radiant.leader", List.of(
                "yoiko_core.newspaper.radiant.leader.1", "yoiko_core.newspaper.radiant.leader.2"));
        templates.put("radiant.tie", List.of(
                "yoiko_core.newspaper.radiant.tie.1", "yoiko_core.newspaper.radiant.tie.2"));
        templates.put("shiny.none", List.of(
                "yoiko_core.newspaper.shiny.none.1", "yoiko_core.newspaper.shiny.none.2"));
        templates.put("shiny.single", List.of(
                "yoiko_core.newspaper.shiny.single.1", "yoiko_core.newspaper.shiny.single.2"));
        templates.put("shiny.leader", List.of(
                "yoiko_core.newspaper.shiny.leader.1", "yoiko_core.newspaper.shiny.leader.2"));
        templates.put("shiny.tie", List.of(
                "yoiko_core.newspaper.shiny.tie.1", "yoiko_core.newspaper.shiny.tie.2"));
        templates.put("rabbit.none", List.of(
                "yoiko_core.newspaper.rabbit.none.1", "yoiko_core.newspaper.rabbit.none.2"));
        templates.put("rabbit.single_golden", List.of(
                "yoiko_core.newspaper.rabbit.single_golden.1", "yoiko_core.newspaper.rabbit.single_golden.2"));
        templates.put("rabbit.single_radiant", List.of(
                "yoiko_core.newspaper.rabbit.single_radiant.1", "yoiko_core.newspaper.rabbit.single_radiant.2"));
        templates.put("rabbit.single_mirror", List.of(
                "yoiko_core.newspaper.rabbit.single_mirror.1", "yoiko_core.newspaper.rabbit.single_mirror.2"));
        templates.put("rabbit.single_crown", List.of(
                "yoiko_core.newspaper.rabbit.single_crown.1", "yoiko_core.newspaper.rabbit.single_crown.2"));
        templates.put("rabbit.leader", List.of(
                "yoiko_core.newspaper.rabbit.leader.1", "yoiko_core.newspaper.rabbit.leader.2"));
        templates.put("rabbit.tie", List.of(
                "yoiko_core.newspaper.rabbit.tie.1", "yoiko_core.newspaper.rabbit.tie.2"));
        templates.put("market.none", List.of(
                "yoiko_core.newspaper.market.none.1", "yoiko_core.newspaper.market.none.2"));
        templates.put("market.single", List.of(
                "yoiko_core.newspaper.market.single.1", "yoiko_core.newspaper.market.single.2"));
        templates.put("market.leader", List.of(
                "yoiko_core.newspaper.market.leader.1", "yoiko_core.newspaper.market.leader.2"));
        templates.put("market.tie", List.of(
                "yoiko_core.newspaper.market.tie.1", "yoiko_core.newspaper.market.tie.2"));
        config.templates = templates;
        return config;
    }

    private static JsonObject reward(String item, int count) {
        JsonObject reward = new JsonObject();
        reward.addProperty("item", item);
        reward.addProperty("count", count);
        return reward;
    }

    private static final class Config {
        private int schemaVersion = 1;
        private boolean enabled = true;
        private boolean autoPublish = true;
        private List<JsonObject> rewards = List.of();
        private Map<String, List<String>> templates = Map.of();
    }

    private record RenderedLine(String encoded, Component component) {
    }

    private record PlayerCount(String name, int count) {
    }

    public record Totals(int radiantRelics, int shinyGacha, int goldenRabbits,
                         int radiantRabbits, int mirrorRabbits, int crownRabbits,
                         int marketSales, long marketRevenue) {
    }

    public record Story(String kind, String title, String body, String meta, String imageId) {
    }

    public record Issue(String title, String message, List<Component> previewLines, Totals totals,
                        List<Story> stories) {
    }

    public record PublicationResult(String weekKey, String issueId,
                                    int recipients, int delivered) {
    }

    public record Status(boolean enabled, boolean autoPublish, String weekKey, int knownPlayers,
                         int radiantRelics, int shinyGacha, int goldenRabbits,
                         int radiantRabbits, int mirrorRabbits, int crownRabbits,
                         int marketSales, long marketRevenue,
                         String lastAutoPublishedWeekKey) {
    }
}
