package com.yoiko.core.menu;

import java.util.Map;

/**
 * Allow-list and routing metadata for the compact menu wire protocol.
 *
 * <p>The payload remains a small UTF-8 command plus arguments so search text and UUIDs do not require a
 * packet type per widget. Only the exact command before the first {@code |} is used for routing; cosmetic,
 * relic, or market IDs can never manufacture a different command family.</p>
 */
final class MenuActionRegistry {
    private static final Map<String, Family> ACTIONS = Map.ofEntries(
            entry("menu_open", Family.MENU),
            entry("menu_open_mailbox", Family.MENU),
            entry("storage_open", Family.STORAGE),
            entry("storage_page_prev", Family.STORAGE),
            entry("storage_page_next", Family.STORAGE),
            entry("storage_sort_page", Family.STORAGE),
            entry("storage_toggle_lock", Family.STORAGE),
            entry("turtle_open", Family.TURTLE),
            entry("profile_open", Family.PROFILE),
            entry("profile_select", Family.PROFILE),
            entry("profile_sharing", Family.PROFILE),

            entry("mailbox_open", Family.MAILBOX),
            entry("mailbox_select", Family.MAILBOX),
            entry("mailbox_claim", Family.MAILBOX),
            entry("mailbox_claim_all", Family.MAILBOX),
            entry("mailbox_delete", Family.MAILBOX),
            entry("mailbox_delete_read", Family.MAILBOX),
            entry("mailbox_daily_box", Family.MAILBOX),
            entry("mailbox_login_claim", Family.MAILBOX),
            entry("mailbox_login_box", Family.MAILBOX),

            entry("dex_rarity", Family.DEX),
            entry("dex_page", Family.DEX),
            entry("dex_search", Family.DEX),
            entry("relic_dex_rarity", Family.RELIC_DEX),
            entry("relic_dex_page", Family.RELIC_DEX),
            entry("relic_dex_search", Family.RELIC_DEX),

            entry("relic_open", Family.RELIC),
            entry("relic_roll", Family.RELIC),
            entry("relic_upgrade", Family.RELIC),
            entry("relic_dismantle", Family.RELIC),
            entry("relic_toggle_lock", Family.RELIC),
            entry("relic_equip_slot", Family.RELIC),
            entry("relic_unequip", Family.RELIC),
            entry("relic_unequip_slot", Family.RELIC),
            entry("relic_unequip_storage", Family.RELIC),
            entry("relic_move_storage", Family.RELIC),
            entry("relic_contract_target", Family.RELIC),
            entry("relic_preset", Family.RELIC),

            entry("cosmetic_open", Family.COSMETIC),
            entry("cosmetic_select", Family.COSMETIC),
            entry("cosmetic_rank_equip", Family.COSMETIC),
            entry("cosmetic_equip", Family.COSMETIC),
            entry("cosmetic_exchange", Family.COSMETIC),
            entry("cosmetic_favorite", Family.COSMETIC),
            entry("cosmetic_favorites_only", Family.COSMETIC),
            entry("cosmetic_unequip", Family.COSMETIC),
            entry("cosmetic_unequip_type", Family.COSMETIC),
            entry("cosmetic_clear", Family.COSMETIC),
            entry("cosmetic_view", Family.COSMETIC),

            entry("market_open", Family.MARKET),
            entry("market_view", Family.MARKET),
            entry("market_category", Family.MARKET),
            entry("market_sort", Family.MARKET),
            entry("market_search", Family.MARKET),
            entry("market_refresh", Family.MARKET),
            entry("market_refresh_if_changed", Family.MARKET),
            entry("market_buy_gold", Family.MARKET),
            entry("market_sell_gold", Family.MARKET),
            entry("market_buy_gem", Family.MARKET),
            entry("market_exchange_relic", Family.MARKET),
            entry("market_buy_listing", Family.MARKET),
            entry("market_cancel_listing", Family.MARKET),
            entry("market_register", Family.MARKET),
            entry("market_update_price", Family.MARKET),
            entry("market_remember_price", Family.MARKET),
            entry("market_relist_expired", Family.MARKET)
    );

    private MenuActionRegistry() {
    }

    static Parsed parse(String wireAction) {
        if (wireAction == null || wireAction.isBlank() || wireAction.length() > 256) {
            return null;
        }
        int separator = wireAction.indexOf('|');
        String command = separator < 0 ? wireAction : wireAction.substring(0, separator);
        Family family = ACTIONS.get(command);
        return family == null ? null : new Parsed(command, family, wireAction);
    }

    private static Map.Entry<String, Family> entry(String command, Family family) {
        return Map.entry(command, family);
    }

    enum Family {
        MENU,
        STORAGE,
        TURTLE,
        PROFILE,
        MAILBOX,
        DEX,
        RELIC_DEX,
        RELIC,
        COSMETIC,
        MARKET
    }

    record Parsed(String command, Family family, String wireAction) {
    }
}
