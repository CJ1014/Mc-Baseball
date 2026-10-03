package com.cj.mcbaseball.physics;

import java.util.function.Function;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class AimSolver {
    public static Vec3 solve(Vec3 start, Vec3 target, double speed, Function<Vec3, Vec3> spinFor, BallPhysics.Params p) {
        Vec3 n = new Vec3(target.x - start.x, 0.0, target.z - start.z);
        if (n.lengthSqr() < 1.0E-6) {
            return target.subtract(start).normalize().scale(speed);
        } else {
            n = n.normalize();
            Vec3 v = target.subtract(start).normalize().scale(speed);

            for (int iter = 0; iter < 12; iter++) {
                AimSolver.Crossing c = toPlane(start, v, spinFor.apply(v), p, target, n, 220);
                Vec3 hit = c == null ? start.add(v.scale(60.0)) : c.point();
                double t = c == null ? 60.0 : Math.max(1.0, c.ticks());
                Vec3 err = target.subtract(hit);
                if (err.lengthSqr() < 4.0E-4) {
                    break;
                }

                v = v.add(err.scale(1.0 / t));
                double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
                if (v.y > horiz * 1.4) {
                    v = new Vec3(v.x, horiz * 1.4, v.z);
                }

                v = v.normalize().scale(speed);
            }

            return v;
        }
    }

    @Nullable
    public static AimSolver.Crossing toPlane(Vec3 pos, Vec3 vel, Vec3 spin, BallPhysics.Params p, Vec3 planePoint, Vec3 n, int maxTicks) {
        double d0 = pos.subtract(planePoint).dot(n);
        if (d0 >= 0.0) {
            return new AimSolver.Crossing(pos, 0.0);
        } else {
            BallPhysics.FlightSim f = new BallPhysics.FlightSim(pos, vel, spin);

            for (int t = 1; t <= maxTicks; t++) {
                Vec3 prev = f.pos;
                f.step(p);
                double d1 = f.pos.subtract(planePoint).dot(n);
                if (d1 >= 0.0) {
                    double fr = d0 / (d0 - d1);
                    return new AimSolver.Crossing(prev.add(f.pos.subtract(prev).scale(fr)), (double)(t - 1) + fr);
                }

                if (f.pos.y < planePoint.y - 40.0) {
                    return null;
                }

                d0 = d1;
            }

            return null;
        }
    }

    private AimSolver() {
    }

    public static record Crossing(Vec3 point, double ticks) {
    }
}
