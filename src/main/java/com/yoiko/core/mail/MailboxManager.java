package com.yoiko.core.mail;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.config.YoikoCommonConfig;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.data.ServerYoikoAuditSavedData;
import com.yoiko.core.economy.CurrencyManager;
import com.yoiko.core.economy.CurrencyType;
import com.yoiko.core.network.OpenMailboxPayload;
import com.yoiko.core.newspaper.WeeklyNewspaperManager;
import com.yoiko.core.menu.MenuBadgeManager;
import com.yoiko.core.menu.MenuSessionManager;
import com.yoiko.core.relic.RelicManager;
import com.yoiko.core.reward.RewardManager;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MailboxManager {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT);

    private MailboxManager() {
    }

    public static boolean handleAction(ServerPlayer player, String action) {
        if (!action.startsWith("mailbox_")) {
            return false;
        }
        String[] parts = action.split("\\|", 3);
        switch (parts[0]) {
            case "mailbox_open" -> open(player);
            case "mailbox_select" -> {
                markRead(player, part(parts, 1));
                send(player, part(parts, 1));
            }
            case "mailbox_claim" -> {
                claim(player, part(parts, 1));
                send(player, part(parts, 1));
            }
            case "mailbox_claim_all" -> {
                int claimed = claimAll(player);
                player.sendSystemMessage(Component.translatable("yoiko_core.message.mail.claimed_all", claimed).withStyle(ChatFormatting.GREEN));
                if (claimed > 0) {
                    playMailClaimSound(player);
                }
                send(player, "");
            }
            case "mailbox_delete" -> {
                delete(player, part(parts, 1));
                send(player, "");
            }
            case "mailbox_delete_read" -> {
                int deleted = deleteRead(player);
                player.sendSystemMessage(Component.translatable("yoiko_core.message.mail.deleted_read", deleted).withStyle(ChatFormatting.GREEN));
                send(player, "");
            }
            case "mailbox_daily_box" -> {
                RewardManager.openDailyBonusBox(player, intPart(parts, 1, -1));
                send(player, part(parts, 2));
            }
            case "mailbox_login_claim" -> {
                claim(player, part(parts, 1));
                send(player, "", true);
            }
            case "mailbox_login_box" -> {
                RewardManager.openDailyBonusBox(player, intPart(parts, 1, -1));
                send(player, "", true);
            }
            default -> open(player);
        }
        return true;
    }

    public static void open(ServerPlayer player) {
        MenuSessionManager.open(player, "mailbox");
        send(player, "");
    }

    /** Opens the compact login reward sheet while keeping all rewards in the mailbox ledger. */
    public static void openLoginReward(ServerPlayer player) {
        MenuSessionManager.open(player, "mailbox");
        send(player, "", true);
    }

    public static boolean sendSystemMail(ServerPlayer recipient, String type, String claimKey, String title, String message, List<ItemStack> items) {
        return sendSystemMail(recipient, type, claimKey, title, message, items, 0L, 0L);
    }

    public static boolean sendSystemMail(ServerPlayer recipient, String type, String claimKey,
                                         String title, String message, List<ItemStack> items,
                                         long attachedGold, long attachedGems) {
        return sendMail(recipient, senderForType(type), type, claimKey, title, message,
                items, attachedGold, attachedGems);
    }

    public static boolean sendSystemMail(MinecraftServer server, UUID recipientUuid, String type, String claimKey,
                                         String title, String message, List<ItemStack> items) {
        return sendSystemMail(server, recipientUuid, type, claimKey, title, message, items, 0L, 0L);
    }

    public static boolean sendSystemMail(MinecraftServer server, UUID recipientUuid, String type, String claimKey,
                                         String title, String message, List<ItemStack> items,
                                         long attachedGold, long attachedGems) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(server);
        PlayerYoikoData data = savedData.get(recipientUuid);
        if (data == null) {
            return false;
        }
        boolean delivered = storeMail(
                server,
                savedData,
                data,
                senderForType(type),
                type,
                claimKey,
                title,
                message,
                items,
                attachedGold,
                attachedGems
        );
        ServerPlayer online = server.getPlayerList().getPlayer(recipientUuid);
        if (delivered && online != null) {
            online.sendSystemMessage(Component.translatable("yoiko_core.message.mail.arrived", mailComponent(title)).withStyle(ChatFormatting.AQUA));
            playMailArrivalSound(online);
            syncBadges(online);
        }
        return delivered;
    }

    public static boolean sendMarketReturn(MinecraftServer server, UUID recipientUuid, UUID listingId,
                                           long price, String title, String message, List<ItemStack> items) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(server);
        PlayerYoikoData data = savedData.get(recipientUuid);
        if (data == null) {
            audit(server, "DELIVERY_REJECTED_PLAYER_MISSING", recipientUuid, "", "type=market_return");
            return false;
        }
        PlayerYoikoData.MailEntry mail = new PlayerYoikoData.MailEntry(
                "market_return", listingId.toString(), senderForType("market_return"), title, message, items)
                .withSource(listingId.toString(), price, 0L, "");
        boolean delivered = storeMail(server, savedData, data, mail);
        ServerPlayer online = server.getPlayerList().getPlayer(recipientUuid);
        if (delivered && online != null) {
            online.sendSystemMessage(Component.translatable("yoiko_core.message.mail.arrived", mailComponent(title))
                    .withStyle(ChatFormatting.AQUA));
            playMailArrivalSound(online);
            syncBadges(online);
        }
        return delivered;
    }

    public static boolean sendAdminMail(ServerPlayer recipient, String title, String message, List<ItemStack> items) {
        return sendAdminMail(recipient, title, message, items, 0L, 0L);
    }

    public static boolean sendAdminMail(ServerPlayer recipient, String title, String message,
                                        List<ItemStack> items, long attachedGold, long attachedGems) {
        return sendMail(recipient,
                (items == null || items.isEmpty()) && attachedGold <= 0L && attachedGems <= 0L
                        ? "yoiko_core.mail.sender.system" : "yoiko_core.mail.sender.reward",
                "admin", UUID.randomUUID().toString(), title, message, items,
                attachedGold, attachedGems);
    }

    public static boolean sendPlayerMail(ServerPlayer sender, ServerPlayer recipient, String message, List<ItemStack> items) {
        return sendMail(recipient, sender.getGameProfile().getName(), "player", UUID.randomUUID().toString(), "yoiko_core.mail.player.title", message, items);
    }

    public static boolean hasPending(ServerPlayer player, String type, String claimKey) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        return hasPending(data, type, claimKey);
    }

    public static int storedMailCount(PlayerYoikoData data) {
        return data.mailbox.size() + data.mailboxOverflow.size();
    }

    public static boolean hasMarketMailCapacity(ServerPlayer player) {
        return hasMarketMailCapacity(ServerYoikoSavedData.get(player.server).getOrCreate(player));
    }

    public static boolean hasMarketMailCapacity(PlayerYoikoData data) {
        return storedMailCount(data) < PlayerYoikoData.MAX_MAILBOX_MAILS;
    }

    public static QueueStatus queueStatus(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        return new QueueStatus(data.mailbox.size(), data.mailboxOverflow.size(),
                data.mailbox.stream().filter(mail -> mail.read && !mail.hasAttachments()).count());
    }

    public static QueueStatus recoverQueue(ServerPlayer player) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        int queuedBefore = data.mailboxOverflow.size();
        int pruned = removeReadEmpty(data);
        promoteQueued(data);
        if (pruned > 0 || queuedBefore != data.mailboxOverflow.size()) {
            savedData.markDirty(player);
            audit(player.server, "ADMIN_RECOVER_QUEUE", player.getUUID(), player.getGameProfile().getName(),
                    "pruned=" + pruned + ";promoted=" + Math.max(0, queuedBefore - data.mailboxOverflow.size())
                            + ";remaining=" + data.mailboxOverflow.size());
            syncBadges(player);
        }
        return queueStatus(player);
    }

    public static boolean hasUnreadMail(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        return data.mailbox.stream().anyMatch(mail -> !mail.read);
    }

    public static int pendingMarketDeliveryCount(ServerPlayer player) {
        return pendingMarketDeliveryCount(ServerYoikoSavedData.get(player.server).getOrCreate(player));
    }

    public static int pendingMarketDeliveryCount(PlayerYoikoData data) {
        return (int) java.util.stream.Stream.concat(data.mailbox.stream(), data.mailboxOverflow.stream())
                .filter(mail -> "market_delivery".equals(mail.type) && mail.hasAttachments())
                .count();
    }

    public static int removeType(ServerPlayer player, String type) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        int before = data.mailbox.size();
        int overflowBefore = data.mailboxOverflow.size();
        data.mailbox.removeIf(mail -> mail.type.equals(type));
        data.mailboxOverflow.removeIf(mail -> mail.type.equals(type));
        promoteQueued(data);
        int removed = before + overflowBefore - data.mailbox.size() - data.mailboxOverflow.size();
        if (removed > 0) {
            savedData.markMailboxDirty(player);
            audit(player.server, "ADMIN_REMOVE_TYPE", player.getUUID(), player.getGameProfile().getName(),
                    "type=" + type + ";count=" + removed);
            syncBadges(player);
        }
        return removed;
    }

    private static boolean sendMail(ServerPlayer recipient, String sender, String type, String claimKey, String title, String message, List<ItemStack> items) {
        return sendMail(recipient, sender, type, claimKey, title, message, items, 0L, 0L);
    }

    private static boolean sendMail(ServerPlayer recipient, String sender, String type, String claimKey,
                                    String title, String message, List<ItemStack> items,
                                    long attachedGold, long attachedGems) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(recipient.server);
        PlayerYoikoData data = savedData.getOrCreate(recipient);
        boolean delivered = storeMail(recipient.server, savedData, data, sender, type, claimKey,
                title, message, items, attachedGold, attachedGems);
        if (!delivered) {
            return false;
        }
        recipient.sendSystemMessage(Component.translatable("yoiko_core.message.mail.arrived", mailComponent(title)).withStyle(ChatFormatting.AQUA));
        playMailArrivalSound(recipient);
        syncBadges(recipient);
        return true;
    }

    private static boolean storeMail(MinecraftServer server, ServerYoikoSavedData savedData, PlayerYoikoData data,
                                     String sender, String type,
                                     String claimKey, String title, String message, List<ItemStack> items) {
        return storeMail(server, savedData, data, sender, type, claimKey, title, message,
                items, 0L, 0L);
    }

    private static boolean storeMail(MinecraftServer server, ServerYoikoSavedData savedData, PlayerYoikoData data,
                                     String sender, String type, String claimKey, String title, String message,
                                     List<ItemStack> items, long attachedGold, long attachedGems) {
        return storeMail(server, savedData, data,
                new PlayerYoikoData.MailEntry(type, claimKey, sender, title, message, items,
                        attachedGold, attachedGems));
    }

    private static boolean storeMail(MinecraftServer server, ServerYoikoSavedData savedData, PlayerYoikoData data,
                                     PlayerYoikoData.MailEntry newMail) {
        String type = newMail.type;
        String claimKey = newMail.claimKey;
        if (!claimKey.isBlank() && hasPending(data, type, claimKey)) {
            return false;
        }
        if (type.startsWith("market_") && !hasMarketMailCapacity(data)) {
            audit(server, "DELIVERY_REJECTED_CAPACITY", data.uuid, data.name,
                    mailAuditDetail(newMail) + ";visible=" + data.mailbox.size()
                            + ";queued=" + data.mailboxOverflow.size());
            return false;
        }
        while (data.mailbox.size() >= PlayerYoikoData.MAX_MAILBOX_MAILS) {
            PlayerYoikoData.MailEntry disposable = data.mailbox.stream()
                    .filter(mail -> mail.read && !mail.hasAttachments())
                    .min(Comparator.comparingLong(mail -> mail.createdAt))
                    .orElse(null);
            if (disposable == null) {
                if (data.mailboxOverflow.size() >= PlayerYoikoData.MAX_MAILBOX_OVERFLOW) {
                    audit(server, "DELIVERY_REJECTED_QUEUE_FULL", data.uuid, data.name,
                            mailAuditDetail(newMail) + ";queued=" + data.mailboxOverflow.size());
                    return false;
                }
                data.mailboxOverflow.add(newMail);
                savedData.markMailboxDirty(data.uuid);
                audit(server, "QUEUED_CAPACITY", data.uuid, data.name,
                        mailAuditDetail(newMail) + ";queued=" + data.mailboxOverflow.size());
                return true;
            }
            data.mailbox.remove(disposable);
            audit(server, "AUTO_PRUNE_READ_EMPTY", data.uuid, data.name, mailAuditDetail(disposable));
        }
        data.mailbox.add(0, newMail);
        savedData.markMailboxDirty(data.uuid);
        return true;
    }

    private static void send(ServerPlayer player, String selectedId) {
        send(player, selectedId, false);
    }

    private static void send(ServerPlayer player, String selectedId, boolean loginRewardScreen) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        WeeklyNewspaperManager.Issue weeklyIssue = WeeklyNewspaperManager.preview(player.server);
        List<PlayerYoikoData.MailEntry> mails = orderedMails(data);
        if (!PlayerYoikoData.MENU_TAB_MAILBOX.equals(data.lastMenuTab)) {
            data.lastMenuTab = PlayerYoikoData.MENU_TAB_MAILBOX;
            savedData.markDirty(player);
        }
        Optional<PlayerYoikoData.MailEntry> requested = findById(mails, selectedId);
        PlayerYoikoData.MailEntry selected = loginRewardScreen
                ? requested.filter(mail -> "daily".equals(mail.type))
                        .or(() -> mails.stream().filter(mail -> "daily".equals(mail.type)).findFirst())
                        .orElse(null)
                : requested.or(() -> mails.isEmpty() ? Optional.empty() : Optional.of(mails.get(0)))
                        .orElse(null);

        List<OpenMailboxPayload.MailSummary> summaries = new ArrayList<>();
        for (PlayerYoikoData.MailEntry mail : mails) {
            summaries.add(new OpenMailboxPayload.MailSummary(
                    mail.id.toString(),
                    displayMailTitle(mail),
                    repairMojibake(mail.sender),
                    formatTime(mail.createdAt),
                    mail.attachmentTypeCount(),
                    mail.read,
                    mail.type
            ));
        }

        RewardManager.DailyBonusState bonus = RewardManager.dailyBonusState(player);
        PacketDistributor.sendToPlayer(player, new OpenMailboxPayload(
                mails.size(),
                summaries,
                selected == null ? "" : selected.id.toString(),
                selected == null ? "yoiko_core.ui.mail.empty" : displayMailTitle(selected),
                selected == null ? "" : repairMojibake(selected.sender),
                selected == null ? "yoiko_core.ui.mail.no_selected" : repairMojibake(selected.message),
                selected == null ? "" : formatTime(selected.createdAt),
                selected == null ? "" : selected.type,
                selected == null ? 0L : selected.sourcePrice,
                selected == null ? 0L : selected.expiresAt,
                selected == null ? "" : selected.rarity,
                selected == null ? List.of() : itemSummaries(selected.items),
                selected == null ? List.of() : selected.copyItems(),
                selected == null ? 0L : selected.attachedGold,
                selected == null ? 0L : selected.attachedGems,
                bonus.boxes(),
                bonus.counts(),
                bonus.rarities(),
                bonus.cosmeticVisuals().stream()
                        .map(visual -> new OpenMailboxPayload.DailyBonusCosmeticVisual(
                                visual.id(), visual.type(), visual.particleCategory()))
                        .toList(),
                bonus.chances().stream()
                        .map(chance -> new OpenMailboxPayload.DailyBonusChance(
                                chance.rarity(), chance.percent(), chance.allOwned()))
                        .toList(),
                bonus.openedMask(),
                bonus.openedCount(),
                bonus.openLimit(),
                bonus.lastOpened(),
                bonus.keys(),
                bonus.maxKeys(),
                bonus.nextKeyText(),
                RewardManager.streakPreview(player),
                data.dailyStreak,
                streakClaimedMask(data.dailyStreak),
                loginRewardScreen,
                newsCards(weeklyIssue),
                weeklyIssue.title(),
                weeklyIssue.message()
        ));
        MenuBadgeManager.sync(player);
    }

    private static List<OpenMailboxPayload.NewsCard> newsCards(WeeklyNewspaperManager.Issue issue) {
        return issue.stories().stream()
                .map(story -> new OpenMailboxPayload.NewsCard(
                        story.kind(), story.title(), story.body(), story.meta(), story.imageId()))
                .toList();
    }

    private static int streakClaimedMask(int dailyStreak) {
        if (dailyStreak <= 0) {
            return 0;
        }
        int claimedInCurrentWeek = Math.floorMod(dailyStreak - 1, 7) + 1;
        return (1 << claimedInCurrentWeek) - 1;
    }

    private static void claim(ServerPlayer player, String mailId) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        Optional<PlayerYoikoData.MailEntry> selected = findById(data.mailbox, mailId);
        if (selected.isEmpty()) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.mail.not_found").withStyle(ChatFormatting.YELLOW));
            return;
        }
        PlayerYoikoData.MailEntry mail = selected.get();
        List<PlayerYoikoData.MailEntry> targets = new ArrayList<>();
        if ("daily".equals(mail.type)) {
            findFirstRewardMail(data).ifPresent(targets::add);
        }
        if (!targets.contains(mail)) {
            targets.add(mail);
        }
        List<ItemStack> combined = targets.stream().flatMap(target -> target.copyItems().stream()).toList();
        if (!canFullyAcceptAttachments(player, combined)) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.mail.inventory_full")
                    .withStyle(ChatFormatting.RED));
            audit(player.server, "CLAIM_REJECTED_INVENTORY_FULL", player.getUUID(),
                    player.getGameProfile().getName(), mailAuditDetail(mail));
            return;
        }
        if (!canFullyAcceptCurrencyAttachments(data, targets)) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.mail.currency_full")
                    .withStyle(ChatFormatting.RED));
            audit(player.server, "CLAIM_REJECTED_CURRENCY_FULL", player.getUUID(),
                    player.getGameProfile().getName(), mailAuditDetail(mail));
            return;
        }
        for (int i = 0; i < targets.size(); i++) {
            claimOneUnchecked(player, data, targets.get(i), i == targets.size() - 1);
        }
        removeClaimedMails(player, data);
    }

    private static int claimAll(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        int claimed = 0;
        for (PlayerYoikoData.MailEntry mail : new ArrayList<>(orderedMails(data))) {
            if (claimOne(player, data, mail, false)) {
                claimed++;
            }
        }
        if (claimed > 0) {
            removeClaimedMails(player, data);
        }
        return claimed;
    }

    private static boolean claimOne(ServerPlayer player, PlayerYoikoData data, PlayerYoikoData.MailEntry mail, boolean notify) {
        if (!mail.hasAttachments()) {
            return false;
        }
        if (!canFullyAcceptAttachments(player, mail.copyItems())) {
            if (notify) {
                player.sendSystemMessage(Component.translatable("yoiko_core.message.mail.inventory_full")
                        .withStyle(ChatFormatting.RED));
            }
            audit(player.server, "CLAIM_REJECTED_INVENTORY_FULL", player.getUUID(),
                    player.getGameProfile().getName(), mailAuditDetail(mail));
            return false;
        }
        if (!canFullyAcceptCurrencyAttachments(data, List.of(mail))) {
            if (notify) {
                player.sendSystemMessage(Component.translatable("yoiko_core.message.mail.currency_full")
                        .withStyle(ChatFormatting.RED));
            }
            audit(player.server, "CLAIM_REJECTED_CURRENCY_FULL", player.getUUID(),
                    player.getGameProfile().getName(), mailAuditDetail(mail));
            return false;
        }
        return claimOneUnchecked(player, data, mail, notify);
    }

    private static boolean claimOneUnchecked(ServerPlayer player, PlayerYoikoData data,
                                             PlayerYoikoData.MailEntry mail, boolean notify) {
        if (!mail.hasAttachments()) {
            return false;
        }
        List<ItemStack> inventorySnapshot = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) {
            inventorySnapshot.add(player.getInventory().getItem(slot).copy());
        }
        long goldBefore = CurrencyManager.balance(data, CurrencyType.GOLD);
        long gemsBefore = CurrencyManager.balance(data, CurrencyType.GEM);
        List<ItemStack> internalMaterials = new ArrayList<>();
        for (ItemStack stack : mail.copyItems()) {
            ItemStack copy = stack.copy();
            if (RelicManager.isInternalMaterial(copy)) {
                internalMaterials.add(copy);
                continue;
            }
            if (!player.getInventory().add(copy)) {
                for (int slot = 0; slot < inventorySnapshot.size(); slot++) {
                    player.getInventory().setItem(slot, inventorySnapshot.get(slot));
                }
                audit(player.server, "CLAIM_INVARIANT_FAILURE", player.getUUID(),
                        player.getGameProfile().getName(), mailAuditDetail(mail));
                YoikoServerCore.LOGGER.error("Mailbox capacity preflight passed but insertion failed for {}: {}",
                        player.getGameProfile().getName(), copy);
                return false;
            }
        }
        long addedGold = CurrencyManager.add(data, CurrencyType.GOLD, mail.attachedGold);
        long addedGems = CurrencyManager.add(data, CurrencyType.GEM, mail.attachedGems);
        if (addedGold != mail.attachedGold || addedGems != mail.attachedGems) {
            for (int slot = 0; slot < inventorySnapshot.size(); slot++) {
                player.getInventory().setItem(slot, inventorySnapshot.get(slot));
            }
            CurrencyManager.set(data, CurrencyType.GOLD, goldBefore);
            CurrencyManager.set(data, CurrencyType.GEM, gemsBefore);
            audit(player.server, "CLAIM_INVARIANT_FAILURE", player.getUUID(),
                    player.getGameProfile().getName(), mailAuditDetail(mail));
            YoikoServerCore.LOGGER.error(
                    "Mailbox currency preflight passed but grant was incomplete for {}: gold={}/{} gems={}/{}",
                    player.getGameProfile().getName(), addedGold, mail.attachedGold,
                    addedGems, mail.attachedGems);
            return false;
        }
        for (ItemStack material : internalMaterials) {
            RelicManager.absorbInternalMaterial(player, material);
        }
        if (mail.attachedGold > 0L || mail.attachedGems > 0L) {
            com.yoiko.core.advancement.YoikoAdvancementManager.recordCurrencyBalances(player);
        }
        if (addedGold > 0L) {
            String action = switch (mail.type) {
                case "daily" -> "RESTED_CALENDAR_GOLD_CREATED";
                case "first_login" -> "FIRST_LOGIN_GOLD_CREATED";
                case "treasure_rabbit" -> "TREASURE_RABBIT_GOLD";
                default -> "";
            };
            if (!action.isBlank()) {
                com.yoiko.core.economy.EconomyManager.recordEconomy(player, action, addedGold,
                        "mail_type=" + mail.type + ";claim_key=" + mail.claimKey);
            }
        }
        if (addedGems > 0L) {
            String action = switch (mail.type) {
                case "advancement" -> "ADVANCEMENT_GEMS_CREATED";
                case "treasure_rabbit" -> "TREASURE_RABBIT_GEMS";
                default -> "";
            };
            if (!action.isBlank()) {
                com.yoiko.core.economy.EconomyManager.recordEconomy(player, action, addedGems,
                        "source=" + mail.type + ";claim_key=" + mail.claimKey + ";delivery=mail");
            }
        }
        mail.items.clear();
        mail.attachedGold = 0L;
        mail.attachedGems = 0L;
        mail.read = true;
        ServerYoikoSavedData.get(player.server).markMailboxDirty(player);
        syncBadges(player);
        if (notify) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.mail.claimed", mailComponent(mail.title)).withStyle(ChatFormatting.GREEN));
            playMailClaimSound(player);
        }
        return true;
    }

    private static void markRead(ServerPlayer player, String mailId) {
        findById(ServerYoikoSavedData.get(player.server).getOrCreate(player).mailbox, mailId).ifPresent(mail -> {
            if (!mail.read) {
                mail.read = true;
                ServerYoikoSavedData.get(player.server).markDirty(player);
                syncBadges(player);
            }
        });
    }

    private static void delete(ServerPlayer player, String mailId) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        Optional<PlayerYoikoData.MailEntry> selected = findById(data.mailbox, mailId);
        if (selected.isEmpty()) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.mail.not_found").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (selected.get().hasAttachments()) {
            player.sendSystemMessage(Component.translatable("yoiko_core.message.mail.delete_has_items").withStyle(ChatFormatting.RED));
            return;
        }
        data.mailbox.remove(selected.get());
        promoteQueued(data);
        savedData.markDirty(player);
        syncBadges(player);
    }

    private static int deleteRead(ServerPlayer player) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        int before = data.mailbox.size();
        removeReadEmpty(data);
        promoteQueued(data);
        int deleted = before - data.mailbox.size();
        if (deleted > 0) {
            savedData.markDirty(player);
            syncBadges(player);
        }
        return deleted;
    }

    private static void playMailClaimSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.55F, 1.25F);
    }

    private static void playMailArrivalSound(ServerPlayer player) {
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.35F);
    }

    private static Component mailComponent(String value) {
        if (value != null && value.startsWith("yoiko_core.")) {
            String[] parts = value.split("\\|", -1);
            if (parts.length == 1) {
                return Component.translatable(value);
            }
            Object[] args = new Object[parts.length - 1];
            for (int index = 0; index < args.length; index++) {
                String argument = parts[index + 1];
                args[index] = argument.startsWith("@")
                        ? Component.translatable(argument.substring(1))
                        : argument;
            }
            return Component.translatable(parts[0], args);
        }
        return Component.literal(value == null ? "" : value);
    }

    private static String displayMailTitle(PlayerYoikoData.MailEntry mail) {
        String repaired = repairMojibake(mail.title);
        if (!isCorruptedText(repaired)) {
            return repaired;
        }
        return switch (mail.type) {
            case "daily" -> "yoiko_core.mail.daily.title";
            case "first_login" -> "yoiko_core.mail.first_login.title";
            case "player" -> "yoiko_core.mail.player.title";
            case "admin" -> !mail.hasAttachments()
                    ? "yoiko_core.mail.admin.message_title"
                    : "yoiko_core.mail.admin.reward_title";
            default -> "yoiko_core.mail.generic.title";
        };
    }

    private static String repairMojibake(String value) {
        if (value == null || value.isBlank() || !isCorruptedText(value)) {
            return value == null ? "" : value;
        }
        try {
            ByteBuffer encoded = java.nio.charset.Charset.forName("MS949")
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            String repaired = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(encoded)
                    .toString();
            return isCorruptedText(repaired) ? value : repaired;
        } catch (CharacterCodingException ignored) {
            return value;
        }
    }

    private static boolean isCorruptedText(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\uFFFD' || (character >= '\u0080' && character <= '\u009F')) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPending(PlayerYoikoData data, String type, String claimKey) {
        return java.util.stream.Stream.concat(data.mailbox.stream(), data.mailboxOverflow.stream())
                .anyMatch(mail -> mail.type.equals(type)
                && mail.claimKey.equals(claimKey)
                && mail.hasAttachments());
    }

    private static boolean canFullyAcceptCurrencyAttachments(PlayerYoikoData data,
                                                              List<PlayerYoikoData.MailEntry> attachments) {
        long goldRoom = CurrencyManager.MAX_BALANCE - CurrencyManager.balance(data, CurrencyType.GOLD);
        long gemRoom = CurrencyManager.MAX_BALANCE - CurrencyManager.balance(data, CurrencyType.GEM);
        for (PlayerYoikoData.MailEntry mail : attachments) {
            if (mail.attachedGold > goldRoom || mail.attachedGems > gemRoom) {
                return false;
            }
            goldRoom -= mail.attachedGold;
            gemRoom -= mail.attachedGems;
        }
        return true;
    }

    private static boolean canFullyAcceptAttachments(ServerPlayer player, List<ItemStack> attachments) {
        List<ItemStack> simulated = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) {
            simulated.add(player.getInventory().getItem(slot).copy());
        }
        for (ItemStack attachment : attachments) {
            if (attachment.isEmpty() || RelicManager.isInternalMaterial(attachment)) {
                continue;
            }
            ItemStack remaining = attachment.copy();
            for (ItemStack target : simulated) {
                if (remaining.isEmpty()) {
                    break;
                }
                if (target.isEmpty() || !ItemStack.isSameItemSameComponents(target, remaining)) {
                    continue;
                }
                int room = Math.max(0, Math.min(target.getMaxStackSize(), 99) - target.getCount());
                int moved = Math.min(room, remaining.getCount());
                target.grow(moved);
                remaining.shrink(moved);
            }
            for (int slot = 0; slot < simulated.size() && !remaining.isEmpty(); slot++) {
                if (!simulated.get(slot).isEmpty()) {
                    continue;
                }
                int moved = Math.min(remaining.getCount(), Math.min(remaining.getMaxStackSize(), 99));
                simulated.set(slot, remaining.copyWithCount(moved));
                remaining.shrink(moved);
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void audit(MinecraftServer server, String action, UUID playerUuid,
                              String playerName, String detail) {
        ServerYoikoAuditSavedData.get(server).addOperational(
                "MAIL", action, playerUuid, playerName, detail);
        YoikoServerCore.LOGGER.warn("[MailAudit] action={} player={} uuid={} detail={}",
                action, playerName, playerUuid, detail);
    }

    private static String mailAuditDetail(PlayerYoikoData.MailEntry mail) {
        String items = mail.items.stream()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                        + "x" + stack.getCount())
                .limit(8)
                .collect(java.util.stream.Collectors.joining(","));
        return "mailId=" + mail.id + ";type=" + mail.type + ";source=" + mail.sourceId
                + ";items=" + items + ";gold=" + mail.attachedGold + ";gems=" + mail.attachedGems;
    }

    private static void syncBadges(ServerPlayer player) {
        MenuBadgeManager.sync(player);
    }

    private static void promoteQueued(PlayerYoikoData data) {
        while (data.mailbox.size() < PlayerYoikoData.MAX_MAILBOX_MAILS
                && !data.mailboxOverflow.isEmpty()) {
            data.mailbox.add(0, data.mailboxOverflow.remove(0));
        }
    }

    private static int removeReadEmpty(PlayerYoikoData data) {
        int before = data.mailbox.size();
        data.mailbox.removeIf(mail -> mail.read && !mail.hasAttachments());
        return before - data.mailbox.size();
    }

    private static void removeClaimedMails(ServerPlayer player, PlayerYoikoData data) {
        int removed = removeReadEmpty(data);
        if (removed <= 0) {
            return;
        }
        promoteQueued(data);
        ServerYoikoSavedData.get(player.server).markDirty(player);
        audit(player.server, "AUTO_REMOVE_CLAIMED", player.getUUID(), player.getGameProfile().getName(),
                "removed=" + removed + ";queued=" + data.mailboxOverflow.size());
        syncBadges(player);
    }

    private static String senderForType(String type) {
        if (type != null && type.startsWith("market_")) {
            return "yoiko_core.mail.sender.market";
        }
        return switch (type == null ? "" : type) {
            case "daily", "first_login", "reward", "daily_bonus", "weekly_reward" -> "yoiko_core.mail.sender.reward";
            case "weekly_newspaper", "incident_result" -> "yoiko_core.mail.sender.newspaper";
            default -> "yoiko_core.mail.sender.system";
        };
    }

    private static List<PlayerYoikoData.MailEntry> orderedMails(PlayerYoikoData data) {
        List<PlayerYoikoData.MailEntry> mails = new ArrayList<>(data.mailbox);
        mails.sort(Comparator.comparingLong((PlayerYoikoData.MailEntry mail) -> mail.createdAt).reversed());
        return mails;
    }

    private static Optional<PlayerYoikoData.MailEntry> findFirstRewardMail(PlayerYoikoData data) {
        return data.mailbox.stream().filter(mail -> "first_login".equals(mail.type)).findFirst();
    }

    private static Optional<PlayerYoikoData.MailEntry> findById(List<PlayerYoikoData.MailEntry> mails, String id) {
        if (id.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID uuid = UUID.fromString(id);
            return mails.stream().filter(mail -> mail.id.equals(uuid)).findFirst();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static List<String> itemSummaries(List<ItemStack> items) {
        List<String> summaries = new ArrayList<>();
        for (ItemStack stack : items) {
            summaries.add(stack.getHoverName().getString() + " x" + stack.getCount());
        }
        return summaries;
    }

    private static String formatTime(long millis) {
        return Instant.ofEpochMilli(millis).atZone(zone()).format(TIME_FORMAT);
    }

    private static ZoneId zone() {
        try {
            return ZoneId.of(YoikoCommonConfig.DAILY_TIMEZONE.get());
        } catch (Exception exception) {
            return ZoneId.of("Asia/Seoul");
        }
    }

    private static int intPart(String[] parts, int index, int fallback) {
        try {
            return Integer.parseInt(part(parts, index));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String part(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    public record QueueStatus(int visible, int queued, long removable) {
    }
}
