package com.yoiko.core.menu;

import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.economy.EconomyManager;
import com.yoiko.core.economy.MarketplaceListing;
import com.yoiko.core.economy.MarketplaceSaleRecord;
import com.yoiko.core.mail.MailboxManager;
import com.yoiko.core.network.OpenMarketPayload;
import com.yoiko.core.registry.YoikoItems;
import com.yoiko.core.relic.RelicAppraisalCategory;
import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.data.ServerYoikoAuditSavedData;
import com.yoiko.core.data.ServerYoikoMarketSavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class YoikoMarketMenu {
    private static final int PAGE_SIZE = 64;
    private static final Map<UUID, Deque<Long>> REQUESTS = new HashMap<>();
    private static final Map<UUID, Long> LAST_RATE_WARNING = new HashMap<>();
    private static final Map<UUID, MarketSession> SESSIONS = new HashMap<>();
    private static final Map<MinecraftServer, ListingIndex> LISTING_INDEXES = new WeakHashMap<>();

    private YoikoMarketMenu() {
    }

    public static boolean handleAction(ServerPlayer player, String action, UUID sessionId, long nonce) {
        if (!action.startsWith("market_")) {
            return false;
        }
        if (!"market_open".equals(action) && !authenticate(player, sessionId, nonce)) {
            ServerYoikoAuditSavedData.get(player.server).addOperational(
                    "MARKET", "REJECTED_NONCE", player.getUUID(), player.getGameProfile().getName(),
                    "session=" + sessionId + ";nonce=" + nonce + ";action=" + action.split("\\|", 2)[0]);
            player.sendSystemMessage(Component.translatable("yoiko_core.message.market.invalid_session")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        if (!allowRequest(player)) {
            warnRateLimit(player);
            return false;
        }
        String[] parts = action.split("\\|", 6);
        String selected = part(parts, 3);
        switch (parts[0]) {
            case "market_open" -> open(player);
            case "market_view" -> open(
                    player,
                    PlayerYoikoData.sanitizeMarketSection(part(parts, 1)),
                    intPart(parts, 2, 0),
                    selected,
                    ""
            );
            case "market_sort" -> {
                PlayerYoikoData data = data(player);
                data.marketSort = PlayerYoikoData.sanitizeMarketSort(part(parts, 1));
                data.marketPage = 0;
                markDirty(player);
                open(player, data.marketSection, 0, "", "");
            }
            case "market_category" -> {
                PlayerYoikoData data = data(player);
                data.marketCategory = PlayerYoikoData.sanitizeMarketCategory(part(parts, 1));
                data.marketPage = 0;
                markDirty(player);
                open(player, data.marketSection, 0, "", "");
            }
            case "market_search" -> {
                PlayerYoikoData data = data(player);
                data.marketSearch = PlayerYoikoData.sanitizeSearch(part(parts, 1));
                data.marketPage = 0;
                markDirty(player);
                open(player, data.marketSection, 0, "", "");
            }
            case "market_refresh" -> reopen(player, selected);
            case "market_refresh_if_changed" -> {
                long knownRevision = longPart(parts, 1, -1L);
                if (knownRevision != ServerYoikoMarketSavedData.get(player.server).revision()) {
                    reopen(player, selected);
                }
            }
            case "market_remember_price" -> {
                PlayerYoikoData data = data(player);
                data.marketRememberPrice = Boolean.parseBoolean(part(parts, 1));
                markDirty(player);
                reopen(player, selected);
            }
            case "market_buy_gold" -> {
                EconomyManager.purchaseGoldOffer(player, part(parts, 1));
                reopen(player, part(parts, 1));
            }
            case "market_sell_gold" -> {
                EconomyManager.sellToServer(player, part(parts, 1));
                reopen(player, part(parts, 1));
            }
            case "market_buy_gem" -> {
                EconomyManager.purchaseGemOffer(player, part(parts, 1));
                reopen(player, part(parts, 1));
            }
            case "market_exchange_relic" -> {
                String offerId = part(parts, 1);
                String categoryId = offerId.startsWith("relic_exchange_")
                        ? offerId.substring("relic_exchange_".length()) : offerId;
                RelicAppraisalCategory category = RelicAppraisalCategory.fromString(categoryId);
                EconomyManager.exchangeFocusedRelicTicket(player, category);
                reopen(player, "relic_exchange_" + category.id());
            }
            case "market_buy_listing" -> {
                UUID id = uuidPart(parts, 1);
                if (id != null) {
                    EconomyManager.buyPlayerListing(player, id);
                }
                reopen(player, "");
            }
            case "market_cancel_listing" -> {
                UUID id = uuidPart(parts, 1);
                if (id != null) {
                    EconomyManager.cancelListing(player, id);
                }
                reopen(player, "");
            }
            case "market_update_price" -> {
                UUID id = uuidPart(parts, 1);
                if (id != null) {
                    EconomyManager.updateListingPrice(player, id, longPart(parts, 2, -1L));
                }
                reopen(player, id == null ? "" : id.toString());
            }
            case "market_relist_expired" -> {
                UUID id = uuidPart(parts, 1);
                if (id != null) {
                    EconomyManager.relistExpired(player, id);
                }
                reopen(player, "");
            }
            case "market_register" -> {
                boolean registered = false;
                if (player.containerMenu instanceof YoikoMarketContainerMenu marketMenu) {
                    registered = marketMenu.registerItem(player, longPart(parts, 1, -1L));
                    if (!registered && marketMenu.registrationStack().isEmpty()) {
                        player.sendSystemMessage(Component.translatable(
                                "yoiko_core.message.market.register_item_required"
                        ).withStyle(ChatFormatting.YELLOW));
                    }
                }
                open(player, "my_listings", 0, "", registered ? "yoiko_core.ui.market.listed_success" : "");
            }
            default -> open(player);
        }
        return true;
    }

    public static void open(ServerPlayer player) {
        MenuSessionManager.open(player, "market");
        SESSIONS.put(player.getUUID(), new MarketSession(UUID.randomUUID()));
        // A previous market container can remain active server-side after the client navigates
        // to a payload-only screen. Navigation must therefore resend the vanilla menu-open
        // packet even when the server still sees YoikoMarketContainerMenu as active.
        openContainer(player);
        PlayerYoikoData data = data(player);
        open(player, data.marketSection, data.marketPage, "", "");
    }

    public static void clearRateLimit(UUID playerUuid) {
        REQUESTS.remove(playerUuid);
        LAST_RATE_WARNING.remove(playerUuid);
        SESSIONS.remove(playerUuid);
    }

    private static PlayerYoikoData data(ServerPlayer player) {
        return ServerYoikoSavedData.get(player.server).getOrCreate(player);
    }

    private static void markDirty(ServerPlayer player) {
        ServerYoikoSavedData.get(player.server).markDirty(player);
    }

    private static void reopen(ServerPlayer player, String selectedId) {
        PlayerYoikoData data = data(player);
        open(player, data.marketSection, data.marketPage, selectedId, "");
    }

    private static void open(ServerPlayer player, String requestedSection, int requestedPage,
                             String requestedId, String message) {
        ensureContainerOpen(player);
        MarketSession session = SESSIONS.computeIfAbsent(player.getUUID(),
                ignored -> new MarketSession(UUID.randomUUID()));
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        String section = PlayerYoikoData.sanitizeMarketSection(requestedSection);
        List<OpenMarketPayload.Entry> pageEntries;
        int totalPages;
        int page;
        if ("player_market".equals(section)) {
            MarketPage result = playerMarketPage(
                    player, data.marketSort, data.marketCategory, data.marketSearch, requestedPage);
            pageEntries = result.entries();
            totalPages = result.totalPages();
            page = result.page();
        } else {
            List<OpenMarketPayload.Entry> allEntries = entries(
                    player, section, data.marketSort, data.marketCategory, data.marketSearch);
            totalPages = Math.max(1, (allEntries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            page = Math.max(0, Math.min(requestedPage, totalPages - 1));
            int from = Math.min(page * PAGE_SIZE, allEntries.size());
            int to = Math.min(from + PAGE_SIZE, allEntries.size());
            pageEntries = new ArrayList<>(allEntries.subList(from, to));
        }
        pageEntries.replaceAll(entry -> attachPriceStatistics(player, entry));
        String selectedId = select(pageEntries, requestedId);

        data.lastMenuTab = PlayerYoikoData.MENU_TAB_MARKET;
        data.unseenMarketSales = 0;
        data.marketSection = section;
        data.marketPage = page;
        savedData.markDirty(player);
        if (player.containerMenu instanceof YoikoMarketContainerMenu marketMenu) {
            marketMenu.setRegistrationActive(player, "my_listings".equals(section));
        }
        PacketDistributor.sendToPlayer(player, new OpenMarketPayload(
                data.gold,
                data.gems,
                EconomyManager.genericRelicTicketCount(player),
                session.id.toString(),
                ServerYoikoMarketSavedData.get(player.server).revision(),
                section,
                page,
                totalPages,
                selectedId,
                message,
                data.marketSort,
                data.marketCategory,
                data.marketSearch,
                MailboxManager.pendingMarketDeliveryCount(data),
                EconomyManager.maxPendingMarketDeliveries(),
                EconomyManager.saleFeePercent(),
                EconomyManager.minReasonableUnitPrice(),
                EconomyManager.maxReasonableUnitPrice(),
                EconomyManager.dailyServerBuyGoldLimit(),
                EconomyManager.remainingDailyServerBuyGold(player),
                data.marketRememberPrice,
                pageEntries
        ));
        MenuBadgeManager.sync(player);
    }

    private static void ensureContainerOpen(ServerPlayer player) {
        if (player.containerMenu instanceof YoikoMarketContainerMenu) {
            return;
        }
        openContainer(player);
    }

    private static void openContainer(ServerPlayer player) {
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new YoikoMarketContainerMenu(containerId, inventory),
                        Component.translatable("yoiko_core.screen.market")
                ),
                buffer -> {
                }
        );
    }

    private static List<OpenMarketPayload.Entry> entries(
            ServerPlayer player,
            String section,
            String requestedSort,
            String requestedCategory,
            String requestedSearch
    ) {
        List<OpenMarketPayload.Entry> entries = new ArrayList<>();
        switch (section) {
            case "gem_shop" -> {
                for (EconomyManager.ShopOffer offer : EconomyManager.gemShopOffers()) {
                    entries.add(offerEntry(offer, "BUY_GEM", "GEM"));
                }
            }
            case "player_market" -> {
                for (MarketplaceListing listing : EconomyManager.publicListings(player.server)) {
                    if (!listing.sellerUuid().equals(player.getUUID())) {
                        entries.add(listingEntry(player, listing, false));
                    }
                }
            }
            case "my_listings" -> {
                for (MarketplaceListing listing : EconomyManager.ownListings(player)) {
                    entries.add(listingEntry(player, listing, true));
                }
                PlayerYoikoData playerData = data(player);
                for (PlayerYoikoData.MailEntry mail : java.util.stream.Stream.concat(
                        playerData.mailbox.stream(), playerData.mailboxOverflow.stream()).toList()) {
                    if ("market_return".equals(mail.type) && mail.sourcePrice > 0L && !mail.items.isEmpty()) {
                        entries.add(expiredEntry(player, mail));
                    }
                }
            }
            case "sales_history" -> {
                for (MarketplaceSaleRecord sale : EconomyManager.saleHistory(player)) {
                    entries.add(saleEntry(player, sale));
                }
            }
            default -> {
                for (RelicAppraisalCategory category : RelicAppraisalCategory.values()) {
                    if (category != RelicAppraisalCategory.ALL) {
                        entries.add(relicExchangeEntry(category));
                    }
                }
                Map<String,ServerTradePair> catalog = new LinkedHashMap<>();
                for (EconomyManager.ShopOffer offer : EconomyManager.goldShopOffers()) {
                    ServerTradePair before=catalog.get(offer.itemId());
                    catalog.put(offer.itemId(),new ServerTradePair(offer,before==null?null:before.buyback()));
                }
                for (EconomyManager.ShopOffer offer : EconomyManager.serverBuyOrders()) {
                    ServerTradePair before=catalog.get(offer.itemId());
                    catalog.put(offer.itemId(),new ServerTradePair(before==null?null:before.purchase(),offer));
                }
                catalog.values().forEach(pair->entries.add(serverTradeEntry(pair.purchase(),pair.buyback())));
            }
        }

        String category = PlayerYoikoData.sanitizeMarketCategory(requestedCategory);
        if (!"all".equals(category)) {
            entries.removeIf(entry -> !category.equals(entry.category()));
        }
        String search = PlayerYoikoData.sanitizeSearch(requestedSearch);
        if (!search.isBlank()) {
            entries.removeIf(entry -> !matchesSearch(entry, search));
        }
        sort(entries, PlayerYoikoData.sanitizeMarketSort(requestedSort));
        return entries;
    }

    private static MarketPage playerMarketPage(ServerPlayer player, String requestedSort,
                                               String requestedCategory, String requestedSearch,
                                               int requestedPage) {
        ServerYoikoMarketSavedData marketData = ServerYoikoMarketSavedData.get(player.server);
        ListingIndex index = LISTING_INDEXES.computeIfAbsent(player.server, ignored -> new ListingIndex());
        List<MarketplaceListing> ordered = index.query(
                marketData, PlayerYoikoData.sanitizeMarketSort(requestedSort),
                PlayerYoikoData.sanitizeMarketCategory(requestedCategory));
        int visibleCount = 0;
        String search = PlayerYoikoData.sanitizeSearch(requestedSearch);
        for (MarketplaceListing listing : ordered) {
            if (!listing.sellerUuid().equals(player.getUUID()) && matchesSearch(listing, search)) {
                visibleCount++;
            }
        }
        int totalPages = Math.max(1, (visibleCount + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        int from = page * PAGE_SIZE;
        int to = from + PAGE_SIZE;
        int visibleIndex = 0;
        List<OpenMarketPayload.Entry> result = new ArrayList<>(Math.min(PAGE_SIZE, visibleCount));
        for (MarketplaceListing listing : ordered) {
            if (listing.sellerUuid().equals(player.getUUID())) {
                continue;
            }
            if (!matchesSearch(listing, search)) {
                continue;
            }
            if (visibleIndex >= from && visibleIndex < to) {
                result.add(listingEntry(player, listing, false));
            }
            visibleIndex++;
            if (visibleIndex >= to) {
                break;
            }
        }
        return new MarketPage(page, totalPages, result);
    }

    private static boolean matchesSearch(MarketplaceListing listing, String query) {
        return query.isBlank() || matchesSearch(listingEntry(null, listing, false), query);
    }

    private static boolean matchesSearch(OpenMarketPayload.Entry entry, String query) {
        if (query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(java.util.Locale.ROOT);
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(entry.stack().getItem()).toString();
        return entry.stack().getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(needle)
                || itemId.toLowerCase(java.util.Locale.ROOT).contains(needle)
                || entry.seller().toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    private static void sort(List<OpenMarketPayload.Entry> entries, String sort) {
        Comparator<OpenMarketPayload.Entry> comparator = switch (sort) {
            case "price_asc" -> Comparator.comparingLong(OpenMarketPayload.Entry::price);
            case "price_desc" -> Comparator.comparingLong(OpenMarketPayload.Entry::price).reversed();
            case "expiring" -> Comparator.comparingLong(entry ->
                    entry.remainingSeconds() <= 0L ? Long.MAX_VALUE : entry.remainingSeconds());
            default -> Comparator.comparingLong(OpenMarketPayload.Entry::createdAt).reversed();
        };
        entries.sort(comparator);
    }

    private static OpenMarketPayload.Entry offerEntry(EconomyManager.ShopOffer offer, String action, String currency) {
        var stack = EconomyManager.offerStack(offer);
        return new OpenMarketPayload.Entry(
                offer.id(), action, stack, offer.price(), currency, "", 0L, false,
                0L, 0L, 0L, EconomyManager.category(stack), 0L, 0
        );
    }

    private record ServerTradePair(EconomyManager.ShopOffer purchase,EconomyManager.ShopOffer buyback) { }

    private static OpenMarketPayload.Entry serverTradeEntry(EconomyManager.ShopOffer purchase,
                                                              EconomyManager.ShopOffer buyback) {
        EconomyManager.ShopOffer display=purchase!=null?purchase:buyback;
        ItemStack stack=EconomyManager.offerStack(display);
        String id="server_trade_"+(purchase!=null?purchase.id():buyback.id());
        long sortPrice=purchase!=null?purchase.price():buyback.price();
        return new OpenMarketPayload.Entry(
                id,"SERVER_TRADE",stack,sortPrice,"GOLD","",0L,false,0L,0L,0L,
                EconomyManager.category(stack),0L,0,
                purchase==null?"":purchase.id(),purchase==null?0:purchase.count(),purchase==null?0L:purchase.price(),
                buyback==null?"":buyback.id(),buyback==null?0:buyback.count(),buyback==null?0L:buyback.price()
        );
    }

    private static OpenMarketPayload.Entry relicExchangeEntry(RelicAppraisalCategory category) {
        ItemStack stack = new ItemStack(YoikoItems.relicGachaTicket(category));
        return new OpenMarketPayload.Entry(
                "relic_exchange_" + category.id(), "RELIC_EXCHANGE", stack,
                EconomyManager.FOCUSED_RELIC_EXCHANGE_COST, "RELIC", "", 0L, false,
                0L, 0L, 0L, EconomyManager.category(stack), 0L, 0
        );
    }

    private static OpenMarketPayload.Entry listingEntry(ServerPlayer player, MarketplaceListing listing, boolean own) {
        long fee = EconomyManager.saleFee(listing.price());
        return new OpenMarketPayload.Entry(
                listing.id().toString(),
                own ? "CANCEL_LISTING" : "BUY_LISTING",
                listing.item(),
                listing.price(),
                "GOLD",
                listing.sellerName(),
                EconomyManager.remainingSeconds(listing),
                own,
                fee,
                EconomyManager.saleProceeds(listing.price()),
                listing.createdAt(),
                EconomyManager.category(listing.item()),
                0L,
                0
        );
    }

    private static OpenMarketPayload.Entry saleEntry(ServerPlayer player, MarketplaceSaleRecord sale) {
        return new OpenMarketPayload.Entry(
                sale.id().toString(),
                "SALE_RECORD",
                sale.item(),
                sale.totalPrice(),
                "GOLD",
                sale.buyerName(),
                0L,
                true,
                sale.fee(),
                sale.proceeds(),
                sale.soldAt(),
                EconomyManager.category(sale.item()),
                0L,
                0
        );
    }

    private static OpenMarketPayload.Entry expiredEntry(ServerPlayer player, PlayerYoikoData.MailEntry mail) {
        long price = mail.sourcePrice;
        ItemStack stack = mail.items.get(0);
        return new OpenMarketPayload.Entry(
                mail.id.toString(), "RELIST_EXPIRED", stack, price, "GOLD", player.getGameProfile().getName(),
                0L, true, EconomyManager.saleFee(price), EconomyManager.saleProceeds(price),
                mail.createdAt, EconomyManager.category(stack), 0L, 0
        );
    }

    private static OpenMarketPayload.Entry attachPriceStatistics(ServerPlayer player, OpenMarketPayload.Entry entry) {
        if (!("BUY_LISTING".equals(entry.action()) || "CANCEL_LISTING".equals(entry.action())
                || "SALE_RECORD".equals(entry.action()) || "RELIST_EXPIRED".equals(entry.action()))) {
            return entry;
        }
        EconomyManager.PriceStatistics statistics = EconomyManager.recentPriceStatistics(player.server, entry.stack());
        return new OpenMarketPayload.Entry(
                entry.id(), entry.action(), entry.stack(), entry.price(), entry.currency(), entry.seller(),
                entry.remainingSeconds(), entry.own(), entry.fee(), entry.proceeds(), entry.createdAt(),
                entry.category(), statistics.medianUnitPrice(), statistics.sampleCount()
        );
    }

    private static boolean allowRequest(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Deque<Long> requests = REQUESTS.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        long window = YoikoCommonConfig.MARKET_REQUEST_WINDOW_MS.get();
        while (!requests.isEmpty() && now - requests.peekFirst() > window) {
            requests.removeFirst();
        }
        if (requests.size() >= YoikoCommonConfig.MARKET_REQUEST_LIMIT.get()) {
            return false;
        }
        requests.addLast(now);
        return true;
    }

    private static boolean authenticate(ServerPlayer player, UUID sessionId, long nonce) {
        MarketSession session = SESSIONS.get(player.getUUID());
        if (session == null || sessionId == null || !session.id.equals(sessionId)
                || nonce <= session.lastAcceptedNonce) {
            return false;
        }
        session.lastAcceptedNonce = nonce;
        return true;
    }

    private static void warnRateLimit(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long last = LAST_RATE_WARNING.getOrDefault(player.getUUID(), 0L);
        if (now - last >= YoikoCommonConfig.MARKET_RATE_WARNING_COOLDOWN_MS.get()) {
            LAST_RATE_WARNING.put(player.getUUID(), now);
            player.sendSystemMessage(Component.translatable("yoiko_core.message.market.too_many_requests")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static final class MarketSession {
        private final UUID id;
        private long lastAcceptedNonce;

        private MarketSession(UUID id) {
            this.id = id;
        }
    }

    private record MarketPage(int page, int totalPages, List<OpenMarketPayload.Entry> entries) {
    }

    private static final class ListingIndex {
        private long revision = Long.MIN_VALUE;
        private final Map<String, List<MarketplaceListing>> queries = new HashMap<>();

        private List<MarketplaceListing> query(ServerYoikoMarketSavedData data, String sort, String category) {
            if (revision != data.revision()) {
                revision = data.revision();
                queries.clear();
            }
            String key = sort + '|' + category;
            return queries.computeIfAbsent(key, ignored -> {
                List<MarketplaceListing> listings = new ArrayList<>();
                for (MarketplaceListing listing : data.marketplaceListings()) {
                    if ("all".equals(category) || category.equals(EconomyManager.category(listing.item()))) {
                        listings.add(listing);
                    }
                }
                Comparator<MarketplaceListing> comparator = switch (sort) {
                    case "price_asc" -> Comparator.comparingLong(MarketplaceListing::price);
                    case "price_desc" -> Comparator.comparingLong(MarketplaceListing::price).reversed();
                    case "expiring" -> Comparator.comparingLong(MarketplaceListing::expiresAt);
                    default -> Comparator.comparingLong(MarketplaceListing::createdAt).reversed();
                };
                listings.sort(comparator);
                return List.copyOf(listings);
            });
        }
    }

    private static String select(List<OpenMarketPayload.Entry> entries, String requestedId) {
        for (OpenMarketPayload.Entry entry : entries) {
            if (entry.id().equals(requestedId)) {
                return requestedId;
            }
        }
        return entries.isEmpty() ? "" : entries.get(0).id();
    }

    private static String part(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    private static int intPart(String[] parts, int index, int fallback) {
        try {
            return Integer.parseInt(part(parts, index));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long longPart(String[] parts, int index, long fallback) {
        try {
            return Long.parseLong(part(parts, index));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static UUID uuidPart(String[] parts, int index) {
        try {
            return UUID.fromString(part(parts, index));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
