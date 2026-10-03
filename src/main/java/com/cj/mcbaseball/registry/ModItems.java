package com.cj.mcbaseball.registry;

import com.cj.mcbaseball.item.BaseballItem;
import com.cj.mcbaseball.item.BatItem;
import com.cj.mcbaseball.item.FieldSetupToolItem;
import com.cj.mcbaseball.item.GloveItem;
import com.cj.mcbaseball.item.UniformArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "mcbaseball");
    public static final RegistryObject<Item> BASEBALL = ITEMS.register("baseball", () -> new BaseballItem(new Properties().stacksTo(16)));
    public static final RegistryObject<Item> WOODEN_BAT = ITEMS.register(
        "wooden_bat", () -> new BatItem(new BatItem.BatStats(0.45F, 1.0F, 1.0F), new Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> ALUMINUM_BAT = ITEMS.register(
        "aluminum_bat", () -> new BatItem(new BatItem.BatStats(0.6F, 1.08F, 1.04F), new Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> BASEBALL_GLOVE = ITEMS.register(
        "baseball_glove", () -> new GloveItem(new GloveItem.GloveStats(0.45F, 1.6F, false), new Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> CATCHERS_MITT = ITEMS.register(
        "catchers_mitt", () -> new GloveItem(new GloveItem.GloveStats(0.35F, 2.6F, false), new Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> FIRST_BASE_MITT = ITEMS.register(
        "first_base_mitt", () -> new GloveItem(new GloveItem.GloveStats(0.55F, 1.8F, true), new Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> BASEBALL_CAP = ITEMS.register("baseball_cap", () -> new UniformArmorItem(Type.HELMET, new Properties()));
    public static final RegistryObject<Item> BASEBALL_JERSEY = ITEMS.register("baseball_jersey", () -> new UniformArmorItem(Type.CHESTPLATE, new Properties()));
    public static final RegistryObject<Item> BASEBALL_PANTS = ITEMS.register("baseball_pants", () -> new UniformArmorItem(Type.LEGGINGS, new Properties()));
    public static final RegistryObject<Item> CLEATS = ITEMS.register("cleats", () -> new UniformArmorItem(Type.BOOTS, new Properties()));
    public static final RegistryObject<Item> FIELD_SETUP_TOOL = ITEMS.register(
        "field_setup_tool", () -> new FieldSetupToolItem(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );
    public static final RegistryObject<Item> FIELD_CONTROLLER = ITEMS.register(
        "field_controller", () -> new BlockItem((Block)ModBlocks.FIELD_CONTROLLER.get(), new Properties().rarity(Rarity.UNCOMMON))
    );
    public static final RegistryObject<Item> HOME_PLATE = ITEMS.register("home_plate", () -> new BlockItem((Block)ModBlocks.HOME_PLATE.get(), new Properties()));
    public static final RegistryObject<Item> BASE = ITEMS.register("base", () -> new BlockItem((Block)ModBlocks.BASE.get(), new Properties()));
    public static final RegistryObject<Item> PITCHERS_RUBBER = ITEMS.register(
        "pitchers_rubber", () -> new BlockItem((Block)ModBlocks.PITCHERS_RUBBER.get(), new Properties())
    );
    public static final RegistryObject<Item> SCOREBOARD = ITEMS.register("scoreboard", () -> new BlockItem((Block)ModBlocks.SCOREBOARD.get(), new Properties()));
    public static final RegistryObject<Item> STADIUM_KIT = ITEMS.register(
        "stadium_kit", () -> new BlockItem((Block)ModBlocks.STADIUM_KIT.get(), new Properties().rarity(Rarity.EPIC))
    );
    public static final RegistryObject<Item> INFIELD_DIRT = block("infield_dirt", ModBlocks.INFIELD_DIRT);
    public static final RegistryObject<Item> OUTFIELD_GRASS_LIGHT = block("outfield_grass_light", ModBlocks.OUTFIELD_GRASS_LIGHT);
    public static final RegistryObject<Item> OUTFIELD_GRASS_DARK = block("outfield_grass_dark", ModBlocks.OUTFIELD_GRASS_DARK);
    public static final RegistryObject<Item> WARNING_TRACK = block("warning_track", ModBlocks.WARNING_TRACK);
    public static final RegistryObject<Item> WALL_PADDING = block("wall_padding", ModBlocks.WALL_PADDING);
    public static final RegistryObject<Item> FOUL_POLE = block("foul_pole", ModBlocks.FOUL_POLE);
    public static final RegistryObject<Item> CHALK_LINE = block("chalk_line", ModBlocks.CHALK_LINE);

    private static RegistryObject<Item> block(String name, RegistryObject<Block> b) {
        return ITEMS.register(name, () -> new BlockItem((Block)b.get(), new Properties()));
    }

    private ModItems() {
    }
}
