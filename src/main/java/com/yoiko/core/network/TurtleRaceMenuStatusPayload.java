package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Lightweight live state. Static betting profiles remain in the full menu payload. */
public record TurtleRaceMenuStatusPayload(
        OpenTurtleMenuPayload.CompetitionStatus competition,
        OpenTurtleMenuPayload.TimeTrialStatus timeTrial,
        String forecastWeather,int forecastSurfaceMask,
        List<OpenTurtleMenuPayload.BetPool> betPools) implements CustomPacketPayload {

    public static final Type<TurtleRaceMenuStatusPayload> TYPE=new Type<>(YoikoServerCore.id("turtle_race_menu_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TurtleRaceMenuStatusPayload> STREAM_CODEC=
            CustomPacketPayload.codec(TurtleRaceMenuStatusPayload::write,TurtleRaceMenuStatusPayload::new);

    public TurtleRaceMenuStatusPayload {
        betPools=List.copyOf(betPools);
        if(betPools.size()>24)throw new IllegalArgumentException("Oversized turtle race pool payload");
    }

    public TurtleRaceMenuStatusPayload(OpenTurtleMenuPayload payload){
        this(payload.competition(),payload.timeTrial(),payload.forecastWeather(),payload.forecastSurfaceMask(),payload.betPools());
    }

    private TurtleRaceMenuStatusPayload(RegistryFriendlyByteBuf buffer){
        this(OpenTurtleMenuPayload.readCompetitionStatus(buffer),OpenTurtleMenuPayload.readTimeTrialStatus(buffer),
                buffer.readUtf(24),buffer.readVarInt(),readPools(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer){
        OpenTurtleMenuPayload.writeCompetitionStatus(buffer,competition);
        OpenTurtleMenuPayload.writeTimeTrialStatus(buffer,timeTrial);
        buffer.writeUtf(forecastWeather,24);buffer.writeVarInt(forecastSurfaceMask);
        buffer.writeVarInt(betPools.size());
        for(OpenTurtleMenuPayload.BetPool pool:betPools){buffer.writeVarInt(pool.heat());buffer.writeUtf(pool.entryId(),64);buffer.writeVarLong(pool.totalPool());buffer.writeVarLong(pool.myStake());}
    }

    private static List<OpenTurtleMenuPayload.BetPool> readPools(RegistryFriendlyByteBuf buffer){
        int size=buffer.readVarInt();if(size<0||size>24)throw new IllegalArgumentException("Invalid turtle race pool count: "+size);
        List<OpenTurtleMenuPayload.BetPool> result=new ArrayList<>(size);
        for(int i=0;i<size;i++)result.add(new OpenTurtleMenuPayload.BetPool(buffer.readVarInt(),buffer.readUtf(64),buffer.readVarLong(),buffer.readVarLong()));
        return result;
    }

    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
