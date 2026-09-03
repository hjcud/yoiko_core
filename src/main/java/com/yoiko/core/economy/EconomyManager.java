package com.yoiko.core.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.advancement.YoikoAdvancementManager;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoAuditSavedData;
import com.yoiko.core.data.ServerYoikoMarketSavedData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.cosmetic.CosmeticManager;
import com.yoiko.core.cosmetic.CosmeticType;
import com.yoiko.core.mail.MailboxManager;
import com.yoiko.core.newspaper.WeeklyNewspaperManager;
import com.yoiko.core.reward.YoikoResetClock;
import com.yoiko.core.registry.YoikoItems;
import com.yoiko.core.relic.RelicAppraisalCategory;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.core.component.DataComponents;
import net.neoforged.fml.loading.FMLPaths;

public final class EconomyManager {
    private static final int ECONOMY_SCHEMA_VERSION = 9;
    private static final int ECONOMY_REPLACE_BEFORE_SCHEMA_VERSION = 9;
    private static final String PARTICLE_GACHA_OFFER_ID = "particle_gacha_ticket";
    private static final long PARTICLE_GACHA_PURCHASE_COOLDOWN_MILLIS = Duration.ofDays(7).toMillis();
    public static final int FOCUSED_RELIC_EXCHANGE_COST = 2;
    private static final long PRICE_STATISTICS_CACHE_MILLIS = 60_000L;
    private static final Map<MinecraftServer, PriceStatisticsIndex> PRICE_STATISTICS_INDEXES = new WeakHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ECONOMY_FILE = FMLPaths.CONFIGDIR.get().resolve(YoikoServerCore.MODID).resolve("economy.json");
    private static final List<ShopOffer> GOLD_SHOP = new ArrayList<>();
    private static final List<ShopOffer> SERVER_BUY_ORDERS = new ArrayList<>();
    private static final List<ShopOffer> GEM_SHOP = new ArrayList<>();
    private static final Map<String, Long> COSMETIC_PRICE_OVERRIDES = new LinkedHashMap<>();
    private static long legendaryDuplicateGems = 2L;
    private static long mythicDuplicateGems = 4L;
    private static long legendaryCosmeticPrice = 8L;
    private static long mythicCosmeticPrice = 20L;
    private static long legendaryParticleCosmeticPrice = 120L;
    private static long mythicParticleCosmeticPrice = 320L;
    private static int maxPlayerListings = 10;
    private static int listingDurationDays = 7;
    private static double saleFeePercent = 5.0D;
    private static int maxPendingMarketDeliveries = 12;
    private static long minReasonableUnitPrice = 1L;
    private static long maxReasonableUnitPrice = 20_000L;
    private static long dailyServerBuyGoldLimit;
    private static int expiryWarningHours = 24;
    private static final int MAX_SALE_HISTORY = 50;
    private static int marketMedianMinimumSamples = 5;

    private EconomyManager() {
    }

    public static void init() {
        reload();
    }

    public static void reload() {
        PRICE_STATISTICS_INDEXES.clear();
        ensureFile();
        try {
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(ECONOMY_FILE)) {
                root = GSON.fromJson(reader, JsonObject.class);
            }
            int schemaVersion = integer(root, "schemaVersion", 0);
            if (root == null || schemaVersion < ECONOMY_REPLACE_BEFORE_SCHEMA_VERSION) {
                YoikoServerCore.LOGGER.info(
                        "Replacing development economy config with balance schema {}.",
                        ECONOMY_SCHEMA_VERSION
                );
                root = defaultConfig();
                writeConfig(root);
            } else if (schemaVersion < ECONOMY_SCHEMA_VERSION) {
                migrateConfig(root, schemaVersion);
                writeConfig(root);
            }
            load(root);
        } catch (Exception exception) {
            YoikoServerCore.LOGGER.warn("Failed to load Yoiko economy config. Using built-in defaults.", exception);
            load(defaultConfig());
        }
    }

    public static List<ShopOffer> goldShopOffers() {
        return List.copyOf(GOLD_SHOP);
    }

    public static List<ShopOffer> serverBuyOrders() {
        return List.copyOf(SERVER_BUY_ORDERS);
    }

    public static List<ShopOffer> gemShopOffers() {
        return List.copyOf(GEM_SHOP);
    }

    public static long duplicateGemValue(String rarity) {
        return "MYTHIC".equalsIgnoreCase(rarity) ? mythicDuplicateGems : legendaryDuplicateGems;
    }

    public static void recordEconomy(ServerPlayer player, String action, long amount, String detail) {
        if (amount <= 0L) {
            return;
        }
        ServerYoikoAuditSavedData.get(player.server).addOperational(
                "ECONOMY",
                action,
                player.getUUID(),
                player.getGameProfile().getName(),
                "amount=" + amount + (detail == null || detail.isBlank() ? "" : ";" + detail)
        );
    }

    public static long cosmeticGemPrice(String cosmeticId, String rarity) {
        Long override = COSMETIC_PRICE_OVERRIDES.get(cosmeticId);
        if (override != null) {
            return Math.max(0L, override);
        }
        var cosmetic = CosmeticManager.get(cosmeticId);
        if (cosmetic != null && cosmetic.type() == CosmeticType.PARTICLE) {
            return "MYTHIC".equalsIgnoreCase(rarity)
                    ? mythicParticleCosmeticPrice : legendaryParticleCosmeticPrice;
        }
        return "MYTHIC".equalsIgnoreCase(rarity) ? mythicCosmeticPrice : legendaryCosmeticPrice;
    }

    public static int maxPlayerListings() {
        return maxPlayerListings;
    }

    public static double saleFeePercent() {
        return saleFeePercent;
    }

    public static int maxPendingMarketDeliveries() {
        return maxPendingMarketDeliveries;
    }

    public static long minReasonableUnitPrice() {
        return minReasonableUnitPrice;
    }

    public static long maxReasonableUnitPrice() {
        return maxReasonableUnitPrice;
    }

    public static long dailyServerBuyGoldLimit() {
        return dailyServerBuyGoldLimit;
    }

    public static long remainingDailyServerBuyGold(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        resetDailyServerBuyCounter(data);
        return Math.max(0L, dailyServerBuyGoldLimit - data.serverBuyGoldToday);
    }

    public static long saleFee(long totalPrice) {
        return Math.max(0L, Math.round(Math.max(0L, totalPrice) * Math.max(0.0D, saleFeePercent) / 100.0D));
    }

    public static long saleProceeds(long totalPrice) {
        return Math.max(0L, totalPrice - saleFee(totalPrice));
    }

    public static boolean canReceiveMarketDelivery(ServerPlayer player) {
        return MailboxManager.hasMarketMailCapacity(player)
                && MailboxManager.pendingMarketDeliveryCount(player) < maxPendingMarketDeliveries;
    }

    public static ItemStack offerStack(ShopOffer offer) {
        if (offer == null) {
            return ItemStack.EMPTY;
        }
        ResourceLocation key;
        try {
            key = ResourceLocation.parse(offer.itemId());
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, Math.max(1, Math.min(item.getDefaultMaxStackSize(), offer.count())));
    }

    public static boolean purchaseGoldOffer(ServerPlayer player, String offerId) {
        ShopOffer offer = findOffer(GOLD_SHOP, offerId);
        ItemStack stack = offerStack(offer);
        if (offer == null || stack.isEmpty()) {
            sendFailure(player, "yoiko_core.message.market.offer_missing");
            return false;
        }
        if (!canReceiveMarketDelivery(player)) {
            sendFailure(player, "yoiko_core.message.market.delivery_limit");
            return false;
        }
        if (!CurrencyManager.take(player, CurrencyType.GOLD, offer.price())) {
            sendFailure(player, "yoiko_core.message.market.not_enough_gold");
            return false;
        }
        boolean delivered = MailboxManager.sendSystemMail(
                player,
                "market_delivery",
                UUID.randomUUID().toString(),
                "yoiko_core.mail.market.purchase_title",
                "yoiko_core.mail.market.purchase_message",
                List.of(stack)
        );
        if (!delivered) {
            CurrencyManager.add(player, CurrencyType.GOLD, offer.price());
            sendFailure(player, "yoiko_core.message.market.delivery_failed");
            return false;
        }
        recordEconomy(player, "GOLD_SPENT", offer.price(), "source=gold_shop;offer=" + offerId);
        playTradeSound(player);
        player.sendSystemMessage(Component.translatable(
                "yoiko_core.message.market.purchased_gold",
                stack.getHoverName(),
                offer.price()
        ).withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static boolean sellToServer(ServerPlayer player, String offerId) {
        ShopOffer offer = findOffer(SERVER_BUY_ORDERS, offerId);
        ItemStack wanted = offerStack(offer);
        if (offer == null || wanted.isEmpty()) {
            sendFailure(player, "yoiko_core.message.market.offer_missing");
            return false;
        }
        int available = countInventoryItem(player, wanted);
        if (available < offer.count()) {
            player.sendSystemMessage(Component.translatable(
                    "yoiko_core.message.market.hold_required",
                    wanted.getHoverName(),
                    offer.count()
            ).withStyle(ChatFormatting.YELLOW));
            return false;
        }
        if (CurrencyManager.balance(player, CurrencyType.GOLD) > CurrencyManager.MAX_BALANCE - offer.price()) {
            sendFailure(player, "yoiko_core.message.market.balance_full");
            return false;
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        resetDailyServerBuyCounter(data);
        if (data.serverBuyGoldToday + offer.price() > dailyServerBuyGoldLimit) {
            sendFailure(player, "yoiko_core.message.market.daily_gold_limit");
            return false;
        }
        removeInventoryItem(player, wanted, offer.count());
        CurrencyManager.add(player, CurrencyType.GOLD, offer.price());
        data.serverBuyGoldToday += offer.price();
        ServerYoikoSavedData.get(player.server).markDirty(player);
        audit(player.server, "SERVER_BUY", player.getUUID(), player.getGameProfile().getName(),
                null, "", wanted.copyWithCount(offer.count()), offer.price(), false, offerId);
        player.getInventory().setChanged();
        playTradeSound(player);
        player.sendSystemMessage(Component.translatable(
                "yoiko_core.message.market.sold_to_server",
                wanted.getHoverName(),
                offer.count(),
                offer.price()
        ).withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static int countInventoryItem(ServerPlayer player, ItemStack wanted) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isPlainServerBuyMatch(stack, wanted)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeInventoryItem(ServerPlayer player, ItemStack wanted, int requested) {
        int remaining = Math.max(0, requested);
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isPlainServerBuyMatch(stack, wanted)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
            if (stack.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static boolean isPlainServerBuyMatch(ItemStack stack, ItemStack wanted) {
        return stack != null && !stack.isEmpty()
                && ItemStack.isSameItemSameComponents(stack, wanted);
    }

    public static boolean purchaseGemOffer(ServerPlayer player, String offerId) {
        ShopOffer offer = findOffer(GEM_SHOP, offerId);
        ItemStack stack = offerStack(offer);
        if (offer == null || stack.isEmpty()) {
            sendFailure(player, "yoiko_core.message.market.offer_missing");
            return false;
        }
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        if (PARTICLE_GACHA_OFFER_ID.equals(offerId)) {
            long remaining = particleGachaPurchaseCooldownRemaining(data, System.currentTimeMillis());
            if (remaining > 0L) {
                long hourMillis = Duration.ofHours(1).toMillis();
                long totalHours = (remaining + hourMillis - 1L) / hourMillis;
                long days = totalHours / 24L;
                long hours = totalHours % 24L;
                player.sendSystemMessage(Component.translatable(
                        "yoiko_core.message.market.particle_gacha_cooldown", days, hours)
                        .withStyle(ChatFormatting.YELLOW));
                return false;
            }
        }
        if (!canReceiveMarketDelivery(player)) {
            sendFailure(player, "yoiko_core.message.market.delivery_limit");
            return false;
        }
        if (!CurrencyManager.take(player, CurrencyType.GEM, offer.price())) {
            sendFailure(player, "yoiko_core.message.market.not_enough_gems");
            return false;
        }
        if(stack.getItem() instanceof com.yoiko.core.item.TurtleHatchTicketItem)com.yoiko.core.item.TurtleHatchTicketItem.bindTo(stack,player.getUUID());
        boolean delivered = MailboxManager.sendSystemMail(
                player,
                "market_delivery",
                UUID.randomUUID().toString(),
                "yoiko_core.mail.market.gem_purchase_title",
                "yoiko_core.mail.market.gem_purchase_message",
                List.of(stack)
        );
        if (!delivered) {
            CurrencyManager.add(player, CurrencyType.GEM, offer.price());
            sendFailure(player, "yoiko_core.message.market.delivery_failed");
            return false;
        }
        if (PARTICLE_GACHA_OFFER_ID.equals(offerId)) {
            data.lastParticleGachaTicketPurchaseAt = System.currentTimeMillis();
            ServerYoikoSavedData.get(player.server).markDirty(player);
        }
        recordEconomy(player, "GEM_SPENT", offer.price(), "source=gem_shop;offer=" + offerId);
        playGemSound(player);
        player.sendSystemMessage(Component.translatable(
                "yoiko_core.message.market.purchased_gems",
                stack.getHoverName(),
                offer.price()
        ).withStyle(ChatFormatting.AQUA));
        return true;
    }

    public static int genericRelicTicketCount(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        if (player.getAbilities().instabuild) {
            return 999;
        }
        return countInventoryItem(player, new ItemStack(YoikoItems.RELIC_GACHA_TICKET.get()));
    }

    public static boolean exchangeFocusedRelicTicket(
            ServerPlayer player, RelicAppraisalCategory appraisalCategory) {
        RelicAppraisalCategory category = appraisalCategory == null
                ? RelicAppraisalCategory.ALL : appraisalCategory;
        if (category == RelicAppraisalCategory.ALL) {
            sendFailure(player, "yoiko_core.message.market.relic_exchange_invalid");
            return false;
        }
        if (!canReceiveMarketDelivery(player)) {
            sendFailure(player, "yoiko_core.message.market.delivery_limit");
            return false;
        }
        ItemStack payment = new ItemStack(
                YoikoItems.RELIC_GACHA_TICKET.get(), FOCUSED_RELIC_EXCHANGE_COST);
        if (!player.getAbilities().instabuild
                && countInventoryItem(player, payment) < FOCUSED_RELIC_EXCHANGE_COST) {
            player.sendSystemMessage(Component.translatable(
                    "yoiko_core.message.market.hold_required",
                    payment.getHoverName(), FOCUSED_RELIC_EXCHANGE_COST
            ).withStyle(ChatFormatting.YELLOW));
            return false;
        }

        if (!player.getAbilities().instabuild) {
            removeInventoryItem(player, payment, FOCUSED_RELIC_EXCHANGE_COST);
        }
        ItemStack result = new ItemStack(YoikoItems.relicGachaTicket(category));
        boolean delivered = MailboxManager.sendSystemMail(
                player,
                "market_delivery",
                UUID.randomUUID().toString(),
                "yoiko_core.mail.market.relic_exchange_title",
                "yoiko_core.mail.market.relic_exchange_message",
                List.of(result)
        );
        if (!delivered) {
            if (!player.getAbilities().instabuild) {
                ItemStack refund = payment.copy();
                if (!player.getInventory().add(refund) && !refund.isEmpty()) {
                    player.drop(refund, false);
                }
                player.getInventory().setChanged();
            }
            sendFailure(player, "yoiko_core.message.market.delivery_failed");
            return false;
        }
        recordEconomy(player, "RELIC_EXCHANGE", FOCUSED_RELIC_EXCHANGE_COST,
                "category=" + category.id());
        playTradeSound(player);
        player.sendSystemMessage(Component.translatable(
                "yoiko_core.message.market.relic_exchanged",
                result.getHoverName(), FOCUSED_RELIC_EXCHANGE_COST
        ).withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static boolean listItem(ServerPlayer player, ItemStack offeredStack, long totalPrice) {
        if (!MailboxManager.hasMarketMailCapacity(player)) {
            sendFailure(player, "yoiko_core.message.market.delivery_limit");
            return false;
        }
        if (totalPrice <= 0L || totalPrice > CurrencyManager.MAX_BALANCE) {
            sendFailure(player, "yoiko_core.message.market.invalid_price");
            return false;
        }
        ServerYoikoMarketSavedData savedData = ServerYoikoMarketSavedData.get(player.server);
        cleanupExpired(player.server);
        long ownCount = savedData.marketplaceListings().stream()
                .filter(listing -> listing.sellerUuid().equals(player.getUUID()))
                .count();
        if (ownCount >= maxPlayerListings) {
            sendFailure(player, "yoiko_core.message.market.listing_limit");
            return false;
        }
        if (!isMarketable(offeredStack)) {
            sendFailure(player, "yoiko_core.message.market.item_not_allowed");
            return false;
        }
        ItemStack escrow = offeredStack.copy();
        long now = System.currentTimeMillis();
        long durationMillis = Duration.ofDays(Math.max(1, listingDurationDays)).toMillis();
        savedData.marketplaceListings().add(new MarketplaceListing(
                UUID.randomUUID(),
                player.getUUID(),
                player.getGameProfile().getName(),
                escrow,
                totalPrice,
                now,
                now + durationMillis,
                false
        ));
        long unit = Math.max(1L, totalPrice / Math.max(1, escrow.getCount()));
        long median = recentMedianUnitPrice(player.server, escrow);
        boolean suspicious = isSuspiciousUnitPrice(unit, median);
        audit(player.server, "LIST", player.getUUID(), player.getGameProfile().getName(),
                null, "", escrow, totalPrice, suspicious, median > 0 ? "median=" + median : "");
        savedData.markChanged();
        playTradeSound(player);
        player.sendSystemMessage(Component.translatable(
                "yoiko_core.message.market.listed",
                escrow.getHoverName(),
                escrow.getCount(),
                totalPrice
        ).withStyle(ChatFormatting.GREEN));
        YoikoAdvancementManager.recordMarketListing(player);
        return true;
    }

    public static boolean buyPlayerListing(ServerPlayer buyer, UUID listingId) {
        ServerYoikoMarketSavedData marketData = ServerYoikoMarketSavedData.get(buyer.server);
        ServerYoikoSavedData playerData = ServerYoikoSavedData.get(buyer.server);
        cleanupExpired(buyer.server);
        Optional<MarketplaceListing> found = marketData.marketplaceListings().stream()
                .filter(listing -> listing.id().equals(listingId))
                .findFirst();
        if (found.isEmpty()) {
            sendFailure(buyer, "yoiko_core.message.market.listing_missing");
            return false;
        }
        MarketplaceListing listing = found.get();
        if (listing.sellerUuid().equals(buyer.getUUID())) {
            sendFailure(buyer, "yoiko_core.message.market.cannot_buy_own");
            return false;
        }
        if (!canReceiveMarketDelivery(buyer)) {
            sendFailure(buyer, "yoiko_core.message.market.delivery_limit");
            return false;
        }
        PlayerYoikoData seller = playerData.get(listing.sellerUuid());
        if (seller == null) {
            sendFailure(buyer, "yoiko_core.message.market.seller_missing");
            return false;
        }
        long fee = saleFee(listing.price());
        long proceeds = saleProceeds(listing.price());
        if (CurrencyManager.balance(seller, CurrencyType.GOLD) > CurrencyManager.MAX_BALANCE - proceeds) {
            sendFailure(buyer, "yoiko_core.message.market.seller_balance_full");
            return false;
        }
        if (!CurrencyManager.take(buyer, CurrencyType.GOLD, listing.price())) {
            sendFailure(buyer, "yoiko_core.message.market.not_enough_gold");
            return false;
        }
        CurrencyManager.add(seller, CurrencyType.GOLD, proceeds);
        marketData.marketplaceListings().remove(listing);
        boolean delivered = MailboxManager.sendSystemMail(
                buyer,
                "market_delivery",
                listing.id().toString(),
                "yoiko_core.mail.market.player_purchase_title",
                "yoiko_core.mail.market.player_purchase_message|" + listing.sellerName(),
                List.of(listing.item())
        );
        if (!delivered) {
            CurrencyManager.add(buyer, CurrencyType.GOLD, listing.price());
            CurrencyManager.take(seller, CurrencyType.GOLD, proceeds);
            playerData.markDirty(listing.sellerUuid());
            marketData.marketplaceListings().add(listing);
            marketData.markChanged();
            sendFailure(buyer, "yoiko_core.message.market.delivery_failed");
            return false;
        }
        seller.marketplaceSales.add(0, new MarketplaceSaleRecord(
                UUID.randomUUID(),
                listing.item(),
                buyer.getGameProfile().getName(),
                listing.price(),
                fee,
                proceeds,
                System.currentTimeMillis()
        ));
        seller.unseenMarketSales++;
        while (seller.marketplaceSales.size() > MAX_SALE_HISTORY) {
            seller.marketplaceSales.remove(seller.marketplaceSales.size() - 1);
        }
        playerData.markDirty(listing.sellerUuid());
        marketData.markChanged();
        audit(buyer.server, "PLAYER_SALE", listing.sellerUuid(), listing.sellerName(),
                buyer.getUUID(), buyer.getGameProfile().getName(), listing.item(), listing.price(),
                isSuspiciousUnitPrice(
                        Math.max(1L, listing.price() / Math.max(1, listing.item().getCount())),
                        recentMedianUnitPrice(buyer.server, listing.item())),
                "fee=" + fee);
        ServerPlayer onlineSeller = buyer.server.getPlayerList().getPlayer(listing.sellerUuid());
        if (onlineSeller != null) {
            onlineSeller.sendSystemMessage(Component.translatable(
                    "yoiko_core.message.market.listing_sold",
                    listing.item().getHoverName(),
                    proceeds,
                    fee
            ).withStyle(ChatFormatting.GOLD));
            playTradeSound(onlineSeller);
            com.yoiko.core.menu.MenuBadgeManager.sync(onlineSeller);
            YoikoAdvancementManager.recordMarketSale(onlineSeller);
        }
        buyer.sendSystemMessage(Component.translatable(
                "yoiko_core.message.market.player_purchase",
                listing.item().getHoverName(),
                listing.price()
        ).withStyle(ChatFormatting.GREEN));
        playTradeSound(buyer);
        YoikoAdvancementManager.recordMarketPurchase(buyer);
        return true;
    }

    public static boolean cancelListing(ServerPlayer player, UUID listingId) {
        ServerYoikoMarketSavedData savedData = ServerYoikoMarketSavedData.get(player.server);
        Optional<MarketplaceListing> found = savedData.marketplaceListings().stream()
                .filter(listing -> listing.id().equals(listingId) && listing.sellerUuid().equals(player.getUUID()))
                .findFirst();
        if (found.isEmpty()) {
            sendFailure(player, "yoiko_core.message.market.listing_missing");
            return false;
        }
        MarketplaceListing listing = found.get();
        if (!returnListingByMail(player.server, listing, "yoiko_core.mail.market.cancel_message")) {
            sendFailure(player, "yoiko_core.message.market.delivery_failed");
            return false;
        }
        savedData.marketplaceListings().remove(listing);
        savedData.markChanged();
        audit(player.server, "CANCEL", player.getUUID(), player.getGameProfile().getName(),
                null, "", listing.item(), listing.price(), false, "");
        player.sendSystemMessage(Component.translatable("yoiko_core.message.market.cancelled").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    public static boolean updateListingPrice(ServerPlayer player, UUID listingId, long totalPrice) {
        if (totalPrice <= 0L || totalPrice > CurrencyManager.MAX_BALANCE) {
            sendFailure(player, "yoiko_core.message.market.invalid_price");
            return false;
        }
        ServerYoikoMarketSavedData savedData = ServerYoikoMarketSavedData.get(player.server);
        for (int index = 0; index < savedData.marketplaceListings().size(); index++) {
            MarketplaceListing listing = savedData.marketplaceListings().get(index);
            if (!listing.id().equals(listingId) || !listing.sellerUuid().equals(player.getUUID())) {
                continue;
            }
            savedData.marketplaceListings().set(index, new MarketplaceListing(
                    listing.id(),
                    listing.sellerUuid(),
                    listing.sellerName(),
                    listing.item(),
                    totalPrice,
                    listing.createdAt(),
                    listing.expiresAt(),
                    listing.expiryWarningSent()
            ));
            savedData.markChanged();
            audit(player.server, "PRICE_UPDATE", player.getUUID(), player.getGameProfile().getName(),
                    null, "", listing.item(), totalPrice,
                    isSuspiciousUnitPrice(Math.max(1L, totalPrice / Math.max(1, listing.item().getCount())),
                            recentMedianUnitPrice(player.server, listing.item())),
                    "previous=" + listing.price());
            player.sendSystemMessage(Component.translatable(
                    "yoiko_core.message.market.price_updated",
                    totalPrice
            ).withStyle(ChatFormatting.GREEN));
            playTradeSound(player);
            return true;
        }
        sendFailure(player, "yoiko_core.message.market.listing_missing");
        return false;
    }

    public static List<MarketplaceSaleRecord> saleHistory(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        return List.copyOf(data.marketplaceSales);
    }

    public static String category(ItemStack stack) {
        if (stack.getItem() instanceof SpawnEggItem) {
            return "spawn_egg";
        }
        if (stack.getItem() instanceof BlockItem) {
            return "block";
        }
        if (stack.isDamageableItem()) {
            return "tool";
        }
        if (stack.get(DataComponents.FOOD) != null || stack.getItem() instanceof PotionItem) {
            return "consumable";
        }
        return "other";
    }

    public static List<MarketplaceListing> publicListings(MinecraftServer server) {
        return ServerYoikoMarketSavedData.get(server).marketplaceListings().stream()
                .sorted(Comparator.comparingLong(MarketplaceListing::createdAt).reversed())
                .toList();
    }

    public static List<MarketplaceListing> ownListings(ServerPlayer player) {
        return publicListings(player.server).stream()
                .filter(listing -> listing.sellerUuid().equals(player.getUUID()))
                .toList();
    }

    public static void cleanupExpired(MinecraftServer server) {
        ServerYoikoMarketSavedData savedData = ServerYoikoMarketSavedData.get(server);
        long now = System.currentTimeMillis();
        List<MarketplaceListing> expired = savedData.marketplaceListings().stream()
                .filter(listing -> listing.expiresAt() > 0L && listing.expiresAt() <= now)
                .toList();
        boolean changed = false;
        long warningWindow = Duration.ofHours(Math.max(1, expiryWarningHours)).toMillis();
        for (int index = 0; index < savedData.marketplaceListings().size(); index++) {
            MarketplaceListing listing = savedData.marketplaceListings().get(index);
            long remaining = listing.expiresAt() - now;
            if (listing.expiryWarningSent() || remaining <= 0L || remaining > warningWindow) {
                continue;
            }
            ServerPlayer seller = server.getPlayerList().getPlayer(listing.sellerUuid());
            if (seller == null) {
                continue;
            }
            seller.sendSystemMessage(Component.translatable(
                    "yoiko_core.message.market.expiring_soon",
                    listing.item().getHoverName(),
                    Math.max(1L, (remaining + 3_599_999L) / 3_600_000L)
            ).withStyle(ChatFormatting.YELLOW));
            seller.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.65F, 1.1F);
            savedData.marketplaceListings().set(index, new MarketplaceListing(
                    listing.id(), listing.sellerUuid(), listing.sellerName(), listing.item(),
                    listing.price(), listing.createdAt(), listing.expiresAt(), true));
            changed = true;
        }
        for (MarketplaceListing listing : expired) {
            if (returnListingByMail(server, listing, "yoiko_core.mail.market.expired_message")) {
                savedData.marketplaceListings().remove(listing);
                changed = true;
            }
        }
        if (changed) {
            savedData.markChanged();
        }
    }

    public static long remainingSeconds(MarketplaceListing listing) {
        return Math.max(0L, (listing.expiresAt() - System.currentTimeMillis() + 999L) / 1000L);
    }

    private static boolean returnListingByMail(MinecraftServer server, MarketplaceListing listing, String message) {
        return MailboxManager.sendMarketReturn(
                server,
                listing.sellerUuid(),
                listing.id(),
                "yoiko_core.mail.market.expired_message".equals(message) ? listing.price() : 0L,
                "yoiko_core.mail.market.return_title",
                message,
                List.of(listing.item())
        );
    }

    public static boolean relistExpired(ServerPlayer player, UUID mailId) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        PlayerYoikoData.MailEntry mail = java.util.stream.Stream.concat(
                        data.mailbox.stream(), data.mailboxOverflow.stream())
                .filter(entry -> entry.id.equals(mailId) && "market_return".equals(entry.type)
                        && entry.sourcePrice > 0L && !entry.items.isEmpty())
                .findFirst().orElse(null);
        if (mail == null) {
            sendFailure(player, "yoiko_core.message.market.relist_missing");
            return false;
        }
        long price = mail.sourcePrice;
        ItemStack stack = mail.items.get(0).copy();
        List<PlayerYoikoData.MailEntry> source = data.mailbox.contains(mail)
                ? data.mailbox : data.mailboxOverflow;
        int originalIndex = source.indexOf(mail);
        source.remove(mail);
        savedData.markDirty(player);
        if (!listItem(player, stack, price)) {
            source.add(Math.max(0, Math.min(originalIndex, source.size())), mail);
            savedData.markDirty(player);
            return false;
        }
        player.sendSystemMessage(Component.translatable("yoiko_core.message.market.relisted",
                stack.getHoverName(), price).withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static long recentMedianUnitPrice(MinecraftServer server, ItemStack item) {
        return recentPriceStatistics(server, item).medianUnitPrice();
    }

    public static PriceStatistics recentPriceStatistics(MinecraftServer server, ItemStack item) {
        long now = System.currentTimeMillis();
        ServerYoikoAuditSavedData audit = ServerYoikoAuditSavedData.get(server);
        PriceStatisticsIndex index = PRICE_STATISTICS_INDEXES.get(server);
        if (index == null || index.marketRevision() != audit.marketRevision()
                || now - index.createdAt() >= PRICE_STATISTICS_CACHE_MILLIS) {
            index = buildPriceStatisticsIndex(audit, now);
            PRICE_STATISTICS_INDEXES.put(server, index);
        }
        return index.statistics().getOrDefault(new ItemComponentsKey(item), new PriceStatistics(0L, 0));
    }

    private static PriceStatisticsIndex buildPriceStatisticsIndex(ServerYoikoAuditSavedData audit, long now) {
        long cutoff = now - Duration.ofDays(30).toMillis();
        Map<ItemComponentsKey, Map<String, Long>> samplesByItem = new LinkedHashMap<>();
        for (MarketplaceTransactionRecord record : audit.marketplaceTransactions()) {
            if (!"PLAYER_SALE".equals(record.action()) || record.createdAt() < cutoff
                    || record.item().isEmpty()
                    || record.sellerUuid() == null || record.buyerUuid() == null
                    || record.sellerUuid().equals(record.buyerUuid())) {
                continue;
            }
            long dayBucket = record.createdAt() / Duration.ofDays(1).toMillis();
            String sampleKey = record.sellerUuid() + "|" + record.buyerUuid() + "|" + dayBucket;
            ItemStack recordItem = record.item();
            samplesByItem.computeIfAbsent(new ItemComponentsKey(recordItem), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(sampleKey,
                            Math.max(1L, record.totalPrice() / Math.max(1, recordItem.getCount())));
        }
        Map<ItemComponentsKey, PriceStatistics> statistics = new LinkedHashMap<>();
        for (Map.Entry<ItemComponentsKey, Map<String, Long>> entry : samplesByItem.entrySet()) {
            List<Long> values = entry.getValue().values().stream().sorted().toList();
            long median = 0L;
            if (values.size() >= marketMedianMinimumSamples) {
                int middle = values.size() / 2;
                median = values.size() % 2 == 1 ? values.get(middle)
                        : Math.max(1L, (values.get(middle - 1) + values.get(middle)) / 2L);
            }
            statistics.put(entry.getKey(), new PriceStatistics(median, values.size()));
        }
        return new PriceStatisticsIndex(audit.marketRevision(), now, Map.copyOf(statistics));
    }

    public static List<MarketplaceTransactionRecord> auditHistory(MinecraftServer server, int limit) {
        List<MarketplaceTransactionRecord> records = ServerYoikoAuditSavedData.get(server).marketplaceTransactions();
        return List.copyOf(records.subList(0, Math.min(Math.max(1, limit), records.size())));
    }

    private static void audit(MinecraftServer server, String action, UUID sellerUuid, String sellerName,
                              UUID buyerUuid, String buyerName, ItemStack item, long totalPrice,
                              boolean suspicious, String detail) {
        ServerYoikoAuditSavedData data = ServerYoikoAuditSavedData.get(server);
        MarketplaceTransactionRecord record = new MarketplaceTransactionRecord(
                UUID.randomUUID(), action, sellerUuid, sellerName, buyerUuid, buyerName,
                item, totalPrice, System.currentTimeMillis(), suspicious, detail);
        data.addMarket(record);
        if ("PLAYER_SALE".equals(action)) {
            PRICE_STATISTICS_INDEXES.remove(server);
            WeeklyNewspaperManager.recordMarketSale(server, sellerUuid, sellerName, totalPrice);
        }
        String line = "[MarketAudit] action={} seller={} buyer={} item={} count={} price={} suspicious={} detail={}";
        if (suspicious) {
            YoikoServerCore.LOGGER.warn(line, action, sellerName, buyerName,
                    BuiltInRegistries.ITEM.getKey(item.getItem()), item.getCount(), totalPrice, true, detail);
        } else {
            YoikoServerCore.LOGGER.info(line, action, sellerName, buyerName,
                    BuiltInRegistries.ITEM.getKey(item.getItem()), item.getCount(), totalPrice, false, detail);
        }
    }

    private static boolean isSuspiciousUnitPrice(long unitPrice, long median) {
        if (unitPrice < minReasonableUnitPrice || unitPrice > maxReasonableUnitPrice) {
            return true;
        }
        return median > 0L && (unitPrice > median * 4L || unitPrice * 4L < median);
    }

    private static void resetDailyServerBuyCounter(PlayerYoikoData data) {
        String today = YoikoResetClock.dailyPeriodDate(System.currentTimeMillis()).toString();
        if (!today.equals(data.serverBuyGoldDate)) {
            data.serverBuyGoldDate = today;
            data.serverBuyGoldToday = 0L;
        }
    }

    public static boolean isMarketable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if(item instanceof com.yoiko.core.item.TurtleHatchTicketItem
                || item instanceof com.yoiko.core.item.TurtleTrainingResetTicketItem)return false;
        return item != YoikoItems.RELIC_UPGRADE_CRYSTAL.get()
                && item != YoikoItems.RELIC_SCRAP.get()
                && item != YoikoItems.RELIC_DISPLAY_COMMON.get()
                && item != YoikoItems.RELIC_DISPLAY_UNCOMMON.get()
                && item != YoikoItems.RELIC_DISPLAY_RARE.get()
                && item != YoikoItems.RELIC_DISPLAY_EPIC.get()
                && item != YoikoItems.RELIC_DISPLAY_LEGENDARY.get()
                && item != YoikoItems.RELIC_DISPLAY_MYSTIC.get()
                && item != YoikoItems.RELIC_DISPLAY_RADIANT.get();
    }

    private static ShopOffer findOffer(List<ShopOffer> offers, String id) {
        for (ShopOffer offer : offers) {
            if (offer.id().equals(id)) {
                return offer;
            }
        }
        return null;
    }

    private static void sendFailure(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
    }

    private static void playTradeSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.55F, 1.15F);
    }

    private static void playGemSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.65F, 1.25F);
    }

    private static void load(JsonObject root) {
        GOLD_SHOP.clear();
        GOLD_SHOP.addAll(readOffers(root.getAsJsonArray("goldShop")));
        SERVER_BUY_ORDERS.clear();
        SERVER_BUY_ORDERS.addAll(readOffers(root.getAsJsonArray("serverBuyOrders")));
        GEM_SHOP.clear();
        GEM_SHOP.addAll(readOffers(root.getAsJsonArray("gemShop")));
        if (GOLD_SHOP.isEmpty() || GEM_SHOP.isEmpty()) {
            JsonObject defaults = defaultConfig();
            if (GOLD_SHOP.isEmpty()) GOLD_SHOP.addAll(readOffers(defaults.getAsJsonArray("goldShop")));
            if (GEM_SHOP.isEmpty()) GEM_SHOP.addAll(readOffers(defaults.getAsJsonArray("gemShop")));
        }
        ensureTurtleTicketOffers();

        JsonObject duplicates = object(root, "duplicateGemValues");
        legendaryDuplicateGems = positiveLong(duplicates, "LEGENDARY", 2L);
        mythicDuplicateGems = positiveLong(duplicates, "MYTHIC", 4L);
        JsonObject cosmeticPrices = object(root, "cosmeticPrices");
        legendaryCosmeticPrice = positiveLong(cosmeticPrices, "defaultLegendary", 8L);
        mythicCosmeticPrice = positiveLong(cosmeticPrices, "defaultMythic", 20L);
        legendaryParticleCosmeticPrice = positiveLong(cosmeticPrices, "particleLegendary", 120L);
        mythicParticleCosmeticPrice = positiveLong(cosmeticPrices, "particleMythic", 320L);
        COSMETIC_PRICE_OVERRIDES.clear();
        JsonObject overrides = object(cosmeticPrices, "overrides");
        for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                COSMETIC_PRICE_OVERRIDES.put(entry.getKey(), Math.max(0L, entry.getValue().getAsLong()));
            }
        }

        JsonObject playerMarket = object(root, "playerMarket");
        maxPlayerListings = Math.max(1, Math.min(100, integer(playerMarket, "maxListingsPerPlayer", 10)));
        listingDurationDays = Math.max(1, Math.min(365, integer(playerMarket, "listingDurationDays", 7)));
        saleFeePercent = Math.max(0.0D, Math.min(100.0D, decimal(playerMarket, "saleFeePercent", 5.0D)));
        maxPendingMarketDeliveries = Math.max(1, Math.min(
                PlayerYoikoData.MAX_MAILBOX_MAILS,
                integer(playerMarket, "maxPendingDeliveries", 12)
        ));
        minReasonableUnitPrice = Math.max(1L, longValue(playerMarket, "minReasonableUnitPrice", 1L));
        maxReasonableUnitPrice = Math.max(
                minReasonableUnitPrice,
                longValue(playerMarket, "maxReasonableUnitPrice", 20_000L)
        );
        dailyServerBuyGoldLimit = Math.max(0L,
                longValue(playerMarket, "dailyServerBuyGoldLimit", 0L));
        marketMedianMinimumSamples = Math.max(3, Math.min(100,
                integer(playerMarket, "marketMedianMinimumSamples", 5)));
        expiryWarningHours = Math.max(1, Math.min(168,
                integer(playerMarket, "expiryWarningHours", 24)));
    }

    private static List<ShopOffer> readOffers(JsonArray array) {
        List<ShopOffer> offers = new ArrayList<>();
        if (array == null) {
            return offers;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id", "");
            String item = string(object, "item", "");
            int count = Math.max(1, integer(object, "count", 1));
            long price = Math.max(1L, longValue(object, "price", 1L));
            ShopOffer offer = new ShopOffer(id, item, count, price);
            if (!id.isBlank() && !offerStack(offer).isEmpty()) {
                offers.add(offer);
            } else {
                YoikoServerCore.LOGGER.warn("Ignoring invalid economy offer: id={}, item={}", id, item);
            }
        }
        return offers;
    }

    private static void ensureFile() {
        try {
            Files.createDirectories(ECONOMY_FILE.getParent());
            if (Files.notExists(ECONOMY_FILE)) {
                writeDefaultFile();
            }
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Failed to create Yoiko economy config.", exception);
        }
    }

    private static void writeDefaultFile() {
        writeConfig(defaultConfig());
    }

    private static void writeConfig(JsonObject config) {
        try {
            Files.createDirectories(ECONOMY_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(ECONOMY_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            YoikoServerCore.LOGGER.error("Failed to write default economy config.", exception);
        }
    }

    private static JsonObject defaultConfig() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", ECONOMY_SCHEMA_VERSION);
        JsonArray goldShop = offers(
                offer("relic_ticket", "yoiko_core:relic_gacha_ticket", 1, 300),
                offer("cobblestone", "minecraft:cobblestone", 64, 4),
                offer("stone", "minecraft:stone", 64, 6),
                offer("dirt", "minecraft:dirt", 64, 4),
                offer("sand", "minecraft:sand", 64, 6),
                offer("gravel", "minecraft:gravel", 64, 6),
                offer("andesite", "minecraft:andesite", 64, 8),
                offer("oak_planks", "minecraft:oak_planks", 64, 8),
                offer("spruce_planks", "minecraft:spruce_planks", 64, 8),
                offer("cherry_planks", "minecraft:cherry_planks", 64, 10),
                offer("mangrove_planks", "minecraft:mangrove_planks", 64, 10),
                offer("glass", "minecraft:glass", 64, 10),
                offer("white_wool", "minecraft:white_wool", 64, 10),
                offer("clay_ball", "minecraft:clay_ball", 64, 6),
                offer("bricks", "minecraft:bricks", 64, 12),
                offer("terracotta", "minecraft:terracotta", 64, 10),
                offer("white_concrete", "minecraft:white_concrete", 64, 12),
                offer("mud_bricks", "minecraft:mud_bricks", 64, 12),
                offer("deepslate_tiles", "minecraft:deepslate_tiles", 64, 12),
                offer("tuff_bricks", "minecraft:tuff_bricks", 64, 12),
                offer("calcite", "minecraft:calcite", 64, 12),
                offer("quartz_block", "minecraft:quartz_block", 32, 12),
                offer("prismarine", "minecraft:prismarine", 32, 12),
                offer("sea_lantern", "minecraft:sea_lantern", 16, 12),
                offer("packed_ice", "minecraft:packed_ice", 64, 12),
                offer("amethyst_block", "minecraft:amethyst_block", 16, 12),
                offer("tinted_glass", "minecraft:tinted_glass", 32, 12),
                offer("honeycomb", "minecraft:honeycomb", 16, 12),
                offer("redstone", "minecraft:redstone", 32, 26),
                offer("copper_ingot", "minecraft:copper_ingot", 32, 40),
                offer("iron_ingot", "minecraft:iron_ingot", 16, 64),
                offer("string", "minecraft:string", 32, 20),
                offer("leather", "minecraft:leather", 16, 32),
                offer("palm_planks", "beachparty:palm_planks", 64, 10),
                offer("pine_planks", "meadow:pine_planks", 64, 10),
                offer("dark_cherry_planks", "vinery:dark_cherry_planks", 64, 10),
                offer("aspen_planks", "bloomingnature:aspen_planks", 64, 10),
                offer("meadow_limestone", "meadow:limestone", 64, 12),
                offer("travertin", "bloomingnature:travertin", 64, 12),
                offer("slate", "bloomingnature:slate", 64, 12),
                offer("marlstone", "bloomingnature:marlstone", 64, 12),
                offer("laterit", "bloomingnature:laterit", 64, 12)
        );
        addMissingOffers(goldShop, spawnEggOffers());
        root.add("goldShop", goldShop);
        root.add("serverBuyOrders", new JsonArray());
        root.add("gemShop", offers(
                offer("all_pokemon_ticket", "yoiko_core:all_pokemon_gacha_ticket", 1, 5),
                offer("turtle_standard_ticket", "yoiko_core:turtle_standard_hatch_ticket", 1, 2),
                offer("turtle_pickup_ticket", "yoiko_core:turtle_pickup_hatch_ticket", 1, 3),
                offer("turtle_rare_strategy_ticket", "yoiko_core:turtle_rare_strategy_ticket", 1, 12),
                offer("turtle_epic_ticket", "yoiko_core:turtle_epic_hatch_ticket", 1, 30),
                offer("turtle_training_reset_ticket", "yoiko_core:turtle_training_reset_ticket", 1, 16),
                offer(PARTICLE_GACHA_OFFER_ID, "yoiko_core:particle_gacha_ticket", 1, 30)
        ));
        JsonObject duplicates = new JsonObject();
        duplicates.addProperty("LEGENDARY", 2);
        duplicates.addProperty("MYTHIC", 4);
        root.add("duplicateGemValues", duplicates);
        JsonObject prices = new JsonObject();
        prices.addProperty("defaultLegendary", 8);
        prices.addProperty("defaultMythic", 20);
        prices.addProperty("particleLegendary", 120);
        prices.addProperty("particleMythic", 320);
        prices.add("overrides", new JsonObject());
        root.add("cosmeticPrices", prices);
        JsonObject playerMarket = new JsonObject();
        playerMarket.addProperty("maxListingsPerPlayer", 10);
        playerMarket.addProperty("listingDurationDays", 7);
        playerMarket.addProperty("saleFeePercent", 5.0D);
        playerMarket.addProperty("maxPendingDeliveries", 12);
        playerMarket.addProperty("minReasonableUnitPrice", 1);
        playerMarket.addProperty("maxReasonableUnitPrice", 20000);
        playerMarket.addProperty("dailyServerBuyGoldLimit", 0);
        playerMarket.addProperty("marketMedianMinimumSamples", 5);
        playerMarket.addProperty("expiryWarningHours", 24);
        root.add("playerMarket", playerMarket);
        return root;
    }

    private static void migrateConfig(JsonObject root, int schemaVersion) {
        if (schemaVersion < 7) {
            JsonArray goldShop = root.has("goldShop") && root.get("goldShop").isJsonArray()
                    ? root.getAsJsonArray("goldShop")
                    : new JsonArray();
            addMissingOffers(goldShop, spawnEggOffers());
            root.add("goldShop", goldShop);
        }
        root.addProperty("schemaVersion", ECONOMY_SCHEMA_VERSION);
    }

    private static JsonObject[] spawnEggOffers() {
        return new JsonObject[]{
                offer("chicken_spawn_egg", "minecraft:chicken_spawn_egg", 1, 40),
                offer("pig_spawn_egg", "minecraft:pig_spawn_egg", 1, 50),
                offer("sheep_spawn_egg", "minecraft:sheep_spawn_egg", 1, 60),
                offer("rabbit_spawn_egg", "minecraft:rabbit_spawn_egg", 1, 60),
                offer("cow_spawn_egg", "minecraft:cow_spawn_egg", 1, 70),
                offer("bee_spawn_egg", "minecraft:bee_spawn_egg", 1, 80),
                offer("goat_spawn_egg", "minecraft:goat_spawn_egg", 1, 80),
                offer("horse_spawn_egg", "minecraft:horse_spawn_egg", 1, 100),
                offer("donkey_spawn_egg", "minecraft:donkey_spawn_egg", 1, 100),
                offer("llama_spawn_egg", "minecraft:llama_spawn_egg", 1, 100),
                offer("camel_spawn_egg", "minecraft:camel_spawn_egg", 1, 120),
                offer("mule_spawn_egg", "minecraft:mule_spawn_egg", 1, 120),
                offer("villager_spawn_egg", "minecraft:villager_spawn_egg", 1, 200)
        };
    }

    private static void addMissingOffers(JsonArray target, JsonObject... requiredOffers) {
        Set<String> existingIds = new java.util.HashSet<>();
        for (JsonElement element : target) {
            if (element.isJsonObject()) {
                String id = string(element.getAsJsonObject(), "id", "");
                if (!id.isBlank()) {
                    existingIds.add(id);
                }
            }
        }
        for (JsonObject required : requiredOffers) {
            String id = string(required, "id", "");
            if (!id.isBlank() && existingIds.add(id)) {
                target.add(required);
            }
        }
    }

    private static JsonArray offers(JsonObject... offers) {
        JsonArray array = new JsonArray();
        for (JsonObject offer : offers) {
            array.add(offer);
        }
        return array;
    }

    private static void ensureTurtleTicketOffers(){
        List<ShopOffer> required=List.of(
                new ShopOffer("turtle_standard_ticket","yoiko_core:turtle_standard_hatch_ticket",1,2),
                new ShopOffer("turtle_pickup_ticket","yoiko_core:turtle_pickup_hatch_ticket",1,3),
                new ShopOffer("turtle_rare_strategy_ticket","yoiko_core:turtle_rare_strategy_ticket",1,12),
                new ShopOffer("turtle_epic_ticket","yoiko_core:turtle_epic_hatch_ticket",1,30),
                new ShopOffer("turtle_training_reset_ticket","yoiko_core:turtle_training_reset_ticket",1,16),
                new ShopOffer(PARTICLE_GACHA_OFFER_ID,"yoiko_core:particle_gacha_ticket",1,30));
        for(ShopOffer offer:required)if(findOffer(GEM_SHOP,offer.id())==null)GEM_SHOP.add(offer);
    }

    private static long particleGachaPurchaseCooldownRemaining(PlayerYoikoData data, long now) {
        if (data.lastParticleGachaTicketPurchaseAt <= 0L) {
            return 0L;
        }
        return Math.max(0L, PARTICLE_GACHA_PURCHASE_COOLDOWN_MILLIS
                - Math.max(0L, now - data.lastParticleGachaTicketPurchaseAt));
    }

    private static JsonObject offer(String id, String item, int count, long price) {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("item", item);
        object.addProperty("count", count);
        object.addProperty("price", price);
        return object;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key)
                : new JsonObject();
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object != null && object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        return object != null && object.has(key) ? object.get(key).getAsLong() : fallback;
    }

    private static long positiveLong(JsonObject object, String key, long fallback) {
        return Math.max(0L, longValue(object, key, fallback));
    }

    private static double decimal(JsonObject object, String key, double fallback) {
        return object != null && object.has(key) ? object.get(key).getAsDouble() : fallback;
    }

    public record ShopOffer(String id, String itemId, int count, long price) {
        public ShopOffer {
            id = id == null ? "" : id;
            itemId = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
            count = Math.max(1, count);
            price = Math.max(1L, price);
        }
    }

    public record PriceStatistics(long medianUnitPrice, int sampleCount) {
        public PriceStatistics {
            medianUnitPrice = Math.max(0L, medianUnitPrice);
            sampleCount = Math.max(0, sampleCount);
        }
    }

    private record PriceStatisticsIndex(
            long marketRevision, long createdAt, Map<ItemComponentsKey, PriceStatistics> statistics
    ) {
    }

    private static final class ItemComponentsKey {
        private final ItemStack item;
        private final int hash;

        private ItemComponentsKey(ItemStack item) {
            this.item = item.copyWithCount(1);
            this.hash = ItemStack.hashItemAndComponents(this.item);
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof ItemComponentsKey key
                    && hash == key.hash && ItemStack.isSameItemSameComponents(item, key.item);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
