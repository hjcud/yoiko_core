package com.yoiko.core.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.config.YoikoClientConfig;
import com.yoiko.core.network.OpenMailboxPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** A login-only presentation of rewards that remain backed by the normal mailbox ledger. */
public final class YoikoLoginRewardScreen extends Screen {
    private static final int PANEL_WIDTH = YoikoMenuLayout.TAB_PANEL_WIDTH;
    private static final int PANEL_HEIGHT = YoikoMenuLayout.TAB_PANEL_HEIGHT;
    private static final int CHEST_WIDTH = 46;
    private static final int CHEST_HEIGHT = 34;
    private static final int CHEST_STEP = 55;
    private static final int CHEST_START_X = 6;
    private static final int CHEST_Y = 74;
    private static final int CONTENT_CENTER_X = 84;
    private static final int KEY_Y = 128;
    private static final int CHANCE_BUTTON_X = 163;
    private static final int CHANCE_BUTTON_Y = 118;
    private static final int CHANCE_BUTTON_SIZE = 16;
    // The divider artwork sits at local y=160; a 9 px font is centered on it from y=156.
    private static final int STREAK_TITLE_Y = 156;
    private static final int STREAK_DAY_Y = 180;
    private static final int STREAK_ITEM_Y = 194;
    private static final int STREAK_DAY_START_X = 4;
    private static final int STREAK_ITEM_START_X = 7;
    private static final int STREAK_COLUMN_STEP = 23;
    private static final int STREAK_CELL_Y = STREAK_DAY_Y - 4;
    private static final int STREAK_CELL_WIDTH = 22;
    private static final int STREAK_CELL_HEIGHT = 38;
    private static final int STREAK_GLOW_SIZE = 42;
    private static final int CHEST_SHAKE_TICKS = 14;
    private static final int REVEAL_STEP_TICKS = 10;
    private static final int REVEAL_COMPLETE_TICKS = 3 * REVEAL_STEP_TICKS;
    private static final int GLOW_SIZE = 72;
    private static final float UNSELECTED_BRIGHTNESS = 0.42F;
    private static final int TEXT_PRIMARY = 0xFF61442E;
    private static final int TEXT_ACCENT = 0xFF9B4F2D;
    private static final int TEXT_SOFT = 0xFFC99368;
    private static final int TEXT_GREEN = 0xFF71854E;
    private static final ResourceLocation CHEST_CLOSED = YoikoServerCore.id(
            "textures/gui/mailbox/daily_bonus/daily_bonus_chest_closed.png");
    private static final ResourceLocation CHEST_HOVER = YoikoServerCore.id(
            "textures/gui/mailbox/daily_bonus/daily_bonus_chest_hover.png");
    private static final ResourceLocation CHEST_SHADOW = YoikoServerCore.id(
            "textures/gui/mailbox/daily_bonus/daily_bonus_chest_shadow.png");
    private static final ResourceLocation ITEM_SHADOW = YoikoServerCore.id(
            "textures/gui/mailbox/daily_bonus/daily_bonus_item_shadow.png");
    private static final ResourceLocation CHEST_GLOW = YoikoServerCore.id(
            "textures/gui/mailbox/daily_bonus/daily_bonus_glow.png");
    private static final ResourceLocation KEY_ICON = YoikoServerCore.id(
            "textures/gui/mailbox/daily_bonus/daily_bonus_key.png");
    private static final ResourceLocation KEY_SILHOUETTE = YoikoServerCore.id(
            "textures/gui/mailbox/daily_bonus/daily_bonus_key_silhouette.png");

    private OpenMailboxPayload payload;
    private ChanceButton chanceButton;
    private int pendingBox = -1;
    private int openingBox = -1;
    private int pendingOpenSoundBox = -1;
    private int openingTicks;
    private int revealTicks = -1;
    private ItemStack hoveredReward = ItemStack.EMPTY;

    public YoikoLoginRewardScreen(OpenMailboxPayload payload) {
        super(YoikoClientText.tr("yoiko_core.screen.login_reward"));
        this.payload = payload;
        if (isDailyLimitReached(payload)) this.revealTicks = REVEAL_COMPLETE_TICKS;
    }

    public void update(OpenMailboxPayload payload) {
        int previousMask = this.payload.dailyBonusOpenedMask();
        boolean wasLimitReached = isDailyLimitReached(this.payload);
        this.payload = payload;
        this.pendingBox = -1;
        int newlyOpenedMask = payload.dailyBonusOpenedMask() & ~previousMask;
        if (newlyOpenedMask != 0) {
            openingBox = validBox(payload.dailyBonusLastOpened())
                    ? payload.dailyBonusLastOpened() : firstOpenedBox(newlyOpenedMask);
            pendingOpenSoundBox = openingBox;
            openingTicks = CHEST_SHAKE_TICKS;
        }
        if (payload.dailyBonusOpenedMask() < previousMask) {
            openingBox = -1;
            pendingOpenSoundBox = -1;
            openingTicks = 0;
        }
        boolean limitReached = isDailyLimitReached(payload);
        if (limitReached && !wasLimitReached) {
            revealTicks = 0;
        } else if (!limitReached) {
            revealTicks = -1;
        } else if (revealTicks < 0) {
            revealTicks = REVEAL_COMPLETE_TICKS;
        }
        rebuild();
    }

    @Override
    public void tick() {
        if (openingTicks > 0) openingTicks--;
        if (openingTicks <= 0 && isDailyLimitReached(payload)
                && revealTicks >= 0 && revealTicks < REVEAL_COMPLETE_TICKS) {
            revealTicks++;
        }
    }

    @Override
    protected void init() {
        rebuild();
        YoikoMousePosition.restoreIfRemembered();
    }

    private void rebuild() {
        clearWidgets();
        int left = left();
        int top = top();
        chanceButton = addRenderableWidget(new ChanceButton(
                left + CHANCE_BUTTON_X, top + CHANCE_BUTTON_Y));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        YoikoScreenStyle.renderBackdrop(graphics, width, height);
        int left = left();
        int top = top();
        YoikoScreenStyle.renderMailboxRightPanel(graphics, left, top);

        hoveredReward = ItemStack.EMPTY;
        for (int index = 0; index < 3; index++) {
            renderChest(graphics, left, top, index, mouseX, mouseY);
        }
        renderKeyStatus(graphics, left, top);
        renderStreak(graphics, left, top, mouseX, mouseY);

        YoikoScreenStyle.renderWidgets(this, graphics, mouseX, mouseY, partialTick);
        ClientServerRequestState.render(graphics, font, "mailbox", width / 2, height - 24);
        if (chanceButton != null && chanceButton.isHovered()) {
            graphics.renderComponentTooltip(font, dailyChanceTooltip(), mouseX, mouseY);
        } else if (!hoveredReward.isEmpty()) {
            graphics.renderTooltip(font, hoveredReward, mouseX, mouseY);
        }
    }

    private void renderChest(GuiGraphics graphics, int left, int top, int index, int mouseX, int mouseY) {
        int x = left + CHEST_START_X + index * CHEST_STEP;
        int y = top + CHEST_Y + (index == 1 ? 1 : 0);
        ItemStack stack = index < payload.dailyBonusBoxes().size()
                ? payload.dailyBonusBoxes().get(index) : ItemStack.EMPTY;
        boolean open = isVisuallyOpen(index);
        boolean hovered = mouseX >= x && mouseX < x + CHEST_WIDTH
                && mouseY >= y && mouseY < y + CHEST_HEIGHT;
        boolean highlighted = hovered || index == pendingBox || (index == openingBox && openingTicks > 0);
        int drawX = x + shakeOffset(index);
        boolean unrevealedChoice = open && !isOpened(index);
        graphics.blit(open ? ITEM_SHADOW : CHEST_SHADOW, x - 3, y + (open ? 1 : 6),
                52, 34, 0, 0, 52, 34, 52, 34);
        if (open && !stack.isEmpty()) {
            renderGlow(graphics, x + CHEST_WIDTH / 2, y + CHEST_HEIGHT / 2,
                    rarityGlow(index), index == payload.dailyBonusLastOpened(), unrevealedChoice);
        }
        if (open && pendingOpenSoundBox == index) {
            playOpenSound(index);
            pendingOpenSoundBox = -1;
            openingBox = -1;
        }
        if (!open) {
            graphics.blit(highlighted ? CHEST_HOVER : CHEST_CLOSED, drawX, y, CHEST_WIDTH, CHEST_HEIGHT,
                    0, 0, 46, 34, 46, 34);
        } else if (!stack.isEmpty()) {
            int itemX = drawX + 15;
            int itemY = y + 9;
            OpenMailboxPayload.DailyBonusCosmeticVisual visual = index < payload.dailyBonusCosmeticVisuals().size()
                    ? payload.dailyBonusCosmeticVisuals().get(index) : null;
            if (unrevealedChoice) {
                RenderSystem.setShaderColor(UNSELECTED_BRIGHTNESS, UNSELECTED_BRIGHTNESS,
                        UNSELECTED_BRIGHTNESS, 1.0F);
            }
            try {
                if (visual != null && !visual.id().isBlank()) {
                    YoikoCosmeticIconRenderer.renderEntryIcon(graphics, itemX, itemY, 16, visual.type(), visual.id(),
                            true, 0, rarity(index), visual.particleCategory());
                } else {
                    graphics.renderItem(stack, itemX, itemY);
                    int count = index < payload.dailyBonusCounts().size()
                            ? payload.dailyBonusCounts().get(index) : stack.getCount();
                    graphics.renderItemDecorations(font, stack, itemX, itemY,
                            count > 1 ? Integer.toString(count) : null);
                }
            } finally {
                if (unrevealedChoice) RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            if (hovered) {
                hoveredReward = stack;
            }
        }
    }

    private void renderGlow(GuiGraphics graphics, int centerX, int centerY, int color,
                            boolean featured, boolean dimmed) {
        float alpha = dimmed ? 0.42F : featured ? 1.0F : 0.82F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, alpha);
        graphics.blit(CHEST_GLOW, centerX - GLOW_SIZE / 2, centerY - GLOW_SIZE / 2,
                GLOW_SIZE, GLOW_SIZE, 0, 0, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderKeyStatus(GuiGraphics graphics, int left, int top) {
        Component next = YoikoClientText.tr("yoiko_core.ui.mail.next_key_in", payload.dailyBonusNextKeyText());
        int maxKeys = Math.max(1, payload.dailyBonusMaxKeys());
        int keys = Math.max(0, Math.min(payload.dailyBonusKeys(), maxKeys));
        int iconWidth = 16 + (maxKeys - 1) * 11;
        int total = font.width(next) + 5 + iconWidth;
        int x = left + CONTENT_CENTER_X - total / 2;
        graphics.drawString(font, next, x, top + KEY_Y + 4, TEXT_SOFT, false);
        int iconX = x + font.width(next) + 5;
        for (int i = maxKeys - 1; i >= 0; i--) {
            ResourceLocation texture = i < keys ? KEY_ICON : KEY_SILHOUETTE;
            graphics.blit(texture, iconX + i * 11, top + KEY_Y, 16, 16, 0, 0, 16, 16, 16, 16);
        }
    }

    private void renderStreak(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, YoikoClientText.tr("yoiko_core.ui.mail.streak_rewards"),
                left + CONTENT_CENTER_X, top + STREAK_TITLE_Y, TEXT_SOFT);
        int current = currentStreakIndex();
        boolean rewardAvailable = canClaimTodayReward();
        for (int index = 0; index < 7; index++) {
            int dayX = left + STREAK_DAY_START_X + index * STREAK_COLUMN_STEP;
            int itemX = left + STREAK_ITEM_START_X + index * STREAK_COLUMN_STEP;
            boolean currentReward = rewardAvailable && index == current;
            boolean hoveredCell = mouseX >= dayX && mouseX < dayX + STREAK_CELL_WIDTH
                    && mouseY >= top + STREAK_CELL_Y
                    && mouseY < top + STREAK_CELL_Y + STREAK_CELL_HEIGHT;
            ItemStack stack = index < payload.streakRewards().size()
                    ? payload.streakRewards().get(index) : ItemStack.EMPTY;
            boolean claimed = (payload.streakClaimedMask() & (1 << index)) != 0 && !currentReward;
            if (currentReward) {
                renderStreakRewardGlow(graphics, dayX, top, hoveredCell);
            }
            if (!stack.isEmpty()) {
                if (claimed) RenderSystem.setShaderColor(0.42F, 0.42F, 0.42F, 1.0F);
                graphics.renderItem(stack, itemX, top + STREAK_ITEM_Y);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                graphics.renderItemDecorations(font, stack, itemX, top + STREAK_ITEM_Y);
                if (claimed) YoikoScreenStyle.renderCheckIcon(graphics, itemX - 1, top + STREAK_ITEM_Y - 1);
                if (hoveredCell) hoveredReward = stack;
            }
            Component day = Component.literal(Integer.toString(index + 1));
            graphics.drawCenteredString(font, day, dayX + 11, top + STREAK_DAY_Y,
                    index == current ? TEXT_ACCENT : TEXT_GREEN);
        }
    }

    private void renderStreakRewardGlow(GuiGraphics graphics, int cellX, int top, boolean hovered) {
        int centerX = cellX + STREAK_CELL_WIDTH / 2;
        int centerY = top + STREAK_ITEM_Y + 8;
        int size = hovered ? STREAK_GLOW_SIZE + 2 : STREAK_GLOW_SIZE;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 0.84F, 0.32F, hovered ? 0.95F : 0.78F);
        graphics.blit(CHEST_GLOW, centerX - size / 2, centerY - size / 2,
                size, size, 0, 0, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.fill(cellX + 1, top + STREAK_CELL_Y + 1,
                cellX + STREAK_CELL_WIDTH - 1, top + STREAK_CELL_Y + STREAK_CELL_HEIGHT - 1,
                hovered ? 0x30FFD05A : 0x20FFD05A);
        graphics.renderOutline(cellX, top + STREAK_CELL_Y,
                STREAK_CELL_WIDTH, STREAK_CELL_HEIGHT, hovered ? 0xFFFFC247 : 0xDDE6B24A);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && canClaimTodayReward()) {
            int current = currentStreakIndex();
            int x = left() + STREAK_DAY_START_X + current * STREAK_COLUMN_STEP;
            int y = top() + STREAK_CELL_Y;
            if (mouseX >= x && mouseX < x + STREAK_CELL_WIDTH
                    && mouseY >= y && mouseY < y + STREAK_CELL_HEIGHT) {
                send("mailbox_login_claim|" + payload.selectedId());
                return true;
            }
        }
        if (button == 0 && payload.dailyBonusKeys() > 0 && pendingBox < 0) {
            for (int index = 0; index < 3; index++) {
                int x = left() + CHEST_START_X + index * CHEST_STEP;
                int y = top() + CHEST_Y + (index == 1 ? 1 : 0);
                if (!isOpened(index) && mouseX >= x && mouseX < x + CHEST_WIDTH
                        && mouseY >= y && mouseY < y + CHEST_HEIGHT) {
                    pendingBox = index;
                    send("mailbox_login_box|" + index);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOpened(int index) {
        return (payload.dailyBonusOpenedMask() & (1 << index)) != 0;
    }

    private boolean isVisuallyOpen(int index) {
        if (index == openingBox && openingTicks > 0) return false;
        if (isOpened(index)) return true;
        if (!isDailyLimitReached(payload)) return false;
        if (revealTicks < 0) return true;
        int order = revealOrderIndex(index);
        return order >= 0 && revealTicks >= (order + 1) * REVEAL_STEP_TICKS;
    }

    private int revealOrderIndex(int boxIndex) {
        int order = 0;
        for (int index = 0; index < 3; index++) {
            if (isOpened(index)) continue;
            if (index == boxIndex) return order;
            order++;
        }
        return -1;
    }

    private int shakeOffset(int index) {
        if (index != openingBox || openingTicks <= 0) return 0;
        int phase = CHEST_SHAKE_TICKS - openingTicks;
        return switch (phase % 4) {
            case 0 -> -2;
            case 1 -> 2;
            case 2 -> -1;
            default -> 1;
        };
    }

    private int rarityGlow(int index) {
        return switch (rarity(index).toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> 0xFFFFD36A;
            case "RARE" -> 0xFF50F3FF;
            case "EPIC" -> 0xFFFF75E6;
            case "LEGENDARY" -> 0xFFFFB000;
            case "MYTHIC", "MYSTIC" -> 0xFFB16CFF;
            default -> 0xFFEFE5C7;
        };
    }

    private void playOpenSound(int index) {
        if (!validBox(index)) return;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHEST_OPEN, 1.0F, 0.75F));
        switch (rarity(index).toUpperCase(Locale.ROOT)) {
            case "MYTHIC", "MYSTIC" -> {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.65F, 0.75F));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.45F, 0.7F));
            }
            case "LEGENDARY" -> {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.5F, 0.65F));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35F, 0.62F));
            }
            case "EPIC" -> minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.45F, 0.65F));
            case "RARE" -> minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.25F, 0.55F));
            default -> minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.95F, 0.5F));
        }
    }

    private static boolean isDailyLimitReached(OpenMailboxPayload payload) {
        return payload.dailyBonusKeys() <= 0;
    }

    private static boolean validBox(int index) {
        return index >= 0 && index < 3;
    }

    private static int firstOpenedBox(int mask) {
        for (int index = 0; index < 3; index++) {
            if ((mask & (1 << index)) != 0) return index;
        }
        return -1;
    }

    private String rarity(int index) {
        return index < payload.dailyBonusRarities().size() ? payload.dailyBonusRarities().get(index) : "COMMON";
    }

    private boolean hasAttachments() {
        return !payload.selectedItemStacks().isEmpty()
                || payload.selectedAttachedGold() > 0L || payload.selectedAttachedGems() > 0L;
    }

    private int currentStreakIndex() {
        return payload.dailyStreak() <= 0 ? -1 : Math.floorMod(payload.dailyStreak() - 1, 7);
    }

    private boolean canClaimTodayReward() {
        return currentStreakIndex() >= 0
                && "daily".equals(payload.selectedType())
                && !payload.selectedId().isBlank()
                && hasAttachments();
    }

    private List<Component> dailyChanceTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(YoikoClientText.tr("yoiko_core.ui.mail.daily_chance.title").withStyle(ChatFormatting.GOLD));
        lines.add(YoikoClientText.tr("yoiko_core.ui.mail.daily_chance.current_pool").withStyle(ChatFormatting.GRAY));
        for (OpenMailboxPayload.DailyBonusChance chance : payload.dailyBonusChances()) {
            Component rarity = YoikoClientText.tr("yoiko_core.rarity." + rarityKey(chance.rarity()))
                    .withStyle(rarityColor(chance.rarity()));
            Component percent = Component.literal(formatChance(chance.percent())).withStyle(ChatFormatting.WHITE);
            if (chance.allOwned()) {
                percent = Component.empty().append(percent).append(Component.literal(" "))
                        .append(YoikoClientText.tr("yoiko_core.ui.mail.daily_chance.gem_conversion")
                                .withStyle(ChatFormatting.AQUA));
            }
            lines.add(Component.empty().append(rarity).append(Component.literal("  ")).append(percent));
        }
        return lines;
    }

    private static String rarityKey(String rarity) {
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> "uncommon";
            case "RARE" -> "rare";
            case "EPIC" -> "epic";
            case "LEGENDARY" -> "legendary";
            case "MYTHIC", "MYSTIC" -> "mystic";
            case "RADIANT" -> "radiant";
            default -> "common";
        };
    }

    private static ChatFormatting rarityColor(String rarity) {
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> ChatFormatting.GREEN;
            case "RARE" -> ChatFormatting.AQUA;
            case "EPIC" -> ChatFormatting.LIGHT_PURPLE;
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "MYTHIC", "MYSTIC" -> ChatFormatting.RED;
            case "RADIANT" -> ChatFormatting.AQUA;
            default -> ChatFormatting.WHITE;
        };
    }

    private static String formatChance(double percent) {
        if (percent <= 0.0D) return "0%";
        if (percent < 0.01D) return String.format(Locale.ROOT, "%.4f%%", percent);
        if (percent < 1.0D) return String.format(Locale.ROOT, "%.3f%%", percent);
        return String.format(Locale.ROOT, "%.2f%%", percent);
    }

    private int left() {
        return width / 2 - PANEL_WIDTH / 2;
    }

    private int top() {
        return height / 2 - PANEL_HEIGHT / 2;
    }

    private void send(String action) {
        if (ClientServerRequestState.begin(this, action)) {
            PacketDistributor.sendToServer(ClientMenuSession.action(action));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class ChanceButton extends AbstractButton {
        private ChanceButton(int x, int y) {
            super(x, y, CHANCE_BUTTON_SIZE, CHANCE_BUTTON_SIZE,
                    YoikoClientText.tr("yoiko_core.ui.mail.daily_chance.title"));
        }

        @Override
        public void onPress() {
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Component mark = Component.literal("?");
            graphics.drawString(font, mark,
                    getX() + (width - font.width(mark)) / 2,
                    getY() + (height - font.lineHeight) / 2,
                    isHovered() ? 0xFF8B4E29 : 0xFF4F3524, false);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            if (YoikoClientConfig.UI_SOUNDS.get()) super.playDownSound(soundManager);
        }
    }
}
