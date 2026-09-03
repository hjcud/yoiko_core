package com.yoiko.core.turtle;

import com.yoiko.core.advancement.YoikoAdvancementManager;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Tracks and mirrors the four highest-class official strategy wins into a visible advancement. */
public final class TurtleGoldenShellAdvancement {
    private TurtleGoldenShellAdvancement() { }

    public static void recordWin(MinecraftServer server,UUID playerId,TurtleStrategy strategy){
        TurtleRacingSavedData saved=TurtleRacingSavedData.get(server);
        TurtlePlayerProgress progress=saved.getOrCreatePlayer(playerId);
        if(progress.recordGoldenShellStrategyWin(strategy))saved.markChanged();
        ServerPlayer player=server.getPlayerList().getPlayer(playerId);
        if(player!=null)sync(player,progress);
    }

    public static void sync(ServerPlayer player,TurtlePlayerProgress progress){
        for(TurtleStrategy strategy:progress.goldenShellStrategyWins()){
            YoikoAdvancementManager.awardExternalCriterion(
                    player,
                    "turtle/golden_shell_mastery",
                    strategy.name().toLowerCase(Locale.ROOT),
                    "turtle_golden_shell_mastery",
                    "advancement.yoiko_core.turtle.golden_shell_mastery");
        }
    }
}
