package com.cj.mcbaseball.network;

import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.live.LiveBaseballManager;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Client -> server: "I'm looking at the live game browser for this day; I have schedule version N."
 * Sent when the browser opens and every few seconds while it stays open. The server answers only when
 * it has something newer, and decides by itself when to actually call the data provider.
 *
 * @param epochDay {@link #TODAY} or a LocalDate epoch day
 */
public record LiveBrowserRequestPacket(BlockPos controller, long epochDay, int knownVersion, boolean force) {
    public static final long TODAY = Long.MIN_VALUE;
    private static final double MAX_DIST_SQR = 144.0;

    public static void encode(LiveBrowserRequestPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.controller);
        b.writeLong(p.epochDay);
        b.writeVarInt(p.knownVersion);
        b.writeBoolean(p.force);
    }

    public static LiveBrowserRequestPacket decode(FriendlyByteBuf b) {
        return new LiveBrowserRequestPacket(b.readBlockPos(), b.readLong(), b.readVarInt(), b.readBoolean());
    }

    public static void handle(LiveBrowserRequestPacket p, Supplier<Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp == null) {
            return;
        }
        ServerLevel level = sp.serverLevel();
        if (!level.isLoaded(p.controller) || sp.distanceToSqr(p.controller.getCenter()) > MAX_DIST_SQR) {
            return;
        }
        if (level.getBlockEntity(p.controller) instanceof FieldControllerBlockEntity) {
            LiveBaseballManager.get(sp.server).requestSchedule(sp, p.epochDay, p.knownVersion, p.force);
        }
    }
}
