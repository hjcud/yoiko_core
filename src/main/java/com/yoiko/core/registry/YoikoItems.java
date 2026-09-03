package com.yoiko.core.registry;

import com.yoiko.core.YoikoServerCore;
import com.yoiko.core.gacha.GachaType;
import com.yoiko.core.item.GachaTicketItem;
import com.yoiko.core.item.CrownSealedRelicItem;
import com.yoiko.core.item.RelicGachaTicketItem;
import com.yoiko.core.item.RelicMaterialItem;
import com.yoiko.core.item.ParticleGachaTicketItem;
import com.yoiko.core.item.TurtleHatchTicketItem;
import com.yoiko.core.item.TurtleTrainingResetTicketItem;
import com.yoiko.core.relic.RelicAppraisalCategory;
import com.yoiko.core.turtle.TurtleTicketType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YoikoItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(YoikoServerCore.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, YoikoServerCore.MODID);

    public static final DeferredItem<GachaTicketItem> ALL_POKEMON_GACHA_TICKET = ITEMS.registerItem(
            "all_pokemon_gacha_ticket",
            properties -> new GachaTicketItem(properties, GachaType.ALL),
            new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)
    );

    public static final DeferredItem<GachaTicketItem> LEGENDARY_POKEMON_GACHA_TICKET = ITEMS.registerItem(
            "legendary_pokemon_gacha_ticket",
            properties -> new GachaTicketItem(properties, GachaType.LEGENDARY),
            new Item.Properties().stacksTo(64).rarity(Rarity.EPIC)
    );

    public static final DeferredItem<GachaTicketItem> SHINY_ALL_POKEMON_GACHA_TICKET = ITEMS.registerItem(
            "shiny_all_pokemon_gacha_ticket",
            properties -> new GachaTicketItem(properties, GachaType.SHINY_ALL),
            new Item.Properties().stacksTo(64).rarity(Rarity.EPIC)
    );

    public static final DeferredItem<RelicGachaTicketItem> RELIC_GACHA_TICKET = ITEMS.registerItem(
            "relic_gacha_ticket",
            RelicGachaTicketItem::new,
            new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)
    );

    public static final DeferredItem<RelicGachaTicketItem> RELIC_GACHA_TICKET_COMBAT = focusedRelicTicket(
            "relic_gacha_ticket_combat", RelicAppraisalCategory.COMBAT);
    public static final DeferredItem<RelicGachaTicketItem> RELIC_GACHA_TICKET_DEFENSE = focusedRelicTicket(
            "relic_gacha_ticket_defense", RelicAppraisalCategory.DEFENSE);
    public static final DeferredItem<RelicGachaTicketItem> RELIC_GACHA_TICKET_POKEMON = focusedRelicTicket(
            "relic_gacha_ticket_pokemon", RelicAppraisalCategory.POKEMON);
    public static final DeferredItem<RelicGachaTicketItem> RELIC_GACHA_TICKET_EXPLORATION = focusedRelicTicket(
            "relic_gacha_ticket_exploration", RelicAppraisalCategory.EXPLORATION);

    public static final DeferredItem<CrownSealedRelicItem> CROWN_SEALED_RELIC = ITEMS.registerItem(
            "crown_sealed_relic",
            CrownSealedRelicItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)
    );

    public static final DeferredItem<ParticleGachaTicketItem> PARTICLE_GACHA_TICKET = ITEMS.registerItem(
            "particle_gacha_ticket",
            ParticleGachaTicketItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)
    );

    public static final DeferredItem<RelicMaterialItem> RELIC_UPGRADE_CRYSTAL = ITEMS.registerItem(
            "relic_upgrade_crystal",
            properties -> new RelicMaterialItem(properties, "tooltip.yoiko_core.relic_upgrade_crystal"),
            new Item.Properties().stacksTo(64).rarity(Rarity.RARE)
    );

    public static final DeferredItem<RelicMaterialItem> RELIC_SCRAP = ITEMS.registerItem(
            "relic_scrap",
            properties -> new RelicMaterialItem(properties, "tooltip.yoiko_core.relic_scrap"),
            new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)
    );

    /** Hidden model carriers used only by ITEM particles for virtual-currency reward bursts. */
    public static final DeferredItem<Item> CURRENCY_GOLD_PARTICLE = ITEMS.registerSimpleItem(
            "currency_gold_particle", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> CURRENCY_GEM_PARTICLE = ITEMS.registerSimpleItem(
            "currency_gem_particle", new Item.Properties().stacksTo(1));

    public static final DeferredItem<TurtleHatchTicketItem> TURTLE_STANDARD_TICKET=turtleTicket("turtle_standard_hatch_ticket",TurtleTicketType.STANDARD,Rarity.UNCOMMON);
    public static final DeferredItem<TurtleHatchTicketItem> TURTLE_PICKUP_TICKET=turtleTicket("turtle_pickup_hatch_ticket",TurtleTicketType.PICKUP,Rarity.RARE);
    public static final DeferredItem<TurtleHatchTicketItem> TURTLE_RARE_STRATEGY_TICKET=turtleTicket("turtle_rare_strategy_ticket",TurtleTicketType.RARE_STRATEGY_SELECT,Rarity.RARE);
    public static final DeferredItem<TurtleHatchTicketItem> TURTLE_EPIC_TICKET=turtleTicket("turtle_epic_hatch_ticket",TurtleTicketType.EPIC_GUARANTEED,Rarity.EPIC);
    public static final DeferredItem<TurtleTrainingResetTicketItem> TURTLE_TRAINING_RESET_TICKET = ITEMS.registerItem(
            "turtle_training_reset_ticket", TurtleTrainingResetTicketItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));

    public static final DeferredItem<Item> RELIC_DISPLAY_COMMON = displayItem("relic_display_common", Rarity.COMMON);
    public static final DeferredItem<Item> RELIC_DISPLAY_UNCOMMON = displayItem("relic_display_uncommon", Rarity.UNCOMMON);
    public static final DeferredItem<Item> RELIC_DISPLAY_RARE = displayItem("relic_display_rare", Rarity.RARE);
    public static final DeferredItem<Item> RELIC_DISPLAY_EPIC = displayItem("relic_display_epic", Rarity.EPIC);
    public static final DeferredItem<Item> RELIC_DISPLAY_LEGENDARY = displayItem("relic_display_legendary", Rarity.EPIC);
    public static final DeferredItem<Item> RELIC_DISPLAY_MYSTIC = displayItem("relic_display_mystic", Rarity.EPIC);
    public static final DeferredItem<Item> RELIC_DISPLAY_RADIANT = displayItem("relic_display_radiant", Rarity.EPIC);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> YOIKO_TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.yoiko_core"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ALL_POKEMON_GACHA_TICKET.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ALL_POKEMON_GACHA_TICKET.get());
                output.accept(SHINY_ALL_POKEMON_GACHA_TICKET.get());
                output.accept(RELIC_GACHA_TICKET.get());
                output.accept(RELIC_GACHA_TICKET_COMBAT.get());
                output.accept(RELIC_GACHA_TICKET_DEFENSE.get());
                output.accept(RELIC_GACHA_TICKET_POKEMON.get());
                output.accept(RELIC_GACHA_TICKET_EXPLORATION.get());
                output.accept(CROWN_SEALED_RELIC.get());
                output.accept(PARTICLE_GACHA_TICKET.get());
                output.accept(TURTLE_STANDARD_TICKET.get());
                output.accept(TURTLE_PICKUP_TICKET.get());
                output.accept(TURTLE_RARE_STRATEGY_TICKET.get());
                output.accept(TURTLE_EPIC_TICKET.get());
                output.accept(TURTLE_TRAINING_RESET_TICKET.get());
            })
            .build());

    private YoikoItems() {
    }

    private static DeferredItem<Item> displayItem(String id, Rarity rarity) {
        return ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(1).rarity(rarity));
    }

    private static DeferredItem<RelicGachaTicketItem> focusedRelicTicket(
            String id, RelicAppraisalCategory appraisalCategory) {
        return ITEMS.registerItem(
                id,
                properties -> new RelicGachaTicketItem(properties, appraisalCategory),
                new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)
        );
    }

    public static Item relicGachaTicket(RelicAppraisalCategory appraisalCategory) {
        return switch (appraisalCategory == null ? RelicAppraisalCategory.ALL : appraisalCategory) {
            case COMBAT -> RELIC_GACHA_TICKET_COMBAT.get();
            case DEFENSE -> RELIC_GACHA_TICKET_DEFENSE.get();
            case POKEMON -> RELIC_GACHA_TICKET_POKEMON.get();
            case EXPLORATION -> RELIC_GACHA_TICKET_EXPLORATION.get();
            case ALL -> RELIC_GACHA_TICKET.get();
        };
    }

    private static DeferredItem<TurtleHatchTicketItem> turtleTicket(String id,TurtleTicketType type,Rarity rarity){
        return ITEMS.registerItem(id,properties->new TurtleHatchTicketItem(properties,type),new Item.Properties().stacksTo(64).rarity(rarity));
    }

    public static Item turtleTicket(TurtleTicketType type){return switch(type){
        case STANDARD->TURTLE_STANDARD_TICKET.get();case PICKUP->TURTLE_PICKUP_TICKET.get();
        case RARE_FRONT,RARE_STEADY,RARE_FOLLOW,RARE_CLOSER->TURTLE_RARE_STRATEGY_TICKET.get();
        case EPIC_GUARANTEED->TURTLE_EPIC_TICKET.get();case RARE_STRATEGY_SELECT->TURTLE_RARE_STRATEGY_TICKET.get();
    };}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
    }
}
