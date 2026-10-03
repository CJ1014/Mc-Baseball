package com.cj.mcbaseball.npc.ai;

import com.cj.mcbaseball.physics.BallMotion;
import com.cj.mcbaseball.physics.BallPhysics;
import net.minecraft.world.phys.Vec3;

public final class InterceptPlanner {
    public static final double CATCH_HEIGHT = 2.3;

    public static InterceptPlanner.Intercept plan(
        Vec3 ballPos, Vec3 ballVel, Vec3 spin, BallMotion motion, double groundY, Vec3 fielder, double speed, int react, BallPhysics.Params p
    ) {
        BallPhysics.FlightSim f = new BallPhysics.FlightSim(ballPos, ballVel, spin);
        boolean rolling = motion != BallMotion.FLYING;
        double floor = groundY + 0.125;
        BallPhysics.Surface ground = BallPhysics.GRASS;
        speed = Math.max(0.05, speed);

        for (int t = 1; t <= 140; t++) {
            if (!rolling) {
                f.step(p);
                if (f.pos.y <= floor && f.vel.y < 0.0) {
                    double e = ground.restitution() * p.bounceScale();
                    double vy = -f.vel.y * e;
                    double keep = 0.7142857142857143;
                    if (vy < p.settleVerticalSpeed()) {
                        vy = 0.0;
                        rolling = true;
                    }

                    f.vel = new Vec3(f.vel.x * keep, vy, f.vel.z * keep);
                    f.pos = new Vec3(f.pos.x, floor, f.pos.z);
                    f.spin = Vec3.ZERO;
                }
            } else {
                double h = Math.sqrt(f.vel.x * f.vel.x + f.vel.z * f.vel.z);
                double nh = Math.max(0.0, h - (ground.rollConst() + ground.rollLinear() * h) * p.rollScale());
                f.vel = h > 1.0E-9 ? new Vec3(f.vel.x / h * nh, 0.0, f.vel.z / h * nh) : Vec3.ZERO;
                f.pos = f.pos.add(f.vel);
            }

            Vec3 pos = f.pos;
            if (pos.y - groundY <= 2.3) {
                double dx = pos.x - fielder.x;
                double dz = pos.z - fielder.z;
                double need = Math.max(0.0, Math.sqrt(dx * dx + dz * dz) - 0.8) / speed + (double)react;
                if (need <= (double)t) {
                    return new InterceptPlanner.Intercept(new Vec3(pos.x, groundY, pos.z), t);
                }
            }

            if (rolling && f.vel.lengthSqr() < 1.0E-5) {
                double dx = pos.x - fielder.x;
                double dz = pos.z - fielder.z;
                int arrive = (int)Math.ceil(Math.sqrt(dx * dx + dz * dz) / speed) + react;
                return new InterceptPlanner.Intercept(new Vec3(pos.x, groundY, pos.z), Math.max(t, arrive));
            }
        }

        Vec3 posx = f.pos;
        double dx = posx.x - fielder.x;
        double dz = posx.z - fielder.z;
        return new InterceptPlanner.Intercept(new Vec3(posx.x, groundY, posx.z), 140 + (int)(Math.sqrt(dx * dx + dz * dz) / speed));
    }

    private InterceptPlanner() {
    }

    public static record Intercept(Vec3 point, int ticks) {
    }
}
