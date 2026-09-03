package com.yoiko.core.reward;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class RewardItemSerializer {
    /** Prevents a malformed reward config from allocating an excessive number of item stacks. */
    public static final int MAX_CONFIGURED_REWARD_COUNT = 2_304;

    private RewardItemSerializer() {
    }

    public static ItemStack fromJson(JsonObject object) {
        ParsedRewardItem parsed = parse(object);
        if (parsed.prototype().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = parsed.prototype().copy();
        stack.setCount(Math.min(parsed.count(), serializableStackSize(stack)));
        return stack;
    }

    /** Returns a single-item visual representation while preserving the configured amount separately. */
    public static ItemStack previewFromJson(JsonObject object) {
        ParsedRewardItem parsed = parse(object);
        return parsed.prototype().copy();
    }

    public static int configuredCount(JsonObject object) {
        return validatedCount(object);
    }

    /** Splits a logical reward into legal Minecraft stacks before inventory, mail, or saved-data use. */
    public static List<ItemStack> split(ItemStack prototype, int totalCount) {
        if (prototype == null || prototype.isEmpty() || totalCount <= 0) {
            return List.of();
        }
        if (totalCount > MAX_CONFIGURED_REWARD_COUNT) {
            throw new IllegalArgumentException("Reward count must be within [1;"
                    + MAX_CONFIGURED_REWARD_COUNT + "]: " + totalCount);
        }
        int stackSize = serializableStackSize(prototype);
        List<ItemStack> stacks = new ArrayList<>((totalCount + stackSize - 1) / stackSize);
        for (int remaining = totalCount; remaining > 0; remaining -= stackSize) {
            ItemStack stack = prototype.copy();
            stack.setCount(Math.min(stackSize, remaining));
            stacks.add(stack);
        }
        return List.copyOf(stacks);
    }

    private static ParsedRewardItem parse(JsonObject object) {
        String itemId = object.has("item") ? object.get("item").getAsString() : "minecraft:air";
        int count = validatedCount(object);
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        Item item = location != null && BuiltInRegistries.ITEM.containsKey(location) ? BuiltInRegistries.ITEM.get(location) : Items.AIR;
        ItemStack stack = new ItemStack(item);
        if (object.has("name")) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(object.get("name").getAsString()));
        }
        JsonObject components = components(object);
        if (components != null) {
            applyComponents(stack, components);
        }
        return new ParsedRewardItem(stack, count);
    }

    public static List<ItemStack> listFromJson(JsonElement element) {
        List<ItemStack> stacks = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return stacks;
        }
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonObject()) {
                ParsedRewardItem parsed = parse(child.getAsJsonObject());
                if (!parsed.prototype().isEmpty()) {
                    stacks.addAll(split(parsed.prototype(), parsed.count()));
                }
            }
        }
        return List.copyOf(stacks);
    }

    private static int validatedCount(JsonObject object) {
        int count = 1;
        if (object.has("count")) {
            try {
                count = new BigDecimal(object.get("count").getAsString()).intValueExact();
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException("Reward count must be a whole number.", exception);
            }
        }
        if (count < 1 || count > MAX_CONFIGURED_REWARD_COUNT) {
            String itemId = object.has("item") ? object.get("item").getAsString() : "minecraft:air";
            throw new IllegalArgumentException("Reward count for " + itemId + " must be within [1;"
                    + MAX_CONFIGURED_REWARD_COUNT + "]: " + count);
        }
        return count;
    }

    private static int serializableStackSize(ItemStack stack) {
        // ItemStack's persistent codec rejects counts above 99 even if a custom item advertises a larger stack size.
        return Math.max(1, Math.min(99, stack.getMaxStackSize()));
    }

    private static JsonObject components(JsonObject object) {
        if (object.has("components") && object.get("components").isJsonObject()) {
            return object.getAsJsonObject("components");
        }
        if (object.has("components_or_nbt") && object.get("components_or_nbt").isJsonObject()) {
            return object.getAsJsonObject("components_or_nbt");
        }
        return null;
    }

    private static void applyComponents(ItemStack stack, JsonObject components) {
        JsonElement customName = firstPresent(components, "minecraft:custom_name", "custom_name", "name");
        if (customName != null) {
            stack.set(DataComponents.CUSTOM_NAME, component(customName));
        }

        JsonElement itemName = firstPresent(components, "minecraft:item_name", "item_name");
        if (itemName != null) {
            stack.set(DataComponents.ITEM_NAME, component(itemName));
        }

        JsonElement lore = firstPresent(components, "minecraft:lore", "lore");
        if (lore != null) {
            List<Component> lines = loreLines(lore);
            if (!lines.isEmpty()) {
                stack.set(DataComponents.LORE, new ItemLore(lines));
            }
        }
    }

    private static JsonElement firstPresent(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key);
            }
        }
        return null;
    }

    private static List<Component> loreLines(JsonElement element) {
        List<Component> lines = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                lines.add(component(child));
            }
        } else if (element.isJsonObject() && element.getAsJsonObject().has("lines")) {
            JsonElement lineElement = element.getAsJsonObject().get("lines");
            if (lineElement.isJsonArray()) {
                for (JsonElement child : lineElement.getAsJsonArray()) {
                    lines.add(component(child));
                }
            }
        } else {
            lines.add(component(element));
        }
        return lines;
    }

    private static Component component(JsonElement element) {
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            if (value.trim().startsWith("{")) {
                try {
                    return component(JsonParser.parseString(value));
                } catch (Exception ignored) {
                }
            }
            return Component.literal(value);
        }
        if (!element.isJsonObject()) {
            return Component.empty();
        }

        JsonObject object = element.getAsJsonObject();
        MutableComponent component;
        if (object.has("translate")) {
            component = Component.translatable(object.get("translate").getAsString());
        } else {
            component = Component.literal(object.has("text") ? object.get("text").getAsString() : "");
        }

        if (object.has("color")) {
            ChatFormatting color = ChatFormatting.getByName(object.get("color").getAsString());
            if (color != null) {
                component.withStyle(color);
            }
        }
        if (object.has("italic") && !object.get("italic").getAsBoolean()) {
            component.withStyle(style -> style.withItalic(false));
        }
        return component;
    }

    private record ParsedRewardItem(ItemStack prototype, int count) {
    }
}
