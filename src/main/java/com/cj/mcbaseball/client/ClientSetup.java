package com.cj.mcbaseball.client;

import com.cj.mcbaseball.client.hud.GameHud;
import com.cj.mcbaseball.client.hud.LiveGameHud;
import com.cj.mcbaseball.client.render.AngledBlockRenderer;
import com.cj.mcbaseball.client.render.BaseballPlayerRenderer;
import com.cj.mcbaseball.client.render.BaseballRenderer;
import com.cj.mcbaseball.client.render.CapModel;
import com.cj.mcbaseball.client.render.ScoreboardRenderer;
import com.cj.mcbaseball.item.UniformArmorItem;
import com.cj.mcbaseball.registry.ModBlockEntities;
import com.cj.mcbaseball.registry.ModEntities;
import com.cj.mcbaseball.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.client.event.RegisterColorHandlersEvent.Item;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
    modid = "mcbaseball",
    bus = Bus.MOD,
    value = {Dist.CLIENT}
)
public final class ClientSetup {
    public static final KeyMapping PITCH_MENU = new KeyMapping(
        "key.mcbaseball.pitch_menu", KeyConflictContext.IN_GAME, Type.KEYSYM, 82, "key.categories.mcbaseball"
    );
    public static final KeyMapping HELP = new KeyMapping("key.mcbaseball.help", KeyConflictContext.IN_GAME, Type.KEYSYM, 72, "key.categories.mcbaseball");

    @SubscribeEvent
    public static void registerRenderers(RegisterRenderers event) {
        event.registerEntityRenderer((EntityType)ModEntities.BASEBALL.get(), BaseballRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntities.BASEBALL_PLAYER.get(), BaseballPlayerRenderer::new);
        event.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.SCOREBOARD.get(), ScoreboardRenderer::new);
        event.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.ANGLED.get(), AngledBlockRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CapModel.LAYER, CapModel::create);
    }

    @SubscribeEvent
    public static void registerItemColors(Item event) {
        event.register(
            (stack, tintIndex) -> tintIndex > 0 ? -1 : ((UniformArmorItem)stack.getItem()).getColor(stack),
            new ItemLike[]{
                (ItemLike)ModItems.BASEBALL_CAP.get(),
                (ItemLike)ModItems.BASEBALL_JERSEY.get(),
                (ItemLike)ModItems.BASEBALL_PANTS.get(),
                (ItemLike)ModItems.CLEATS.get()
            }
        );
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(PITCH_MENU);
        event.register(HELP);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("baseball_hud", (gui, graphics, partialTick, width, height) -> GameHud.render(graphics, width, height));
        event.registerAboveAll("live_game_hud", (gui, graphics, partialTick, width, height) -> LiveGameHud.render(graphics, width, height));
    }

    private ClientSetup() {
    }
}
