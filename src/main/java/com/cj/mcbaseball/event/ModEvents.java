package com.cj.mcbaseball.event;

import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import com.cj.mcbaseball.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
    modid = "mcbaseball",
    bus = Bus.MOD
)
public final class ModEvents {
    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put((EntityType)ModEntities.BASEBALL_PLAYER.get(), BaseballPlayerEntity.createAttributes().build());
    }

    private ModEvents() {
    }
}
