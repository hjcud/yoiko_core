package com.yoiko.core.network;

import com.yoiko.core.YoikoServerCore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenProfilePayload(Profile profile, List<PlayerChoice> players) implements CustomPacketPayload {
    public static final Type<OpenProfilePayload> TYPE = new Type<>(YoikoServerCore.id("open_profile"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenProfilePayload> STREAM_CODEC =
            CustomPacketPayload.codec(OpenProfilePayload::write, OpenProfilePayload::new);

    private OpenProfilePayload(RegistryFriendlyByteBuf buffer) {
        this(new Profile(buffer), readPlayers(buffer));
    }

    public OpenProfilePayload {
        players = List.copyOf(players);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        profile.write(buffer);
        buffer.writeVarInt(players.size());
        for (PlayerChoice player : players) {
            player.write(buffer);
        }
    }

    private static List<PlayerChoice> readPlayers(RegistryFriendlyByteBuf buffer) {
        int encoded = Math.max(0, buffer.readVarInt());
        List<PlayerChoice> result = new ArrayList<>(Math.min(256, encoded));
        for (int i = 0; i < encoded; i++) {
            PlayerChoice choice = new PlayerChoice(buffer);
            if (result.size() < 256) {
                result.add(choice);
            }
        }
        return result;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Profile(
            UUID playerId,
            String playerName,
            boolean ownProfile,
            boolean sharingEnabled,
            String activeRankId,
            String activeRankName,
            int rankColor,
            List<ShowcaseTitle> showcaseTitles,
            String headCosmeticId,
            String chestCosmeticId,
            String feetCosmeticId,
            int completedAchievements,
            int totalAchievements,
            int dailyStreak,
            int marketSales,
            int ownedRelics,
            int appraisedRelics,
            String highestRelicRarity,
            int caughtRabbits,
            boolean radiantRabbit,
            boolean mirrorRabbit,
            boolean crownRabbit,
            int gachaRolls,
            int shinyGachaRolls,
            int legendaryGachaRolls,
            int ownedTurtles,
            int officialFinishes,
            int officialWins,
            int timeTrialRecords,
            boolean goldenShell
    ) {
        private Profile(RegistryFriendlyByteBuf buffer) {
            this(
                    buffer.readUUID(), buffer.readUtf(64), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readUtf(128), buffer.readUtf(160), buffer.readInt(), readTitles(buffer),
                    buffer.readUtf(128), buffer.readUtf(128), buffer.readUtf(128),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(32), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean()
            );
        }

        public Profile {
            showcaseTitles = List.copyOf(showcaseTitles);
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(playerId);
            buffer.writeUtf(playerName, 64);
            buffer.writeBoolean(ownProfile);
            buffer.writeBoolean(sharingEnabled);
            buffer.writeUtf(activeRankId, 128);
            buffer.writeUtf(activeRankName, 160);
            buffer.writeInt(rankColor);
            buffer.writeVarInt(showcaseTitles.size());
            for (ShowcaseTitle title : showcaseTitles) title.write(buffer);
            buffer.writeUtf(headCosmeticId, 128);
            buffer.writeUtf(chestCosmeticId, 128);
            buffer.writeUtf(feetCosmeticId, 128);
            buffer.writeVarInt(completedAchievements);
            buffer.writeVarInt(totalAchievements);
            buffer.writeVarInt(dailyStreak);
            buffer.writeVarInt(marketSales);
            buffer.writeVarInt(ownedRelics);
            buffer.writeVarInt(appraisedRelics);
            buffer.writeUtf(highestRelicRarity, 32);
            buffer.writeVarInt(caughtRabbits);
            buffer.writeBoolean(radiantRabbit);
            buffer.writeBoolean(mirrorRabbit);
            buffer.writeBoolean(crownRabbit);
            buffer.writeVarInt(gachaRolls);
            buffer.writeVarInt(shinyGachaRolls);
            buffer.writeVarInt(legendaryGachaRolls);
            buffer.writeVarInt(ownedTurtles);
            buffer.writeVarInt(officialFinishes);
            buffer.writeVarInt(officialWins);
            buffer.writeVarInt(timeTrialRecords);
            buffer.writeBoolean(goldenShell);
        }
    }

    public record ShowcaseTitle(String id, String displayName, int color) {
        private ShowcaseTitle(RegistryFriendlyByteBuf buffer) {
            this(buffer.readUtf(128), buffer.readUtf(160), buffer.readInt());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(id, 128);
            buffer.writeUtf(displayName, 160);
            buffer.writeInt(color);
        }
    }

    public record PlayerChoice(UUID playerId, String playerName) {
        private PlayerChoice(RegistryFriendlyByteBuf buffer) {
            this(buffer.readUUID(), buffer.readUtf(64));
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(playerId);
            buffer.writeUtf(playerName, 64);
        }
    }

    private static List<ShowcaseTitle> readTitles(RegistryFriendlyByteBuf buffer) {
        int encoded = Math.max(0, buffer.readVarInt());
        List<ShowcaseTitle> result = new ArrayList<>(Math.min(3, encoded));
        for (int i = 0; i < encoded; i++) {
            ShowcaseTitle title = new ShowcaseTitle(buffer);
            if (result.size() < 3) result.add(title);
        }
        return result;
    }
}
