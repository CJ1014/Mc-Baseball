package com.cj.mcbaseball;

import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.network.ModNetwork;
import com.cj.mcbaseball.registry.ModBlockEntities;
import com.cj.mcbaseball.registry.ModBlocks;
import com.cj.mcbaseball.registry.ModCreativeTabs;
import com.cj.mcbaseball.registry.ModEntities;
import com.cj.mcbaseball.registry.ModItems;
import com.cj.mcbaseball.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod("mcbaseball")
public class MCBaseball {
    public static final String MODID = "mcbaseball";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MCBaseball(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        context.registerConfig(Type.SERVER, BaseballConfig.SPEC);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
