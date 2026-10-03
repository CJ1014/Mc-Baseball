package com.cj.mcbaseball.physics;

import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class BallPhysics {
    public static final double RADIUS = 0.125;
    public static final double SPIN_RADIUS = 0.037;
    public static final double MAX_STEP = 0.2;
    public static final int MAX_SUBSTEPS = 24;
    public static final BallPhysics.Surface HARD = new BallPhysics.Surface("hard", 0.52, 0.35, 0.0022, 0.016);
    public static final BallPhysics.Surface DIRT = new BallPhysics.Surface("dirt", 0.4, 0.55, 0.003, 0.028);
    public static final BallPhysics.Surface GRASS = new BallPhysics.Surface("grass", 0.44, 0.5, 0.0035, 0.04);
    public static final BallPhysics.Surface PADDING = new BallPhysics.Surface("padding", 0.14, 0.8, 0.008, 0.08);
    public static final BallPhysics.Surface SNOW = new BallPhysics.Surface("snow", 0.12, 0.8, 0.01, 0.1);
    public static final BallPhysics.Surface ICE = new BallPhysics.Surface("ice", 0.5, 0.03, 2.0E-4, 0.003);
    public static final BallPhysics.Surface SLIME = new BallPhysics.Surface("slime", 0.85, 0.5, 0.003, 0.03);
    public static final BallPhysics.Surface HONEY = new BallPhysics.Surface("honey", 0.05, 1.0, 0.03, 0.3);

    public static double airDensity(double y, BallPhysics.Params p) {
        return p.altitude() ? Mth.clamp(1.0 - (y - 64.0) * 0.0019, 0.55, 1.15) : 1.0;
    }

    public static Vec3 airAccel(Vec3 v, Vec3 w, double y, BallPhysics.Params p) {
        double rho = airDensity(y, p);
        double sp = v.length();
        double dk = p.drag() * rho * sp;
        double mk = p.magnus() * rho;
        return new Vec3(
            -dk * v.x + mk * (w.y * v.z - w.z * v.y),
            -p.gravity() - dk * v.y + mk * (w.z * v.x - w.x * v.z),
            -dk * v.z + mk * (w.x * v.y - w.y * v.x)
        );
    }

    public static Vec3 spinForAccel(Vec3 v, Vec3 accel, BallPhysics.Params p) {
        double v2 = v.lengthSqr();
        return !(v2 < 1.0E-9) && !(p.magnus() <= 0.0) ? v.cross(accel).scale(1.0 / (p.magnus() * v2)) : Vec3.ZERO;
    }

    public static Vec3 backspin(Vec3 v, double rate) {
        Vec3 d = new Vec3(v.x, 0.0, v.z);
        return d.lengthSqr() < 1.0E-9 ? Vec3.ZERO : d.normalize().cross(new Vec3(0.0, 1.0, 0.0)).scale(rate);
    }

    public static void tick(Level level, Entity ctx, BallPhysics.State s, BallPhysics.Params p, @Nullable BallPhysics.Listener listener) {
        if (s.motion == BallMotion.RESTING) {
            if (isSupported(level, ctx, s.pos)) {
                s.vel = Vec3.ZERO;
                return;
            }

            s.motion = BallMotion.FLYING;
        }

        double speed = s.vel.length();
        int steps = Mth.clamp((int)Math.ceil(speed / 0.2), 1, 24);
        double dt = 1.0 / (double)steps;
        BallPhysics.Surface rollSurface = s.motion == BallMotion.ROLLING ? surfaceUnder(level, ctx, s.pos) : null;

        for (int i = 0; i < steps; i++) {
            Vec3 v = s.vel;
            if (s.motion == BallMotion.ROLLING && rollSurface != null) {
                double h = Math.sqrt(v.x * v.x + v.z * v.z);
                double decel = (rollSurface.rollConst() + rollSurface.rollLinear() * h) * p.rollScale() * dt;
                double nh = Math.max(0.0, h - decel);
                v = h > 1.0E-9 ? new Vec3(v.x / h * nh, 0.0, v.z / h * nh) : Vec3.ZERO;
            } else {
                v = v.add(airAccel(v, s.spin, s.pos.y, p).scale(dt));
                s.spin = s.spin.scale(1.0 - p.spinDecay() * dt);
            }

            if (s.inWater) {
                v = v.scale(Math.max(0.0, 1.0 - p.waterDrag() * dt));
            }

            s.vel = v;
            Vec3 from = s.pos;
            Vec3 to = from.add(v.scale(dt));
            if (listener != null && listener.onSegment(s, from, to)) {
                return;
            }

            BlockHitResult hit = level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, ctx));
            if (hit.getType() == Type.BLOCK) {
                bounce(level, ctx, s, p, hit, listener);
                if (s.motion == BallMotion.ROLLING) {
                    rollSurface = surfaceUnder(level, ctx, s.pos);
                }
            } else {
                s.pos = to;
            }
        }

        if (s.motion == BallMotion.ROLLING) {
            if (!isSupported(level, ctx, s.pos)) {
                s.motion = BallMotion.FLYING;
            } else if (s.vel.horizontalDistance() < p.restSpeed()) {
                s.vel = Vec3.ZERO;
                s.motion = BallMotion.RESTING;
            }
        }
    }

    private static void bounce(Level level, Entity ctx, BallPhysics.State s, BallPhysics.Params p, BlockHitResult hit, @Nullable BallPhysics.Listener listener) {
        Direction face = hit.getDirection();
        Vec3 n = new Vec3((double)face.getStepX(), (double)face.getStepY(), (double)face.getStepZ());
        Vec3 v = s.vel;
        double into = v.dot(n);
        if (into >= 0.0) {
            s.pos = hit.getLocation().add(n.scale(0.127));
        } else {
            BallPhysics.Surface surf = surfaceOf(level, hit.getBlockPos(), ctx);
            double e = Mth.clamp(surf.restitution() * p.bounceScale() * (1.0 - 0.12 * Math.min(1.0, -into)), 0.0, 0.95);
            Vec3 vn = n.scale(into);
            Vec3 vt = v.subtract(vn);
            Vec3 r = n.scale(-0.037);
            Vec3 slip = vt.add(s.spin.cross(r));
            double slipLen = slip.length();
            double jMax = surf.friction() * p.frictionScale() * (1.0 + e) * -into;
            double j = Math.min(slipLen * 2.0 / 7.0, jMax);
            Vec3 impulse = slipLen > 1.0E-9 ? slip.scale(-j / slipLen) : Vec3.ZERO;
            Vec3 vtOut = vt.add(impulse);
            s.spin = s.spin.add(r.cross(impulse).scale(1826.1504747991237));
            Vec3 out = vtOut.add(n.scale(-into * e));
            boolean floor = face == Direction.UP;
            if (floor && Math.abs(out.y) < p.settleVerticalSpeed()) {
                out = new Vec3(out.x, 0.0, out.z);
                s.motion = BallMotion.ROLLING;
                s.spin = Vec3.ZERO;
            } else if (s.motion != BallMotion.ROLLING || floor) {
                s.motion = BallMotion.FLYING;
            }

            Vec3 resolved = hit.getLocation().add(n.scale(0.127));
            if (!overlapsBlocks(level, ctx, resolved)) {
                s.pos = resolved;
            }

            s.vel = out;
            s.bounces++;
            if (listener != null) {
                listener.onBounce(s, face, hit.getBlockPos(), -into);
            }
        }
    }

    public static BallPhysics.Surface surfaceUnder(Level level, Entity ctx, Vec3 center) {
        return surfaceOf(level, BlockPos.containing(center.x, center.y - 0.125 - 0.05, center.z), ctx);
    }

    public static BallPhysics.Surface surfaceOf(Level level, BlockPos pos, Entity ctx) {
        BlockState st = level.getBlockState(pos);
        if (st.getFriction(level, pos, ctx) > 0.9F) {
            return ICE;
        } else if (st.is((net.minecraft.world.level.block.Block)ModBlocks.WALL_PADDING.get())) {
            return PADDING;
        } else if (st.is((net.minecraft.world.level.block.Block)ModBlocks.OUTFIELD_GRASS_LIGHT.get())
            || st.is((net.minecraft.world.level.block.Block)ModBlocks.OUTFIELD_GRASS_DARK.get())) {
            return GRASS;
        } else if (!st.is((net.minecraft.world.level.block.Block)ModBlocks.INFIELD_DIRT.get())
            && !st.is((net.minecraft.world.level.block.Block)ModBlocks.WARNING_TRACK.get())) {
            SoundType snd = st.getSoundType(level, pos, ctx);
            if (snd == SoundType.WOOL) {
                return PADDING;
            } else if (snd == SoundType.SLIME_BLOCK) {
                return SLIME;
            } else if (snd == SoundType.HONEY_BLOCK) {
                return HONEY;
            } else if (snd == SoundType.SNOW || snd == SoundType.POWDER_SNOW) {
                return SNOW;
            } else if (snd == SoundType.GRASS || snd == SoundType.MOSS || snd == SoundType.MOSS_CARPET || snd == SoundType.WET_GRASS) {
                return GRASS;
            } else {
                return snd != SoundType.GRAVEL
                        && snd != SoundType.SAND
                        && snd != SoundType.ROOTED_DIRT
                        && snd != SoundType.MUD
                        && snd != SoundType.PACKED_MUD
                        && snd != SoundType.SOUL_SAND
                        && snd != SoundType.SOUL_SOIL
                    ? HARD
                    : DIRT;
            }
        } else {
            return DIRT;
        }
    }

    public static boolean isSupported(Level level, Entity ctx, Vec3 center) {
        BlockHitResult down = level.clip(new ClipContext(center, center.subtract(0.0, 0.185, 0.0), Block.COLLIDER, Fluid.NONE, ctx));
        return down.getType() == Type.BLOCK && down.getDirection() == Direction.UP;
    }

    public static boolean overlapsBlocks(Level level, Entity ctx, Vec3 center) {
        return !level.noCollision(ctx, ballBox(center).deflate(0.01));
    }

    public static AABB ballBox(Vec3 c) {
        return new AABB(c.x - 0.125, c.y - 0.125, c.z - 0.125, c.x + 0.125, c.y + 0.125, c.z + 0.125);
    }

    public static boolean unstick(Level level, Entity ctx, BallPhysics.State s) {
        BlockPos bp = BlockPos.containing(s.pos);
        VoxelShape shape = level.getBlockState(bp).getCollisionShape(level, bp);
        if (shape.isEmpty()) {
            return false;
        } else {
            Vec3 local = s.pos.subtract((double)bp.getX(), (double)bp.getY(), (double)bp.getZ());

            for (AABB box : shape.toAabbs()) {
                if (box.contains(local)) {
                    s.pos = new Vec3(s.pos.x, (double)bp.getY() + box.maxY + 0.125 + 0.002, s.pos.z);
                    if (s.vel.y < 0.0) {
                        s.vel = new Vec3(s.vel.x, 0.0, s.vel.z);
                    }

                    return true;
                }
            }

            return false;
        }
    }

    @Nullable
    public static BallPhysics.Prediction predictLanding(Vec3 startCenter, Vec3 startVel, Vec3 spin, BallPhysics.Params p, double groundY, int maxTicks) {
        BallPhysics.FlightSim f = new BallPhysics.FlightSim(startCenter, startVel, spin);
        double targetY = groundY + 0.125;

        for (int t = 1; t <= maxTicks; t++) {
            Vec3 prev = f.pos;
            f.step(p);
            if (f.vel.y < 0.0 && f.pos.y <= targetY) {
                double frac = (prev.y - targetY) / Math.max(1.0E-9, prev.y - f.pos.y);
                return new BallPhysics.Prediction(prev.add(f.pos.subtract(prev).scale(frac)), t);
            }
        }

        return null;
    }

    private BallPhysics() {
    }

    public static final class FlightSim {
        public Vec3 pos;
        public Vec3 vel;
        public Vec3 spin;

        public FlightSim(Vec3 pos, Vec3 vel, Vec3 spin) {
            this.pos = pos;
            this.vel = vel;
            this.spin = spin;
        }

        public void step(BallPhysics.Params p) {
            this.vel = this.vel.add(BallPhysics.airAccel(this.vel, this.spin, this.pos.y, p));
            this.pos = this.pos.add(this.vel);
            this.spin = this.spin.scale(1.0 - p.spinDecay());
        }
    }

    public interface Listener {
        boolean onSegment(BallPhysics.State var1, Vec3 var2, Vec3 var3);

        void onBounce(BallPhysics.State var1, Direction var2, BlockPos var3, double var4);
    }

    public static record Params(
        double gravity,
        double drag,
        double magnus,
        double spinDecay,
        double bounceScale,
        double frictionScale,
        double rollScale,
        double settleVerticalSpeed,
        double restSpeed,
        double waterDrag,
        boolean altitude
    ) {
        public static BallPhysics.Params fromConfig() {
            return new BallPhysics.Params(
                (Double)BaseballConfig.GRAVITY.get(),
                (Double)BaseballConfig.DRAG_COEFFICIENT.get(),
                (Double)BaseballConfig.MAGNUS_COEFFICIENT.get(),
                (Double)BaseballConfig.SPIN_DECAY.get(),
                (Double)BaseballConfig.BOUNCE_SCALE.get(),
                (Double)BaseballConfig.FRICTION_SCALE.get(),
                (Double)BaseballConfig.ROLLING_SCALE.get(),
                (Double)BaseballConfig.SETTLE_VERTICAL_SPEED.get(),
                (Double)BaseballConfig.REST_SPEED.get(),
                (Double)BaseballConfig.WATER_DRAG.get(),
                (Boolean)BaseballConfig.ALTITUDE_AFFECTS_CARRY.get()
            );
        }
    }

    public static record Prediction(Vec3 point, int ticks) {
    }

    public static final class State {
        public Vec3 pos = Vec3.ZERO;
        public Vec3 vel = Vec3.ZERO;
        public Vec3 spin = Vec3.ZERO;
        public BallMotion motion = BallMotion.FLYING;
        public boolean inWater;
        public int bounces;
    }

    public static record Surface(String name, double restitution, double friction, double rollConst, double rollLinear) {
    }
}
