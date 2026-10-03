package com.cj.mcbaseball.registry;

import com.cj.mcbaseball.entity.BaseballEntity;
import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "mcbaseball");
    public static final RegistryObject<EntityType<BaseballEntity>> BASEBALL = ENTITIES.register(
        "baseball",
        () -> Builder.of(BaseballEntity::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .setShouldReceiveVelocityUpdates(true)
                .build("mcbaseball:baseball")
    );
    public static final RegistryObject<EntityType<BaseballPlayerEntity>> BASEBALL_PLAYER = ENTITIES.register(
        "baseball_player",
        () -> Builder.of(BaseballPlayerEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(10).build("mcbaseball:baseball_player")
    );

    private ModEntities() {
    }
}
