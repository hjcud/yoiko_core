package com.yoiko.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class YoikoClientKeys {
    public static final String CATEGORY = "key.categories.yoiko_core";
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.yoiko_core.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            CATEGORY
    );
    public static final KeyMapping STORAGE_SLOT_LOCK = new KeyMapping(
            "key.yoiko_core.storage_slot_lock",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    );
    public static final KeyMapping STORAGE_SORT = new KeyMapping(
            "key.yoiko_core.storage_sort",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
    );

    private YoikoClientKeys() {
    }
}
