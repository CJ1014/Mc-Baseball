package com.cj.mcbaseball.game;

import com.cj.mcbaseball.field.FieldLayout;
import com.cj.mcbaseball.field.FieldMarker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class FieldGeometry {
    public final Vec3 home;
    public final Vec3 mound;
    public final Vec3[] bases = new Vec3[4];
    public final Vec3 forward;
    public final Vec3 right;
    public final double baseDist;
    public final double groundY;
    private final double angle1B;
    private final double angle3B;
    private final double[] wallAngles;
    private final double[] wallDists;
    private final double defaultFence;
    @Nullable
    private final Vec3 homeDugout;
    @Nullable
    private final Vec3 awayDugout;
    public static final double ZONE_HALF_WIDTH = 0.5;
    public static final double ZONE_BOTTOM = 0.45;
    public static final double ZONE_TOP = 1.35;
    public static final double CONTACT_PLANE = 0.35;
    public static final double BASE_TOUCH_RADIUS = 1.05;

    private FieldGeometry(Level level, FieldLayout l) {
        this.home = top(level, l.get(FieldMarker.HOME_PLATE));
        this.mound = top(level, l.get(FieldMarker.PITCHERS_MOUND));
        this.bases[0] = this.home;
        this.bases[1] = top(level, l.get(FieldMarker.FIRST_BASE));
        this.bases[2] = top(level, l.get(FieldMarker.SECOND_BASE));
        this.bases[3] = top(level, l.get(FieldMarker.THIRD_BASE));
        this.groundY = this.home.y;
        this.forward = flat(this.bases[2].subtract(this.home)).normalize();
        this.right = new Vec3(-this.forward.z, 0.0, this.forward.x);
        this.baseDist = (
                flatDist(this.home, this.bases[1])
                    + flatDist(this.bases[1], this.bases[2])
                    + flatDist(this.bases[2], this.bases[3])
                    + flatDist(this.bases[3], this.home)
            )
            / 4.0;
        this.angle1B = this.angleOf(this.bases[1]);
        this.angle3B = this.angleOf(this.bases[3]);
        this.defaultFence = flatDist(this.home, this.bases[2]) * 3.0;
        List<double[]> pts = new ArrayList<>();

        for (BlockPos p : l.outfieldWall()) {
            Vec3 v = new Vec3((double)p.getX() + 0.5, (double)p.getY(), (double)p.getZ() + 0.5);
            pts.add(new double[]{this.angleOf(v), flatDist(this.home, v)});
        }

        for (FieldMarker pole : new FieldMarker[]{FieldMarker.LEFT_FOUL_POLE, FieldMarker.RIGHT_FOUL_POLE}) {
            BlockPos p = l.get(pole);
            if (p != null) {
                Vec3 v = new Vec3((double)p.getX() + 0.5, (double)p.getY(), (double)p.getZ() + 0.5);
                pts.add(new double[]{this.angleOf(v), flatDist(this.home, v)});
            }
        }

        pts.sort((a, b) -> Double.compare(a[0], b[0]));
        this.wallAngles = new double[pts.size()];
        this.wallDists = new double[pts.size()];

        for (int i = 0; i < pts.size(); i++) {
            this.wallAngles[i] = pts.get(i)[0];
            this.wallDists[i] = pts.get(i)[1];
        }

        BlockPos hd = l.get(FieldMarker.HOME_DUGOUT);
        BlockPos ad = l.get(FieldMarker.AWAY_DUGOUT);
        this.homeDugout = hd == null ? null : top(level, hd);
        this.awayDugout = ad == null ? null : top(level, ad);
    }

    public static FieldGeometry of(Level level, FieldLayout layout) {
        return new FieldGeometry(level, layout);
    }

    private static Vec3 top(Level level, BlockPos p) {
        VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
        double h = s.isEmpty() ? 0.0 : s.max(Axis.Y);
        return new Vec3((double)p.getX() + 0.5, (double)p.getY() + h, (double)p.getZ() + 0.5);
    }

    public static Vec3 flat(Vec3 v) {
        return new Vec3(v.x, 0.0, v.z);
    }

    public static double flatDist(Vec3 a, Vec3 b) {
        return flat(a.subtract(b)).length();
    }

    public double angleOf(Vec3 p) {
        Vec3 rel = flat(p.subtract(this.home));
        return Math.atan2(rel.dot(this.right), rel.dot(this.forward));
    }

    public double distFromHome(Vec3 p) {
        return flatDist(p, this.home);
    }

    public Vec3 fieldPoint(double x, double y) {
        return this.home.add(this.forward.scale(x * this.baseDist)).add(this.right.scale(y * this.baseDist));
    }

    public boolean isFairDirection(Vec3 p) {
        Vec3 rel = flat(p.subtract(this.home));
        if (rel.dot(this.forward) <= 0.0) {
            return false;
        } else {
            double a = this.angleOf(p);
            return a >= this.angle3B - 0.01 && a <= this.angle1B + 0.01;
        }
    }

    public boolean pastBases(Vec3 p) {
        return this.distFromHome(p) > this.baseDist * 1.02;
    }

    public double fenceDistance(double angle) {
        int n = this.wallAngles.length;
        if (n == 0) {
            return this.defaultFence;
        } else if (n == 1) {
            return this.wallDists[0];
        } else if (angle <= this.wallAngles[0]) {
            return this.wallDists[0];
        } else if (angle >= this.wallAngles[n - 1]) {
            return this.wallDists[n - 1];
        } else {
            for (int i = 1; i < n; i++) {
                if (angle <= this.wallAngles[i]) {
                    double t = (angle - this.wallAngles[i - 1]) / Math.max(1.0E-6, this.wallAngles[i] - this.wallAngles[i - 1]);
                    return Mth.lerp(t, this.wallDists[i - 1], this.wallDists[i]);
                }
            }

            return this.wallDists[n - 1];
        }
    }

    public boolean beyondFence(Vec3 p) {
        return this.distFromHome(p) > this.fenceDistance(this.angleOf(p));
    }

    public double planeDistance(Vec3 p) {
        return p.subtract(this.home).dot(this.forward);
    }

    public double lateral(Vec3 p) {
        return p.subtract(this.home).dot(this.right);
    }

    public boolean inStrikeZone(Vec3 crossing) {
        double lat = Math.abs(this.lateral(crossing));
        double h = crossing.y - this.groundY;
        double r = 0.125;
        return lat <= 0.5 + r && h >= 0.45 - r && h <= 1.35 + r;
    }

    public Vec3 zonePoint(double lateral, double height) {
        return new Vec3(this.home.x, this.groundY + height, this.home.z).add(this.right.scale(lateral));
    }

    public Vec3 spot(Position p) {
        return switch (p) {
            case PITCHER -> this.mound.subtract(this.forward.scale(0.4));
            case CATCHER -> this.catcherSpot();
            default -> {
                Vec3 s = this.fieldPoint(p.x, p.y);
                if (p.outfield()) {
                    double fence = this.fenceDistance(this.angleOf(s));
                    double d = this.distFromHome(s);
                    if (d > fence * 0.82) {
                        s = this.home.add(flat(s.subtract(this.home)).normalize().scale(fence * 0.82));
                    }
                }

                yield new Vec3(s.x, this.groundY, s.z);
            }
        };
    }

    public Vec3 catcherSpot() {
        return this.home.subtract(this.forward.scale(1.5));
    }

    public Vec3 batterBox(boolean batsRight) {
        return this.home.add(this.right.scale(batsRight ? -1.8 : 1.8)).add(this.forward.scale(0.1));
    }

    public Vec3 dugout(TeamSide side) {
        Vec3 marked = side == TeamSide.HOME ? this.homeDugout : this.awayDugout;
        if (marked != null) {
            return marked;
        } else {
            double y = side == TeamSide.HOME ? -1.25 : 1.25;
            return this.fieldPoint(0.25, y);
        }
    }

    public Vec3 dugoutLine(TeamSide side) {
        Vec3 d = this.dugout(side).subtract(this.home);
        d = new Vec3(d.x, 0.0, d.z).normalize();
        Vec3 l1 = this.base(1).subtract(this.home);
        Vec3 l3 = this.base(3).subtract(this.home);
        l1 = new Vec3(l1.x, 0.0, l1.z).normalize();
        l3 = new Vec3(l3.x, 0.0, l3.z).normalize();
        return d.dot(l1) > d.dot(l3) ? l1 : l3;
    }

    public Vec3 dugoutSpot(TeamSide side, int index, int count) {
        return this.dugout(side).add(this.dugoutLine(side).scale(((double)index - (double)(count - 1) / 2.0) * 1.0));
    }

    public Vec3 dugoutFacing(TeamSide side) {
        Vec3 dir = this.dugoutLine(side);
        double t = this.dugout(side).subtract(this.home).dot(dir);
        return this.home.add(dir.scale(t)).add(0.0, 1.5, 0.0);
    }

    public int baseAt(Vec3 p) {
        for (int b = 1; b <= 4; b++) {
            if (this.touching(p, b)) {
                return b;
            }
        }

        return -1;
    }

    public Vec3 base(int b) {
        return this.bases[b % 4];
    }

    public boolean touching(Vec3 feet, int b) {
        Vec3 c = this.base(b);
        return flatDist(feet, c) <= 1.05 && Math.abs(feet.y - c.y) < 1.6;
    }

    public static float yawToward(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
    }
}
