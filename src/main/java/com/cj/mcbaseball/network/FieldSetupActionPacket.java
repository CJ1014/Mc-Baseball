package com.cj.mcbaseball.network;

import com.cj.mcbaseball.field.BuildJob;
import com.cj.mcbaseball.field.BuildJobs;
import com.cj.mcbaseball.field.FieldBuilder;
import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.field.FieldMarker;
import com.cj.mcbaseball.field.FieldSetupSessions;
import com.cj.mcbaseball.game.GameManager;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record FieldSetupActionPacket(BlockPos controller, FieldSetupActionPacket.Action action, int marker) {
    private static final double MAX_DIST_SQR = 144.0;

    public static void encode(FieldSetupActionPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.controller);
        buf.writeEnum(p.action);
        buf.writeVarInt(p.marker);
    }

    public static FieldSetupActionPacket decode(FriendlyByteBuf buf) {
        return new FieldSetupActionPacket(buf.readBlockPos(), (FieldSetupActionPacket.Action)buf.readEnum(FieldSetupActionPacket.Action.class), buf.readVarInt());
    }

    public static void handle(FieldSetupActionPacket p, Supplier<Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            if (player.level().isLoaded(p.controller)) {
                if (!(player.distanceToSqr(p.controller.getCenter()) > 144.0)) {
                    if (player.level().getBlockEntity(p.controller) instanceof FieldControllerBlockEntity be) {
                        if (!be.canEdit(player)) {
                            player.displayClientMessage(
                                Component.translatable("mcbaseball.field.no_permission", new Object[]{be.ownerName()}).withStyle(ChatFormatting.RED), true
                            );
                        } else {
                            FieldMarker marker = FieldMarker.byId(p.marker);
                            switch (p.action) {
                                case BEGIN_MARK:
                                    FieldSetupSessions.begin(player, p.controller, marker);
                                    break;
                                case CLEAR:
                                    be.clearMarker(marker, player);
                                    break;
                                case AUTO_DETECT:
                                    player.displayClientMessage(be.autoDetect(player), true);
                                    break;
                                case BUILD_FIELD:
                                    if (GameManager.at(player.serverLevel(), p.controller) != null) {
                                        player.displayClientMessage(Component.translatable("mcbaseball.start.already_running").withStyle(ChatFormatting.RED), true);
                                    } else if (BuildJobs.busyNear(player.serverLevel(), p.controller)) {
                                        player.displayClientMessage(Component.translatable("mcbaseball.build.busy").withStyle(ChatFormatting.RED), true);
                                    } else {
                                        BuildJob job = FieldBuilder.job(player.serverLevel(), be, FieldBuilder.Size.byId(p.marker), player);
                                        if (job == null) {
                                            player.displayClientMessage(Component.translatable("mcbaseball.build.not_loaded").withStyle(ChatFormatting.RED), true);
                                        } else {
                                            BuildJobs.start(job);
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    public static enum Action {
        BEGIN_MARK,
        CLEAR,
        AUTO_DETECT,
        BUILD_FIELD;
    }
}
