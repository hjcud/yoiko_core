package com.yoiko.core.client.event;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import xaero.map.WorldMapSession;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.HighlighterRegistry;

/** Compatibility bridge for Xaero's registry, which is frozen before a world session is exposed. */
final class XaeroHighlighterRegistration {
    private XaeroHighlighterRegistration() {
    }

    static void register(WorldMapSession session, AbstractHighlighter highlighter)
            throws ReflectiveOperationException {
        HighlighterRegistry registry = session.getMapProcessor().getHighlighterRegistry();
        List<AbstractHighlighter> registered = registry.getHighlighters();
        for (AbstractHighlighter existing : registered) {
            if (existing.getClass() == highlighter.getClass()) {
                return;
            }
        }

        // Xaero calls HighlighterRegistry.end() before WorldMapSession becomes accessible. Make a
        // mutable copy, add our optional integration, and freeze it again to preserve its contract.
        Field field = HighlighterRegistry.class.getDeclaredField("highlighters");
        field.setAccessible(true);
        field.set(registry, new ArrayList<>(registered));
        registry.register(highlighter);
        registry.end();
    }

    static void invalidateCaches(WorldMapSession session) {
        if (session == null || session.getMapProcessor() == null
                || session.getMapProcessor().getMapWorld() == null) {
            return;
        }
        session.getMapProcessor().getMapWorld().clearAllCachedHighlightHashes();
    }

    /** Xaero 1.45 stores highlight pixels as {@code 0xBBGGRRAA}; alpha is the low byte. */
    static int rgba(int red, int green, int blue, int alpha) {
        return (blue & 0xFF) << 24
                | (green & 0xFF) << 16
                | (red & 0xFF) << 8
                | (alpha & 0xFF);
    }
}
