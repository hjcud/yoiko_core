package com.yoiko.core.network;

import java.util.Set;

public final class MenuActionFeedback {
    private static final Set<String> TRACKED = Set.of(
            "storage_sort_page",
            "mailbox_claim", "mailbox_claim_all", "mailbox_delete", "mailbox_delete_read", "mailbox_daily_box",
            "mailbox_login_claim", "mailbox_login_box",
            "relic_roll", "relic_toggle_lock", "relic_move_storage", "relic_contract_target", "relic_equip_slot", "relic_unequip",
            "relic_upgrade", "relic_dismantle", "relic_batch_dismantle", "relic_preset",
            "cosmetic_exchange", "cosmetic_rank_equip", "cosmetic_equip", "cosmetic_unequip",
            "cosmetic_unequip_type", "cosmetic_clear",
            "profile_sharing",
            "market_buy_gold", "market_sell_gold", "market_buy_gem", "market_exchange_relic", "market_buy_listing",
            "market_cancel_listing", "market_update_price", "market_relist_expired", "market_register"
    );

    private MenuActionFeedback() {
    }

    public static boolean isTracked(String action) {
        return TRACKED.contains(root(action));
    }

    public static String scope(String action) {
        String root = root(action);
        int separator = root.indexOf('_');
        return separator <= 0 ? "menu" : root.substring(0, separator);
    }

    public static String root(String action) {
        if (action == null) {
            return "";
        }
        int separator = action.indexOf('|');
        return separator < 0 ? action : action.substring(0, separator);
    }
}
