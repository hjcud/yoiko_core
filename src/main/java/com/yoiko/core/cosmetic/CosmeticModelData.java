package com.yoiko.core.cosmetic;

public record CosmeticModelData(
        String modelId,
        CosmeticAnchor anchor,
        int primaryColor,
        int accentColor
) {
    public static final CosmeticModelData NONE =
            new CosmeticModelData("", CosmeticAnchor.NONE, 0xFFFFFFFF, 0xFFFFFFFF);

    public CosmeticModelData {
        modelId = modelId == null ? "" : modelId;
        anchor = anchor == null ? CosmeticAnchor.NONE : anchor;
    }

    public boolean present() {
        return !modelId.isBlank() && anchor != CosmeticAnchor.NONE;
    }
}
