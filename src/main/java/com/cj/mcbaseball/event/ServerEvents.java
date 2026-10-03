package com.cj.mcbaseball.event;

import com.cj.mcbaseball.command.BaseballCommand;
import com.cj.mcbaseball.field.BuildJobs;
import com.cj.mcbaseball.field.FieldSetupSessions;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.GameKit;
import com.cj.mcbaseball.game.GameManager;
import com.cj.mcbaseball.live.LiveBaseballManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
    modid = "mcbaseball",
    bus = Bus.FORGE
)
public final class ServerEvents {
    @SubscribeEvent
    public static void onRightClickBlock(RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer sp && FieldSetupSessions.get(sp) != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                FieldSetupSessions.handleClick(sp, event.getPos());
            }

            return;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase == Phase.END) {
            GameManager.tick(event.getServer());
            LiveBaseballManager.tickIfRunning(event.getServer());
            BuildJobs.tick(event.getServer());
            if (event.getServer().getTickCount() % 20 == 0) {
                FieldSetupSessions.tickExpiry(event.getServer());
            }
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            BaseballGame g = GameManager.forPlayer(sp.getUUID());
            if (g != null) {
                g.fielding.humanTag(sp, event.getTarget());
                if (g.slotOf(event.getTarget()) != null) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        ItemEntity e = event.getEntity();
        if (GameKit.gameBallId(e.getItem()) != null && GameManager.get(GameKit.gameBallId(e.getItem())) != null) {
            event.getPlayer().getInventory().add(e.getItem().copy());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent event) {
        FieldSetupSessions.forget(event.getEntity().getUUID());
        GameManager.onLogout(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent event) {
        GameManager.onLogin(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        BaseballCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FieldSetupSessions.clearAll();
        GameManager.endAll();
        BuildJobs.clear();
        LiveBaseballManager.shutdown();
    }

    private ServerEvents() {
    }
}
