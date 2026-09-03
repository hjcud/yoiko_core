package com.yoiko.core.menu;

import com.yoiko.core.mail.MailboxManager;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.data.ServerYoikoSavedData;
import com.yoiko.core.network.MenuActionPayload;
import com.yoiko.core.storage.YoikoStorageManager;
import com.yoiko.core.turtle.TurtleMenuService;
import com.yoiko.core.profile.PublicProfileService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class PlayerMenuManager {
    private PlayerMenuManager() {
    }

    public static void openMenu(ServerPlayer player) {
        PlayerYoikoData data = ServerYoikoSavedData.get(player.server).getOrCreate(player);
        switch (PlayerYoikoData.sanitizeMenuTab(data.lastMenuTab)) {
            case PlayerYoikoData.MENU_TAB_MAILBOX -> MailboxManager.open(player);
            case PlayerYoikoData.MENU_TAB_RELIC -> YoikoRelicMenu.open(player);
            case PlayerYoikoData.MENU_TAB_COSMETIC -> YoikoStorageManager.open(player);
            case PlayerYoikoData.MENU_TAB_MARKET -> YoikoMarketMenu.open(player);
            case PlayerYoikoData.MENU_TAB_TURTLE -> TurtleMenuService.open(player);
            default -> YoikoStorageManager.open(player);
        }
    }

    public static boolean handleAction(ServerPlayer player, MenuActionPayload payload) {
        MenuActionRegistry.Parsed parsed = MenuActionRegistry.parse(payload.action());
        if (parsed == null) {
            MenuSessionManager.rejectUnknown(player, payload);
            return false;
        }
        String action = parsed.wireAction();
        if ("menu_open".equals(parsed.command())) {
            if (MenuSessionManager.allowOpenRequest(player)) {
                openMenu(player);
                return true;
            }
            return false;
        }
        if ("menu_open_mailbox".equals(parsed.command())) {
            if (MenuSessionManager.allowOpenRequest(player)) {
                MailboxManager.open(player);
                return true;
            }
            return false;
        }
        if (parsed.family() == MenuActionRegistry.Family.MARKET) {
            if ("market_open".equals(parsed.command())) {
                if (MenuSessionManager.allowOpenRequest(player)) {
                    YoikoMarketMenu.open(player);
                    return true;
                }
                return false;
            }
            return YoikoMarketMenu.handleAction(player, action, payload.sessionId(), payload.nonce());
        }
        if (!MenuSessionManager.authenticate(player, payload)) {
            return false;
        }
        switch (parsed.command()) {
            case "storage_open" -> {
                YoikoStorageManager.open(player);
                return true;
            }
            case "turtle_open" -> {
                TurtleMenuService.open(player);
                return true;
            }
            case "profile_open" -> {
                return PublicProfileService.open(player, player.getUUID());
            }
            case "storage_page_prev" -> {
                boolean changed = YoikoStorageManager.openPreviousPage(player);
                if (changed) playStoragePageSound(player);
                return changed;
            }
            case "storage_page_next" -> {
                boolean changed = YoikoStorageManager.openNextPage(player);
                if (changed) playStoragePageSound(player);
                return changed;
            }
            case "storage_sort_page" -> {
                YoikoStorageManager.sortCurrentPage(player);
                return true;
            }
            default -> {
            }
        }
        if ("storage_toggle_lock".equals(parsed.command())) {
            try {
                YoikoStorageManager.toggleSlotLock(player, Integer.parseInt(action.substring(action.indexOf('|') + 1)));
            } catch (NumberFormatException ignored) {
            }
            return true;
        }

        return switch (parsed.family()) {
            case MAILBOX -> MailboxManager.handleAction(player, action);
            case DEX -> YoikoDexMenu.handleAction(player, action);
            case RELIC_DEX -> YoikoRelicDexMenu.handleAction(player, action);
            case RELIC -> YoikoRelicMenu.handleAction(player, action);
            case COSMETIC -> YoikoCosmeticMenu.handleAction(player, action);
            case PROFILE -> PublicProfileService.handleAction(player, action);
            case MENU, STORAGE, TURTLE, MARKET -> false;
        };
    }

    private static void playStoragePageSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHULKER_BOX_OPEN, SoundSource.PLAYERS, 0.35F, 0.8F);
    }

}
