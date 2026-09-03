package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Compact, quantized race state used by the client-side broadcast progress strip. */
public record TurtleRaceHudPayload(
        boolean visible,
        long sessionId,
        int ownEntryNumber,
        List<Marker> markers,
        List<SurfaceSegment> surfaces
) implements CustomPacketPayload {
    public record Marker(int entryNumber, int progressU16, int laneU8, int currentRank, int finishRank,
                         boolean activeEffect) {
        public Marker {
            if (entryNumber < 1 || entryNumber > 8) throw new IllegalArgumentException("Invalid race entry number");
            if (progressU16 < 0 || progressU16 > 65_535) throw new IllegalArgumentException("Invalid race progress");
            if (laneU8 < 0 || laneU8 > 255) throw new IllegalArgumentException("Invalid race lane position");
            if (currentRank < 1 || currentRank > 8) throw new IllegalArgumentException("Invalid current race rank");
            if (finishRank < 0 || finishRank > 8) throw new IllegalArgumentException("Invalid race finish rank");
        }
    }

    public record SurfaceSegment(int startU16, int endU16, int surface) {
        public SurfaceSegment {
            if (startU16 < 0 || startU16 > 65_535 || endU16 < startU16 || endU16 > 65_535)
                throw new IllegalArgumentException("Invalid race surface segment");
            if (surface < 0 || surface > 2) throw new IllegalArgumentException("Invalid turtle surface");
        }
    }

    public static final Type<TurtleRaceHudPayload> TYPE =
            new Type<>(YoikoServerCore.id("turtle_race_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TurtleRaceHudPayload> STREAM_CODEC =
            CustomPacketPayload.codec(TurtleRaceHudPayload::write, TurtleRaceHudPayload::new);

    public TurtleRaceHudPayload {
        markers = List.copyOf(markers);
        surfaces = List.copyOf(surfaces);
        if (ownEntryNumber < 0 || ownEntryNumber > 8) throw new IllegalArgumentException("Invalid own entry number");
        if (markers.size() > 8) throw new IllegalArgumentException("Too many race HUD markers");
        if (surfaces.size() > 32) throw new IllegalArgumentException("Too many race HUD surface segments");
    }

    private TurtleRaceHudPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readLong(), buffer.readUnsignedByte(),
                readMarkers(buffer), readSurfaces(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(visible);
        buffer.writeLong(sessionId);
        buffer.writeByte(ownEntryNumber);
        buffer.writeByte(markers.size());
        for (Marker marker : markers) {
            buffer.writeByte(marker.entryNumber());
            buffer.writeShort(marker.progressU16());
            buffer.writeByte(marker.laneU8());
            buffer.writeByte(marker.currentRank());
            buffer.writeByte(marker.finishRank());
            buffer.writeBoolean(marker.activeEffect());
        }
        buffer.writeByte(surfaces.size());
        for (SurfaceSegment segment : surfaces) {
            buffer.writeShort(segment.startU16());
            buffer.writeShort(segment.endU16());
            buffer.writeByte(segment.surface());
        }
    }

    private static List<Marker> readMarkers(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readUnsignedByte();
        if (count > 8) throw new IllegalArgumentException("Invalid race HUD marker count");
        List<Marker> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            result.add(new Marker(buffer.readUnsignedByte(), buffer.readUnsignedShort(), buffer.readUnsignedByte(),
                    buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readBoolean()));
        return result;
    }

    private static List<SurfaceSegment> readSurfaces(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readUnsignedByte();
        if (count > 32) throw new IllegalArgumentException("Invalid race HUD surface count");
        List<SurfaceSegment> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            result.add(new SurfaceSegment(buffer.readUnsignedShort(), buffer.readUnsignedShort(), buffer.readUnsignedByte()));
        return result;
    }

    public static TurtleRaceHudPayload hidden(long sessionId) {
        return new TurtleRaceHudPayload(false, sessionId, 0, List.of(), List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
