package com.cj.mcbaseball.network;

import com.cj.mcbaseball.block.FieldControllerBlock;
import com.cj.mcbaseball.block.StadiumKitBlock;
import com.cj.mcbaseball.field.BuildJob;
import com.cj.mcbaseball.field.BuildJobs;
import com.cj.mcbaseball.field.FieldBuilder;
import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.field.FieldPlan;
import com.cj.mcbaseball.field.StadiumBuilder;
import com.cj.mcbaseball.field.StadiumKitBlockEntity;
import com.cj.mcbaseball.registry.ModBlocks;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent.Context;

public record StadiumKitPacket(BlockPos pos, int action, int value) {
    public static final int SIZE = 0;
    public static final int BUILD = 1;

    public static void encode(StadiumKitPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.pos);
        b.writeByte(p.action);
        b.writeByte(p.value);
    }

    public static StadiumKitPacket decode(FriendlyByteBuf b) {
        return new StadiumKitPacket(b.readBlockPos(), b.readByte(), b.readByte());
    }

    public static void handle(StadiumKitPacket p, Supplier<Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) {
            ServerLevel level = sp.serverLevel();
            if (level.isLoaded(p.pos) && !(sp.distanceToSqr(p.pos.getCenter()) > 144.0)) {
                if (level.getBlockEntity(p.pos) instanceof StadiumKitBlockEntity kit) {
                    if (!kit.canBuild(sp)) {
                        sp.displayClientMessage(Component.translatable("mcbaseball.stadium.not_yours").withStyle(ChatFormatting.RED), true);
                    } else if (p.action == 0) {
                        kit.setSize(p.value);
                    } else if (BuildJobs.busyNear(level, p.pos)) {
                        sp.displayClientMessage(Component.translatable("mcbaseball.build.busy").withStyle(ChatFormatting.RED), true);
                    } else {
                        FieldPlan plan = kit.plan();
                        if (!FieldBuilder.loaded(level, plan, StadiumBuilder.minCoord(), StadiumBuilder.maxCoord(plan))) {
                            sp.displayClientMessage(Component.translatable("mcbaseball.build.not_loaded").withStyle(ChatFormatting.RED), true);
                        } else {
                            Direction facing = (Direction)level.getBlockState(p.pos).getValue(StadiumKitBlock.FACING);
                            FieldBuilder.Size size = kit.size();
                            level.setBlock(
                                p.pos, (BlockState)((Block)ModBlocks.FIELD_CONTROLLER.get()).defaultBlockState().setValue(FieldControllerBlock.FACING, facing), 3
                            );
                            if (level.getBlockEntity(p.pos) instanceof FieldControllerBlockEntity be) {
                                be.setOwner(sp);
                                BuildJob job = StadiumBuilder.job(level, be, size, sp);
                                if (job != null) {
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
