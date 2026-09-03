package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.turtle.TurtleRarity;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TurtleHatchRevealPayload(UUID turtleId,double x,double y,double z,float yaw,TurtleRarity rarity,String name,String activeSkill,boolean specialPassive,int total,String league,String recommendedTraining,String bodyAppearance,String ticketType,long effectSeed) implements CustomPacketPayload {
    public static final Type<TurtleHatchRevealPayload> TYPE=new Type<>(YoikoServerCore.id("turtle_hatch_reveal"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TurtleHatchRevealPayload> STREAM_CODEC=CustomPacketPayload.codec(TurtleHatchRevealPayload::write,TurtleHatchRevealPayload::new);
    private TurtleHatchRevealPayload(RegistryFriendlyByteBuf b){this(b.readUUID(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),readRarity(b),b.readUtf(32),b.readUtf(64),b.readBoolean(),b.readVarInt(),b.readUtf(24),b.readUtf(32),b.readUtf(16),b.readUtf(32),b.readLong());}
    private void write(RegistryFriendlyByteBuf b){b.writeUUID(turtleId);b.writeDouble(x);b.writeDouble(y);b.writeDouble(z);b.writeFloat(yaw);b.writeVarInt(rarity.ordinal());b.writeUtf(name,32);b.writeUtf(activeSkill,64);b.writeBoolean(specialPassive);b.writeVarInt(total);b.writeUtf(league,24);b.writeUtf(recommendedTraining,32);b.writeUtf(bodyAppearance,16);b.writeUtf(ticketType,32);b.writeLong(effectSeed);}
    private static TurtleRarity readRarity(RegistryFriendlyByteBuf b){int i=b.readVarInt();return i>=0&&i<TurtleRarity.values().length?TurtleRarity.values()[i]:TurtleRarity.COMMON;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
