package com.yoiko.core.event;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.reward.YoikoResetClock;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

/** Configurable, server-authoritative schedule for global wild shiny-rate events. */
public final class DailyServerEventManager {
    private static final int CONFIG_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get()
            .resolve(YoikoServerCore.MODID).resolve("server_events.json");
    private static final EventDefinition NONE = EventDefinition.none();
    private static final List<EventDefinition> EVENTS = new ArrayList<>();

    private static boolean enabled = true;
    private static boolean broadcastOnLogin = true;
    private static boolean announceChanges = true;
    private static EventDefinition cachedActive = NONE;
    private static long lastTick = Long.MIN_VALUE;

    private DailyServerEventManager() {
    }

    public static void init() {
        reload();
    }

    public static void reload() {
        ensureFile();
        List<EventDefinition> previous = List.copyOf(EVENTS);
        boolean previousEnabled = enabled;
        boolean previousLogin = broadcastOnLogin;
        boolean previousAnnounce = announceChanges;
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            Config config = GSON.fromJson(reader, Config.class);
            if (config == null || config.schemaVersion != CONFIG_VERSION) {
                config = defaultConfig();
                writeConfig(config);
                YoikoServerCore.LOGGER.info(
                        "Reset Yoiko server event config to schema {}. Species/type and Treasure Rabbit rules were removed.",
                        CONFIG_VERSION);
            }
            loadValidated(config);
        } catch (Exception exception) {
            YoikoServerCore.LOGGER.warn(
                    "Failed to load Yoiko server event config; keeping the last known-good schedule.", exception);
            if (previous.isEmpty()) {
                loadValidated(defaultConfig());
            } else {
                EVENTS.clear();
                EVENTS.addAll(previous);
                enabled = previousEnabled;
                broadcastOnLogin = previousLogin;
                announceChanges = previousAnnounce;
            }
        }
    }

    public static void refreshActive(MinecraftServer server) {
        DailyServerEventSavedData saved = DailyServerEventSavedData.get(server);
        LocalDate today = YoikoResetClock.dailyPeriodDate(System.currentTimeMillis());
        EventDefinition selected = saved.eventId().isBlank() ? NONE : find(saved.eventId());
        if (!today.toString().equals(saved.periodDate()) || selected == null) {
            selected = scheduledFor(today);
            saved.select(today.toString(), selected.id, false);
        }
        cachedActive = enabled ? selected : NONE;
    }

    public static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (lastTick != Long.MIN_VALUE && tick >= lastTick && tick - lastTick < 1_200L) {
            return;
        }
        lastTick = tick;
        ensureCurrent(server, true);
    }

    public static void onPlayerLogin(ServerPlayer player) {
        EventDefinition active = ensureCurrent(player.server, true);
        if (enabled && broadcastOnLogin && active != NONE) {
            player.sendSystemMessage(summary(active).copy().withStyle(ChatFormatting.AQUA));
        }
    }

    /** The Pokemon argument is retained for the Cobblemon hook; all configured events now affect every species. */
    public static double shinyMultiplier(MinecraftServer server, Pokemon pokemon) {
        EventDefinition active = server == null ? cachedActive : ensureCurrent(server, false);
        if (!enabled || active == NONE || active.shinyMultiplier <= 1.0D || pokemon == null) {
            return 1.0D;
        }
        return active.shinyMultiplier;
    }

    public static List<String> ids() {
        return EVENTS.stream().map(event -> event.id).sorted().toList();
    }

    public static boolean force(MinecraftServer server, String eventId) {
        EventDefinition selected = find(eventId);
        if (selected == null) {
            return false;
        }
        LocalDate today = YoikoResetClock.dailyPeriodDate(System.currentTimeMillis());
        DailyServerEventSavedData.get(server).select(today.toString(), selected.id, true);
        cachedActive = enabled ? selected : NONE;
        announce(server, selected);
        return true;
    }

    public static EventStatus clearOverride(MinecraftServer server) {
        LocalDate today = YoikoResetClock.dailyPeriodDate(System.currentTimeMillis());
        EventDefinition selected = scheduledFor(today);
        DailyServerEventSavedData.get(server).select(today.toString(), selected.id, false);
        cachedActive = enabled ? selected : NONE;
        announce(server, selected);
        return status(server);
    }

    public static EventStatus status(MinecraftServer server) {
        EventDefinition active = ensureCurrent(server, false);
        DailyServerEventSavedData saved = DailyServerEventSavedData.get(server);
        return new EventStatus(
                enabled,
                saved.periodDate(),
                active.id,
                active.nameKey,
                active.shinyMultiplier,
                saved.forced()
        );
    }

    /** Returns the next deterministic scheduled event without mutating the active event. */
    public static ScheduledEvent nextScheduledEvent(int lookaheadDays) {
        LocalDate today = YoikoResetClock.dailyPeriodDate(System.currentTimeMillis());
        for (int offset = 1; offset <= Math.max(1, lookaheadDays); offset++) {
            LocalDate date = today.plusDays(offset);
            EventDefinition event = scheduledFor(date);
            if (event != NONE) {
                return new ScheduledEvent(date.toString(), event.id, event.nameKey, event.shinyMultiplier);
            }
        }
        return new ScheduledEvent("", "", "", 1.0D);
    }

    public static Component summary(EventStatus status) {
        if (!status.enabled() || status.id().isBlank()) {
            return Component.translatable("yoiko_core.event.status.none");
        }
        return summary(new EventDefinition(status.id(), status.nameKey(), 1,
                List.of(), status.shinyMultiplier()));
    }

    private static EventDefinition ensureCurrent(MinecraftServer server, boolean announceChange) {
        if (server == null) {
            return cachedActive;
        }
        DailyServerEventSavedData saved = DailyServerEventSavedData.get(server);
        LocalDate today = YoikoResetClock.dailyPeriodDate(System.currentTimeMillis());
        EventDefinition selected = saved.eventId().isBlank() ? NONE : find(saved.eventId());
        boolean changed = !today.toString().equals(saved.periodDate()) || selected == null;
        if (changed) {
            selected = scheduledFor(today);
            saved.select(today.toString(), selected.id, false);
        }
        cachedActive = enabled ? selected : NONE;
        if (changed && announceChange && announceChanges && cachedActive != NONE) {
            announce(server, cachedActive);
        }
        return cachedActive;
    }

    private static void announce(MinecraftServer server, EventDefinition event) {
        if (!enabled || event == NONE || !announceChanges) {
            return;
        }
        server.getPlayerList().broadcastSystemMessage(
                summary(event).copy().withStyle(ChatFormatting.AQUA), false);
    }

    private static Component summary(EventDefinition event) {
        return Component.translatable("yoiko_core.event.broadcast.shiny", Component.translatable(event.nameKey),
                format(event.shinyMultiplier));
    }

    private static String format(double value) {
        return value == Math.rint(value)
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

    private static EventDefinition scheduledFor(LocalDate date) {
        if (!enabled) {
            return NONE;
        }
        List<EventDefinition> eligible = EVENTS.stream()
                .filter(event -> event.days.contains("ALL") || event.days.contains(date.getDayOfWeek().name()))
                .sorted(Comparator.comparing(event -> event.id))
                .toList();
        int totalWeight = eligible.stream().mapToInt(event -> event.weight).sum();
        if (totalWeight <= 0) {
            return NONE;
        }
        Random random = new Random(date.toEpochDay() * 0x9E3779B97F4A7C15L ^ 0x594F494B4F4CL);
        int roll = random.nextInt(totalWeight);
        for (EventDefinition event : eligible) {
            roll -= event.weight;
            if (roll < 0) {
                return event;
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    private static EventDefinition find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return EVENTS.stream().filter(event -> event.id.equals(id)).findFirst().orElse(null);
    }

    private static void loadValidated(Config config) {
        List<EventDefinition> validated = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        if (config.events != null) {
            for (EventDefinition raw : config.events) {
                EventDefinition event = raw == null ? null : raw.validated();
                if (event == null || !ids.add(event.id)) {
                    continue;
                }
                validated.add(event);
            }
        }
        if (validated.isEmpty()) {
            throw new IllegalArgumentException("At least one valid server event is required.");
        }
        enabled = config.enabled;
        broadcastOnLogin = config.broadcastOnLogin;
        announceChanges = config.announceChanges;
        EVENTS.clear();
        EVENTS.addAll(validated);
    }

    private static void ensureFile() {
        if (Files.exists(CONFIG_FILE)) {
            return;
        }
        writeConfig(defaultConfig());
    }

    private static void writeConfig(Config config) {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception exception) {
            YoikoServerCore.LOGGER.warn("Failed to write Yoiko server event config.", exception);
        }
    }

    private static Config defaultConfig() {
        Config config = new Config();
        config.events = List.of(
                event("midweek_shimmer", "yoiko_core.event.midweek_shimmer", DayOfWeek.WEDNESDAY, 1.5D),
                event("weekend_sparkle", "yoiko_core.event.weekend_sparkle", DayOfWeek.SATURDAY, 2.0D)
        );
        return config;
    }

    private static EventDefinition event(String id, String nameKey, DayOfWeek day, double shinyMultiplier) {
        return new EventDefinition(id, nameKey, 1, List.of(day.name()), shinyMultiplier);
    }

    private static List<String> normalizedDays(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static final class Config {
        private int schemaVersion = CONFIG_VERSION;
        private boolean enabled = true;
        private boolean broadcastOnLogin = true;
        private boolean announceChanges = true;
        private List<EventDefinition> events = List.of();
    }

    private static final class EventDefinition {
        private String id = "";
        private String nameKey = "yoiko_core.event.unnamed";
        private int weight = 1;
        private List<String> days = List.of("ALL");
        private double shinyMultiplier = 1.0D;

        private EventDefinition() {
        }

        private EventDefinition(String id, String nameKey, int weight, List<String> days,
                                double shinyMultiplier) {
            this.id = id;
            this.nameKey = nameKey;
            this.weight = weight;
            this.days = days;
            this.shinyMultiplier = shinyMultiplier;
        }

        private static EventDefinition none() {
            return new EventDefinition("", "yoiko_core.event.none", 0, List.of(), 1.0D);
        }

        private EventDefinition validated() {
            String safeId = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            if (!safeId.matches("[a-z0-9_.-]{1,64}") || weight <= 0) {
                return null;
            }
            List<String> safeDays = normalizedDays(days);
            boolean validDay = safeDays.stream().allMatch(day -> "ALL".equals(day)
                    || java.util.Arrays.stream(DayOfWeek.values()).anyMatch(value -> value.name().equals(day)));
            if (safeDays.isEmpty() || !validDay) {
                return null;
            }
            return new EventDefinition(
                    safeId,
                    nameKey == null || nameKey.isBlank() ? "yoiko_core.event.unnamed" : nameKey,
                    Math.min(weight, 1_000_000),
                    safeDays,
                    Math.max(1.0D, Math.min(1_000.0D, shinyMultiplier))
            );
        }
    }

    public record EventStatus(boolean enabled, String periodDate, String id, String nameKey,
                              double shinyMultiplier, boolean forced) {
        public IncidentCategory category() {
            return IncidentCategory.SERVER;
        }
    }

    public record ScheduledEvent(String periodDate, String id, String nameKey, double shinyMultiplier) {
    }
}
