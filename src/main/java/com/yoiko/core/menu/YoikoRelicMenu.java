package com.yoiko.core.menu;

import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.network.OpenRelicPayload;
import com.yoiko.core.relic.RelicManager;
import com.yoiko.core.relic.RelicEquipGroup;
import com.yoiko.core.registry.YoikoItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class YoikoRelicMenu {
    private YoikoRelicMenu() {
    }

    public static boolean handleAction(ServerPlayer player, String action) {
        if (!action.startsWith("relic_")) {
            return false;
        }
        String[] parts = action.split("\\|", 4);
        switch (parts[0]) {
            case "relic_open" -> open(player, part(parts, 1), "");
            case "relic_equip_slot" -> equipSlot(player, uuidPart(parts, 1), intPart(parts, 2, -1));
            case "relic_unequip" -> unequip(player, uuidPart(parts, 1));
            case "relic_unequip_storage" -> unequipToStorage(
                    player, uuidPart(parts, 1), intPart(parts, 2, -1));
            case "relic_unequip_slot" -> {
                RelicManager.EquipResult result = RelicManager.unequipSlot(player, intPart(parts, 1, -1));
                open(player, result.selectedUuid(), result.message());
            }
            case "relic_dismantle" -> dismantle(player, uuidPart(parts, 1));
            case "relic_toggle_lock" -> {
                UUID uuid = uuidPart(parts, 1);
                if (uuid != null) {
                    RelicManager.toggleLock(player, uuid);
                }
                open(player, uuid == null ? "" : uuid.toString(), "");
            }
            case "relic_move_storage" -> {
                UUID uuid = uuidPart(parts, 1);
                RelicManager.moveStoredRelic(player, uuid, intPart(parts, 2, -1));
                open(player, uuid == null ? "" : uuid.toString(), "");
            }
            case "relic_roll" -> {
                RelicManager.RollResult result = RelicManager.rollFromInventory(player);
                open(player, result.selectedUuid(), result.message());
            }
            case "relic_upgrade" -> upgrade(player, uuidPart(parts, 1), Boolean.parseBoolean(part(parts, 2)));
            case "relic_contract_target" -> selectContractTarget(player, uuidPart(parts, 1));
            case "relic_preset" -> switchPreset(player, intPart(parts, 1, -1));
            default -> open(player, "", "yoiko_core.message.relic.unknown_action");
        }
        return true;
    }

    public static void open(ServerPlayer player) {
        MenuSessionManager.open(player, "relic");
        open(player, "", "");
    }

    private static void equipSlot(ServerPlayer player, UUID uuid, int slot) {
        if (uuid == null) {
            open(player, "", "yoiko_core.message.relic.select_equip");
            return;
        }
        RelicManager.EquipResult result = RelicManager.equipToSlot(player, uuid, slot);
        open(player, result.selectedUuid().isBlank() ? uuid.toString() : result.selectedUuid(), result.message());
    }

    private static void unequip(ServerPlayer player, UUID uuid) {
        if (uuid == null) {
            open(player, "", "yoiko_core.message.relic.select_unequip");
            return;
        }
        RelicManager.EquipResult result = RelicManager.unequip(player, uuid);
        open(player, result.selectedUuid().isBlank() ? uuid.toString() : result.selectedUuid(), result.message());
    }

    private static void unequipToStorage(ServerPlayer player, UUID uuid, int storageSlot) {
        if (uuid == null) {
            open(player, "", "yoiko_core.message.relic.select_unequip");
            return;
        }
        RelicManager.EquipResult result = RelicManager.unequipToStorageSlot(player, uuid, storageSlot);
        open(player, result.selectedUuid().isBlank() ? uuid.toString() : result.selectedUuid(), result.message());
    }

    private static void dismantle(ServerPlayer player, UUID uuid) {
        if (uuid == null) {
            open(player, "", "yoiko_core.message.relic.select_dismantle");
            return;
        }
        RelicManager.DismantleResult result = RelicManager.dismantle(player, uuid);
        open(player, result.selectedUuid().isBlank() ? "" : result.selectedUuid(), result.message());
    }

    public static void batchDismantle(ServerPlayer player, List<UUID> uuids) {
        RelicManager.BatchDismantleResult result = RelicManager.dismantleBatch(player, uuids);
        open(player, "", result.message());
    }

    private static void upgrade(ServerPlayer player, UUID uuid, boolean protectedAttempt) {
        if (uuid == null) {
            open(player, "", "yoiko_core.message.relic.select_upgrade");
            return;
        }
        RelicManager.UpgradeResult result = RelicManager.upgradeWithResult(player, uuid, protectedAttempt);
        open(player, result.selectedUuid().isBlank() ? uuid.toString() : result.selectedUuid(), result.message());
        RelicManager.sendUpgradeResultFeedback(player, result);
    }

    private static void selectContractTarget(ServerPlayer player, UUID uuid) {
        RelicManager.EquipResult result = RelicManager.selectChromaticContractTarget(player, uuid);
        open(player, result.selectedUuid(), result.message());
    }

    private static void switchPreset(ServerPlayer player, int presetIndex) {
        RelicManager.EquipResult result = RelicManager.switchRelicPreset(player, presetIndex);
        open(player, "", result.message());
    }

    private static void open(ServerPlayer player, String requestedUuid, String message) {
        ServerYoikoSavedData savedData = ServerYoikoSavedData.get(player.server);
        PlayerYoikoData data = savedData.getOrCreate(player);
        if (!PlayerYoikoData.MENU_TAB_RELIC.equals(data.lastMenuTab)) {
            data.lastMenuTab = PlayerYoikoData.MENU_TAB_RELIC;
            savedData.markDirty(player);
        }
        List<RelicManager.RelicView> views = RelicManager.views(player);
        String selected = selectUuid(views, requestedUuid);
        List<OpenRelicPayload.Entry> entries = new ArrayList<>();
        for (RelicManager.RelicView view : views) {
            entries.add(new OpenRelicPayload.Entry(
                    view.uuid(),
                    view.relicId(),
                    RelicManager.displayNameKey(view.relicId(), view.displayName()),
                    view.effect(),
                    view.secondaryEffect(),
                    view.rarity(),
                    view.level(),
                    view.effectiveLevel(),
                    view.value(),
                    view.nextValue(),
                    view.secondaryValue(),
                    view.secondaryNextValue(),
                    view.greatSuccess(),
                    view.success(),
                    view.downgrade(),
                    view.destroy(),
                    view.equippedSlot(),
                    view.scrapValue(),
                    view.locked(),
                    view.upgradeable(),
                    view.primaryUpgradeable(),
                    view.secondaryUpgradeable(),
                    view.storageSlot(),
                    view.primaryEffectSuppressed(),
                    view.secondaryEffectSuppressed()
            ));
        }
        int ticketCount = RelicManager.countItem(player, YoikoItems.RELIC_GACHA_TICKET.get());
        int upgradeCrystalCount = RelicManager.upgradeCrystalCount(player);
        int scrapCount = RelicManager.scrapCount(player);
        int specialEquippedCount = RelicManager.specialEquippedCount(player);
        String contractTargetUuid = RelicManager.chromaticContractTargetUuid(player);
        List<String> equippedSlots = RelicManager.equippedSlots(player);
        int activePreset = RelicManager.activeRelicPreset(player);
        List<Integer> presetFilledCounts = RelicManager.relicPresetFilledCounts(player);
        PacketDistributor.sendToPlayer(player, new OpenRelicPayload(
                    ticketCount,
                    upgradeCrystalCount,
                    PlayerYoikoData.MAX_OWNED_RELICS,
                    scrapCount,
                    PlayerYoikoData.RELIC_PROTECTION_SCRAP_COST,
                    specialEquippedCount,
                    RelicEquipGroup.SPECIAL.maxEquipped(),
                    contractTargetUuid,
                    activePreset,
                    presetFilledCounts,
                    selected,
                    message,
                    equippedSlots,
                    entries
            ));
    }

    private static String selectUuid(List<RelicManager.RelicView> views, String requestedUuid) {
        if (!requestedUuid.isBlank()) {
            for (RelicManager.RelicView view : views) {
                if (view.uuid().equals(requestedUuid)) {
                    return requestedUuid;
                }
            }
        }
        return views.isEmpty() ? "" : views.get(0).uuid();
    }

    private static UUID uuidPart(String[] parts, int index) {
        String value = part(parts, index);
        if (value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
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
}
