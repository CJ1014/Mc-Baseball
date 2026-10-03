package com.cj.mcbaseball.field;

import com.cj.mcbaseball.block.FieldControllerBlock;
import com.cj.mcbaseball.block.HomePlateBlock;
import com.cj.mcbaseball.block.PitchersRubberBlock;
import com.cj.mcbaseball.block.ScoreboardBlock;
import com.cj.mcbaseball.registry.ModBlocks;
import java.util.function.BiPredicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class FieldBuilder {
    static final int FLAGS = 2;

    public static boolean inFieldFootprint(FieldPlan p, int a, int b) {
        return a >= -9 && b >= -9 && Math.sqrt((double)(a * a + b * b)) <= (double)p.fence + 3.5;
    }

    public static boolean loaded(ServerLevel level, FieldPlan p, int min, int max) {
        for (int a = min; a <= max; a += 8) {
            for (int b = min; b <= max; b += 8) {
                if (!level.isLoaded(p.at(a, b, p.y0))) {
                    return false;
                }
            }
        }

        return true;
    }

    public static int paintFieldColumn(ServerLevel level, FieldPlan p, int a, int b, int clearHeight) {
        if (!inFieldFootprint(p, a, b)) {
            return 0;
        } else {
            int d = p.bases;
            int R = p.fence;
            int y0 = p.y0;
            int w = 0;
            double dist = Math.sqrt((double)(a * a + b * b));
            boolean fair = a >= 0 && b >= 0;
            BlockPos ground = p.at(a, b, y0 - 1);

            for (int y = 0; y <= clearHeight; y++) {
                BlockPos pos = ground.above(1 + y);
                if (!pos.equals(p.ctrl) && !level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    w++;
                }
            }

            w += fillBelow(level, ground);
            boolean infieldGrass = a >= 2 && b >= 2 && a <= d - 3 && b <= d - 3;
            boolean nearHome = dist <= 3.6;
            boolean nearMound = Math.hypot((double)a - p.mound, (double)b - p.mound) <= 2.6;
            boolean nearBase = Math.hypot((double)(a - d), (double)b) <= 2.2
                || Math.hypot((double)(a - d), (double)(b - d)) <= 2.2
                || Math.hypot((double)a, (double)(b - d)) <= 2.2;
            boolean infieldArc = a >= -1 && b >= -1 && dist <= (double)d * 1.45;
            BlockState surface;
            if (!nearHome && !nearMound && !nearBase && (!infieldArc || infieldGrass)) {
                if (infieldGrass) {
                    surface = ((Block)ModBlocks.OUTFIELD_GRASS_LIGHT.get()).defaultBlockState();
                } else if (fair && dist >= (double)(R - 3) && dist < (double)R) {
                    surface = ((Block)ModBlocks.WARNING_TRACK.get()).defaultBlockState();
                } else {
                    surface = Math.floorDiv(a + b, 4) % 2 == 0
                        ? ((Block)ModBlocks.OUTFIELD_GRASS_LIGHT.get()).defaultBlockState()
                        : ((Block)ModBlocks.OUTFIELD_GRASS_DARK.get()).defaultBlockState();
                }
            } else {
                surface = ((Block)ModBlocks.INFIELD_DIRT.get()).defaultBlockState();
            }

            level.setBlock(ground, surface, 2);
            w++;
            BlockState pad = ((Block)ModBlocks.WALL_PADDING.get()).defaultBlockState();
            if (fair && dist >= (double)R && dist < (double)R + 1.25) {
                for (int yx = 0; yx < 3; yx++) {
                    level.setBlock(ground.above(1 + yx), pad, 2);
                }

                w += 3;
            }

            if (a <= 1 && b <= 1 && dist >= 7.0 && dist < 8.2) {
                for (int yx = 0; yx < 2; yx++) {
                    BlockPos pos = ground.above(1 + yx);
                    if (!pos.equals(p.ctrl)) {
                        level.setBlock(pos, pad, 2);
                        w++;
                    }
                }
            }

            boolean onLine = b == 0 && a >= 2 && a < R || a == 0 && b >= 2 && b < R;
            boolean baseSpot = a == d && b == 0 || a == 0 && b == d;
            if (onLine && !baseSpot) {
                level.setBlock(ground.above(), ((Block)ModBlocks.CHALK_LINE.get()).defaultBlockState(), 2);
                w++;
            }

            return w;
        }
    }

    static int fillBelow(ServerLevel level, BlockPos ground) {
        int w = 0;

        for (int y = 1; y <= 5; y++) {
            BlockPos pos = ground.below(y);
            BlockState st = level.getBlockState(pos);
            if (!st.isAir() && st.getFluidState().isEmpty()) {
                break;
            }

            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
            w++;
        }

        return w;
    }

    public static int sealRing(ServerLevel level, FieldPlan p, int a, int b, BiPredicate<Integer, Integer> inside, BlockState wall, int topY) {
        if (inside.test(a, b)) {
            return 0;
        } else {
            int w = 0;

            for (int y = p.y0 - 1; y <= topY; y++) {
                BlockPos pos = p.at(a, b, y);
                if (!level.getFluidState(pos).isEmpty()) {
                    level.setBlock(pos, wall, 2);
                    w++;
                }
            }

            return w;
        }
    }

    public static int drainAbove(ServerLevel level, FieldPlan p, int a, int b, BiPredicate<Integer, Integer> inside, int from, int to) {
        if (!inside.test(a, b)) {
            return 0;
        } else {
            int w = 0;

            for (int y = from; y <= to; y++) {
                BlockPos pos = p.at(a, b, y);
                if (!level.getFluidState(pos).isEmpty()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    w++;
                }
            }

            return w;
        }
    }

    public static void drain(ServerLevel level, FieldPlan p, int min, int max, BiPredicate<Integer, Integer> inside) {
        for (int a = min; a <= max; a++) {
            for (int b = min; b <= max; b++) {
                if (inside.test(a, b)) {
                    for (int y = p.y0; y <= p.y0 + 45; y++) {
                        BlockPos pos = p.at(a, b, y);
                        if (!level.getFluidState(pos).isEmpty() && !pos.equals(p.ctrl)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    public static void placeFeatures(ServerLevel level, FieldPlan p, FieldControllerBlockEntity be) {
        int d = p.bases;
        int R = p.fence;
        int y0 = p.y0;
        BlockPos first = p.at(d, 0, y0);
        BlockPos second = p.at(d, d, y0);
        BlockPos third = p.at(0, d, y0);
        BlockPos mound = p.at((int)p.mound, (int)p.mound, y0);
        level.setBlock(p.home, (BlockState)((Block)ModBlocks.HOME_PLATE.get()).defaultBlockState().setValue(HomePlateBlock.FACING, p.d1), 2);

        for (BlockPos pos : new BlockPos[]{first, second, third}) {
            level.setBlock(pos, ((Block)ModBlocks.BASE.get()).defaultBlockState(), 2);
        }

        level.setBlock(mound, (BlockState)((Block)ModBlocks.PITCHERS_RUBBER.get()).defaultBlockState().setValue(PitchersRubberBlock.FACING, p.d1), 2);
        BlockPos poleR = p.at(R + 1, 0, y0);
        BlockPos poleL = p.at(0, R + 1, y0);

        for (int y = 0; y < 10; y++) {
            level.setBlock(poleR.above(y), ((Block)ModBlocks.FOUL_POLE.get()).defaultBlockState(), 2);
            level.setBlock(poleL.above(y), ((Block)ModBlocks.FOUL_POLE.get()).defaultBlockState(), 2);
        }

        int dug = (int)((double)d * 0.55);
        BlockPos homeDug = p.at(-5, dug, y0);
        BlockPos awayDug = p.at(dug, -5, y0);

        for (int i = -3; i <= 3; i++) {
            level.setBlock(p.at(-6, dug + i, y0), Blocks.SPRUCE_SLAB.defaultBlockState(), 2);
            level.setBlock(p.at(dug + i, -6, y0), Blocks.SPRUCE_SLAB.defaultBlockState(), 2);
        }

        BlockPos sbPos = p.ctrl.above();
        level.setBlock(sbPos, (BlockState)((Block)ModBlocks.SCOREBOARD.get()).defaultBlockState().setValue(ScoreboardBlock.FACING, p.d1), 3);
        ScoreboardBlock.link(level, sbPos, p.ctrl, null);
        FieldLayout layout = be.layout();
        layout.set(FieldMarker.HOME_PLATE, p.home);
        layout.set(FieldMarker.FIRST_BASE, first);
        layout.set(FieldMarker.SECOND_BASE, second);
        layout.set(FieldMarker.THIRD_BASE, third);
        layout.set(FieldMarker.PITCHERS_MOUND, mound);
        layout.set(FieldMarker.RIGHT_FOUL_POLE, poleR);
        layout.set(FieldMarker.LEFT_FOUL_POLE, poleL);
        layout.set(FieldMarker.HOME_DUGOUT, homeDug);
        layout.set(FieldMarker.AWAY_DUGOUT, awayDug);
        layout.clear(FieldMarker.OUTFIELD_WALL);

        for (int deg = 0; deg <= 90; deg += 6) {
            double t = Math.toRadians((double)deg);
            layout.addWallPoint(p.at((int)Math.round(((double)R + 0.4) * Math.cos(t)), (int)Math.round(((double)R + 0.4) * Math.sin(t)), y0));
        }

        be.relayout();
    }

    @Nullable
    public static BuildJob job(ServerLevel level, FieldControllerBlockEntity be, FieldBuilder.Size size, @Nullable ServerPlayer player) {
        FieldPlan p = new FieldPlan(be.getBlockPos(), (Direction)be.getBlockState().getValue(FieldControllerBlock.FACING), size);
        int min = -11;
        int max = p.fence + 6;
        if (!loaded(level, p, min, max)) {
            return null;
        } else {
            BiPredicate<Integer, Integer> inside = (a, b) -> inFieldFootprint(p, a, b);
            return new BuildJob(
                level,
                be.getBlockPos(),
                min,
                max,
                min,
                max,
                (a, b) -> paintFieldColumn(level, p, a, b, 9)
                        + sealRing(level, p, a, b, inside, Blocks.DIRT.defaultBlockState(), p.y0 + 45)
                        + drainAbove(level, p, a, b, inside, p.y0 + 10, p.y0 + 45),
                () -> {
                    drain(level, p, min, max, inside);
                    placeFeatures(level, p, be);
                    level.playSound(null, p.home, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);
                    ServerPlayer pl = player == null ? null : level.getServer().getPlayerList().getPlayer(player.getUUID());
                    if (pl != null) {
                        pl.displayClientMessage(Component.translatable("mcbaseball.build.done").withStyle(ChatFormatting.GREEN), true);
                    }
                },
                player,
                "mcbaseball.build.progress"
            );
        }
    }

    public static Component build(ServerLevel level, FieldControllerBlockEntity be, FieldBuilder.Size size) {
        BuildJob j = job(level, be, size, null);
        if (j == null) {
            return Component.translatable("mcbaseball.build.not_loaded").withStyle(ChatFormatting.RED);
        } else {
            j.runAll();
            return Component.translatable("mcbaseball.build.done").withStyle(ChatFormatting.GREEN);
        }
    }

    private FieldBuilder() {
    }

    public static enum Size {
        SMALL(14, 56),
        STANDARD(18, 62),
        BIG(22, 76);

        public final int bases;
        public final int fence;

        private Size(int bases, int fence) {
            this.bases = bases;
            this.fence = fence;
        }

        public static FieldBuilder.Size byId(int i) {
            return values()[Mth.clamp(i, 0, 2)];
        }
    }
}
