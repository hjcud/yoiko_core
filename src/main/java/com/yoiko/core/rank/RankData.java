package com.yoiko.core.rank;

import java.util.List;
import net.minecraft.ChatFormatting;

public record RankData(
        String id,
        String displayName,
        String acquisitionPath,
        int priority,
        ChatFormatting color,
        String iconTexture,
        String nameplateIcon,
        String iconGlyph,
        String chatPrefix,
        String tabPrefix,
        List<String> grantCosmetics,
        boolean allowUserSelect
) {
}
