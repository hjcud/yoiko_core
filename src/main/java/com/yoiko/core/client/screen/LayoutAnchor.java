package com.yoiko.core.client.screen;

/**
 * Immutable UI region used to keep drawing and hit-test coordinates on the same source of truth.
 */
record LayoutAnchor(int x, int y, int width, int height) {
    int right() {
        return x + width;
    }

    int bottom() {
        return y + height;
    }

    LayoutAnchor offset(int dx, int dy) {
        return new LayoutAnchor(x + dx, y + dy, width, height);
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
    }
}
