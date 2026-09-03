package com.yoiko.core.storage;

import com.yoiko.core.cosmetic.CosmeticData;
import com.yoiko.core.cosmetic.CosmeticEquipSlot;
import com.yoiko.core.cosmetic.CosmeticManager;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.menu.MenuSessionManager;
import com.yoiko.core.relic.RelicManager;
import com.yoiko.core.rank.RankData;
import com.yoiko.core.rank.RankManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public final class YoikoStorageManager {
    private YoikoStorageManager() {
    }

    public static boolean open(ServerPlayer player) {
        MenuSessionManager.open(player, "storage");
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        return openPage(player, data.yoikoStoragePage);
    }

    public static boolean openNextPage(ServerPlayer player) {
        if (!(player.containerMenu instanceof YoikoStorageMenu menu)
                || !menu.getCarried().isEmpty()
                || menu.getPage() + 1 >= menu.getTotalPages()) {
            return false;
        }
        return openPage(player, menu.getPage() + 1);
    }

    public static boolean openPreviousPage(ServerPlayer player) {
        if (!(player.containerMenu instanceof YoikoStorageMenu menu)
                || !menu.getCarried().isEmpty()
                || menu.getPage() <= 0) {
            return false;
        }
        return openPage(player, menu.getPage() - 1);
    }

    private static boolean openPage(ServerPlayer player, int requestedPage) {
        int activeSlots = activeSlots(player);
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        int page = PlayerYoikoData.clampYoikoStoragePage(requestedPage, activeSlots);
        data.ensureYoikoStorageSize(activeSlots);
        boolean dirty = false;
        if (!PlayerYoikoData.MENU_TAB_STORAGE.equals(data.lastMenuTab)) {
            data.lastMenuTab = PlayerYoikoData.MENU_TAB_STORAGE;
            dirty = true;
        }
        if (data.yoikoStoragePage != page) {
            data.yoikoStoragePage = page;
            dirty = true;
        }
        if (dirty) {
            savedData.markDirty(player);
        }

        YoikoStorageContainer container = new YoikoStorageContainer(player, savedData, data, activeSlots, page);
        YoikoStorageMenu.StatusSummary status = statusSummary(player, data);

        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new YoikoStorageMenu(containerId, inventory, container, status),
                        Component.translatable("yoiko_core.screen.storage")
                ),
                buffer -> {
                    buffer.writeVarInt(activeSlots);
                    buffer.writeVarInt(page);
                    status.write(buffer);
                    buffer.writeLong(lockMask(data, page));
                }
        );
        return true;
    }

    private static YoikoStorageMenu.StatusSummary statusSummary(ServerPlayer player, PlayerYoikoData data) {
        RankData activeRank = RankManager.get(data.activeRank);
        Integer rankColor = activeRank == null ? null : activeRank.color().getColor();
        RelicStatus relics = relicStatus(player);
        String feetCosmeticId = data.equippedCosmetics.getOrDefault(CosmeticEquipSlot.FEET, "");
        CosmeticData feetCosmetic = CosmeticManager.get(feetCosmeticId);
        return new YoikoStorageMenu.StatusSummary(
                data.mailbox.size(),
                activeRank == null ? "" : data.activeRank,
                activeRank == null ? "" : activeRank.displayName(),
                rankColor == null ? 0xFFFFFF : rankColor,
                relics.count(),
                PlayerYoikoData.RELIC_EQUIP_SLOT_COUNT,
                relics.entries(),
                data.equippedCosmetics.getOrDefault(CosmeticEquipSlot.HEAD, ""),
                data.equippedCosmetics.getOrDefault(CosmeticEquipSlot.CHEST, ""),
                feetCosmeticId,
                feetCosmetic == null ? "" : feetCosmetic.rarity().name(),
                feetCosmetic == null ? "NONE" : feetCosmetic.particleCategory().name()
        );
    }

    private static RelicStatus relicStatus(ServerPlayer player) {
        List<YoikoStorageMenu.RelicSummaryEntry> equipped = new ArrayList<>();
        int count = 0;
        for (RelicManager.RelicView view : RelicManager.views(player)) {
            if (view.equippedSlot() < 0) {
                continue;
            }
            count++;
            if (equipped.size() < 5) {
                equipped.add(new YoikoStorageMenu.RelicSummaryEntry(
                        RelicManager.displayNameKey(view.relicId(), view.displayName()),
                        view.effect(),
                        view.secondaryEffect(),
                        view.rarity(),
                        view.level(),
                        view.effectiveLevel(),
                        view.value(),
                        view.secondaryValue(),
                        view.scrapValue(),
                        view.primaryEffectSuppressed(),
                        view.secondaryEffectSuppressed()
                ));
            }
        }
        return new RelicStatus(count, List.copyOf(equipped));
    }

    public static Component statusComponent(ServerPlayer player) {
        int activeSlots = activeSlots(player);
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        data.ensureYoikoStorageSize(activeSlots);
        int usedSlots = usedSlots(data, activeSlots);
        int currentPage = PlayerYoikoData.clampYoikoStoragePage(data.yoikoStoragePage, activeSlots) + 1;
        int totalPages = PlayerYoikoData.yoikoStoragePageCount(activeSlots);
        return Component.translatable("yoiko_core.command.storage.status",
                usedSlots, activeSlots, currentPage, totalPages);
    }

    public static int activeSlots(ServerPlayer player) {
        int slots = PlayerYoikoData.MIN_YOIKO_STORAGE_SLOTS
                + (int) Math.floor(RelicManager.applyEffect(player, "yoiko_bag_slots", 0.0D));
        return PlayerYoikoData.clampYoikoStorageSlots(slots);
    }

    public static boolean toggleSlotLock(ServerPlayer player, int localSlot) {
        if (!(player.containerMenu instanceof YoikoStorageMenu menu)
                || !menu.getCarried().isEmpty()
                || localSlot < 0 || localSlot >= menu.getVisibleActiveSlots()) {
            return false;
        }
        boolean changed = menu.toggleStorageSlotLock(localSlot);
        if (changed) {
            openPage(player, menu.getPage());
        }
        return changed;
    }

    public static boolean sortCurrentPage(ServerPlayer player) {
        if (!(player.containerMenu instanceof YoikoStorageMenu menu)) {
            return false;
        }
        if (!menu.getCarried().isEmpty()) {
            return false;
        }
        boolean sorted = menu.sortCurrentPage();
        if (!sorted) {
            com.yoiko.core.data.ServerYoikoAuditSavedData.get(player.server).addOperational(
                    "STORAGE", "SORT_INVARIANT_REJECTED", player.getUUID(),
                    player.getGameProfile().getName(), "page=" + menu.getPage());
            player.sendSystemMessage(Component.translatable("yoiko_core.message.storage.sort_failed"));
            return false;
        }
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.45F, 1.3F);
        return true;
    }

    private static long lockMask(PlayerYoikoData data, int page) {
        long mask = 0L;
        int start = page * PlayerYoikoData.YOIKO_STORAGE_PAGE_SIZE;
        for (int local = 0; local < PlayerYoikoData.YOIKO_STORAGE_PAGE_SIZE; local++) {
            if (data.lockedYoikoStorageSlots.contains(start + local)) {
                mask |= 1L << local;
            }
        }
        return mask;
    }

    private static int usedSlots(PlayerYoikoData data, int activeSlots) {
        int used = 0;
        for (int i = 0; i < activeSlots && i < data.yoikoStorage.size(); i++) {
            if (!data.yoikoStorage.get(i).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    private record RelicStatus(int count, List<YoikoStorageMenu.RelicSummaryEntry> entries) {
    }
}
