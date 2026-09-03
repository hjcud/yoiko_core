package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TurtleTimeTrialGhostPayload(int centerX,int baseY,int centerZ,int presetIndex,List<Sample> samples) implements CustomPacketPayload {
    public record Sample(int progressU16,byte laneOffset,short hopHeightU8){}
    public static final Type<TurtleTimeTrialGhostPayload> TYPE=new Type<>(YoikoServerCore.id("turtle_time_trial_ghost"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TurtleTimeTrialGhostPayload> STREAM_CODEC=CustomPacketPayload.codec(TurtleTimeTrialGhostPayload::write,TurtleTimeTrialGhostPayload::new);
    public TurtleTimeTrialGhostPayload { samples=List.copyOf(samples);if(samples.size()>750)throw new IllegalArgumentException("Too many ghost samples"); }
    private TurtleTimeTrialGhostPayload(RegistryFriendlyByteBuf buffer){this(buffer.readInt(),buffer.readInt(),buffer.readInt(),buffer.readUnsignedByte(),readSamples(buffer));}
    private void write(RegistryFriendlyByteBuf buffer){buffer.writeInt(centerX);buffer.writeInt(baseY);buffer.writeInt(centerZ);buffer.writeByte(presetIndex);buffer.writeVarInt(samples.size());for(Sample s:samples){buffer.writeShort(s.progressU16);buffer.writeByte(s.laneOffset);buffer.writeByte(s.hopHeightU8);}}
    private static List<Sample> readSamples(RegistryFriendlyByteBuf buffer){int size=buffer.readVarInt();if(size<0||size>750)throw new IllegalArgumentException("Invalid ghost sample count");List<Sample> out=new ArrayList<>(size);for(int i=0;i<size;i++)out.add(new Sample(buffer.readUnsignedShort(),buffer.readByte(),(short)buffer.readUnsignedByte()));return out;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
