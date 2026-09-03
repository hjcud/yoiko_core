package com.yoiko.core.client.screen;

import com.yoiko.core.config.YoikoClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.data.PlayerYoikoData;
import com.yoiko.core.network.MenuActionPayload;
import com.yoiko.core.network.OpenMailboxPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class YoikoMailboxScreen extends Screen {
    private static final int SLOT_SIZE = 18;
    private static final int MAIL_LIST_X = 11;
    private static final int MAIL_LIST_Y = 32;
    private static final int MAIL_LIST_WIDTH = 170;
    private static final int MAIL_ROW_HEIGHT = 27;
    private static final int MAIL_VISIBLE_ROWS = 4;
    private static final int MAIL_ICON_X = 3;
    private static final int MAIL_ICON_Y = 5;
    private static final int MAIL_ICON_WIDTH = 22;
    private static final int MAIL_ICON_HEIGHT = 18;
    private static final int MAIL_DELETE_X = 151;
    private static final int MAIL_DELETE_Y = 8;
    private static final int MAIL_DELETE_WIDTH = 10;
    private static final int MAIL_DELETE_HEIGHT = 11;
    private static final int MAIL_SCROLLBAR_X = MAIL_LIST_X + MAIL_LIST_WIDTH;
    private static final int MAIL_SCROLLBAR_WIDTH = 2;
    private static final int MAIL_SCROLLBAR_Y_OFFSET = -1;
    private static final int MAIL_SCROLLBAR_HEIGHT = MAIL_VISIBLE_ROWS * MAIL_ROW_HEIGHT + 2;
    private static final int MAIL_SCROLLBAR_MIN_THUMB_HEIGHT = 12;
    private static final int MAIL_SCROLLBAR_THUMB_COLOR = 0xFFF3D29F;
    private static final int MAIL_SCROLLBAR_THUMB_HIGHLIGHT_COLOR = 0xFFFFE9BE;
    private static final int MAIL_UNSELECTED_DIM_COLOR = 0x26000000;
    private static final int MAIL_TEXT_STRONG = 0xFF3F2C1D;
    private static final int MAIL_TEXT_PRIMARY = 0xFF61442E;
    private static final int MAIL_TEXT_SECONDARY = 0xFF806247;
    private static final int MAIL_TEXT_ACCENT = 0xFF9B4F2D;
    private static final int MAIL_TEXT_TEAL = 0xFF2E6E69;
    private static final int MAIL_TEXT_LIGHT = 0xFFFFE6B0;
    private static final int MAIL_TEXT_SOFT = 0xFFC99368;
    private static final int MAIL_TEXT_SOFT_GREEN = 0xFF8BAF68;
    private static final int MAIL_DETAIL_X = 8;
    private static final int MAIL_DETAIL_Y = 151;
    private static final int MAIL_DETAIL_WIDTH = YoikoMenuLayout.LEFT_WIDTH;
    private static final int MAIL_DETAIL_TEXT_X = MAIL_DETAIL_X + 4;
    private static final int MAIL_DETAIL_TITLE_X = MAIL_DETAIL_TEXT_X + 20;
    private static final int MAIL_DETAIL_TITLE_Y = MAIL_DETAIL_Y + 5;
    private static final int MAIL_DETAIL_TITLE_WIDTH = 112;
    private static final int MAIL_DETAIL_META_Y = MAIL_DETAIL_Y + 24;
    private static final int MAIL_DETAIL_BODY_Y = MAIL_DETAIL_Y + 42;
    private static final int MAIL_DETAIL_TEXT_WIDTH = 150;
    private static final int MAIL_DETAIL_BODY_OFFSET_X = 5;
    private static final int MAIL_DETAIL_BODY_WIDTH = MAIL_DETAIL_TEXT_WIDTH - MAIL_DETAIL_BODY_OFFSET_X;
    private static final int MAIL_DETAIL_ATTACHMENT_X = MAIL_DETAIL_TEXT_X + MAIL_DETAIL_BODY_OFFSET_X;
    private static final int MAIL_DETAIL_ATTACHMENT_Y_OFFSET = -6;
    private static final int MAIL_DETAIL_ATTACHMENT_VISIBLE = 3;
    private static final int RIGHT_PANEL_X = YoikoMenuLayout.SPLIT_RIGHT_PANEL_X + 1;
    private static final int NEWS_CONTENT_CENTER_X = 84;
    private static final int NEWS_MASTHEAD_Y = 14;
    private static final int NEWS_SUBTITLE_Y = 28;
    private static final int NEWS_STORY_X = 7;
    private static final int NEWS_STORY_TITLE_Y = 48;
    private static final int NEWS_STORY_TITLE_WIDTH = 154;
    private static final int NEWS_STORY_TITLE_LINES = 2;
    private static final int NEWS_STORY_RULE_Y = 69;
    private static final int NEWS_STORY_BODY_Y = 76;
    private static final int NEWS_STORY_BODY_WIDTH = 154;
    private static final int NEWS_STORY_BODY_LINES = 5;
    private static final int NEWS_STORY_META_Y = 137;
    private static final int NEWS_EVENT_TITLE_Y = 153;
    private static final int NEWS_EVENT_CARD_X = 16;
    private static final int NEWS_EVENT_CARD_Y = 170;
    private static final int NEWS_EVENT_CARD_WIDTH = 136;
    private static final int NEWS_EVENT_CARD_HEIGHT = 64;
    private static final int NEWS_EVENT_CARD_TITLE_X = 7;
    private static final int NEWS_EVENT_CARD_TITLE_Y = 46;
    private static final int NEWS_EVENT_CARD_META_Y = 55;
    private static final int NEWS_EVENT_ARROW_Y = 190;
    private static final int NEWS_EVENT_ARROW_WIDTH = 12;
    private static final int NEWS_EVENT_ARROW_HEIGHT = 18;
    private static final int NEWS_EVENT_LEFT_ARROW_X = 1;
    private static final int NEWS_EVENT_RIGHT_ARROW_X = 155;
    private static final int NEWS_EVENT_DOT_Y = 254;
    private static final int MAIL_PANEL_OVERFLOW = 25;
    private static final int MAIL_DETAIL_ACTION_X = 182 - MAIL_PANEL_OVERFLOW;
    private static final int MAIL_DETAIL_ACTION_Y = 258 - MAIL_PANEL_OVERFLOW;
    private static final int MAIL_DETAIL_ACTION_SIZE = 11;
    private static final int MAIL_DETAIL_CLAIM_OFFSET_X = -4;
    private static final int MAIL_DETAIL_DELETE_OFFSET_X = -3;
    private static final int MAIL_DETAIL_CLAIM_HOVER_SOURCE_X = 0;
    private static final int MAIL_DETAIL_DELETE_HOVER_SOURCE_X = 11;
    private static final int DAILY_CHEST_SOURCE_WIDTH = 46;
    private static final int DAILY_CHEST_SOURCE_HEIGHT = 34;
    private static final int DAILY_CHEST_WIDTH = 46;
    private static final int DAILY_CHEST_HEIGHT = 34;
    private static final int DAILY_SHADOW_WIDTH = 52;
    private static final int DAILY_SHADOW_HEIGHT = 34;
    private static final int DAILY_SHADOW_OFFSET_X = (DAILY_CHEST_WIDTH - DAILY_SHADOW_WIDTH) / 2;
    private static final int DAILY_SHADOW_OFFSET_Y = 1;
    private static final int DAILY_CHEST_SHADOW_OFFSET_Y = 5;
    private static final int DAILY_CHEST_STEP = 55;
    private static final int DAILY_CHEST_START_X = 6;
    private static final int DAILY_BOX_Y = 74;
    private static final int DAILY_STATUS_CENTER_X = 84;
    private static final int DAILY_KEY_ROW_Y = 128;
    private static final int DAILY_KEY_ICON_SIZE = 16;
    private static final int DAILY_KEY_ICON_STEP = 11;
    private static final int DAILY_KEY_TEXT_GAP = 5;
    private static final int DAILY_CHANCE_BUTTON_SIZE = 16;
    private static final int DAILY_CHANCE_BUTTON_X = 163;
    private static final int DAILY_CHANCE_BUTTON_Y = 118;
    private static final int STREAK_TITLE_CENTER_X = 84;
    private static final int STREAK_TITLE_Y = 155;
    private static final int STREAK_COLUMN_START_X = 4;
    private static final int STREAK_COLUMN_STEP = 23;
    private static final int STREAK_DAY_Y = 180;
    private static final int STREAK_DAY_CELL_WIDTH = 22;
    private static final int STREAK_DAY_NUMBER_OFFSET_X = 1;
    private static final int STREAK_ITEM_START_X = 7;
    private static final int STREAK_ITEM_Y = 194;
    private static final int CHEST_SHAKE_TICKS = 14;
    private static final int REVEAL_STEP_TICKS = 10;
    private static final int REVEAL_COMPLETE_TICKS = 3 * REVEAL_STEP_TICKS;
    private static final int DAILY_CHEST_ITEM_X = (DAILY_CHEST_WIDTH - 16) / 2;
    private static final int DAILY_CHEST_ITEM_Y = (DAILY_CHEST_HEIGHT - 16) / 2;
    private static final float DAILY_UNSELECTED_ITEM_BRIGHTNESS = 0.42F;
    private static final int DAILY_GLOW_SIZE = 72;
    private static final ResourceLocation DAILY_CHEST_CLOSED = YoikoServerCore.id("textures/gui/mailbox/daily_bonus/daily_bonus_chest_closed.png");
    private static final ResourceLocation DAILY_CHEST_HOVER = YoikoServerCore.id("textures/gui/mailbox/daily_bonus/daily_bonus_chest_hover.png");
    private static final ResourceLocation DAILY_CHEST_SHADOW = YoikoServerCore.id("textures/gui/mailbox/daily_bonus/daily_bonus_chest_shadow.png");
    private static final ResourceLocation DAILY_ITEM_SHADOW = YoikoServerCore.id("textures/gui/mailbox/daily_bonus/daily_bonus_item_shadow.png");
    private static final ResourceLocation DAILY_CHEST_GLOW = YoikoServerCore.id("textures/gui/mailbox/daily_bonus/daily_bonus_glow.png");
    private static final ResourceLocation DAILY_KEY_ICON = YoikoServerCore.id("textures/gui/mailbox/daily_bonus/daily_bonus_key.png");
    private static final ResourceLocation DAILY_KEY_SILHOUETTE = YoikoServerCore.id("textures/gui/mailbox/daily_bonus/daily_bonus_key_silhouette.png");
    private static final ResourceLocation MAILBOX_BUTTONS = YoikoServerCore.id("textures/gui/mailbox/mail_row.png");
    private static final ResourceLocation NEWS_CARD_RABBIT = YoikoServerCore.id("textures/gui/mailbox/news_cards/treasure_rabbit_swarm.png");
    private static final ResourceLocation NEWS_CARD_FISHING = YoikoServerCore.id("textures/gui/mailbox/news_cards/fishing_festival.png");
    private static final ResourceLocation NEWS_CARD_CAPTURE = YoikoServerCore.id("textures/gui/mailbox/news_cards/pokemon_capture_goal.png");
    private static final ResourceLocation NEWS_CARD_OUTBREAK = YoikoServerCore.id("textures/gui/mailbox/news_cards/mass_outbreak.png");
    private static final int MAIL_BUTTON_TEXTURE_WIDTH = 170;
    private static final int MAIL_BUTTON_TEXTURE_HEIGHT = 45;
    private static final int MAIL_ROW_SOURCE_Y = 0;
    private static final int MAIL_DETAIL_ACTION_HOVER_SOURCE_Y = 27;
    private static final int MAIL_DELETE_HOVER_SOURCE_X = 87;
    private static final int MAIL_DELETE_HOVER_SOURCE_Y = 31;
    private static final int MAIL_UNREAD_SOURCE_X = 98;
    private static final int MAIL_READ_SOURCE_X = 120;
    private static final int MAIL_ICON_SOURCE_Y = 27;
    private static final int MAIL_SOURCE_REWARD_X = 23;
    private static final int MAIL_SOURCE_MARKET_X = 33;
    private static final int MAIL_SOURCE_SYSTEM_X = 43;
    private static final int MAIL_SOURCE_ICON_Y = 28;
    private static final int MAIL_SOURCE_ICON_WIDTH = 8;
    private static final int MAIL_SOURCE_REWARD_HEIGHT = 8;
    private static final int MAIL_SOURCE_COMPACT_HEIGHT = 7;
    private static final int MAIL_ICON_OUTLINE_COLOR = 0xFFFFFFFF;
    private static final int[] MAIL_SOURCE_REWARD_MASK = {36,90,255,255,126,126,126,126};
    private static final int[] MAIL_SOURCE_MARKET_MASK = {36,66,255,255,126,126,126};
    private static final int[] MAIL_SOURCE_SYSTEM_MASK = {128,240,255,255,254,134,2};
    private static final int MAIL_LIST_ATTACHMENT_X = 53;
    private static final int MAIL_LIST_ATTACHMENT_Y = 28;
    private static final int MAIL_LIST_ATTACHMENT_WIDTH = 5;
    private static final int MAIL_LIST_ATTACHMENT_HEIGHT = 8;
    private static final int[] MAIL_LIST_ATTACHMENT_MASK = {14,17,31,27,27,25,9,14};
    private static final int MAIL_ATTACHMENT_SOURCE_X = 142;
    private static final int MAIL_ATTACHMENT_SOURCE_Y = 27;

    private OpenMailboxPayload payload;
    private final List<YoikoIconButton> iconButtons = new ArrayList<>();
    private DailyChanceButton dailyChanceButton;
    private MailboxDetailActionButton claimButton;
    private YoikoFocusGrid mailListFocus;
    private YoikoFocusGrid dailyBoxFocus;
    private int openingBox = -1;
    private int pendingDailyBox = -1;
    private int pendingOpenSoundBox = -1;
    private int openingTicks;
    private int revealTicks = -1;
    private int mailScroll;
    private int eventCarouselIndex;
    private List<Component> hoveredExtraAttachments = List.of();
    private List<Component> hoveredCurrencyAttachment = List.of();

    public YoikoMailboxScreen(OpenMailboxPayload payload) {
        super(YoikoClientText.tr("yoiko_core.screen.mailbox"));
        this.payload = payload;
        if (isDailyLimitReached(payload)) {
            this.revealTicks = REVEAL_COMPLETE_TICKS;
        }
    }

    @Override
    protected void init() {
        rebuild();
        YoikoMousePosition.restoreIfRemembered();
    }

    public void update(OpenMailboxPayload payload) {
        int previousMask = this.payload.dailyBonusOpenedMask();
        List<OpenMailboxPayload.NewsCard> previousNewsCards = this.payload.newsCards();
        boolean wasLimitReached = isDailyLimitReached(this.payload);
        this.payload = payload;
        if (!previousNewsCards.equals(payload.newsCards())) {
            eventCarouselIndex = 0;
        }
        eventCarouselIndex = Math.max(0, Math.min(eventCarouselIndex, payload.newsCards().size() - 1));
        clampMailScroll();
        int newlyOpenedMask = payload.dailyBonusOpenedMask() & ~previousMask;
        if (newlyOpenedMask != 0) {
            openingBox = validDailyBox(payload.dailyBonusLastOpened()) ? payload.dailyBonusLastOpened() : firstOpenedBox(newlyOpenedMask);
            pendingOpenSoundBox = openingBox;
            openingTicks = CHEST_SHAKE_TICKS;
        }
        pendingDailyBox = -1;
        if (payload.dailyBonusOpenedMask() < previousMask) {
            openingBox = -1;
            pendingOpenSoundBox = -1;
            openingTicks = 0;
        }
        boolean isLimitReached = isDailyLimitReached(payload);
        if (isLimitReached && !wasLimitReached) {
            revealTicks = 0;
        } else if (!isLimitReached) {
            revealTicks = -1;
        } else if (revealTicks < 0) {
            revealTicks = REVEAL_COMPLETE_TICKS;
        }
        rebuild();
    }

    @Override
    public void tick() {
        if (openingTicks > 0) {
            openingTicks--;
            if (openingTicks == 0) {
                openingTicks = 0;
            }
        }
        if (openingTicks <= 0 && isDailyLimitReached(payload) && revealTicks >= 0 && revealTicks < REVEAL_COMPLETE_TICKS) {
            revealTicks++;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        YoikoScreenStyle.renderBackdrop(graphics, this.width, this.height);
        int left = left();
        int mailLeft = mailLeft();
        int top = top();

        YoikoScreenStyle.renderMailboxNewsPanel(graphics, left + RIGHT_PANEL_X, top);
        YoikoScreenStyle.renderMailboxLeftPanel(graphics, mailLeft, top);

        renderHeaders(graphics, mailLeft, top);
        OpenMailboxPayload.MailSummary hoveredMail = renderMailList(graphics, mailLeft, top, mouseX, mouseY);
        ItemStack hoveredMailStack = renderMailDetails(graphics, mailLeft, top, mouseX, mouseY);
        renderNewsBoard(graphics, left, top, mouseX, mouseY);

        YoikoScreenStyle.renderWidgets(this, graphics, mouseX, mouseY, partialTick);
        ClientServerRequestState.render(graphics, this.font, "mailbox", this.width / 2, this.height - 30);
        if (claimButton != null && claimButton.isHoveredOrFocused() && !claimButton.active) {
            graphics.renderTooltip(this.font,
                    YoikoClientText.tr(payload.selectedId().isBlank()
                            ? "yoiko_core.ui.disabled.select_mail"
                            : "yoiko_core.ui.disabled.no_attachments"), mouseX, mouseY);
        } else if (!hoveredCurrencyAttachment.isEmpty()) {
            graphics.renderComponentTooltip(this.font, hoveredCurrencyAttachment, mouseX, mouseY);
        } else if (!hoveredExtraAttachments.isEmpty()) {
            graphics.renderComponentTooltip(this.font, hoveredExtraAttachments, mouseX, mouseY);
        } else if (!hoveredMailStack.isEmpty()) {
            graphics.renderComponentTooltip(this.font, attachmentTooltip(hoveredMailStack), mouseX, mouseY);
        } else if (hoveredMail != null) {
            graphics.renderTooltip(this.font, YoikoClientText.data(hoveredMail.title()), mouseX, mouseY);
        } else {
            YoikoNavigationTabs.renderTooltip(graphics, this.font, iconButtons, mouseX, mouseY);
        }
    }

    private List<Component> attachmentTooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, stack));
        lines.add(YoikoClientText.tr("yoiko_core.ui.mail.attachment_quantity", stack.getCount())
                .withStyle(ChatFormatting.GRAY));
        if (!payload.selectedType().isBlank()) {
            lines.add(YoikoClientText.tr("yoiko_core.ui.mail.attachment_source",
                    YoikoClientText.tr("yoiko_core.ui.mail.source."
                            + attachmentSourceType(payload.selectedType())))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!payload.selectedRarity().isBlank()) {
            lines.add(YoikoClientText.tr("yoiko_core.ui.mail.attachment_rarity",
                    payload.selectedRarity()).withStyle(ChatFormatting.GOLD));
        }
        if (payload.selectedExpiresAt() > 0L) {
            lines.add(YoikoClientText.tr("yoiko_core.ui.mail.attachment_expires",
                    java.time.Instant.ofEpochMilli(payload.selectedExpiresAt()).toString())
                    .withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    private static String attachmentSourceType(String type) {
        if (type != null && type.startsWith("market_")) {
            return "market_return".equals(type) ? "market_return" : "market_delivery";
        }
        return switch (type == null ? "" : type) {
            case "daily", "daily_bonus" -> "daily";
            case "first_login" -> "first_login";
            case "reward" -> "reward";
            case "weekly_reward" -> "reward";
            case "weekly_newspaper" -> "newspaper";
            default -> "admin";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (clickEventCarousel((int) mouseX, (int) mouseY)) {
                return true;
            }
            OpenMailboxPayload.MailSummary deleteMail = deleteMailAt((int) mouseX, (int) mouseY);
            if (deleteMail != null) {
                send(action("mailbox_delete", deleteMail.id()));
                return true;
            }
            OpenMailboxPayload.MailSummary mail = mailAt((int) mouseX, (int) mouseY);
            if (mail != null) {
                focusMail(mail);
                send(action("mailbox_select", mail.id()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideMailList((int) mouseX, (int) mouseY) && payload.mails().size() > MAIL_VISIBLE_ROWS) {
            int previous = mailScroll;
            mailScroll = Math.max(0, Math.min(maxMailScroll(), mailScroll - (int) Math.signum(scrollY)));
            if (mailScroll != previous) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.5F, 0.08F));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void rebuild() {
        boolean restoreMailFocus = mailListFocus != null && mailListFocus.isFocused();
        int previousMailIndex = mailListFocus == null ? 0 : mailListFocus.focusedIndex();
        boolean restoreDailyFocus = dailyBoxFocus != null && dailyBoxFocus.isFocused();
        int previousDailyIndex = dailyBoxFocus == null ? 0 : dailyBoxFocus.focusedIndex();
        clearWidgets();
        iconButtons.clear();
        int left = left();
        int mailLeft = mailLeft();
        int top = top();
        YoikoNavigationTabs.add(iconButtons, mailLeft, top, "mailbox", this::send, button -> this.addRenderableWidget(button));
        dailyChanceButton = null;

        claimButton = addMailboxDetailAction(
                mailLeft + MAIL_DETAIL_ACTION_X + MAIL_DETAIL_CLAIM_OFFSET_X,
                top + MAIL_DETAIL_ACTION_Y,
                MAIL_DETAIL_CLAIM_HOVER_SOURCE_X,
                0,
                YoikoClientText.tr("yoiko_core.ui.mail.claim_items"),
                action("mailbox_claim", payload.selectedId())
        );
        claimButton.active = !payload.selectedId().isBlank() && hasSelectedAttachments();
        MailboxDetailActionButton deleteRead = addMailboxDetailAction(
                mailLeft + MAIL_DETAIL_ACTION_X + MAIL_DETAIL_ACTION_SIZE + MAIL_DETAIL_DELETE_OFFSET_X,
                top + MAIL_DETAIL_ACTION_Y,
                MAIL_DETAIL_DELETE_HOVER_SOURCE_X,
                2,
                YoikoClientText.tr("yoiko_core.ui.mail.delete_read"),
                "mailbox_delete_read"
        );
        deleteRead.active = payload.totalMails() > 0;

        mailListFocus = this.addRenderableWidget(new YoikoFocusGrid(
                mailLeft + MAIL_LIST_X,
                top + MAIL_LIST_Y,
                1,
                MAIL_VISIBLE_ROWS,
                MAIL_LIST_WIDTH,
                MAIL_ROW_HEIGHT,
                MAIL_LIST_WIDTH,
                MAIL_ROW_HEIGHT,
                () -> Math.min(MAIL_VISIBLE_ROWS, Math.max(0, payload.mails().size() - mailScroll)),
                this::activateVisibleMail
        ).withoutFocusOutline());
        int selectedMailIndex = selectedMailIndex();
        if (restoreMailFocus) {
            mailListFocus.setFocusedIndex(previousMailIndex);
        } else if (selectedMailIndex >= mailScroll && selectedMailIndex < mailScroll + MAIL_VISIBLE_ROWS) {
            mailListFocus.setFocusedIndex(selectedMailIndex - mailScroll);
        }

        dailyBoxFocus = null;
        if (restoreMailFocus) {
            setFocused(mailListFocus);
        } else if (restoreDailyFocus && dailyBoxFocus != null) {
            setFocused(dailyBoxFocus);
        }
    }

    private void activateVisibleMail(int visibleIndex) {
        int index = mailScroll + visibleIndex;
        if (index >= 0 && index < payload.mails().size()) {
            OpenMailboxPayload.MailSummary mail = payload.mails().get(index);
            send(action("mailbox_select", mail.id()));
        }
    }

    private void activateDailyBox(int index) {
        if (index < 0 || index >= 3 || isDailyBoxOpened(index)
                || isDailyLimitReached(payload) || pendingDailyBox >= 0) {
            return;
        }
        pendingDailyBox = index;
        send(action("mailbox_daily_box", index, payload.selectedId()));
    }

    private void focusMail(OpenMailboxPayload.MailSummary mail) {
        int index = payload.mails().indexOf(mail) - mailScroll;
        if (mailListFocus != null && index >= 0 && index < MAIL_VISIBLE_ROWS) {
            setFocused(mailListFocus);
            mailListFocus.setFocusedIndex(index);
        }
    }

    private void focusDailyBox(int index) {
        if (dailyBoxFocus != null && index >= 0 && index < 3) {
            setFocused(dailyBoxFocus);
            dailyBoxFocus.setFocusedIndex(index);
        }
    }

    private int selectedMailIndex() {
        for (int i = 0; i < payload.mails().size(); i++) {
            if (payload.mails().get(i).id().equals(payload.selectedId())) {
                return i;
            }
        }
        return -1;
    }

    private void renderHeaders(GuiGraphics graphics, int mailLeft, int top) {
        Component count = Component.literal(payload.totalMails() + "/" + PlayerYoikoData.MAX_MAILBOX_MAILS);
        int color = payload.totalMails() >= PlayerYoikoData.MAX_MAILBOX_MAILS
                ? 0xFFFF7A68
                : payload.totalMails() >= PlayerYoikoData.MAX_MAILBOX_MAILS - 2 ? 0xFFFFC56F : MAIL_TEXT_LIGHT;
        graphics.drawCenteredString(this.font, count, mailLeft + 166, top + 15, color);
        if (payload.totalMails() >= PlayerYoikoData.MAX_MAILBOX_MAILS - 2) {
            graphics.drawString(this.font, Component.literal("!"), mailLeft + 146, top + 15, color, false);
        }
    }

    private OpenMailboxPayload.MailSummary renderMailList(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        OpenMailboxPayload.MailSummary hovered = null;
        int listX = left + MAIL_LIST_X;
        int listY = top + MAIL_LIST_Y;
        if (payload.mails().isEmpty()) {
            graphics.drawCenteredString(this.font, YoikoClientText.tr("yoiko_core.ui.empty.mailbox"),
                    listX + MAIL_LIST_WIDTH / 2,
                    listY + MAIL_VISIBLE_ROWS * MAIL_ROW_HEIGHT / 2,
                    MAIL_TEXT_SECONDARY);
        }
        for (int row = 0; row < MAIL_VISIBLE_ROWS; row++) {
            int index = mailScroll + row;
            int rowY = listY + row * MAIL_ROW_HEIGHT;
            boolean occupied = index < payload.mails().size();
            if (!occupied) {
                continue;
            }

            OpenMailboxPayload.MailSummary mail = payload.mails().get(index);
            boolean selected = mail.id().equals(payload.selectedId());
            graphics.blit(MAILBOX_BUTTONS, listX, rowY, MAIL_LIST_WIDTH, MAIL_ROW_HEIGHT,
                    0.0F, MAIL_ROW_SOURCE_Y, MAIL_LIST_WIDTH, MAIL_ROW_HEIGHT,
                    MAIL_BUTTON_TEXTURE_WIDTH, MAIL_BUTTON_TEXTURE_HEIGHT);
            if (selected) {
                graphics.fill(listX + 2, rowY + 2, listX + MAIL_LIST_WIDTH - 2, rowY + MAIL_ROW_HEIGHT - 2, 0x22FFFFFF);
            }

            int mailIconSourceX = mail.read() ? MAIL_READ_SOURCE_X : MAIL_UNREAD_SOURCE_X;
            graphics.blit(MAILBOX_BUTTONS, listX + MAIL_ICON_X, rowY + MAIL_ICON_Y,
                    MAIL_ICON_WIDTH, MAIL_ICON_HEIGHT,
                    (float) mailIconSourceX, MAIL_ICON_SOURCE_Y, MAIL_ICON_WIDTH, MAIL_ICON_HEIGHT,
                    MAIL_BUTTON_TEXTURE_WIDTH, MAIL_BUTTON_TEXTURE_HEIGHT);
            int textX = listX + 29;
            int deleteX = listX + MAIL_DELETE_X;
            int dateRight = deleteX - 11;
            int attachmentX = dateRight - MAIL_LIST_ATTACHMENT_WIDTH - 1;
            int titleRight = mail.itemCount() > 0 ? attachmentX - 2 : dateRight;
            Component title = YoikoClientText.data(mail.title());
            int sourceIconX=textX;
            int titleX=textX+MAIL_SOURCE_ICON_WIDTH+3;
            String titleText = this.font.plainSubstrByWidth(title.getString(),
                    Math.max(16, titleRight - titleX));
            int titleColor = mail.read() ? MAIL_TEXT_PRIMARY : MAIL_TEXT_STRONG;
            renderMailSourceBadge(graphics, sourceIconX, rowY + 4, mail.type());
            graphics.drawString(this.font, Component.literal(titleText), titleX, rowY + 4, titleColor, false);

            String date = mailListDate(mail.createdAt());
            int dateX = Math.max(textX + 48, dateRight - this.font.width(date) - 1);
            String senderText = YoikoClientText.dataText(mail.sender());
            senderText = this.font.plainSubstrByWidth(senderText, Math.max(12, dateX - textX - 4));
            graphics.drawString(this.font, senderText, textX, rowY + 15, MAIL_TEXT_SECONDARY, false);
            graphics.drawString(this.font, date, dateX, rowY + 15, MAIL_TEXT_PRIMARY, false);
            if (mail.itemCount() > 0) {
                renderPixelOutline(graphics,attachmentX,rowY+4,MAIL_LIST_ATTACHMENT_WIDTH,
                        MAIL_LIST_ATTACHMENT_HEIGHT,MAIL_LIST_ATTACHMENT_MASK);
                graphics.blit(MAILBOX_BUTTONS, attachmentX, rowY + 4,
                        MAIL_LIST_ATTACHMENT_WIDTH, MAIL_LIST_ATTACHMENT_HEIGHT,
                        (float) MAIL_LIST_ATTACHMENT_X, MAIL_LIST_ATTACHMENT_Y,
                        MAIL_LIST_ATTACHMENT_WIDTH, MAIL_LIST_ATTACHMENT_HEIGHT,
                        MAIL_BUTTON_TEXTURE_WIDTH, MAIL_BUTTON_TEXTURE_HEIGHT);
            }

            boolean deleteHovered = isInside(mouseX, mouseY, deleteX, rowY + MAIL_DELETE_Y,
                    MAIL_DELETE_WIDTH, MAIL_DELETE_HEIGHT);
            if (mail.itemCount() > 0 || deleteHovered) {
                if (mail.itemCount() > 0) {
                    RenderSystem.setShaderColor(0.55F, 0.48F, 0.40F, 0.55F);
                }
                graphics.blit(MAILBOX_BUTTONS, deleteX, rowY + MAIL_DELETE_Y,
                        MAIL_DELETE_WIDTH, MAIL_DELETE_HEIGHT,
                        (float) MAIL_DELETE_HOVER_SOURCE_X, MAIL_DELETE_HOVER_SOURCE_Y,
                        MAIL_DELETE_WIDTH, MAIL_DELETE_HEIGHT,
                        MAIL_BUTTON_TEXTURE_WIDTH, MAIL_BUTTON_TEXTURE_HEIGHT);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            if (!selected) {
                graphics.fill(
                        listX + 1,
                        rowY + 1,
                        listX + MAIL_LIST_WIDTH - 1,
                        rowY + MAIL_ROW_HEIGHT - 2,
                        MAIL_UNSELECTED_DIM_COLOR
                );
                if (deleteHovered && mail.itemCount() == 0) {
                    graphics.blit(MAILBOX_BUTTONS, deleteX, rowY + MAIL_DELETE_Y,
                            MAIL_DELETE_WIDTH, MAIL_DELETE_HEIGHT,
                            (float) MAIL_DELETE_HOVER_SOURCE_X, MAIL_DELETE_HOVER_SOURCE_Y,
                            MAIL_DELETE_WIDTH, MAIL_DELETE_HEIGHT,
                            MAIL_BUTTON_TEXTURE_WIDTH, MAIL_BUTTON_TEXTURE_HEIGHT);
                }
            }
            if (isInside(mouseX, mouseY, listX, rowY, MAIL_LIST_WIDTH, MAIL_ROW_HEIGHT - 2)) {
                hovered = mail;
            }
        }

        renderMailScrollbar(graphics, left, top);

        return hovered;
    }

    private static void renderMailSourceBadge(GuiGraphics graphics, int x, int y, String type) {
        boolean market = type != null && type.startsWith("market_");
        boolean reward = switch (type == null ? "" : type) {
            case "daily", "first_login", "reward", "daily_bonus" -> true;
            default -> false;
        };
        int sourceX = market ? MAIL_SOURCE_MARKET_X
                : reward ? MAIL_SOURCE_REWARD_X : MAIL_SOURCE_SYSTEM_X;
        int height = reward ? MAIL_SOURCE_REWARD_HEIGHT : MAIL_SOURCE_COMPACT_HEIGHT;
        int[] mask=market?MAIL_SOURCE_MARKET_MASK:reward?MAIL_SOURCE_REWARD_MASK:MAIL_SOURCE_SYSTEM_MASK;
        renderPixelOutline(graphics,x,y,MAIL_SOURCE_ICON_WIDTH,height,mask);
        graphics.blit(MAILBOX_BUTTONS, x, y, MAIL_SOURCE_ICON_WIDTH, height,
                (float) sourceX, MAIL_SOURCE_ICON_Y, MAIL_SOURCE_ICON_WIDTH, height,
                MAIL_BUTTON_TEXTURE_WIDTH, MAIL_BUTTON_TEXTURE_HEIGHT);
    }

    /** Draws an eight-connected one-pixel silhouette without changing the source atlas. */
    private static void renderPixelOutline(GuiGraphics graphics,int x,int y,int width,int height,int[] rows){
        for(int py=-1;py<=height;py++){
            for(int px=-1;px<=width;px++){
                if(maskPixel(rows,width,height,px,py))continue;
                boolean edge=false;
                for(int oy=-1;oy<=1&&!edge;oy++)for(int ox=-1;ox<=1;ox++){
                    if((ox!=0||oy!=0)&&maskPixel(rows,width,height,px+ox,py+oy)){edge=true;break;}
                }
                if(edge)graphics.fill(x+px,y+py,x+px+1,y+py+1,MAIL_ICON_OUTLINE_COLOR);
            }
        }
    }

    private static boolean maskPixel(int[] rows,int width,int height,int x,int y){
        return x>=0&&x<width&&y>=0&&y<height&&y<rows.length&&(rows[y]&(1<<x))!=0;
    }

    private void renderMailScrollbar(GuiGraphics graphics, int left, int top) {
        if (maxMailScroll() == 0) {
            return;
        }
        int x = left + MAIL_SCROLLBAR_X;
        int y = top + MAIL_LIST_Y + MAIL_SCROLLBAR_Y_OFFSET;

        int mailCount = payload.mails().size();
        int thumbHeight = Math.max(MAIL_SCROLLBAR_MIN_THUMB_HEIGHT,
                MAIL_SCROLLBAR_HEIGHT * MAIL_VISIBLE_ROWS / mailCount);
        int travel = Math.max(0, MAIL_SCROLLBAR_HEIGHT - thumbHeight - 1);
        int thumbOffset = Math.round((float) travel * mailScroll / maxMailScroll());
        int thumbY = y + thumbOffset;
        graphics.fill(x, thumbY, x + MAIL_SCROLLBAR_WIDTH, thumbY + thumbHeight, MAIL_SCROLLBAR_THUMB_COLOR);
        graphics.fill(x, thumbY, x + MAIL_SCROLLBAR_WIDTH, thumbY + 1,
                MAIL_SCROLLBAR_THUMB_HIGHLIGHT_COLOR);
    }

    private ItemStack renderMailDetails(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        hoveredExtraAttachments = List.of();
        hoveredCurrencyAttachment = List.of();
        int titleX = left + MAIL_DETAIL_TITLE_X;
        int textX = left + MAIL_DETAIL_TEXT_X;
        int titleY = top + MAIL_DETAIL_TITLE_Y;
        Component selectedTitle = payload.selectedTitle().isBlank()
                ? YoikoClientText.tr("yoiko_core.ui.mail.empty")
                : YoikoClientText.data(payload.selectedTitle());
        drawWrapped(graphics, selectedTitle, titleX, titleY,
                MAIL_DETAIL_TITLE_WIDTH, 1, MAIL_TEXT_ACCENT);

        String sender = YoikoClientText.dataText(payload.selectedSender());
        String meta = sender.isBlank() ? payload.selectedCreatedAt() : sender + " / " + payload.selectedCreatedAt();
        if (!meta.isBlank()) {
            String clippedMeta = this.font.plainSubstrByWidth(meta, MAIL_DETAIL_BODY_WIDTH);
            graphics.drawString(this.font, Component.literal(clippedMeta),
                    textX + MAIL_DETAIL_BODY_OFFSET_X, top + MAIL_DETAIL_META_Y, MAIL_TEXT_SECONDARY, false);
        }
        Component detailMessage = YoikoClientText.data(payload.selectedMessage());
        drawWrapped(graphics, detailMessage, textX + MAIL_DETAIL_BODY_OFFSET_X, top + MAIL_DETAIL_BODY_Y,
                MAIL_DETAIL_BODY_WIDTH, 2, MAIL_TEXT_PRIMARY);

        ItemStack hovered = ItemStack.EMPTY;
        int visibleItems = Math.min(MAIL_DETAIL_ATTACHMENT_VISIBLE, payload.selectedItemStacks().size());
        int itemX = left + MAIL_DETAIL_ATTACHMENT_X;
        int itemY = top + MAIL_DETAIL_ACTION_Y + MAIL_DETAIL_ATTACHMENT_Y_OFFSET;
        int currencyX = itemX;
        int currencyY = itemY - 12;
        if (payload.selectedAttachedGold() > 0L) {
            currencyX = renderCurrencyAttachment(graphics, "GOLD", payload.selectedAttachedGold(),
                    currencyX, currencyY, mouseX, mouseY);
        }
        if (payload.selectedAttachedGems() > 0L) {
            renderCurrencyAttachment(graphics, "GEM", payload.selectedAttachedGems(),
                    currencyX, currencyY, mouseX, mouseY);
        }
        for (int i = 0; i < visibleItems; i++) {
            int slotX = itemX + i * SLOT_SIZE;
            int slotY = itemY;
            ItemStack stack = payload.selectedItemStacks().get(i);
            graphics.blit(MAILBOX_BUTTONS, slotX, slotY, SLOT_SIZE, SLOT_SIZE,
                    (float) MAIL_ATTACHMENT_SOURCE_X, MAIL_ATTACHMENT_SOURCE_Y, SLOT_SIZE, SLOT_SIZE,
                    MAIL_BUTTON_TEXTURE_WIDTH, MAIL_BUTTON_TEXTURE_HEIGHT);
            int stackX = slotX + 1;
            int stackY = slotY + 1;
            graphics.renderItem(stack, stackX, stackY);
            graphics.renderItemDecorations(this.font, stack, stackX, stackY);
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                hovered = stack;
            }
        }
        if (payload.selectedItemStacks().size() > MAIL_DETAIL_ATTACHMENT_VISIBLE) {
            int slotX = itemX + MAIL_DETAIL_ATTACHMENT_VISIBLE * SLOT_SIZE;
            int slotY = itemY;
            graphics.blit(MAILBOX_BUTTONS, slotX, slotY, SLOT_SIZE, SLOT_SIZE,
                    (float) MAIL_ATTACHMENT_SOURCE_X, MAIL_ATTACHMENT_SOURCE_Y, SLOT_SIZE, SLOT_SIZE,
                    MAIL_BUTTON_TEXTURE_WIDTH, MAIL_BUTTON_TEXTURE_HEIGHT);
            graphics.drawCenteredString(this.font, Component.literal("+"),
                    slotX + SLOT_SIZE / 2, slotY + 5, 0xFFE29B45);
            if (isInside(mouseX, mouseY, slotX, slotY, SLOT_SIZE, SLOT_SIZE)) {
                List<Component> extra = new ArrayList<>();
                extra.add(YoikoClientText.tr("yoiko_core.ui.mail.more_attachments",
                        payload.selectedItemStacks().size() - MAIL_DETAIL_ATTACHMENT_VISIBLE));
                for (int i = MAIL_DETAIL_ATTACHMENT_VISIBLE; i < payload.selectedItemStacks().size(); i++) {
                    extra.add(Component.literal(payload.selectedItemStacks().get(i).getHoverName().getString()));
                }
                hoveredExtraAttachments = List.copyOf(extra);
            }
        }
        return hovered;
    }

    private int renderCurrencyAttachment(GuiGraphics graphics, String currency, long amount,
                                         int x, int y, int mouseX, int mouseY) {
        String displayed = formatCurrencyAttachment(amount);
        int contentWidth = YoikoCurrencyIconRenderer.ICON_SIZE
                + YoikoCurrencyIconRenderer.TEXT_GAP + this.font.width(displayed);
        int width = contentWidth;
        YoikoCurrencyIconRenderer.render(graphics, currency, x, y + 1);
        graphics.drawString(this.font, Component.literal(displayed),
                x + YoikoCurrencyIconRenderer.ICON_SIZE + YoikoCurrencyIconRenderer.TEXT_GAP,
                y + 1, YoikoCurrencyIconRenderer.color(currency), false);
        if (isInside(mouseX, mouseY, x, y, width, 10)) {
            String formatted = String.format(Locale.ROOT, "%,d", amount);
            hoveredCurrencyAttachment = List.of(YoikoClientText.tr(
                    "GEM".equals(currency)
                            ? "yoiko_core.ui.mail.attached_gems"
                            : "yoiko_core.ui.mail.attached_gold",
                    formatted));
        }
        return x + width + 6;
    }

    private static String formatCurrencyAttachment(long amount) {
        if (amount < 1_000L) return Long.toString(amount);
        if (amount < 1_000_000L) return compactAmount(amount, 1_000.0D, "K");
        if (amount < 1_000_000_000L) return compactAmount(amount, 1_000_000.0D, "M");
        if (amount < 1_000_000_000_000L) return compactAmount(amount, 1_000_000_000.0D, "B");
        if (amount < 1_000_000_000_000_000L) return compactAmount(amount, 1_000_000_000_000.0D, "T");
        return compactAmount(amount, 1_000_000_000_000_000.0D, "Q");
    }

    private static String compactAmount(long amount, double divisor, String suffix) {
        double value = amount / divisor;
        return value >= 100.0D
                ? String.format(Locale.ROOT, "%.0f%s", value, suffix)
                : value >= 10.0D
                ? String.format(Locale.ROOT, "%.1f%s", value, suffix)
                : String.format(Locale.ROOT, "%.2f%s", value, suffix);
    }

    private boolean hasSelectedAttachments() {
        return !payload.selectedItemStacks().isEmpty()
                || payload.selectedAttachedGold() > 0L
                || payload.selectedAttachedGems() > 0L;
    }

    private void renderNewsBoard(GuiGraphics graphics, int left, int top,
                                 int mouseX, int mouseY) {
        int panelX = left + RIGHT_PANEL_X;
        int centerX = panelX + NEWS_CONTENT_CENTER_X;
        graphics.drawCenteredString(this.font,
                YoikoClientText.tr("yoiko_core.ui.news.title").copy().withStyle(ChatFormatting.BOLD),
                centerX, top + NEWS_MASTHEAD_Y, MAIL_TEXT_STRONG);
        graphics.drawCenteredString(this.font, newspaperPeriod(),
                centerX, top + NEWS_SUBTITLE_Y, MAIL_TEXT_SECONDARY);

        renderFrontPageStory(graphics, panelX, top);

        Component eventTitle = YoikoClientText.tr("yoiko_core.ui.news.event_records")
                .copy().withStyle(ChatFormatting.BOLD);
        int eventTitleX = centerX - this.font.width(eventTitle) / 2;
        graphics.fill(eventTitleX - 4, top + NEWS_EVENT_TITLE_Y - 1,
                eventTitleX + this.font.width(eventTitle) + 4,
                top + NEWS_EVENT_TITLE_Y + this.font.lineHeight, 0xFFF8E9C2);
        graphics.drawCenteredString(this.font,
                eventTitle, centerX, top + NEWS_EVENT_TITLE_Y, MAIL_TEXT_SECONDARY);
        renderEventCards(graphics, panelX, top, mouseX, mouseY);
    }

    private void renderFrontPageStory(GuiGraphics graphics, int panelX, int top) {
        OpenMailboxPayload.NewsCard card = selectedNewsCard();
        Component headline = card == null
                ? YoikoClientText.tr("yoiko_core.newspaper.empty.headline")
                : YoikoClientText.data(card.title());
        Component article = card == null
                ? YoikoClientText.tr("yoiko_core.newspaper.empty.body")
                : YoikoClientText.data(card.body());

        List<FormattedCharSequence> headlineLines = this.font.split(headline, NEWS_STORY_TITLE_WIDTH);
        int headlineCount = Math.min(NEWS_STORY_TITLE_LINES, headlineLines.size());
        for (int index = 0; index < headlineCount; index++) {
            FormattedCharSequence line = headlineLines.get(index);
            int lineX = panelX + NEWS_CONTENT_CENTER_X - this.font.width(line) / 2;
            graphics.drawString(this.font, line, lineX,
                    top + NEWS_STORY_TITLE_Y + index * this.font.lineHeight,
                    MAIL_TEXT_STRONG, false);
        }

        graphics.fill(panelX + NEWS_STORY_X, top + NEWS_STORY_RULE_Y,
                panelX + NEWS_STORY_X + NEWS_STORY_BODY_WIDTH, top + NEWS_STORY_RULE_Y + 1,
                0x668B6D4E);

        List<FormattedCharSequence> bodyLines = this.font.split(article, NEWS_STORY_BODY_WIDTH);
        int bodyCount = Math.min(NEWS_STORY_BODY_LINES, bodyLines.size());
        for (int index = 0; index < bodyCount; index++) {
            graphics.drawString(this.font, bodyLines.get(index),
                    panelX + NEWS_STORY_X, top + NEWS_STORY_BODY_Y + index * this.font.lineHeight,
                    MAIL_TEXT_PRIMARY, false);
        }

        if (card != null && !card.meta().isBlank()) {
            Component meta = YoikoClientText.data(card.meta());
            graphics.drawString(this.font, meta,
                    panelX + NEWS_STORY_X + NEWS_STORY_BODY_WIDTH - this.font.width(meta),
                    top + NEWS_STORY_META_Y, MAIL_TEXT_SOFT, false);
        }
    }

    private void renderEventCards(GuiGraphics graphics, int panelX, int top,
                                  int mouseX, int mouseY) {
        List<OpenMailboxPayload.NewsCard> cards = payload.newsCards();
        if (cards.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    YoikoClientText.tr("yoiko_core.ui.news.no_event_cards"),
                    panelX + NEWS_CONTENT_CENTER_X, top + NEWS_EVENT_CARD_Y + 28,
                    MAIL_TEXT_SOFT);
            return;
        }

        eventCarouselIndex = Math.max(0, Math.min(eventCarouselIndex, cards.size() - 1));
        int cardX = panelX + NEWS_EVENT_CARD_X;
        int cardY = top + NEWS_EVENT_CARD_Y;
        renderEventCard(graphics, cards.get(eventCarouselIndex), cardX, cardY);

        if (cards.size() > 1) {
            renderEventCarouselArrow(graphics,
                    panelX + NEWS_EVENT_LEFT_ARROW_X, top + NEWS_EVENT_ARROW_Y,
                    true, isInside(mouseX, mouseY,
                            panelX + NEWS_EVENT_LEFT_ARROW_X, top + NEWS_EVENT_ARROW_Y,
                            NEWS_EVENT_ARROW_WIDTH, NEWS_EVENT_ARROW_HEIGHT));
            renderEventCarouselArrow(graphics,
                    panelX + NEWS_EVENT_RIGHT_ARROW_X, top + NEWS_EVENT_ARROW_Y,
                    false, isInside(mouseX, mouseY,
                            panelX + NEWS_EVENT_RIGHT_ARROW_X, top + NEWS_EVENT_ARROW_Y,
                            NEWS_EVENT_ARROW_WIDTH, NEWS_EVENT_ARROW_HEIGHT));
            renderEventPageDots(graphics, panelX + NEWS_CONTENT_CENTER_X, top, cards.size());
        }
    }

    private void renderEventCard(GuiGraphics graphics, OpenMailboxPayload.NewsCard card,
                                 int x, int y) {
        ResourceLocation texture = eventCardTexture(card.kind());
        if (texture == null) {
            renderUnknownEventCard(graphics, x, y);
        } else {
            graphics.blit(texture, x, y, NEWS_EVENT_CARD_WIDTH, NEWS_EVENT_CARD_HEIGHT,
                    0.0F, 0.0F, NEWS_EVENT_CARD_WIDTH, NEWS_EVENT_CARD_HEIGHT,
                    NEWS_EVENT_CARD_WIDTH, NEWS_EVENT_CARD_HEIGHT);
        }

        graphics.fill(x + 4, y + 43, x + NEWS_EVENT_CARD_WIDTH - 4, y + 44,
                0x668B6D4E);

        Component label = fittedNewsText(eventCardLabel(card.kind()),
                NEWS_EVENT_CARD_WIDTH - NEWS_EVENT_CARD_TITLE_X - 7);
        graphics.drawString(this.font, label,
                x + NEWS_EVENT_CARD_TITLE_X, y + NEWS_EVENT_CARD_TITLE_Y,
                MAIL_TEXT_STRONG, false);
        if (!card.meta().isBlank()) {
            Component meta = YoikoClientText.data(card.meta());
            graphics.drawString(this.font, meta,
                    x + NEWS_EVENT_CARD_WIDTH - this.font.width(meta) - 7,
                    y + NEWS_EVENT_CARD_META_Y, MAIL_TEXT_SOFT, false);
        }
    }

    private void renderUnknownEventCard(GuiGraphics graphics, int x, int y) {
        int centerX = x + NEWS_EVENT_CARD_WIDTH / 2;
        int centerY = y + 21;
        int ink = 0xFF6B4A32;
        graphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, ink);
        graphics.fill(centerX - 8, centerY, centerX - 5, centerY + 1, ink);
        graphics.fill(centerX + 5, centerY, centerX + 8, centerY + 1, ink);
        graphics.fill(centerX, centerY - 8, centerX + 1, centerY - 5, ink);
        graphics.fill(centerX, centerY + 5, centerX + 1, centerY + 8, ink);
    }

    private void renderEventCarouselArrow(GuiGraphics graphics, int x, int y,
                                          boolean pointsLeft, boolean hovered) {
        int color = hovered ? MAIL_TEXT_ACCENT : MAIL_TEXT_SOFT;
        int centerY = y + NEWS_EVENT_ARROW_HEIGHT / 2;
        for (int offset = 0; offset < 5; offset++) {
            int arrowX = pointsLeft ? x + 7 - offset : x + 4 + offset;
            graphics.fill(arrowX, centerY - offset,
                    arrowX + 1, centerY + offset + 1, color);
        }
    }

    private void renderEventPageDots(GuiGraphics graphics, int centerX, int top, int cardCount) {
        int visibleCount = Math.min(cardCount, 7);
        int spacing = 6;
        int startX = centerX - ((visibleCount - 1) * spacing) / 2;
        int firstCard = Math.max(0, Math.min(eventCarouselIndex - visibleCount / 2,
                cardCount - visibleCount));
        for (int index = 0; index < visibleCount; index++) {
            int x = startX + index * spacing;
            int color = firstCard + index == eventCarouselIndex ? MAIL_TEXT_ACCENT : 0xFFCCB585;
            graphics.fill(x - 1, top + NEWS_EVENT_DOT_Y + 1, x + 2, top + NEWS_EVENT_DOT_Y + 2, color);
            graphics.fill(x, top + NEWS_EVENT_DOT_Y, x + 1, top + NEWS_EVENT_DOT_Y + 3, color);
        }
    }

    private boolean clickEventCarousel(int mouseX, int mouseY) {
        List<OpenMailboxPayload.NewsCard> cards = payload.newsCards();
        if (cards.isEmpty()) {
            return false;
        }
        int panelX = left() + RIGHT_PANEL_X;
        int panelY = top();
        if (cards.size() <= 1) {
            return false;
        }
        if (isInside(mouseX, mouseY,
                panelX + NEWS_EVENT_LEFT_ARROW_X, panelY + NEWS_EVENT_ARROW_Y,
                NEWS_EVENT_ARROW_WIDTH, NEWS_EVENT_ARROW_HEIGHT)) {
            advanceEventCard(-1);
            return true;
        }
        if (isInside(mouseX, mouseY,
                panelX + NEWS_EVENT_RIGHT_ARROW_X, panelY + NEWS_EVENT_ARROW_Y,
                NEWS_EVENT_ARROW_WIDTH, NEWS_EVENT_ARROW_HEIGHT)) {
            advanceEventCard(1);
            return true;
        }
        return false;
    }

    private void advanceEventCard(int direction) {
        eventCarouselIndex = Math.floorMod(eventCarouselIndex + direction,
                payload.newsCards().size());
        playNewspaperPageSound();
    }

    private void playNewspaperPageSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.45F));
    }

    private OpenMailboxPayload.NewsCard selectedNewsCard() {
        if (payload.newsCards().isEmpty()) {
            return null;
        }
        eventCarouselIndex = Math.max(0, Math.min(eventCarouselIndex, payload.newsCards().size() - 1));
        return payload.newsCards().get(eventCarouselIndex);
    }

    private static ResourceLocation eventCardTexture(String kind) {
        return switch (kind == null ? "" : kind) {
            case "treasure_rabbit_swarm" -> NEWS_CARD_RABBIT;
            case "fishing_festival" -> NEWS_CARD_FISHING;
            case "pokemon_capture_goal" -> NEWS_CARD_CAPTURE;
            case "mass_outbreak" -> NEWS_CARD_OUTBREAK;
            default -> null;
        };
    }

    private static Component eventCardLabel(String kind) {
        String suffix = switch (kind == null ? "" : kind) {
            case "treasure_rabbit_swarm" -> "rabbit_swarm";
            case "fishing_festival" -> "fishing";
            case "pokemon_capture_goal" -> "capture";
            case "mass_outbreak" -> "outbreak";
            default -> "unknown";
        };
        return YoikoClientText.tr("yoiko_core.ui.news.card." + suffix);
    }

    private Component fittedNewsText(Component text, int width) {
        return Component.literal(this.font.plainSubstrByWidth(text.getString(), width));
    }

    private Component newspaperPeriod() {
        String title = YoikoClientText.dataText(payload.weeklyNewspaperTitle());
        int separator = title.indexOf('·');
        String period = separator >= 0 ? title.substring(separator + 1).trim() : title;
        if (period.isBlank()) {
            return YoikoClientText.tr("yoiko_core.ui.news.subtitle");
        }
        return fittedNewsText(Component.literal(period), NEWS_STORY_TITLE_WIDTH);
    }

    private static String mailListDate(String createdAt) {
        int separator = createdAt.indexOf(' ');
        return separator > 0 ? createdAt.substring(0, separator) : createdAt;
    }

    private ItemStack renderDailyBonus(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int panelX = left + RIGHT_PANEL_X;

        ItemStack hoveredStack = ItemStack.EMPTY;
        for (int i = 0; i < 3; i++) {
            int boxX = dailyBoxX(left, i);
            int boxY = dailyBoxY(top, i);
            ItemStack stack = i < payload.dailyBonusBoxes().size() ? payload.dailyBonusBoxes().get(i) : ItemStack.EMPTY;
            int rewardCount = i < payload.dailyBonusCounts().size()
                    ? Math.max(1, payload.dailyBonusCounts().get(i))
                    : Math.max(1, stack.getCount());
            OpenMailboxPayload.DailyBonusCosmeticVisual cosmeticVisual = i < payload.dailyBonusCosmeticVisuals().size()
                    ? payload.dailyBonusCosmeticVisuals().get(i)
                    : null;
            boolean open = isDailyBoxVisuallyOpen(i);
            boolean chestHovered = isDailyBoxHovered(mouseX, mouseY, boxX, boxY);
            boolean chestHighlighted = chestHovered
                    || i == pendingDailyBox
                    || (i == openingBox && openingTicks > 0);
            renderDailyChest(graphics, boxX, boxY, i, stack, rewardCount, cosmeticVisual, open, chestHighlighted);
            if (chestHovered && open && !stack.isEmpty()) {
                hoveredStack = stack;
            }
        }

        renderDailyKeyStatus(graphics, panelX, top);
        return hoveredStack;
    }

    private void renderDailyKeyStatus(GuiGraphics graphics, int panelX, int top) {
        Component next = YoikoClientText.tr("yoiko_core.ui.mail.next_key_in", payload.dailyBonusNextKeyText());
        int rowY = top + DAILY_KEY_ROW_Y;
        int maxKeys = Math.max(1, payload.dailyBonusMaxKeys());
        int availableKeys = Math.max(0, Math.min(payload.dailyBonusKeys(), maxKeys));
        int iconRowWidth = DAILY_KEY_ICON_SIZE + (maxKeys - 1) * DAILY_KEY_ICON_STEP;
        int totalWidth = this.font.width(next) + DAILY_KEY_TEXT_GAP + iconRowWidth;
        int startX = panelX + DAILY_STATUS_CENTER_X - totalWidth / 2;
        graphics.drawString(this.font, next, startX, rowY + 4, MAIL_TEXT_SOFT, false);

        int iconX = startX + this.font.width(next) + DAILY_KEY_TEXT_GAP;
        for (int i = maxKeys - 1; i >= 0; i--) {
            ResourceLocation texture = i < availableKeys ? DAILY_KEY_ICON : DAILY_KEY_SILHOUETTE;
            graphics.blit(
                    texture,
                    iconX + i * DAILY_KEY_ICON_STEP,
                    rowY,
                    DAILY_KEY_ICON_SIZE,
                    DAILY_KEY_ICON_SIZE,
                    0.0F,
                    0.0F,
                    DAILY_KEY_ICON_SIZE,
                    DAILY_KEY_ICON_SIZE,
                    DAILY_KEY_ICON_SIZE,
                    DAILY_KEY_ICON_SIZE
            );
        }
    }

    private List<Component> dailyChanceTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(YoikoClientText.tr("yoiko_core.ui.mail.daily_chance.title").withStyle(ChatFormatting.GOLD));
        lines.add(YoikoClientText.tr("yoiko_core.ui.mail.daily_chance.current_pool").withStyle(ChatFormatting.GRAY));
        for (OpenMailboxPayload.DailyBonusChance chance : payload.dailyBonusChances()) {
            ChatFormatting color = dailyChanceColor(chance.rarity());
            Component rarity = YoikoClientText.tr(dailyRarityTranslationKey(chance.rarity())).withStyle(color);
            Component percent = Component.literal(formatDailyChance(chance.percent())).withStyle(ChatFormatting.WHITE);
            if (chance.allOwned()) {
                percent = Component.empty()
                        .append(percent)
                        .append(Component.literal(" "))
                        .append(YoikoClientText.tr("yoiko_core.ui.mail.daily_chance.gem_conversion")
                                .withStyle(ChatFormatting.AQUA));
            }
            lines.add(Component.empty().append(rarity).append(Component.literal("  ")).append(percent));
        }
        return lines;
    }

    private static String dailyRarityTranslationKey(String rarity) {
        return "yoiko_core.rarity." + switch (rarity.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> "uncommon";
            case "RARE" -> "rare";
            case "EPIC" -> "epic";
            case "LEGENDARY" -> "legendary";
            case "MYTHIC", "MYSTIC" -> "mystic";
            case "RADIANT" -> "radiant";
            default -> "common";
        };
    }

    private static ChatFormatting dailyChanceColor(String rarity) {
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

    private static String formatDailyChance(double percent) {
        if (percent <= 0.0D) {
            return "0%";
        }
        if (percent < 0.01D) {
            return String.format(Locale.ROOT, "%.4f%%", percent);
        }
        if (percent < 1.0D) {
            return String.format(Locale.ROOT, "%.3f%%", percent);
        }
        return String.format(Locale.ROOT, "%.2f%%", percent);
    }

    private void renderDailyChest(GuiGraphics graphics, int x, int y, int index, ItemStack stack, int rewardCount,
                                  OpenMailboxPayload.DailyBonusCosmeticVisual cosmeticVisual,
                                  boolean open, boolean hovered) {
        int shake = shakeOffset(index);
        int drawX = x + shake;
        int drawY = y;
        boolean unopenedReveal = open && !isDailyBoxOpened(index);
        renderDailyShadow(
                graphics,
                open ? DAILY_ITEM_SHADOW : DAILY_CHEST_SHADOW,
                x,
                drawY + DAILY_SHADOW_OFFSET_Y + (open ? 0 : DAILY_CHEST_SHADOW_OFFSET_Y)
        );
        if (open && !stack.isEmpty()) {
            renderDailyGlow(
                    graphics,
                    x + DAILY_CHEST_WIDTH / 2,
                    y + DAILY_CHEST_HEIGHT / 2,
                    dailyRarityColor(index, stack),
                    index == payload.dailyBonusLastOpened(),
                    unopenedReveal
            );
        }
        if (open && pendingOpenSoundBox == index) {
            playDailyOpenSound(index);
            pendingOpenSoundBox = -1;
            openingBox = -1;
        }
        if (!open) {
            graphics.blit(
                    hovered ? DAILY_CHEST_HOVER : DAILY_CHEST_CLOSED,
                    drawX,
                    drawY,
                    DAILY_CHEST_WIDTH,
                    DAILY_CHEST_HEIGHT,
                    0.0F,
                    0.0F,
                    DAILY_CHEST_SOURCE_WIDTH,
                    DAILY_CHEST_SOURCE_HEIGHT,
                    DAILY_CHEST_SOURCE_WIDTH,
                    DAILY_CHEST_SOURCE_HEIGHT
            );
        }

        if (open && !stack.isEmpty()) {
            int itemX = drawX + DAILY_CHEST_ITEM_X;
            int itemY = drawY + DAILY_CHEST_ITEM_Y;
            if (unopenedReveal) {
                RenderSystem.setShaderColor(
                        DAILY_UNSELECTED_ITEM_BRIGHTNESS,
                        DAILY_UNSELECTED_ITEM_BRIGHTNESS,
                        DAILY_UNSELECTED_ITEM_BRIGHTNESS,
                        1.0F
                );
            }
            try {
                if (cosmeticVisual != null && !cosmeticVisual.id().isBlank()) {
                    YoikoCosmeticIconRenderer.renderEntryIcon(
                            graphics,
                            itemX,
                            itemY,
                            16,
                            cosmeticVisual.type(),
                            cosmeticVisual.id(),
                            true,
                            0,
                            dailyRarity(index, stack),
                            cosmeticVisual.particleCategory()
                    );
                } else {
                    graphics.renderItem(stack, itemX, itemY);
                }
            } finally {
                if (unopenedReveal) {
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                }
            }
            if (cosmeticVisual == null || cosmeticVisual.id().isBlank()) {
                String countText = rewardCount > 1 ? Integer.toString(rewardCount) : null;
                graphics.renderItemDecorations(this.font, stack, itemX, itemY, countText);
            }
        }
    }

    private static void renderDailyShadow(GuiGraphics graphics, ResourceLocation shadow, int chestX, int chestY) {
        graphics.blit(
                shadow,
                chestX + DAILY_SHADOW_OFFSET_X,
                chestY,
                DAILY_SHADOW_WIDTH,
                DAILY_SHADOW_HEIGHT,
                0.0F,
                0.0F,
                DAILY_SHADOW_WIDTH,
                DAILY_SHADOW_HEIGHT,
                DAILY_SHADOW_WIDTH,
                DAILY_SHADOW_HEIGHT
        );
    }

    private static boolean isDailyBoxHovered(int mouseX, int mouseY, int boxX, int boxY) {
        return mouseX >= boxX && mouseX < boxX + DAILY_CHEST_WIDTH
                && mouseY >= boxY && mouseY < boxY + DAILY_CHEST_HEIGHT;
    }

    private void renderDailyGlow(GuiGraphics graphics, int centerX, int centerY, int color, boolean featured, boolean dimmed) {
        float alpha = dimmed ? 0.42F : featured ? 1.0F : 0.82F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                alpha
        );
        graphics.blit(
                DAILY_CHEST_GLOW,
                centerX - DAILY_GLOW_SIZE / 2,
                centerY - DAILY_GLOW_SIZE / 2,
                DAILY_GLOW_SIZE,
                DAILY_GLOW_SIZE,
                0.0F,
                0.0F,
                DAILY_GLOW_SIZE,
                DAILY_GLOW_SIZE,
                DAILY_GLOW_SIZE,
                DAILY_GLOW_SIZE
        );
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private ItemStack renderStreakCalendar(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int panelX = left + RIGHT_PANEL_X;
        Component streakTitle = YoikoClientText.tr("yoiko_core.ui.mail.streak_rewards");
        graphics.drawString(
                this.font,
                streakTitle,
                panelX + STREAK_TITLE_CENTER_X - this.font.width(streakTitle) / 2,
                top + STREAK_TITLE_Y,
                MAIL_TEXT_SOFT,
                false
        );
        ItemStack hovered = ItemStack.EMPTY;
        int slotY = top + STREAK_ITEM_Y;
        int currentDay = payload.dailyStreak() <= 0 ? -1 : Math.floorMod(payload.dailyStreak() - 1, 7);
        int claimedMask = payload.streakClaimedMask();
        for (int i = 0; i < 7; i++) {
            int columnX = panelX + STREAK_COLUMN_START_X + i * STREAK_COLUMN_STEP;
            int sx = panelX + STREAK_ITEM_START_X + i * STREAK_COLUMN_STEP;
            ItemStack stack = i < payload.streakRewards().size() ? payload.streakRewards().get(i) : ItemStack.EMPTY;
            boolean claimed = (claimedMask & (1 << i)) != 0;
            if (!stack.isEmpty()) {
                if (claimed) {
                    RenderSystem.setShaderColor(
                            DAILY_UNSELECTED_ITEM_BRIGHTNESS,
                            DAILY_UNSELECTED_ITEM_BRIGHTNESS,
                            DAILY_UNSELECTED_ITEM_BRIGHTNESS,
                            1.0F
                    );
                }
                try {
                    graphics.renderItem(stack, sx, slotY);
                } finally {
                    if (claimed) {
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    }
                }
                graphics.renderItemDecorations(this.font, stack, sx, slotY);
            }
            if (claimed) {
                YoikoScreenStyle.renderCheckIcon(graphics, sx - 1, slotY - 1);
            }
            Component dayNumber = Component.literal(String.valueOf(i + 1));
            if (i == currentDay) {
                dayNumber = dayNumber.copy().withStyle(ChatFormatting.BOLD);
            }
            graphics.drawString(
                    this.font,
                    dayNumber,
                    columnX + (STREAK_DAY_CELL_WIDTH - this.font.width(dayNumber)) / 2
                            + STREAK_DAY_NUMBER_OFFSET_X,
                    top + STREAK_DAY_Y,
                    i == currentDay ? MAIL_TEXT_ACCENT : MAIL_TEXT_SOFT_GREEN,
                    false
            );
            if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                hovered = stack;
            }
        }
        return hovered;
    }

    private int dailyBoxAt(int mouseX, int mouseY) {
        if (pendingDailyBox >= 0) {
            return -1;
        }
        int top = top();
        for (int i = 0; i < 3; i++) {
            int x = dailyBoxX(left(), i);
            int y = dailyBoxY(top, i);
            if (mouseX >= x && mouseX < x + DAILY_CHEST_WIDTH && mouseY >= y && mouseY < y + DAILY_CHEST_HEIGHT) {
                if (isDailyBoxOpened(i) || isDailyLimitReached(payload)) {
                    return -1;
                }
                return i;
            }
        }
        return -1;
    }

    private int dailyBoxX(int left, int index) {
        return left + RIGHT_PANEL_X + DAILY_CHEST_START_X + index * DAILY_CHEST_STEP;
    }

    private static int dailyBoxY(int top, int index) {
        return top + DAILY_BOX_Y + (index == 1 ? 1 : 0);
    }

    private boolean isDailyBoxOpened(int index) {
        return (payload.dailyBonusOpenedMask() & (1 << index)) != 0;
    }

    private boolean isDailyBoxVisuallyOpen(int index) {
        if (index == openingBox && openingTicks > 0) {
            return false;
        }
        if (isDailyBoxOpened(index)) {
            return true;
        }
        if (!isDailyLimitReached(payload)) {
            return false;
        }
        if (revealTicks < 0) {
            return true;
        }
        int revealIndex = revealOrderIndex(index);
        return revealIndex >= 0 && revealTicks >= (revealIndex + 1) * REVEAL_STEP_TICKS;
    }

    private int revealOrderIndex(int boxIndex) {
        int order = 0;
        for (int i = 0; i < 3; i++) {
            if (isDailyBoxOpened(i)) {
                continue;
            }
            if (i == boxIndex) {
                return order;
            }
            order++;
        }
        return -1;
    }

    private int shakeOffset(int index) {
        if (index != openingBox || openingTicks <= 0) {
            return 0;
        }
        int phase = CHEST_SHAKE_TICKS - openingTicks;
        return switch (phase % 4) {
            case 0 -> -2;
            case 1 -> 2;
            case 2 -> -1;
            default -> 1;
        };
    }

    private static boolean isDailyLimitReached(OpenMailboxPayload payload) {
        return payload.dailyBonusKeys() <= 0;
    }

    private static boolean validDailyBox(int index) {
        return index >= 0 && index < 3;
    }

    private static int firstOpenedBox(int mask) {
        for (int i = 0; i < 3; i++) {
            if ((mask & (1 << i)) != 0) {
                return i;
            }
        }
        return -1;
    }

    private int dailyRarityColor(int boxIndex, ItemStack stack) {
        return switch (dailyRarity(boxIndex, stack)) {
            case "UNCOMMON" -> 0xFFFFD36A;
            case "RARE" -> 0xFF50F3FF;
            case "EPIC" -> 0xFFFF75E6;
            case "LEGENDARY" -> 0xFFFFB000;
            case "MYTHIC" -> 0xFFB16CFF;
            default -> 0xFFEFE5C7;
        };
    }

    private void playDailyOpenSound(int boxIndex) {
        if (!validDailyBox(boxIndex) || boxIndex >= payload.dailyBonusBoxes().size()) {
            return;
        }
        ItemStack reward = payload.dailyBonusBoxes().get(boxIndex);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHEST_OPEN, 1.0F, 0.75F));
        switch (dailyRarity(boxIndex, reward)) {
            case "MYTHIC" -> {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.65F, 0.75F));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.45F, 0.7F));
            }
            case "LEGENDARY" -> {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.5F, 0.65F));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35F, 0.62F));
            }
            case "EPIC" -> minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.45F, 0.65F));
            case "RARE" -> minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.25F, 0.55F));
            case "UNCOMMON" -> minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1F, 0.55F));
            default -> minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 0.45F));
        }
    }

    private String dailyRarity(int boxIndex, ItemStack stack) {
        if (boxIndex >= 0 && boxIndex < payload.dailyBonusRarities().size()) {
            String rarity = payload.dailyBonusRarities().get(boxIndex);
            if (!rarity.isBlank()) {
                return rarity.toUpperCase(java.util.Locale.ROOT);
            }
        }
        return switch (stack.getRarity()) {
            case UNCOMMON -> "UNCOMMON";
            case RARE -> "RARE";
            case EPIC -> "EPIC";
            default -> "COMMON";
        };
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int maxLines, int color) {
        int lineY = y;
        int lines = 0;
        for (FormattedCharSequence sequence : this.font.split(text, width)) {
            if (lines >= maxLines) {
                break;
            }
            graphics.drawString(this.font, sequence, x, lineY, color, false);
            lineY += 10;
            lines++;
        }
        return lineY;
    }

    private OpenMailboxPayload.MailSummary mailAt(int mouseX, int mouseY) {
        int listX = mailLeft() + MAIL_LIST_X;
        int listY = top() + MAIL_LIST_Y;
        if (!isInside(mouseX, mouseY, listX, listY, MAIL_LIST_WIDTH, MAIL_VISIBLE_ROWS * MAIL_ROW_HEIGHT)) {
            return null;
        }
        int row = (mouseY - listY) / MAIL_ROW_HEIGHT;
        int index = mailScroll + row;
        if (index >= payload.mails().size()) {
            return null;
        }
        OpenMailboxPayload.MailSummary mail = payload.mails().get(index);
        int deleteX = listX + MAIL_DELETE_X;
        int rowY = listY + row * MAIL_ROW_HEIGHT;
        if (isInside(mouseX, mouseY, deleteX, rowY + MAIL_DELETE_Y, MAIL_DELETE_WIDTH, MAIL_DELETE_HEIGHT)) {
            return null;
        }
        return mail;
    }

    private OpenMailboxPayload.MailSummary deleteMailAt(int mouseX, int mouseY) {
        int listX = mailLeft() + MAIL_LIST_X;
        int listY = top() + MAIL_LIST_Y;
        if (!isInside(mouseX, mouseY, listX, listY, MAIL_LIST_WIDTH, MAIL_VISIBLE_ROWS * MAIL_ROW_HEIGHT)) {
            return null;
        }
        int row = (mouseY - listY) / MAIL_ROW_HEIGHT;
        int index = mailScroll + row;
        if (index >= payload.mails().size()) {
            return null;
        }
        int deleteX = listX + MAIL_DELETE_X;
        int rowY = listY + row * MAIL_ROW_HEIGHT;
        OpenMailboxPayload.MailSummary mail = payload.mails().get(index);
        return mail.itemCount() == 0
                && isInside(mouseX, mouseY, deleteX, rowY + MAIL_DELETE_Y, MAIL_DELETE_WIDTH, MAIL_DELETE_HEIGHT)
                ? mail : null;
    }

    private boolean isInsideMailList(int mouseX, int mouseY) {
        return isInside(mouseX, mouseY, mailLeft() + MAIL_LIST_X, top() + MAIL_LIST_Y,
                MAIL_LIST_WIDTH, MAIL_VISIBLE_ROWS * MAIL_ROW_HEIGHT);
    }

    private int maxMailScroll() {
        return Math.max(0, payload.mails().size() - MAIL_VISIBLE_ROWS);
    }

    private void clampMailScroll() {
        mailScroll = Math.max(0, Math.min(mailScroll, maxMailScroll()));
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private MailboxDetailActionButton addMailboxDetailAction(
            int x,
            int y,
            int hoverSourceX,
            int hoverOffsetX,
            Component label,
            String action
    ) {
        MailboxDetailActionButton button = new MailboxDetailActionButton(
                x,
                y,
                hoverSourceX,
                hoverOffsetX,
                label,
                () -> send(action)
        );
        this.addRenderableWidget(button);
        return button;
    }

    private void send(String action) {
        if (!ClientServerRequestState.begin(this, action)) {
            return;
        }
        PacketDistributor.sendToServer(ClientMenuSession.action(action));
    }

    private int left() {
        return YoikoMenuLayout.splitLeft(this.width);
    }

    private int mailLeft() {
        return left();
    }

    private int top() {
        return YoikoMenuLayout.splitTop(this.height);
    }

    private static String action(String command, Object... parts) {
        StringBuilder builder = new StringBuilder(command);
        for (Object part : parts) {
            builder.append('|').append(part);
        }
        return builder.toString();
    }

    private final class MailboxDetailActionButton extends AbstractButton {
        private final int hoverSourceX;
        private final int hoverOffsetX;
        private final Runnable action;

        private MailboxDetailActionButton(
                int x,
                int y,
                int hoverSourceX,
                int hoverOffsetX,
                Component label,
                Runnable action
        ) {
            super(x, y, MAIL_DETAIL_ACTION_SIZE, MAIL_DETAIL_ACTION_SIZE, label);
            this.hoverSourceX = hoverSourceX;
            this.hoverOffsetX = hoverOffsetX;
            this.action = action;
        }

        @Override
        public void onPress() {
            if (this.active) {
                action.run();
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (isHoveredOrFocused() && active) {
                graphics.blit(
                        MAILBOX_BUTTONS,
                        getX() + hoverOffsetX,
                        getY(),
                        MAIL_DETAIL_ACTION_SIZE,
                        MAIL_DETAIL_ACTION_SIZE,
                        (float) hoverSourceX,
                        MAIL_DETAIL_ACTION_HOVER_SOURCE_Y,
                        MAIL_DETAIL_ACTION_SIZE,
                        MAIL_DETAIL_ACTION_SIZE,
                        MAIL_BUTTON_TEXTURE_WIDTH,
                        MAIL_BUTTON_TEXTURE_HEIGHT
                );
            }
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            if (YoikoClientConfig.UI_SOUNDS.get()) {
                super.playDownSound(soundManager);
            }
        }
    }

    private final class DailyChanceButton extends AbstractButton {
        private DailyChanceButton(int x, int y) {
            super(x, y, DAILY_CHANCE_BUTTON_SIZE, DAILY_CHANCE_BUTTON_SIZE,
                    YoikoClientText.tr("yoiko_core.ui.mail.daily_chance.title"));
        }

        @Override
        public void onPress() {
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Component mark = Component.literal("?");
            int textX = getX() + (width - YoikoMailboxScreen.this.font.width(mark)) / 2;
            int textY = getY() + (height - YoikoMailboxScreen.this.font.lineHeight) / 2;
            graphics.drawString(YoikoMailboxScreen.this.font, mark, textX, textY, 0xFF4F3524, false);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            if (YoikoClientConfig.UI_SOUNDS.get()) {
                super.playDownSound(soundManager);
            }
        }
    }
}
