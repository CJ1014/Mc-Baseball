package com.cj.mcbaseball.field;

import com.cj.mcbaseball.block.BaseBlock;
import com.cj.mcbaseball.block.HomePlateBlock;
import com.cj.mcbaseball.block.PitchersRubberBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public final class FieldAutoDetector {
    public static FieldAutoDetector.Result scan(Level level, BlockPos origin, int radius, int vertical) {
        List<BlockPos> homes = new ArrayList<>();
        List<BlockPos> rubbers = new ArrayList<>();
        List<BlockPos> bases = new ArrayList<>();
        MutableBlockPos m = new MutableBlockPos();
        int r2 = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= r2) {
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    if (level.hasChunk(x >> 4, z >> 4)) {
                        for (int dy = -vertical; dy <= vertical; dy++) {
                            m.set(x, origin.getY() + dy, z);
                            Block b = level.getBlockState(m).getBlock();
                            if (b instanceof HomePlateBlock) {
                                homes.add(m.immutable());
                            } else if (b instanceof PitchersRubberBlock) {
                                rubbers.add(m.immutable());
                            } else if (b instanceof BaseBlock) {
                                bases.add(m.immutable());
                            }
                        }
                    }
                }
            }
        }

        BlockPos home = nearest(homes, origin);
        if (home == null) {
            return new FieldAutoDetector.Result(null, null, null, null, null);
        } else {
            BlockPos mound = nearest(rubbers, home);
            if (mound == null) {
                return new FieldAutoDetector.Result(home, null, null, null, null);
            } else {
                double dx = (double)(mound.getX() - home.getX());
                double dzx = (double)(mound.getZ() - home.getZ());
                double len = Math.sqrt(dx * dx + dzx * dzx);
                if (len < 1.0E-6) {
                    return new FieldAutoDetector.Result(home, mound, null, null, null);
                } else {
                    dx /= len;
                    dzx /= len;
                    double rx = -dzx;
                    double rz = dx;
                    BlockPos second = null;
                    BlockPos first = null;
                    BlockPos third = null;
                    double bestForward = Double.NEGATIVE_INFINITY;

                    for (BlockPos b : bases) {
                        double fx = (double)(b.getX() - home.getX());
                        double fz = (double)(b.getZ() - home.getZ());
                        double forward = fx * dx + fz * dzx;
                        double side = Math.abs(fx * rx + fz * rz);
                        double score = forward - side * 2.0;
                        if (score > bestForward) {
                            bestForward = score;
                            second = b;
                        }
                    }

                    double bestRight = 0.0;
                    double bestLeft = 0.0;

                    for (BlockPos bx : bases) {
                        if (!bx.equals(second)) {
                            double fx = (double)(bx.getX() - home.getX());
                            double fz = (double)(bx.getZ() - home.getZ());
                            double side = fx * rx + fz * rz;
                            if (side > bestRight) {
                                bestRight = side;
                                first = bx;
                            }

                            if (side < bestLeft) {
                                bestLeft = side;
                                third = bx;
                            }
                        }
                    }

                    return new FieldAutoDetector.Result(home, mound, first, second, third);
                }
            }
        }
    }

    @Nullable
    private static BlockPos nearest(List<BlockPos> list, BlockPos to) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;

        for (BlockPos p : list) {
            double d = p.distSqr(to);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }

        return best;
    }

    private FieldAutoDetector() {
    }

    public static record Result(
        @Nullable BlockPos home, @Nullable BlockPos mound, @Nullable BlockPos first, @Nullable BlockPos second, @Nullable BlockPos third
    ) {
    }
}
