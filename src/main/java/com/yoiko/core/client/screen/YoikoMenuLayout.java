package com.yoiko.core.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class YoikoMenuLayout {
    static final int PANEL_WIDTH = 370;
    static final int PANEL_HEIGHT = 260;
    static final int TAB_PANEL_WIDTH = 192;
    static final int TAB_PANEL_HEIGHT = 260;
    static final int SPLIT_PANEL_GAP = 10;
    static final int SPLIT_PANEL_WIDTH = TAB_PANEL_WIDTH * 2 + SPLIT_PANEL_GAP;
    static final int HEADER_Y = 8;
    static final int HEADER_BUTTON_Y = 4;
    static final int CONTENT_Y = 22;
    static final int BOTTOM_BUTTON_Y = 232;
    static final int LEFT_X = 8;
    static final int LEFT_WIDTH = 176;
    static final int RIGHT_X = 194;
    static final int RIGHT_WIDTH = 168;
    static final int SPLIT_RIGHT_PANEL_X = TAB_PANEL_WIDTH + SPLIT_PANEL_GAP;
    static final int SPLIT_RIGHT_X = SPLIT_RIGHT_PANEL_X + LEFT_X;
    static final int SPLIT_RIGHT_WIDTH = TAB_PANEL_WIDTH - LEFT_X * 2;
    // panel_right.png is drawn with 25 px of texture overflow. These anchors follow
    // the actual illustrated frames instead of the logical 176 px content box.
    static final int PROFILE_CENTER_X = SPLIT_RIGHT_PANEL_X + 80;
    static final int PROFILE_PLAYER_X = PROFILE_CENTER_X - 34;
    static final int PROFILE_PLAYER_Y = 51;
    static final int PROFILE_PLAYER_WIDTH = 68;
    static final int PROFILE_PLAYER_HEIGHT = 68;
    // The illustrated slot frames occupy source pixels 37..60 / 149..172 and
    // 80..103 / 115..138. Account for the panel's 25 px overflow so each 18 px
    // icon canvas lands on the exact half-pixel center of its 24 px frame.
    static final int PROFILE_SLOT_LEFT_X = SPLIT_RIGHT_PANEL_X + 15;
    static final int PROFILE_SLOT_RIGHT_X = SPLIT_RIGHT_PANEL_X + 127;
    static final int PROFILE_SLOT_TOP_Y = 58;
    static final int PROFILE_SLOT_BOTTOM_Y = 93;
    static final int PROFILE_PAPER_X = SPLIT_RIGHT_PANEL_X + 6;
    static final int PROFILE_PAPER_Y = 129;
    static final int PROFILE_PAPER_WIDTH = 148;
    static final int TAB_CONTENT_X = LEFT_X;
    static final int TAB_CONTENT_WIDTH = LEFT_WIDTH;
    static final int TAB_BOTTOM_BUTTON_Y = BOTTOM_BUTTON_Y;
    static final LayoutAnchor LEFT_CONTENT =
            new LayoutAnchor(LEFT_X, CONTENT_Y, LEFT_WIDTH, BOTTOM_BUTTON_Y - CONTENT_Y);
    static final LayoutAnchor RIGHT_CONTENT =
            new LayoutAnchor(SPLIT_RIGHT_X, CONTENT_Y, SPLIT_RIGHT_WIDTH, BOTTOM_BUTTON_Y - CONTENT_Y);

    private YoikoMenuLayout() {
    }

    static int left(int screenWidth) {
        return screenWidth / 2 - PANEL_WIDTH / 2;
    }

    static int splitLeft(int screenWidth) {
        return screenWidth / 2 - SPLIT_PANEL_WIDTH / 2;
    }

    static int top(int screenHeight) {
        return screenHeight / 2 - PANEL_HEIGHT / 2;
    }

    static int splitTop(int screenHeight) {
        return screenHeight / 2 - TAB_PANEL_HEIGHT / 2;
    }

    static int headerPageNextButtonX() {
        return LEFT_X + LEFT_WIDTH - 20;
    }

    static int headerPageTextX(Font font, Component pageText) {
        return headerPageNextButtonX() - 4 - font.width(pageText);
    }

    static int headerPagePrevButtonX(Font font, Component pageText) {
        return headerPageTextX(font, pageText) - 22;
    }

    static int headerCountX(Font font, Component countText, Component pageText) {
        return headerPagePrevButtonX(font, pageText) - 6 - font.width(countText);
    }
}
