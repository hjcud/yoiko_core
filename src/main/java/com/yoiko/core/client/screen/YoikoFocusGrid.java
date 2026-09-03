package com.yoiko.core.client.screen;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Keyboard-only focus surface for custom-drawn item grids. */
public final class YoikoFocusGrid extends AbstractWidget {
    private final int columns;
    private final int rows;
    private final int cellWidth;
    private final int cellHeight;
    private final int stepX;
    private final int stepY;
    private final IntSupplier itemCount;
    private final IntPredicate enabled;
    private final IntConsumer activate;
    private int focusedIndex;
    private boolean focusOutlineVisible = true;

    public YoikoFocusGrid(int x, int y, int columns, int rows, int cellWidth, int cellHeight,
                          int stepX, int stepY, IntSupplier itemCount, IntConsumer activate) {
        this(x, y, columns, rows, cellWidth, cellHeight, stepX, stepY, itemCount, ignored -> true, activate);
    }

    public YoikoFocusGrid(int x, int y, int columns, int rows, int cellWidth, int cellHeight,
                          int stepX, int stepY, IntSupplier itemCount, IntPredicate enabled,
                          IntConsumer activate) {
        super(x, y,
                Math.max(0, columns - 1) * Math.max(1, stepX) + Math.max(1, cellWidth),
                Math.max(0, rows - 1) * Math.max(1, stepY) + Math.max(1, cellHeight),
                Component.empty());
        this.columns = Math.max(1, columns);
        this.rows = Math.max(1, rows);
        this.cellWidth = Math.max(1, cellWidth);
        this.cellHeight = Math.max(1, cellHeight);
        this.stepX = Math.max(1, stepX);
        this.stepY = Math.max(1, stepY);
        this.itemCount = itemCount;
        this.enabled = enabled;
        this.activate = activate;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int count = count();
        if (!focusOutlineVisible || !isFocused() || count <= 0) {
            return;
        }
        clampIndex(count);
        int column = focusedIndex % columns;
        int row = focusedIndex / columns;
        int x = getX() + column * stepX;
        int y = getY() + row * stepY;
        graphics.renderOutline(x - 2, y - 2, cellWidth + 4, cellHeight + 4, 0xFF15100A);
        graphics.renderOutline(x - 1, y - 1, cellWidth + 2, cellHeight + 2, 0xFFFFD36A);
    }

    /** Keeps keyboard and mouse focus behavior while letting a screen render its own selection state. */
    public YoikoFocusGrid withoutFocusOutline() {
        this.focusOutlineVisible = false;
        return this;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || count() <= 0) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            move(keyCode);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE) {
            activateFocused();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !active || !visible) {
            return false;
        }
        int localX = (int) mouseX - getX();
        int localY = (int) mouseY - getY();
        if (localX < 0 || localY < 0) {
            return false;
        }
        int column = localX / stepX;
        int row = localY / stepY;
        if (column >= columns || row >= rows || localX % stepX >= cellWidth || localY % stepY >= cellHeight) {
            return false;
        }
        int index = row * columns + column;
        if (index >= count()) {
            return false;
        }
        focusedIndex = index;
        setFocused(true);
        activateFocused();
        return true;
    }

    public void setFocusedIndex(int index) {
        focusedIndex = Math.max(0, index);
        clampIndex(count());
    }

    public int focusedIndex() {
        clampIndex(count());
        return focusedIndex;
    }

    @Override
    public NarratableEntry.NarrationPriority narrationPriority() {
        return NarratableEntry.NarrationPriority.NONE;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Intentionally silent: this widget provides visual and keyboard focus only.
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
        // Grid activation uses the target screen's normal action feedback.
    }

    private void move(int keyCode) {
        int count = count();
        clampIndex(count);
        int column = focusedIndex % columns;
        int row = focusedIndex / columns;
        int target = switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> column > 0 ? focusedIndex - 1 : focusedIndex;
            case GLFW.GLFW_KEY_RIGHT -> column + 1 < columns && focusedIndex + 1 < count
                    ? focusedIndex + 1 : focusedIndex;
            case GLFW.GLFW_KEY_UP -> row > 0 ? focusedIndex - columns : focusedIndex;
            case GLFW.GLFW_KEY_DOWN -> focusedIndex + columns < count ? focusedIndex + columns : focusedIndex;
            default -> focusedIndex;
        };
        focusedIndex = target;
    }

    private void activateFocused() {
        if (focusedIndex >= 0 && focusedIndex < count() && enabled.test(focusedIndex)) {
            activate.accept(focusedIndex);
        }
    }

    private int count() {
        return Math.max(0, Math.min(columns * rows, itemCount.getAsInt()));
    }

    private void clampIndex(int count) {
        focusedIndex = count <= 0 ? 0 : Math.max(0, Math.min(focusedIndex, count - 1));
    }
}
