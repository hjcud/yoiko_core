package com.yoiko.core.client.relic;

import com.yoiko.core.network.RelicAirStepPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RelicAirStepClientEvents {
    private boolean jumpWasDown;
    private boolean airborneJumpArmed;
    private int sequence;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            reset();
            return;
        }

        boolean jumpDown = minecraft.options.keyJump.isDown();
        if (minecraft.player.onGround()) {
            airborneJumpArmed = false;
            jumpWasDown = jumpDown;
            return;
        }
        if (!jumpDown) {
            airborneJumpArmed = true;
        } else if (!jumpWasDown && airborneJumpArmed) {
            PacketDistributor.sendToServer(new RelicAirStepPayload(++sequence));
            airborneJumpArmed = false;
        }
        jumpWasDown = jumpDown;
    }

    private void reset() {
        jumpWasDown = false;
        airborneJumpArmed = false;
    }
}
