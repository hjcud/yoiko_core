package com.yoiko.core.turtle.arena;

import com.yoiko.core.turtle.TurtleMenuService;
import com.yoiko.core.turtle.TurtleRacingSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Connects the three vanilla-block arena kiosks to the server-validated turtle UI. */
public final class TurtleArenaInteractionService {
    @SubscribeEvent
    public void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        TurtleRacingSavedData saved = TurtleRacingSavedData.get(player.server);
        BlockPos center = saved.arenaCenter().orElse(null);
        if (center == null || !saved.arenaDimension().equals(player.level().dimension().location().toString())) return;
        BlockPos clicked = event.getPos();
        int lecternZ = center.getZ() + TurtleArenaManager.KIOSK_Z_OFFSET - 1;
        if (clicked.getZ() != lecternZ || clicked.getY() != center.getY()) return;
        String tab;
        if (Math.abs(clicked.getX() - (center.getX() - 14)) <= 1) tab = "RACE";
        else if (Math.abs(clicked.getX() - center.getX()) <= 1) tab = "RACE";
        else if (Math.abs(clicked.getX() - (center.getX() + 14)) <= 1) tab = "RACE";
        else return;
        TurtleMenuService.open(player, tab);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
