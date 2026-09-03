package com.yoiko.core.turtle.arena;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Single source of truth for visual-only arena palettes. */
public enum TurtleArenaTheme {
    BEACH_FESTIVAL("beach_festival"), MANGROVE_BOARDWALK("mangrove_boardwalk"),
    FOREST_POND("forest_pond"), MEADOW_FAIR("meadow_fair");
    private final String id;
    TurtleArenaTheme(String id){this.id=id;} public String id(){return id;}
    public static TurtleArenaTheme fromId(String value){String normalized=value==null?"":value.toLowerCase(Locale.ROOT);return Arrays.stream(values()).filter(v->v.id.equals(normalized)||v.name().equalsIgnoreCase(normalized)).findFirst().orElse(FOREST_POND);}
    public static List<String> ids(){return Arrays.stream(values()).map(TurtleArenaTheme::id).toList();}
    public BlockState background(){return switch(this){case BEACH_FESTIVAL->Blocks.SMOOTH_SANDSTONE.defaultBlockState();case MANGROVE_BOARDWALK->Blocks.PACKED_MUD.defaultBlockState();default->Blocks.GRASS_BLOCK.defaultBlockState();};}
    public BlockState curb(){return switch(this){case BEACH_FESTIVAL->Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState();case MANGROVE_BOARDWALK->Blocks.MANGROVE_SLAB.defaultBlockState();case MEADOW_FAIR->Blocks.BIRCH_SLAB.defaultBlockState();default->Blocks.OAK_SLAB.defaultBlockState();};}
    public BlockState facilityFloor(){return switch(this){case BEACH_FESTIVAL->Blocks.CUT_SANDSTONE.defaultBlockState();case MANGROVE_BOARDWALK->Blocks.MANGROVE_PLANKS.defaultBlockState();case MEADOW_FAIR->Blocks.BIRCH_PLANKS.defaultBlockState();default->Blocks.OAK_PLANKS.defaultBlockState();};}
    public BlockState landmarkBase(){return this==BEACH_FESTIVAL?Blocks.CUT_SANDSTONE.defaultBlockState():Blocks.OAK_PLANKS.defaultBlockState();}
    public BlockState sapling(SplittableRandom random){return switch(this){case BEACH_FESTIVAL->Blocks.JUNGLE_SAPLING.defaultBlockState();case MANGROVE_BOARDWALK->Blocks.MANGROVE_PROPAGULE.defaultBlockState();case MEADOW_FAIR->(random.nextBoolean()?Blocks.CHERRY_SAPLING:Blocks.BIRCH_SAPLING).defaultBlockState();default->(random.nextBoolean()?Blocks.OAK_SAPLING:Blocks.BIRCH_SAPLING).defaultBlockState();};}
    public BlockState patch(SplittableRandom random){return switch(this){case BEACH_FESTIVAL->(random.nextBoolean()?Blocks.SAND:Blocks.GRAVEL).defaultBlockState();case MANGROVE_BOARDWALK->(random.nextBoolean()?Blocks.MUD:Blocks.MUDDY_MANGROVE_ROOTS).defaultBlockState();case MEADOW_FAIR->(random.nextBoolean()?Blocks.MOSS_BLOCK:Blocks.GRASS_BLOCK).defaultBlockState();default->(random.nextBoolean()?Blocks.COARSE_DIRT:Blocks.PODZOL).defaultBlockState();};}
    public BlockState accent(SplittableRandom random){return switch(this){case BEACH_FESTIVAL->Blocks.DEAD_BUSH.defaultBlockState();case MANGROVE_BOARDWALK->(random.nextBoolean()?Blocks.MANGROVE_PROPAGULE:Blocks.FERN).defaultBlockState();case MEADOW_FAIR->switch(random.nextInt(4)){case 0->Blocks.PINK_TULIP.defaultBlockState();case 1->Blocks.OXEYE_DAISY.defaultBlockState();case 2->Blocks.CORNFLOWER.defaultBlockState();default->Blocks.AZURE_BLUET.defaultBlockState();};default->switch(random.nextInt(4)){case 0->Blocks.DANDELION.defaultBlockState();case 1->Blocks.POPPY.defaultBlockState();case 2->Blocks.CORNFLOWER.defaultBlockState();default->Blocks.FERN.defaultBlockState();};};}
}
