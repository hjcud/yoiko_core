package com.yoiko.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class YoikoClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue UI_SOUNDS = BUILDER
            .comment("Play Yoiko menu interaction sounds.")
            .translation("yoiko_core.configuration.ui.sounds")
            .define("ui.sounds", true);
    public static final ModConfigSpec.BooleanValue SHOW_MENU_HUD = BUILDER
            .comment("Show the Yoiko diary shortcut and unread-mail badge on the HUD.")
            .translation("yoiko_core.configuration.ui.showMenuHud")
            .define("ui.showMenuHud", true);
    public static final ModConfigSpec.BooleanValue SHOW_RELIC_EFFECT_HUD = BUILDER
            .comment("Show server-confirmed relic effect feedback above the action bar.")
            .translation("yoiko_core.configuration.ui.showRelicEffectHud")
            .define("ui.showRelicEffectHud", true);
    public static final ModConfigSpec.IntValue PARTICLE_DENSITY = BUILDER
            .comment("Yoiko particle density used when ui.followMinecraftParticles is false.")
            .translation("yoiko_core.configuration.ui.particleDensity")
            .defineInRange("ui.particleDensity", 2, 0, 3);
    public static final ModConfigSpec.BooleanValue FOLLOW_MINECRAFT_PARTICLES = BUILDER
            .comment("Follow Minecraft's Particles video setting for Yoiko particles.")
            .translation("yoiko_core.configuration.ui.followMinecraftParticles")
            .define("ui.followMinecraftParticles", true);
    public static final ModConfigSpec.BooleanValue RENDER_OTHER_PLAYER_PARTICLES = BUILDER
            .comment("Render particle cosmetics equipped by other players.")
            .translation("yoiko_core.configuration.ui.renderOtherPlayerParticles")
            .define("ui.renderOtherPlayerParticles", true);
    public static final ModConfigSpec.DoubleValue PARTICLE_RENDER_DISTANCE = BUILDER
            .comment("Maximum distance for rendering other players' particle cosmetics.")
            .translation("yoiko_core.configuration.ui.particleRenderDistance")
            .defineInRange("ui.particleRenderDistance", 32.0D, 8.0D, 64.0D);
    public static final ModConfigSpec.IntValue MAX_PARTICLE_PLAYERS = BUILDER
            .comment("Maximum nearby other players whose particle cosmetics are rendered. 0 disables other players only.")
            .translation("yoiko_core.configuration.ui.maxParticlePlayers")
            .defineInRange("ui.maxParticlePlayers", 16, 0, 100);
    public static final ModConfigSpec.BooleanValue AUTO_ADJUST_PARTICLE_PERFORMANCE = BUILDER
            .comment("Automatically reduce other-player particle load when the client frame rate stays low, then restore it gradually.")
            .translation("yoiko_core.configuration.ui.autoAdjustParticlePerformance")
            .define("ui.autoAdjustParticlePerformance", true);
    public static final ModConfigSpec.BooleanValue DEBUG_LAYOUT_OVERLAY = BUILDER
            .comment("Draw widget hit boxes for UI layout development.")
            .translation("yoiko_core.configuration.ui.debugLayoutOverlay")
            .define("ui.debugLayoutOverlay", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private YoikoClientConfig() {
    }
}
