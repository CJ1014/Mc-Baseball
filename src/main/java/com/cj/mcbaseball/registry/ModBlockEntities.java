package com.cj.mcbaseball.registry;

import com.cj.mcbaseball.block.AngledBlockEntity;
import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.field.StadiumKitBlockEntity;
import com.cj.mcbaseball.scoreboard.ScoreboardBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "mcbaseball");
    public static final RegistryObject<BlockEntityType<FieldControllerBlockEntity>> FIELD_CONTROLLER = BLOCK_ENTITIES.register(
        "field_controller", () -> Builder.of(FieldControllerBlockEntity::new, new Block[]{(Block)ModBlocks.FIELD_CONTROLLER.get()}).build(null)
    );
    public static final RegistryObject<BlockEntityType<ScoreboardBlockEntity>> SCOREBOARD = BLOCK_ENTITIES.register(
        "scoreboard", () -> Builder.of(ScoreboardBlockEntity::new, new Block[]{(Block)ModBlocks.SCOREBOARD.get()}).build(null)
    );
    public static final RegistryObject<BlockEntityType<StadiumKitBlockEntity>> STADIUM_KIT = BLOCK_ENTITIES.register(
        "stadium_kit", () -> Builder.of(StadiumKitBlockEntity::new, new Block[]{(Block)ModBlocks.STADIUM_KIT.get()}).build(null)
    );
    public static final RegistryObject<BlockEntityType<AngledBlockEntity>> ANGLED = BLOCK_ENTITIES.register(
        "angled",
        () -> Builder.of(AngledBlockEntity::new, new Block[]{(Block)ModBlocks.HOME_PLATE.get(), (Block)ModBlocks.PITCHERS_RUBBER.get()}).build(null)
    );

    private ModBlockEntities() {
    }
}
