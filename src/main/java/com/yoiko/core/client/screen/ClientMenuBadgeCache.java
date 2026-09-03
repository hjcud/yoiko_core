package com.yoiko.core.client.screen;

import com.yoiko.core.network.MenuBadgePayload;

public final class ClientMenuBadgeCache {
    private static int mailCount;
    private static int mailCapacity = 20;
    private static int unreadMailCount;
    private static int unseenMarketSales;

    private ClientMenuBadgeCache() {
    }

    public static void update(MenuBadgePayload payload) {
        mailCount = payload.mailCount();
        unreadMailCount = payload.unreadMailCount();
        mailCapacity = Math.max(1, payload.mailCapacity());
        unseenMarketSales = payload.unseenMarketSales();
    }

    public static boolean mailboxNearCapacity() {
        return mailCount >= Math.max(1, mailCapacity - 2);
    }

    public static int unseenMarketSales() {
        return unseenMarketSales;
    }

    public static int unreadMailCount() {
        return unreadMailCount;
    }
}
