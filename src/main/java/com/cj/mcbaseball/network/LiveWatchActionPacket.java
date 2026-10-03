package com.cj.mcbaseball.network;

import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.live.LiveBaseballManager;
import com.cj.mcbaseball.config.BaseballConfig;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

/** Client -> server: make this Field Controller start / stop following a real game. */
public record LiveWatchActionPacket(BlockPos controller, boolean start, long gameId) {
    private static final double MAX_DIST_SQR = 144.0;

    public static LiveWatchActionPacket start(BlockPos controller, long gameId) {
        return new LiveWatchActionPacket(controller, true, gameId);
    }

    public static LiveWatchActionPacket stop(BlockPos controller) {
        return new LiveWatchActionPacket(controller, false, 0L);
    }

    public static void encode(LiveWatchActionPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.controller);
        b.writeBoolean(p.start);
        b.writeLong(p.gameId);
    }

    public static LiveWatchActionPacket decode(FriendlyByteBuf b) {
        return new LiveWatchActionPacket(b.readBlockPos(), b.readBoolean(), b.readLong());
    }

    public static void handle(LiveWatchActionPacket p, Supplier<Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp == null) {
            return;
        }
        ServerLevel level = sp.serverLevel();
        if (!level.isLoaded(p.controller) || sp.distanceToSqr(p.controller.getCenter()) > MAX_DIST_SQR) {
            return;
        }
        if (!(level.getBlockEntity(p.controller) instanceof FieldControllerBlockEntity be)) {
            return;
        }
        if (!be.canEdit(sp)) {
            sp.displayClientMessage(Component.translatable("mcbaseball.field.no_permission", be.ownerName()).withStyle(ChatFormatting.RED), true);
            return;
        }
        LiveBaseballManager live = LiveBaseballManager.get(sp.server);
        if (!p.start) {
            live.stopWatching(level, p.controller);
            sp.displayClientMessage(Component.translatable("mcbaseball.live.watch_stopped").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!BaseballConfig.LIVE_ENABLED.get()) {
            sp.displayClientMessage(Component.translatable("mcbaseball.gui.live.disabled").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (be.isGameActive()) {
            sp.displayClientMessage(Component.translatable("mcbaseball.live.game_in_progress").withStyle(ChatFormatting.RED), true);
            return;
        }
        // Negative ids are recorded games (developer test mode only).
        if (p.gameId == 0 || p.gameId < 0 && !BaseballConfig.LIVE_DEBUG_MODE.get()) {
            return;
        }
        live.startWatching(level, p.controller, p.gameId);
        sp.displayClientMessage(Component.translatable("mcbaseball.live.watch_started").withStyle(ChatFormatting.GREEN), true);
    }
}
