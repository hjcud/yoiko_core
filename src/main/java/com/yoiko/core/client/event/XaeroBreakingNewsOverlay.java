package com.yoiko.core.client.event;

import com.yoiko.core.network.BreakingNewsZonePayload;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.WorldMapSession;
import xaero.map.highlight.ChunkHighlighter;

/** Xaero World Map 1.44.x chunk highlighter for the active server incident. */
public final class XaeroBreakingNewsOverlay {
    private static WorldMapSession registeredSession;
    private static long invalidatedRevision = Long.MIN_VALUE;

    private XaeroBreakingNewsOverlay() {
    }

    public static void ensureRegistered() throws ReflectiveOperationException {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable() || session.getMapProcessor() == null) {
            return;
        }
        if (session != registeredSession) {
            XaeroHighlighterRegistration.register(session, new IncidentHighlighter());
            registeredSession = session;
            invalidatedRevision = Long.MIN_VALUE;
        }
        long revision = ClientBreakingNewsZone.revision();
        if (revision != invalidatedRevision) {
            XaeroHighlighterRegistration.invalidateCaches(session);
            invalidatedRevision = revision;
        }
    }

    private static final class IncidentHighlighter extends ChunkHighlighter {
        // A light warm tint keeps terrain readable; only the thin rim is strongly emphasized.
        private static final int FILL = XaeroHighlighterRegistration.rgba(0xF2, 0xB8, 0x64, 0x52);
        private static final int BORDER = XaeroHighlighterRegistration.rgba(0xE8, 0x8F, 0x44, 0xD0);

        private IncidentHighlighter() {
            super(true);
        }

        @Override
        public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
            BreakingNewsZonePayload zone = active(dimension);
            if (zone == null) {
                return false;
            }
            int minimumX = regionX << 9;
            int minimumZ = regionZ << 9;
            return circleIntersectsSquare(zone, minimumX, minimumZ, minimumX + 511, minimumZ + 511);
        }

        @Override
        protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
            if (!chunkIsHighlit(dimension, chunkX, chunkZ)) {
                return null;
            }
            resultStore[0] = FILL;
            resultStore[1] = chunkIsHighlit(dimension, chunkX, chunkZ - 1) ? FILL : BORDER;
            resultStore[2] = chunkIsHighlit(dimension, chunkX + 1, chunkZ) ? FILL : BORDER;
            resultStore[3] = chunkIsHighlit(dimension, chunkX, chunkZ + 1) ? FILL : BORDER;
            resultStore[4] = chunkIsHighlit(dimension, chunkX - 1, chunkZ) ? FILL : BORDER;
            return resultStore;
        }

        @Override
        public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
            BreakingNewsZonePayload zone = active(dimension);
            return zone == null ? 0 : Objects.hash(zone.runId(), zone.centerX(), zone.centerZ(),
                    zone.radius(), zone.eventType(), regionX, regionZ);
        }

        @Override
        public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
            BreakingNewsZonePayload zone = active(dimension);
            if (zone == null) {
                return false;
            }
            int minimumX = chunkX << 4;
            int minimumZ = chunkZ << 4;
            return circleIntersectsSquare(zone, minimumX, minimumZ, minimumX + 15, minimumZ + 15);
        }

        @Override
        public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
            return tooltip();
        }

        @Override
        public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
            return tooltip();
        }

        @Override
        public void addMinimapBlockHighlightTooltips(List<Component> tooltips,
                                                     ResourceKey<Level> dimension,
                                                     int blockX, int blockY, int blockZ) {
            BreakingNewsZonePayload zone = active(dimension);
            if (zone != null && inside(zone, blockX, blockZ)) {
                tooltips.add(tooltip());
            }
        }

        private static BreakingNewsZonePayload active(ResourceKey<Level> dimension) {
            BreakingNewsZonePayload zone = ClientBreakingNewsZone.current();
            return zone.visible() && zone.radius() > 0
                    && dimension.location().toString().equals(zone.dimensionId()) ? zone : null;
        }

        private static Component tooltip() {
            BreakingNewsZonePayload zone = ClientBreakingNewsZone.current();
            if ("fishing_festival".equals(zone.eventType())) {
                return Component.translatable("yoiko_core.map.breaking_zone.fishing");
            }
            if ("mass_outbreak".equals(zone.eventType()) && !zone.targetSpecies().isBlank()) {
                return Component.translatable("yoiko_core.map.breaking_zone.outbreak",
                        speciesName(zone.targetSpecies()));
            }
            return Component.translatable("yoiko_core.map.breaking_zone.rabbits");
        }

        private static Component speciesName(String id) {
            int separator = id.indexOf(':');
            String path = separator >= 0 ? id.substring(separator + 1) : id;
            return Component.translatable("cobblemon.species." + path + ".name");
        }

        private static boolean inside(BreakingNewsZonePayload zone, int x, int z) {
            long dx = (long) x - zone.centerX();
            long dz = (long) z - zone.centerZ();
            return dx * dx + dz * dz <= (long) zone.radius() * zone.radius();
        }

        private static boolean circleIntersectsSquare(BreakingNewsZonePayload zone,
                                                       int minimumX, int minimumZ,
                                                       int maximumX, int maximumZ) {
            long nearestX = Math.max(minimumX, Math.min(maximumX, zone.centerX()));
            long nearestZ = Math.max(minimumZ, Math.min(maximumZ, zone.centerZ()));
            long dx = nearestX - zone.centerX();
            long dz = nearestZ - zone.centerZ();
            return dx * dx + dz * dz <= (long) zone.radius() * zone.radius();
        }
    }
}
