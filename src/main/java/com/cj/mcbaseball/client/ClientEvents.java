package com.cj.mcbaseball.client;

import com.cj.mcbaseball.client.render.WorldOverlays;
import com.cj.mcbaseball.client.screen.PitchSelectScreen;
import com.cj.mcbaseball.game.PlayerRole;
import com.cj.mcbaseball.item.BatItem;
import com.cj.mcbaseball.network.ModNetwork;
import com.cj.mcbaseball.network.SwingPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.client.event.InputEvent.MouseButton.Pre;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
    modid = "mcbaseball",
    bus = Bus.FORGE,
    value = {Dist.CLIENT}
)
public final class ClientEvents {
    @SubscribeEvent
    public static void onRightClickBlock(RightClickBlock event) {
        if (event.getLevel().isClientSide && ClientFieldSetupState.isMarking()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onMouse(Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null && event.getAction() == 1) {
            if (event.getButton() == 0) {
                if (ClientGameState.role() == PlayerRole.BATTER) {
                    if (mc.player.getMainHandItem().getItem() instanceof BatItem) {
                        ModNetwork.toServer(new SwingPacket(mc.player.isShiftKeyDown()));
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase == Phase.END) {
            while (ClientSetup.PITCH_MENU.consumeClick()) {
                if (ClientGameState.role() == PlayerRole.PITCHER && Minecraft.getInstance().screen == null) {
                    Minecraft.getInstance().setScreen(new PitchSelectScreen());
                }
            }

            while (ClientSetup.HELP.consumeClick()) {
                ClientGameState.showHelp = !ClientGameState.showHelp;
            }

            WorldOverlays.tickLandingMarkers();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == Stage.AFTER_TRANSLUCENT_BLOCKS) {
            WorldOverlays.renderAll(event.getPoseStack(), event.getCamera(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onLogout(LoggingOut event) {
        ClientFieldSetupState.setMarker(-1);
        ClientGameState.reset();
    }

    private ClientEvents() {
    }
}
