package com.yoiko.core.client.relic;

import com.yoiko.core.config.YoikoClientConfig;
import com.yoiko.core.network.RelicEffectHudPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Two-line, shadowless relic feedback rendered independently above Minecraft's action bar. */
public final class ClientRelicEffectHud {
    public static final ClientRelicEffectHud INSTANCE = new ClientRelicEffectHud();

    private static final int MAX_STORED_ENTRIES = 8;
    private static final int MAX_RENDERED_LINES = 2;
    private static final int ACTION_BAR_CLEARANCE = 82;
    private static final int LINE_STEP = 11;
    private static final int FADE_TICKS = 8;
    private final List<Entry> entries = new ArrayList<>();

    private ClientRelicEffectHud() {
    }

    public void update(RelicEffectHudPayload payload) {
        entries.removeIf(entry -> entry.effectKey.equals(payload.effectKey()));
        entries.addFirst(new Entry(
                payload.effectKey(),
                payload.displayKind(),
                payload.valueKind(),
                payload.value(),
                Math.max(0, payload.stateTicks()),
                Math.max(1, payload.displayTicks()),
                Math.max(0, payload.progress()),
                Math.max(0, payload.progressMaximum())
        ));
        while (entries.size() > MAX_STORED_ENTRIES) {
            entries.removeLast();
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        for (Entry entry : entries) {
            entry.displayTicks--;
            if (entry.stateTicks > 0) {
                entry.stateTicks--;
            }
        }
        entries.removeIf(entry -> entry.displayTicks <= 0);
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!YoikoClientConfig.SHOW_RELIC_EFFECT_HUD.get() || minecraft.options.hideGui
                || minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || entries.isEmpty()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int count = Math.min(MAX_RENDERED_LINES, entries.size());
        int bottomY = graphics.guiHeight() - ACTION_BAR_CLEARANCE;
        for (int index = 0; index < count; index++) {
            Entry entry = entries.get(index);
            int y = bottomY - (count - 1 - index) * LINE_STEP;
            renderEntry(graphics, minecraft, entry, y);
        }
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        entries.clear();
    }

    private static void renderEntry(GuiGraphics graphics, Minecraft minecraft, Entry entry, int y) {
        Component line = line(entry);
        int width = minecraft.font.width(line);
        int x = (graphics.guiWidth() - width) / 2;
        int alpha = entry.displayTicks >= FADE_TICKS
                ? 255
                : Math.clamp((int) Math.round(entry.displayTicks / (double) FADE_TICKS * 255.0D), 0, 255);
        int backgroundAlpha = Math.round(alpha * 0.30F);
        graphics.fill(x - 4, y - 2, x + width + 4, y + minecraft.font.lineHeight + 1,
                backgroundAlpha << 24);
        graphics.drawString(minecraft.font, line, x, y, alpha << 24 | 0xFFFFFF, false);
    }

    private static Component line(Entry entry) {
        MutableComponent line = Component.translatable("yoiko_core.effect_short." + entry.effectKey)
                .withColor(RelicEffectPresentation.hudColor(entry.effectKey));
        line.append(separator());
        switch (entry.displayKind) {
            case PROC -> line.append(state("proc", 0xFFD06A));
            case ACTIVE -> line.append(state("active", 0x7BE7A5));
            case CHARGED -> line.append(state("charged", 0xFFD06A));
            case PROGRESS -> line.append(Component.literal(entry.progress + "/" + entry.progressMaximum)
                    .withColor(0x67E5E8));
            case READY -> line.append(state("ready", 0xFFD06A));
            case COOLDOWN -> line.append(state("cooldown", 0xA9A39B));
            case MARKED -> line.append(state("marked", 0xFF7E73));
        }

        Component value = value(entry.valueKind, entry.value);
        if (value != null) {
            line.append(separator()).append(value);
        }
        if (entry.stateTicks > 0 && entry.displayKind != RelicEffectHudPayload.DisplayKind.PROGRESS) {
            line.append(separator()).append(seconds(entry.stateTicks));
        }
        return line;
    }

    private static Component state(String key, int color) {
        return Component.translatable("yoiko_core.hud.relic.state." + key).withColor(color);
    }

    private static Component seconds(int ticks) {
        double seconds = Math.max(0.0D, ticks / 20.0D);
        String formatted = seconds >= 10.0D
                ? Integer.toString((int) Math.ceil(seconds))
                : String.format(Locale.ROOT, "%.1f", seconds);
        return Component.translatable("yoiko_core.hud.relic.seconds", formatted).withColor(0xA9A39B);
    }

    private static Component value(RelicEffectHudPayload.ValueKind kind, double value) {
        if (kind == RelicEffectHudPayload.ValueKind.NONE) {
            return null;
        }
        String formatted = formatValue(value);
        return Component.translatable("yoiko_core.hud.relic.value." + kind.name().toLowerCase(Locale.ROOT), formatted)
                .withColor(0x67E5E8);
    }

    private static String formatValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.005D) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static Component separator() {
        return Component.literal(" · ").withColor(0x77706A);
    }

    private static final class Entry {
        private final String effectKey;
        private final RelicEffectHudPayload.DisplayKind displayKind;
        private final RelicEffectHudPayload.ValueKind valueKind;
        private final double value;
        private int stateTicks;
        private int displayTicks;
        private final int progress;
        private final int progressMaximum;

        private Entry(
                String effectKey,
                RelicEffectHudPayload.DisplayKind displayKind,
                RelicEffectHudPayload.ValueKind valueKind,
                double value,
                int stateTicks,
                int displayTicks,
                int progress,
                int progressMaximum
        ) {
            this.effectKey = effectKey;
            this.displayKind = displayKind;
            this.valueKind = valueKind;
            this.value = value;
            this.stateTicks = stateTicks;
            this.displayTicks = displayTicks;
            this.progress = progress;
            this.progressMaximum = progressMaximum;
        }
    }
}
