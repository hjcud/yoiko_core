package com.yoiko.core.client.event;

import com.yoiko.core.event.IncidentCategory;
import com.yoiko.core.network.TreasureRabbitSearchZonePayload;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.WorldMapSession;
import xaero.map.highlight.ChunkHighlighter;

/** Xaero World Map 1.44.x highlighter for private, approximate Treasure Rabbit search areas. */
public final class XaeroTreasureRabbitOverlay {
    private static WorldMapSession registeredSession;
    private static long invalidatedRevision = Long.MIN_VALUE;

    private XaeroTreasureRabbitOverlay() {
    }

    public static void ensureRegistered() throws ReflectiveOperationException {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable() || session.getMapProcessor() == null) {
            return;
        }
        if (session != registeredSession) {
            XaeroHighlighterRegistration.register(session, new RabbitSearchHighlighter());
            registeredSession = session;
            invalidatedRevision = Long.MIN_VALUE;
        }
        long revision = ClientTreasureRabbitSearchZones.revision();
        if (revision != invalidatedRevision) {
            XaeroHighlighterRegistration.invalidateCaches(session);
            invalidatedRevision = revision;
        }
    }

    private static final class RabbitSearchHighlighter extends ChunkHighlighter {
        // Fill alpha stays below 35% so terrain remains readable. The rim is intentionally stronger.
        private static final Colors GOLD = new Colors(
                XaeroHighlighterRegistration.rgba(0xFF, 0xC4, 0x40, 0x48),
                XaeroHighlighterRegistration.rgba(0xFF, 0xD6, 0x54, 0xD8));
        private static final Colors RADIANT = new Colors(
                XaeroHighlighterRegistration.rgba(0xDC, 0xF4, 0xFF, 0x58),
                XaeroHighlighterRegistration.rgba(0xF7, 0xDE, 0xFF, 0xD8));
        private static final Colors MIRROR = new Colors(
                XaeroHighlighterRegistration.rgba(0xC8, 0xF4, 0xFF, 0x4E),
                XaeroHighlighterRegistration.rgba(0xD8, 0xF5, 0xFF, 0xD8));

        private RabbitSearchHighlighter() {
            super(true);
        }

        @Override
        public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
            int minimumX = regionX << 9;
            int minimumZ = regionZ << 9;
            String dimensionId = dimension.location().toString();
            for (TreasureRabbitSearchZonePayload zone : ClientTreasureRabbitSearchZones.current()) {
                if (active(zone, dimensionId) && circleIntersectsSquare(
                        zone, minimumX, minimumZ, minimumX + 511, minimumZ + 511)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
            TreasureRabbitSearchZonePayload zone = zoneAtChunk(dimension, chunkX, chunkZ);
            if (zone == null) {
                return null;
            }
            Colors colors = colors(zone);
            resultStore[0] = colors.fill();
            resultStore[1] = sameZoneAtChunk(zone, chunkX, chunkZ - 1) ? colors.fill() : colors.border();
            resultStore[2] = sameZoneAtChunk(zone, chunkX + 1, chunkZ) ? colors.fill() : colors.border();
            resultStore[3] = sameZoneAtChunk(zone, chunkX, chunkZ + 1) ? colors.fill() : colors.border();
            resultStore[4] = sameZoneAtChunk(zone, chunkX - 1, chunkZ) ? colors.fill() : colors.border();
            return resultStore;
        }

        @Override
        public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
            int hash = 0;
            String dimensionId = dimension.location().toString();
            for (TreasureRabbitSearchZonePayload zone : ClientTreasureRabbitSearchZones.current()) {
                if (active(zone, dimensionId) && circleIntersectsSquare(zone, regionX << 9, regionZ << 9,
                        (regionX << 9) + 511, (regionZ << 9) + 511)) {
                    hash += Objects.hash(zone.rabbitId(), zone.variantId(), zone.centerX(),
                            zone.centerZ(), zone.radius());
                }
            }
            return hash;
        }

        @Override
        public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
            return zoneAtChunk(dimension, chunkX, chunkZ) != null;
        }

        @Override
        public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension,
                                                         int chunkX, int chunkZ) {
            return tooltip(zoneAtChunk(dimension, chunkX, chunkZ));
        }

        @Override
        public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension,
                                                        int chunkX, int chunkZ) {
            return tooltip(zoneAtChunk(dimension, chunkX, chunkZ));
        }

        @Override
        public void addMinimapBlockHighlightTooltips(List<Component> tooltips,
                                                     ResourceKey<Level> dimension,
                                                     int blockX, int blockY, int blockZ) {
            String dimensionId = dimension.location().toString();
            for (TreasureRabbitSearchZonePayload zone : ClientTreasureRabbitSearchZones.current()) {
                if (active(zone, dimensionId) && inside(zone, blockX, blockZ)) {
                    tooltips.add(tooltip(zone));
                    return;
                }
            }
        }

        private static boolean active(TreasureRabbitSearchZonePayload zone, String dimensionId) {
            return zone.visible() && zone.radius() > 0 && dimensionId.equals(zone.dimensionId());
        }

        private static TreasureRabbitSearchZonePayload zoneAtChunk(ResourceKey<Level> dimension,
                                                                    int chunkX, int chunkZ) {
            int minimumX = chunkX << 4;
            int minimumZ = chunkZ << 4;
            String dimensionId = dimension.location().toString();
            for (TreasureRabbitSearchZonePayload zone : ClientTreasureRabbitSearchZones.current()) {
                if (active(zone, dimensionId) && circleIntersectsSquare(
                        zone, minimumX, minimumZ, minimumX + 15, minimumZ + 15)) {
                    return zone;
                }
            }
            return null;
        }

        private static boolean sameZoneAtChunk(TreasureRabbitSearchZonePayload zone,
                                               int chunkX, int chunkZ) {
            int minimumX = chunkX << 4;
            int minimumZ = chunkZ << 4;
            return circleIntersectsSquare(zone, minimumX, minimumZ, minimumX + 15, minimumZ + 15);
        }

        private static Colors colors(TreasureRabbitSearchZonePayload zone) {
            return switch (zone.variantId()) {
                case "radiant" -> RADIANT;
                case "mirror" -> MIRROR;
                default -> GOLD;
            };
        }

        private static Component tooltip(TreasureRabbitSearchZonePayload zone) {
            if (zone == null) {
                return Component.translatable("yoiko_core.map.treasure_rabbit_zone.generic",
                        IncidentCategory.PERSONAL.displayName());
            }
            return Component.translatable("yoiko_core.map.treasure_rabbit_zone",
                    IncidentCategory.PERSONAL.displayName(),
                    Component.translatable("entity.yoiko_core.treasure_rabbit." + zone.variantId()));
        }

        private static boolean inside(TreasureRabbitSearchZonePayload zone, int x, int z) {
            long dx = (long) x - zone.centerX();
            long dz = (long) z - zone.centerZ();
            return dx * dx + dz * dz <= (long) zone.radius() * zone.radius();
        }

        private static boolean circleIntersectsSquare(TreasureRabbitSearchZonePayload zone,
                                                       int minimumX, int minimumZ,
                                                       int maximumX, int maximumZ) {
            long nearestX = Math.max(minimumX, Math.min(maximumX, zone.centerX()));
            long nearestZ = Math.max(minimumZ, Math.min(maximumZ, zone.centerZ()));
            long dx = nearestX - zone.centerX();
            long dz = nearestZ - zone.centerZ();
            return dx * dx + dz * dz <= (long) zone.radius() * zone.radius();
        }

        private record Colors(int fill, int border) {
        }
    }
}
