package com.yoiko.core.client.screen;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

final class YoikoMousePosition {
    private static boolean remembered;
    private static double rememberedX;
    private static double rememberedY;

    private YoikoMousePosition() {
    }

    static void remember() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return;
        }
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(minecraft.getWindow().getWindow(), x, y);
        rememberedX = x[0];
        rememberedY = y[0];
        remembered = true;
    }

    static void restoreIfRemembered() {
        if (!remembered) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            remembered = false;
            return;
        }
        GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), rememberedX, rememberedY);
        remembered = false;
    }
}
