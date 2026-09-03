package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record OpenMailboxPayload(
        int totalMails,
        List<MailSummary> mails,
        String selectedId,
        String selectedTitle,
        String selectedSender,
        String selectedMessage,
        String selectedCreatedAt,
        String selectedType,
        long selectedSourcePrice,
        long selectedExpiresAt,
        String selectedRarity,
        List<String> selectedItems,
        List<ItemStack> selectedItemStacks,
        long selectedAttachedGold,
        long selectedAttachedGems,
        List<ItemStack> dailyBonusBoxes,
        List<Integer> dailyBonusCounts,
        List<String> dailyBonusRarities,
        List<DailyBonusCosmeticVisual> dailyBonusCosmeticVisuals,
        List<DailyBonusChance> dailyBonusChances,
        int dailyBonusOpenedMask,
        int dailyBonusOpenedCount,
        int dailyBonusOpenLimit,
        int dailyBonusLastOpened,
        int dailyBonusKeys,
        int dailyBonusMaxKeys,
        String dailyBonusNextKeyText,
        List<ItemStack> streakRewards,
        int dailyStreak,
        int streakClaimedMask,
        boolean loginRewardScreen,
        List<NewsCard> newsCards,
        String weeklyNewspaperTitle,
        String weeklyNewspaperMessage
) implements CustomPacketPayload {
    public static final Type<OpenMailboxPayload> TYPE = new Type<>(YoikoServerCore.id("open_mailbox"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMailboxPayload> STREAM_CODEC =
            CustomPacketPayload.codec(OpenMailboxPayload::write, OpenMailboxPayload::new);

    private OpenMailboxPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                readMails(buffer),
                buffer.readUtf(64),
                buffer.readUtf(128),
                buffer.readUtf(64),
                buffer.readUtf(1024),
                buffer.readUtf(64),
                buffer.readUtf(32),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readUtf(32),
                readStrings(buffer),
                readStacks(buffer),
                buffer.readVarLong(),
                buffer.readVarLong(),
                readStacks(buffer),
                readIntegers(buffer),
                readStrings(buffer),
                readDailyBonusCosmeticVisuals(buffer),
                readDailyBonusChances(buffer),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(64),
                readStacks(buffer),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                readNewsCards(buffer),
                buffer.readUtf(256),
                buffer.readUtf(4096)
        );
    }

    public OpenMailboxPayload {
        mails = List.copyOf(mails);
        selectedItems = List.copyOf(selectedItems);
        selectedItemStacks = copyStacks(selectedItemStacks);
        selectedAttachedGold = Math.max(0L, selectedAttachedGold);
        selectedAttachedGems = Math.max(0L, selectedAttachedGems);
        dailyBonusBoxes = copyStacks(dailyBonusBoxes);
        dailyBonusCounts = List.copyOf(dailyBonusCounts);
        dailyBonusRarities = List.copyOf(dailyBonusRarities);
        dailyBonusCosmeticVisuals = List.copyOf(dailyBonusCosmeticVisuals);
        dailyBonusChances = List.copyOf(dailyBonusChances);
        streakRewards = copyStacks(streakRewards);
        newsCards = List.copyOf(newsCards);
        weeklyNewspaperTitle = weeklyNewspaperTitle == null ? "" : weeklyNewspaperTitle;
        weeklyNewspaperMessage = weeklyNewspaperMessage == null ? "" : weeklyNewspaperMessage;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(totalMails);
        buffer.writeVarInt(mails.size());
        for (MailSummary mail : mails) {
            buffer.writeUtf(mail.id(), 64);
            buffer.writeUtf(mail.title(), 128);
            buffer.writeUtf(mail.sender(), 64);
            buffer.writeUtf(mail.createdAt(), 64);
            buffer.writeVarInt(mail.itemCount());
            buffer.writeBoolean(mail.read());
            buffer.writeUtf(mail.type(), 32);
        }
        buffer.writeUtf(selectedId, 64);
        buffer.writeUtf(selectedTitle, 128);
        buffer.writeUtf(selectedSender, 64);
        buffer.writeUtf(selectedMessage, 1024);
        buffer.writeUtf(selectedCreatedAt, 64);
        buffer.writeUtf(selectedType, 32);
        buffer.writeVarLong(Math.max(0L, selectedSourcePrice));
        buffer.writeVarLong(Math.max(0L, selectedExpiresAt));
        buffer.writeUtf(selectedRarity, 32);
        buffer.writeVarInt(selectedItems.size());
        for (String item : selectedItems) {
            buffer.writeUtf(item, 256);
        }
        buffer.writeVarInt(selectedItemStacks.size());
        for (ItemStack stack : selectedItemStacks) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
        }
        buffer.writeVarLong(Math.max(0L, selectedAttachedGold));
        buffer.writeVarLong(Math.max(0L, selectedAttachedGems));
        buffer.writeVarInt(dailyBonusBoxes.size());
        for (ItemStack stack : dailyBonusBoxes) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
        }
        buffer.writeVarInt(dailyBonusCounts.size());
        for (int count : dailyBonusCounts) {
            buffer.writeVarInt(Math.max(0, count));
        }
        buffer.writeVarInt(dailyBonusRarities.size());
        for (String rarity : dailyBonusRarities) {
            buffer.writeUtf(rarity, 32);
        }
        buffer.writeVarInt(dailyBonusCosmeticVisuals.size());
        for (DailyBonusCosmeticVisual visual : dailyBonusCosmeticVisuals) {
            buffer.writeUtf(visual.id(), 128);
            buffer.writeUtf(visual.type(), 32);
            buffer.writeUtf(visual.particleCategory(), 32);
        }
        buffer.writeVarInt(dailyBonusChances.size());
        for (DailyBonusChance chance : dailyBonusChances) {
            buffer.writeUtf(chance.rarity(), 32);
            buffer.writeDouble(chance.percent());
            buffer.writeBoolean(chance.allOwned());
        }
        buffer.writeVarInt(dailyBonusOpenedMask);
        buffer.writeVarInt(dailyBonusOpenedCount);
        buffer.writeVarInt(dailyBonusOpenLimit);
        buffer.writeVarInt(dailyBonusLastOpened);
        buffer.writeVarInt(dailyBonusKeys);
        buffer.writeVarInt(dailyBonusMaxKeys);
        buffer.writeUtf(dailyBonusNextKeyText, 64);
        buffer.writeVarInt(streakRewards.size());
        for (ItemStack stack : streakRewards) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
        }
        buffer.writeVarInt(dailyStreak);
        buffer.writeVarInt(streakClaimedMask);
        buffer.writeBoolean(loginRewardScreen);
        buffer.writeVarInt(newsCards.size());
        for (NewsCard card : newsCards) {
            buffer.writeUtf(card.kind(), 32);
            buffer.writeUtf(card.title(), 256);
            buffer.writeUtf(card.body(), 512);
            buffer.writeUtf(card.meta(), 128);
            buffer.writeUtf(card.imageId(), 128);
        }
        buffer.writeUtf(weeklyNewspaperTitle, 256);
        buffer.writeUtf(weeklyNewspaperMessage, 4096);
    }

    private static List<MailSummary> readMails(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<MailSummary> mails = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            mails.add(new MailSummary(
                    buffer.readUtf(64),
                    buffer.readUtf(128),
                    buffer.readUtf(64),
                    buffer.readUtf(64),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readUtf(32)
            ));
        }
        return mails;
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> strings = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            strings.add(buffer.readUtf(256));
        }
        return strings;
    }

    private static List<Integer> readIntegers(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Integer> integers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            integers.add(Math.max(0, buffer.readVarInt()));
        }
        return integers;
    }

    private static List<ItemStack> readStacks(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<ItemStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private static List<DailyBonusChance> readDailyBonusChances(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<DailyBonusChance> chances = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            chances.add(new DailyBonusChance(buffer.readUtf(32), buffer.readDouble(), buffer.readBoolean()));
        }
        return chances;
    }

    private static List<DailyBonusCosmeticVisual> readDailyBonusCosmeticVisuals(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<DailyBonusCosmeticVisual> visuals = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            visuals.add(new DailyBonusCosmeticVisual(
                    buffer.readUtf(128),
                    buffer.readUtf(32),
                    buffer.readUtf(32)
            ));
        }
        return visuals;
    }

    private static List<NewsCard> readNewsCards(RegistryFriendlyByteBuf buffer) {
        int encodedSize = buffer.readVarInt();
        int retainedSize = Math.min(encodedSize, 8);
        List<NewsCard> cards = new ArrayList<>(retainedSize);
        for (int i = 0; i < encodedSize; i++) {
            NewsCard card = new NewsCard(
                    buffer.readUtf(32),
                    buffer.readUtf(256),
                    buffer.readUtf(512),
                    buffer.readUtf(128),
                    buffer.readUtf(128)
            );
            if (i < retainedSize) {
                cards.add(card);
            }
        }
        return cards;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                copies.add(stack.copy());
            }
        }
        return List.copyOf(copies);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record MailSummary(String id, String title, String sender, String createdAt, int itemCount, boolean read, String type) {
    }

    public record DailyBonusChance(String rarity, double percent, boolean allOwned) {
    }

    public record DailyBonusCosmeticVisual(String id, String type, String particleCategory) {
    }

    public record NewsCard(String kind, String title, String body, String meta, String imageId) {
    }
}
