package com.yoiko.core.economy;

import com.yoiko.core.data.OperationalAuditRecord;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Durable, compact economy telemetry for one server reset-day. */
public final class EconomyDailyAggregate {
    public static final String MARKET_FEES = "MARKET_FEES";
    public static final String MARKET_SUSPICIOUS = "MARKET_SUSPICIOUS";

    private final String period;
    private final Map<String, Metric> metrics = new LinkedHashMap<>();
    private final Map<String, Metric> breakdowns = new LinkedHashMap<>();
    private final Set<UUID> participants = new LinkedHashSet<>();

    public EconomyDailyAggregate(String period) {
        this.period = period == null ? "" : period;
    }

    public String period() {
        return period;
    }

    public Map<String, Metric> metrics() {
        return Collections.unmodifiableMap(metrics);
    }

    public Map<String, Metric> breakdowns() {
        return Collections.unmodifiableMap(breakdowns);
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    public Metric metric(String key) {
        return metrics.getOrDefault(key, Metric.EMPTY);
    }

    public void recordOperational(OperationalAuditRecord record) {
        if (record == null || !"ECONOMY".equals(record.category()) || record.action().isBlank()) {
            return;
        }
        long amount = detailAmount(record.detail(), "amount");
        add(metrics, record.action(), amount);
        if (record.playerUuid() != null) {
            participants.add(record.playerUuid());
        }
        String dimension = detailDimension(record.detail());
        if (!dimension.isBlank()) {
            add(breakdowns, record.action() + "|" + dimension, amount);
        }
    }

    public void recordMarket(MarketplaceTransactionRecord record) {
        if (record == null || record.action().isBlank()) {
            return;
        }
        add(metrics, record.action(), record.totalPrice());
        if (record.sellerUuid() != null) {
            participants.add(record.sellerUuid());
        }
        if (record.buyerUuid() != null) {
            participants.add(record.buyerUuid());
        }
        if (!record.item().isEmpty()) {
            String itemId = BuiltInRegistries.ITEM.getKey(record.item().getItem()).toString();
            add(breakdowns, record.action() + "|item=" + itemId, record.totalPrice());
        }
        if ("PLAYER_SALE".equals(record.action())) {
            add(metrics, MARKET_FEES, detailAmount(record.detail(), "fee"));
        }
        if (record.suspicious()) {
            add(metrics, MARKET_SUSPICIOUS, record.totalPrice());
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("period", period);
        tag.put("metrics", saveMetrics(metrics));
        tag.put("breakdowns", saveMetrics(breakdowns));
        ListTag players = new ListTag();
        for (UUID participant : participants) {
            CompoundTag player = new CompoundTag();
            player.putUUID("uuid", participant);
            players.add(player);
        }
        tag.put("participants", players);
        return tag;
    }

    public static EconomyDailyAggregate load(CompoundTag tag) {
        EconomyDailyAggregate aggregate = new EconomyDailyAggregate(tag.getString("period"));
        loadMetrics(tag.getList("metrics", Tag.TAG_COMPOUND), aggregate.metrics);
        loadMetrics(tag.getList("breakdowns", Tag.TAG_COMPOUND), aggregate.breakdowns);
        ListTag players = tag.getList("participants", Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag player = players.getCompound(index);
            if (player.hasUUID("uuid")) {
                aggregate.participants.add(player.getUUID("uuid"));
            }
        }
        return aggregate;
    }

    private static ListTag saveMetrics(Map<String, Metric> source) {
        ListTag values = new ListTag();
        source.forEach((key, metric) -> {
            CompoundTag value = new CompoundTag();
            value.putString("key", key);
            value.putLong("events", metric.events());
            value.putLong("amount", metric.amount());
            values.add(value);
        });
        return values;
    }

    private static void loadMetrics(ListTag values, Map<String, Metric> destination) {
        for (int index = 0; index < values.size(); index++) {
            CompoundTag value = values.getCompound(index);
            String key = value.getString("key");
            if (!key.isBlank()) {
                destination.put(key, new Metric(value.getLong("events"), value.getLong("amount")));
            }
        }
    }

    private static void add(Map<String, Metric> destination, String key, long amount) {
        if (key == null || key.isBlank()) {
            return;
        }
        Metric before = destination.getOrDefault(key, Metric.EMPTY);
        destination.put(key, new Metric(saturatedAdd(before.events(), 1L),
                saturatedAdd(before.amount(), Math.max(0L, amount))));
    }

    private static String detailDimension(String detail) {
        String source = detailValue(detail, "source");
        String discriminator = firstDetailValue(detail,
                "offer", "cosmetic", "appearance", "activity", "variant", "advancement", "mail_type", "operation");
        if (!source.isBlank() && !discriminator.isBlank()) {
            return "source=" + source + "/" + discriminator;
        }
        if (!source.isBlank()) {
            return "source=" + source;
        }
        return discriminator;
    }

    private static String firstDetailValue(String detail, String... keys) {
        for (String key : keys) {
            String value = detailValue(detail, key);
            if (!value.isBlank()) {
                return key + "=" + value;
            }
        }
        return "";
    }

    public static long detailAmount(String detail, String key) {
        String value = detailValue(detail, key);
        if (value.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String detailValue(String detail, String key) {
        if (detail == null || detail.isBlank() || key == null || key.isBlank()) {
            return "";
        }
        String prefix = key + "=";
        for (String part : detail.split("[;,]")) {
            String value = part.trim();
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    public record Metric(long events, long amount) {
        private static final Metric EMPTY = new Metric(0L, 0L);

        public Metric {
            events = Math.max(0L, events);
            amount = Math.max(0L, amount);
        }
    }
}
