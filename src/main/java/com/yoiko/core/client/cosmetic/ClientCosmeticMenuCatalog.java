package com.yoiko.core.client.cosmetic;

import com.yoiko.core.network.CosmeticMenuCatalogPayload;
import com.yoiko.core.network.OpenCosmeticPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientCosmeticMenuCatalog {
    private static final int PAGE_SIZE = 90;
    private static final Map<String, CosmeticMenuCatalogPayload.Entry> ENTRIES = new HashMap<>();
    private static int revision;

    private ClientCosmeticMenuCatalog() {
    }

    public static void replace(CosmeticMenuCatalogPayload payload) {
        ENTRIES.clear();
        for (CosmeticMenuCatalogPayload.Entry entry : payload.entries()) {
            ENTRIES.put(entry.id(), entry);
        }
        revision = payload.revision();
    }

    public static OpenCosmeticPayload prepare(OpenCosmeticPayload payload) {
        List<OpenCosmeticPayload.Entry> all = new ArrayList<>();
        for (OpenCosmeticPayload.Entry state : payload.cosmetics()) {
            OpenCosmeticPayload.Entry entry = enrich(state);
            if (entry != null) {
                all.add(entry);
            }
        }
        all.sort(Comparator
                .comparingInt((OpenCosmeticPayload.Entry entry) -> entry.favorite() ? 0 : 1)
                .thenComparingInt(entry -> entry.owned() ? 0 : 1)
                .thenComparingInt(entry -> typeOrder(entry.type()))
                .thenComparingInt(entry -> particleOrder(entry.particleCategory()))
                .thenComparingInt(entry -> -sortPriority(entry.id()))
                .thenComparing(OpenCosmeticPayload.Entry::id));

        List<OpenCosmeticPayload.Entry> filtered = all.stream()
                .filter(entry -> payload.category().equals(equipSlot(entry)))
                .filter(entry -> !payload.favoritesOnly() || entry.favorite())
                .toList();
        int totalPages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(payload.page(), totalPages - 1));
        int from = Math.min(page * PAGE_SIZE, filtered.size());
        int to = Math.min(from + PAGE_SIZE, filtered.size());
        List<OpenCosmeticPayload.Entry> pageEntries = new ArrayList<>(filtered.subList(from, to));

        List<OpenCosmeticPayload.Entry> equippedEntries = new ArrayList<>();
        for (OpenCosmeticPayload.EquippedSlot slot : payload.equippedSlots()) {
            if (slot.cosmeticId().isBlank()) {
                continue;
            }
            all.stream().filter(entry -> entry.id().equals(slot.cosmeticId())).findFirst()
                    .filter(entry -> equippedEntries.stream().noneMatch(existing -> existing.id().equals(entry.id())))
                    .ifPresent(equippedEntries::add);
        }
        String selected = pageEntries.stream().anyMatch(entry -> entry.id().equals(payload.selectedId()))
                ? payload.selectedId() : pageEntries.isEmpty() ? "" : pageEntries.get(0).id();
        return new OpenCosmeticPayload(selected, payload.message(), payload.category(), page, totalPages,
                payload.favoritesOnly(), payload.gems(), payload.equippedSlots(), equippedEntries, pageEntries);
    }

    public static void clear() {
        ENTRIES.clear();
        revision = 0;
    }

    public static int revision() {
        return revision;
    }

    public static CosmeticMenuCatalogPayload.Entry entry(String id) {
        return id == null ? null : ENTRIES.get(id);
    }

    private static OpenCosmeticPayload.Entry enrich(OpenCosmeticPayload.Entry state) {
        CosmeticMenuCatalogPayload.Entry staticEntry = ENTRIES.get(state.id());
        if (staticEntry == null) {
            return null;
        }
        return new OpenCosmeticPayload.Entry(
                staticEntry.id(), staticEntry.displayName(), staticEntry.type(), staticEntry.particleCategory(),
                staticEntry.requiredRank(), staticEntry.acquisitionPath(), staticEntry.creator(), staticEntry.rarity(),
                state.owned(), state.equipped(), state.favorite(), staticEntry.color(), staticEntry.gemPrice(),
                staticEntry.modelId(), staticEntry.modelAnchor(), staticEntry.modelPrimaryColor(),
                staticEntry.modelAccentColor());
    }

    private static int sortPriority(String id) {
        CosmeticMenuCatalogPayload.Entry entry = ENTRIES.get(id);
        return entry == null ? 0 : entry.sortPriority();
    }

    private static int typeOrder(String type) {
        return switch (type) {
            case "HEAD" -> 0;
            case "CHEST" -> 1;
            case "PARTICLE" -> 2;
            case "RANK" -> 3;
            default -> 99;
        };
    }

    private static String equipSlot(OpenCosmeticPayload.Entry entry) {
        if (!"PARTICLE".equals(entry.type())) {
            return entry.type();
        }
        return switch (entry.particleCategory()) {
            case "RING", "COMPANION" -> "HEAD";
            case "WINGS", "AURA", "ORBIT" -> "CHEST";
            case "TRAIL" -> "FEET";
            default -> "";
        };
    }

    private static int particleOrder(String category) {
        return switch (category) {
            case "TRAIL" -> 0;
            case "RING" -> 1;
            case "ORBIT" -> 2;
            case "AURA" -> 3;
            case "COMPANION" -> 4;
            case "WINGS" -> 5;
            default -> 99;
        };
    }
}
