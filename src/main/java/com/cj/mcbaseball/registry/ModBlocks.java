package com.cj.mcbaseball.registry;

import com.cj.mcbaseball.block.BaseBlock;
import com.cj.mcbaseball.block.FieldControllerBlock;
import com.cj.mcbaseball.block.FoulPoleBlock;
import com.cj.mcbaseball.block.HomePlateBlock;
import com.cj.mcbaseball.block.PitchersRubberBlock;
import com.cj.mcbaseball.block.ScoreboardBlock;
import com.cj.mcbaseball.block.StadiumKitBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "mcbaseball");
    public static final RegistryObject<Block> FIELD_CONTROLLER = BLOCKS.register(
        "field_controller",
        () -> new FieldControllerBlock(Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops())
    );
    public static final RegistryObject<Block> HOME_PLATE = BLOCKS.register("home_plate", () -> new HomePlateBlock(markerProps()));
    public static final RegistryObject<Block> BASE = BLOCKS.register("base", () -> new BaseBlock(markerProps()));
    public static final RegistryObject<Block> PITCHERS_RUBBER = BLOCKS.register("pitchers_rubber", () -> new PitchersRubberBlock(markerProps()));
    public static final RegistryObject<Block> SCOREBOARD = BLOCKS.register(
        "scoreboard",
        () -> new ScoreboardBlock(Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.0F).sound(SoundType.METAL).lightLevel(s -> 7))
    );
    public static final RegistryObject<Block> STADIUM_KIT = BLOCKS.register(
        "stadium_kit", () -> new StadiumKitBlock(Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.0F, 6.0F).sound(SoundType.METAL))
    );
    public static final RegistryObject<Block> INFIELD_DIRT = BLOCKS.register(
        "infield_dirt", () -> new Block(Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.6F).sound(SoundType.GRAVEL))
    );
    public static final RegistryObject<Block> OUTFIELD_GRASS_LIGHT = BLOCKS.register(
        "outfield_grass_light", () -> new Block(Properties.of().mapColor(MapColor.GRASS).strength(0.6F).sound(SoundType.GRASS))
    );
    public static final RegistryObject<Block> OUTFIELD_GRASS_DARK = BLOCKS.register(
        "outfield_grass_dark", () -> new Block(Properties.of().mapColor(MapColor.PLANT).strength(0.6F).sound(SoundType.GRASS))
    );
    public static final RegistryObject<Block> WARNING_TRACK = BLOCKS.register(
        "warning_track", () -> new Block(Properties.of().mapColor(MapColor.TERRACOTTA_RED).strength(0.6F).sound(SoundType.GRAVEL))
    );
    public static final RegistryObject<Block> WALL_PADDING = BLOCKS.register(
        "wall_padding", () -> new Block(Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.8F).sound(SoundType.WOOL))
    );
    public static final RegistryObject<Block> FOUL_POLE = BLOCKS.register(
        "foul_pole", () -> new FoulPoleBlock(Properties.of().mapColor(MapColor.GOLD).strength(2.0F).sound(SoundType.METAL).noOcclusion())
    );
    public static final RegistryObject<Block> CHALK_LINE = BLOCKS.register(
        "chalk_line", () -> new CarpetBlock(Properties.of().mapColor(MapColor.SNOW).strength(0.1F).sound(SoundType.SAND).noOcclusion())
    );

    private static Properties markerProps() {
        return Properties.of().mapColor(MapColor.SNOW).strength(0.6F).sound(SoundType.WOOL).noOcclusion();
    }

    private ModBlocks() {
    }
}
