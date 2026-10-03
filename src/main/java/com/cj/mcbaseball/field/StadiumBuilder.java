package com.cj.mcbaseball.field;

import com.cj.mcbaseball.block.FieldControllerBlock;
import com.cj.mcbaseball.block.ScoreboardBlock;
import com.cj.mcbaseball.game.FieldGeometry;
import com.cj.mcbaseball.registry.ModBlocks;
import com.cj.mcbaseball.registry.ModSounds;
import com.cj.mcbaseball.scoreboard.ScoreboardBlockEntity;
import java.util.function.BiPredicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class StadiumBuilder {
    public static final int GAP = 8;
    public static final int ROWS = 10;
    public static final int BLEACHER_ROWS = 8;
    static final int CLEAR = 26;
    static final int FLAGS = 2;

    public static int minCoord() {
        return -21;
    }

    public static int maxCoord(FieldPlan p) {
        return p.fence + 26;
    }

    @Nullable
    public static BuildJob job(ServerLevel level, FieldControllerBlockEntity be, FieldBuilder.Size size, @Nullable ServerPlayer player) {
        return job(level, be, size, player, StadiumBuilder.Options.ALL);
    }

    @Nullable
    public static BuildJob job(
        ServerLevel level, FieldControllerBlockEntity be, FieldBuilder.Size size, @Nullable ServerPlayer player, StadiumBuilder.Options o
    ) {
        FieldPlan p = new FieldPlan(be.getBlockPos(), (Direction)be.getBlockState().getValue(FieldControllerBlock.FACING), size);
        int min = minCoord();
        int max = maxCoord(p);
        if (!FieldBuilder.loaded(level, p, min, max)) {
            return null;
        } else {
            BiPredicate<Integer, Integer> inside = (a, b) -> FieldBuilder.inFieldFootprint(p, a, b) || o.stands() && inStands(p, a, b);
            return new BuildJob(level, be.getBlockPos(), min, max, min, max, (a, b) -> {
                int w = FieldBuilder.paintFieldColumn(level, p, a, b, 26);
                if (o.stands()) {
                    w += paintStands(level, p, a, b);
                }

                w += FieldBuilder.sealRing(level, p, a, b, inside, Blocks.GRAY_CONCRETE.defaultBlockState(), p.y0 + 45);
                return w + FieldBuilder.drainAbove(level, p, a, b, inside, p.y0 + 26 + 1, p.y0 + 45);
            }, () -> {
                FieldBuilder.drain(level, p, min, max, inside);
                FieldBuilder.placeFeatures(level, p, be);
                if (o.extras()) {
                    placeTowers(level, p);
                }

                if (o.lights()) {
                    placeLights(level, p);
                }

                if (o.extras()) {
                    placeVideoBoard(level, p, be);
                }

                celebrate(level, p, player);
            }, player, "mcbaseball.stadium.progress");
        }
    }

    static boolean inStands(FieldPlan p, int a, int b) {
        int R = p.fence;
        double dist = Math.sqrt((double)(a * a + b * b));
        double edge = (double)R * 0.8;
        double df = -1.0;
        if (a < 0 && b >= 0 && (double)b <= edge) {
            df = (double)(-a);
        } else if (b < 0 && a >= 0 && (double)a <= edge) {
            df = (double)(-b);
        } else if (a < 0 && b < 0) {
            df = dist;
        }

        if (df >= 0.0) {
            int row = (int)Math.floor(df - 8.0);
            if (row >= -1 && row <= 10) {
                return true;
            }
        }

        if (a >= 0 && b >= 0) {
            double out = dist - ((double)R + 1.25);
            return out >= 1.0 && out < 10.0;
        } else {
            return false;
        }
    }

    static int paintStands(ServerLevel level, FieldPlan p, int a, int b) {
        int R = p.fence;
        int y0 = p.y0;
        int w = 0;
        double dist = Math.sqrt((double)(a * a + b * b));
        double edge = (double)R * 0.8;
        double df = -1.0;
        Direction uphill = null;
        int along = 0;
        if (a < 0 && b >= 0 && (double)b <= edge) {
            df = (double)(-a);
            uphill = p.u.getOpposite();
            along = b;
        } else if (b < 0 && a >= 0 && (double)a <= edge) {
            df = (double)(-b);
            uphill = p.v.getOpposite();
            along = a;
        } else if (a < 0 && b < 0) {
            df = dist;
            uphill = -a >= -b ? p.u.getOpposite() : p.v.getOpposite();
            along = a - b;
        }

        if (df >= 0.0) {
            int row = (int)Math.floor(df - 8.0);
            if (row != -1 || a < 0 && b < 0) {
                if (row >= 0 && row < 10) {
                    w += ground(level, p, a, b);
                    w += seatColumn(level, p, a, b, y0, row, uphill, Math.floorMod(along, 9) == 4);
                } else if (row == 10) {
                    w += ground(level, p, a, b);
                    w += wallColumn(level, p, a, b, y0, y0 + 10 + 2);
                }
            } else {
                w += ground(level, p, a, b);
                level.setBlock(p.at(a, b, y0), ((Block)ModBlocks.WALL_PADDING.get()).defaultBlockState(), 2);
                w++;
            }
        }

        if (a >= 0 && b >= 0) {
            double out = dist - ((double)R + 1.25);
            if (out >= 1.0 && out < 10.0) {
                int row = (int)Math.floor(out) - 1;
                w += ground(level, p, a, b);
                Direction up = a >= b ? p.u : p.v;
                if (row < 8) {
                    w += seatColumn(level, p, a, b, y0 + 3, row, up, Math.floorMod(a - b, 9) == 0);
                } else {
                    w += wallColumn(level, p, a, b, y0, y0 + 3 + 8 + 1);
                }
            }
        }

        return w;
    }

    private static int ground(ServerLevel level, FieldPlan p, int a, int b) {
        if (FieldBuilder.inFieldFootprint(p, a, b)) {
            return 0;
        } else {
            int w = 0;
            BlockPos g = p.at(a, b, p.y0 - 1);

            for (int y = 0; y <= 26; y++) {
                BlockPos pos = g.above(1 + y);
                if (!pos.equals(p.ctrl) && !level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    w++;
                }
            }

            level.setBlock(g, Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
            return w + 1 + FieldBuilder.fillBelow(level, g);
        }
    }

    private static int seatColumn(ServerLevel level, FieldPlan p, int a, int b, int base, int row, Direction uphill, boolean aisle) {
        int w = 0;

        for (int y = base; y < base + row; y++) {
            level.setBlock(p.at(a, b, y), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
            w++;
        }

        BlockState seat = (BlockState)(aisle ? Blocks.STONE_BRICK_STAIRS : Blocks.DARK_PRISMARINE_STAIRS).defaultBlockState().setValue(StairBlock.FACING, uphill);
        level.setBlock(p.at(a, b, base + row), seat, 2);
        return w + 1;
    }

    private static int wallColumn(ServerLevel level, FieldPlan p, int a, int b, int from, int to) {
        for (int y = from; y < to; y++) {
            level.setBlock(p.at(a, b, y), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
        }

        level.setBlock(p.at(a, b, to), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 2);
        return to - from + 1;
    }

    private static void placeTowers(ServerLevel level, FieldPlan p) {
        int R = p.fence;
        int y0 = p.y0;
        int back = 19;
        int[][] spots = new int[][]{
            {(int)((double)R * 0.8), -back},
            {-back, (int)((double)R * 0.8)},
            {(int)Math.round((double)(R + 12) * Math.cos(Math.toRadians(18.0))), (int)Math.round((double)(R + 12) * Math.sin(Math.toRadians(18.0)))},
            {(int)Math.round((double)(R + 12) * Math.cos(Math.toRadians(72.0))), (int)Math.round((double)(R + 12) * Math.sin(Math.toRadians(72.0)))}
        };
        int top = y0 + 10 + 16;

        for (int[] s : spots) {
            for (int y = y0; y < top; y++) {
                level.setBlock(p.at(s[0], s[1], y), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 2);
            }

            for (int[] o : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                level.setBlock(p.at(s[0] + o[0], s[1] + o[1], top), Blocks.SEA_LANTERN.defaultBlockState(), 3);
                level.setBlock(p.at(s[0] + o[0], s[1] + o[1], top + 1), Blocks.SEA_LANTERN.defaultBlockState(), 3);
            }
        }
    }

    private static void placeLights(ServerLevel level, FieldPlan p) {
        int R = p.fence;
        BlockState light = Blocks.LIGHT.defaultBlockState();

        for (int a = -8; a <= R; a += 7) {
            for (int b = -8; b <= R; b += 7) {
                if (!(Math.sqrt((double)(a * a + b * b)) > (double)R)) {
                    BlockPos pos = p.at(a, b, p.y0 + 6);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, light, 3);
                    }
                }
            }
        }
    }

    private static void placeVideoBoard(ServerLevel level, FieldPlan p, FieldControllerBlockEntity be) {
        int c = (int)Math.round((double)(p.fence + 13) / Math.sqrt(2.0));
        int y0 = p.y0;
        int center = y0 + 10 + 6;
        BlockPos boardPos = p.at(c, c, center);
        Vec3 at = Vec3.atCenterOf(boardPos);
        Vec3 home = Vec3.atCenterOf(p.home);
        Vec3 to = new Vec3(home.x - at.x, 0.0, home.z - at.z).normalize();
        Vec3 right = new Vec3(-to.z, 0.0, to.x);

        for (int k : new int[]{-3, 3}) {
            BlockPos base = BlockPos.containing(at.add(right.scale((double)k)));

            for (int y = y0; y < center - 2; y++) {
                level.setBlock(new BlockPos(base.getX(), y, base.getZ()), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
            }
        }

        level.setBlock(boardPos, ((Block)ModBlocks.SCOREBOARD.get()).defaultBlockState(), 3);
        if (level.getBlockEntity(boardPos) instanceof ScoreboardBlockEntity sb) {
            sb.setScale(6);
            sb.setYaw(FieldGeometry.yawToward(at, home));
        }

        ScoreboardBlock.link(level, boardPos, p.ctrl, null);
    }

    private static void celebrate(ServerLevel level, FieldPlan p, @Nullable ServerPlayer player) {
        level.playSound(null, p.home, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 2.0F, 1.0F);
        level.playSound(null, p.home, (SoundEvent)ModSounds.GAME_START.get(), SoundSource.BLOCKS, 2.0F, 1.0F);

        for (int i = 0; i < 60; i++) {
            BlockPos at = p.at(level.random.nextInt(p.fence), level.random.nextInt(p.fence), p.y0 + 8 + level.random.nextInt(8));
            level.sendParticles(ParticleTypes.FIREWORK, (double)at.getX() + 0.5, (double)at.getY(), (double)at.getZ() + 0.5, 6, 0.5, 0.5, 0.5, 0.08);
        }

        if (player != null) {
            ServerPlayer pl = level.getServer().getPlayerList().getPlayer(player.getUUID());
            if (pl != null) {
                pl.displayClientMessage(Component.translatable("mcbaseball.stadium.done").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}), true);
            }
        }
    }

    private StadiumBuilder() {
    }

    public static record Options(boolean stands, boolean lights, boolean extras) {
        public static final StadiumBuilder.Options ALL = new StadiumBuilder.Options(true, true, true);
    }
}
