package com.cj.mcbaseball.game;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class ChunkKeeper {
    private static final TicketType<ChunkPos> FIELD = TicketType.create("mcbaseball_field", Comparator.comparingLong(ChunkPos::toLong), 100);
    private static final TicketType<ChunkPos> BALL = TicketType.create("mcbaseball_ball", Comparator.comparingLong(ChunkPos::toLong), 40);

    public static void keepField(ServerLevel level, FieldGeometry geo) {
        double reach = Math.max(geo.fenceDistance(0.0), Math.max(geo.fenceDistance(-0.8), geo.fenceDistance(0.8))) + 24.0;
        int chunks = Mth.clamp((int)Math.ceil(reach / 16.0), 2, 10);
        ChunkPos c = new ChunkPos(BlockPos.containing(geo.home));
        level.getChunkSource().addRegionTicket(FIELD, c, chunks + 2, c);
    }

    public static void keepBall(ServerLevel level, Vec3 pos) {
        ChunkPos c = new ChunkPos(BlockPos.containing(pos));
        level.getChunkSource().addRegionTicket(BALL, c, 3, c);
    }

    private ChunkKeeper() {
    }
}
