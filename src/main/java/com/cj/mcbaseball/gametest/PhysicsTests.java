package com.cj.mcbaseball.gametest;

import com.cj.mcbaseball.MCBaseball;
import com.cj.mcbaseball.entity.BaseballEntity;
import com.cj.mcbaseball.physics.AimSolver;
import com.cj.mcbaseball.physics.BallPhysics;
import com.cj.mcbaseball.physics.BaseballUnits;
import com.cj.mcbaseball.pitching.PitchType;
import com.cj.mcbaseball.registry.ModBlocks;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("mcbaseball")
@PrefixGameTestTemplate(false)
public class PhysicsTests {
    private static final String LANE = "test_lane";

    private static void floor(GameTestHelper h, int z0, int z1, Block b) {
        for (int x = 0; x <= 47; x++) {
            for (int z = z0; z <= z1; z++) {
                h.setBlock(new BlockPos(x, 1, z), b);
            }
        }
    }

    private static BaseballEntity ball(GameTestHelper h, Vec3 rel, Vec3 vel, Vec3 spin) {
        BaseballEntity b = BaseballEntity.create(h.getLevel(), h.absoluteVec(rel));
        b.launch(null, vel);
        b.setSpin(spin);
        h.getLevel().addFreshEntity(b);
        return b;
    }

    @GameTest(
        template = "test_lane",
        timeoutTicks = 120
    )
    public static void topspinSkipsBackspinChecks(GameTestHelper h) {
        floor(h, 0, 4, Blocks.STONE);
        floor(h, 7, 11, Blocks.STONE);
        Vec3 v = new Vec3(0.7, -0.35, 0.0);
        BaseballEntity top = ball(h, new Vec3(2.0, 3.5, 2.0), v, BallPhysics.backspin(v, -20.0));
        BaseballEntity back = ball(h, new Vec3(2.0, 3.5, 9.0), v, BallPhysics.backspin(v, 20.0));
        double x0 = h.absoluteVec(new Vec3(2.0, 0.0, 0.0)).x;
        h.runAtTickTime(40L, () -> {
            double dt = top.getX() - x0;
            double db = back.getX() - x0;
            MCBaseball.LOGGER.info("[GameTest] topspin traveled {} vs backspin {}", String.format("%.2f", dt), String.format("%.2f", db));
            h.assertTrue(dt > db + 1.0, "topspin should run away from backspin: top=" + dt + " back=" + db);
            h.succeed();
        });
    }

    @GameTest(
        template = "test_lane",
        timeoutTicks = 100
    )
    public static void paddingIsDeadStoneIsLively(GameTestHelper h) {
        floor(h, 0, 4, Blocks.STONE);
        floor(h, 7, 11, (Block)ModBlocks.WALL_PADDING.get());
        BaseballEntity onStone = ball(h, new Vec3(10.0, 8.0, 2.0), new Vec3(0.0, -0.6, 0.0), Vec3.ZERO);
        BaseballEntity onPad = ball(h, new Vec3(10.0, 8.0, 9.0), new Vec3(0.0, -0.6, 0.0), Vec3.ZERO);
        double[] maxStone = new double[]{Double.NEGATIVE_INFINITY};
        double[] maxPad = new double[]{Double.NEGATIVE_INFINITY};
        boolean[] bounced = new boolean[]{false, false};
        h.onEachTick(() -> {
            if (onStone.getBounces() > 0) {
                bounced[0] = true;
                maxStone[0] = Math.max(maxStone[0], onStone.getY());
            }

            if (onPad.getBounces() > 0) {
                bounced[1] = true;
                maxPad[0] = Math.max(maxPad[0], onPad.getY());
            }
        });
        h.runAtTickTime(60L, () -> {
            double floorY = h.absoluteVec(new Vec3(0.0, 2.0, 0.0)).y;
            double rs = maxStone[0] - floorY;
            double rp = maxPad[0] - floorY;
            MCBaseball.LOGGER.info("[GameTest] rebound height stone {} vs padding {}", String.format("%.2f", rs), String.format("%.2f", rp));
            h.assertTrue(bounced[0] && bounced[1], "balls never landed");
            h.assertTrue(rs > rp * 3.0 && rs > 0.5, "stone should rebound much higher: stone=" + rs + " pad=" + rp);
            h.succeed();
        });
    }

    @GameTest(
        template = "test_lane",
        timeoutTicks = 20
    )
    public static void flightModelChecks(GameTestHelper h) {
        BallPhysics.Params p = BallPhysics.Params.fromConfig();
        Vec3 start = new Vec3(0.0, 70.0, 0.0);
        Vec3 v = new Vec3(1.2, 0.55, 0.0);
        double back = BallPhysics.predictLanding(start, v, BallPhysics.backspin(v, 12.0), p, 69.0, 400).point().x;
        double none = BallPhysics.predictLanding(start, v, Vec3.ZERO, p, 69.0, 400).point().x;
        double top = BallPhysics.predictLanding(start, v, BallPhysics.backspin(v, -12.0), p, 69.0, 400).point().x;
        h.assertTrue(back > none && none > top, "backspin should carry, topspin dive: " + back + " / " + none + " / " + top);
        double low = BallPhysics.predictLanding(new Vec3(0.0, 64.0, 0.0), v, Vec3.ZERO, p, 63.0, 400).point().x;
        double high = BallPhysics.predictLanding(new Vec3(0.0, 220.0, 0.0), v, Vec3.ZERO, p, 219.0, 400).point().x;
        h.assertTrue(high > low * 1.03, "thin air should carry farther: high=" + high + " low=" + low);
        Vec3 release = new Vec3(15.0, 71.4, 0.0);
        Vec3 target = new Vec3(0.0, 70.9, 0.3);
        double fastDrop = 0.0;
        double curveDrop = 0.0;

        for (PitchType t : PitchType.values()) {
            double speed = BaseballUnits.blocksPerTickFromDisplayMph((double)(t.minMph + t.maxMph) / 2.0);
            Vec3 armSide = new Vec3(0.0, 0.0, -1.0);
            Vec3 brk = armSide.scale(t.armSide).add(0.0, t.vertical, 0.0);
            Function<Vec3, Vec3> spinFor = vel -> BallPhysics.spinForAccel(vel, brk, p);
            Vec3 launch = AimSolver.solve(release, target, speed, spinFor, p);
            AimSolver.Crossing c = AimSolver.toPlane(release, launch, spinFor.apply(launch), p, target, new Vec3(-1.0, 0.0, 0.0), 200);
            h.assertTrue(c != null && c.point().distanceTo(target) < 0.12, t + " missed its target: " + (c == null ? "null" : c.point().distanceTo(target)));
            AimSolver.Crossing straight = AimSolver.toPlane(release, launch, Vec3.ZERO, p, target, new Vec3(-1.0, 0.0, 0.0), 200);
            double drop = straight.point().y - c.point().y;
            if (t == PitchType.FOUR_SEAM) {
                fastDrop = drop;
            }

            if (t == PitchType.CURVEBALL) {
                curveDrop = drop;
            }
        }

        h.assertTrue(fastDrop < 0.0, "four-seam backspin should hold the ball up (rise vs gravity): " + fastDrop);
        h.assertTrue(curveDrop > 0.3, "curveball topspin should drop it a lot: " + curveDrop);
        MCBaseball.LOGGER
            .info(
                "[GameTest] carry back/none/top {}/{}/{}  sea/high {}/{}  4-seam drop {} curve drop {}",
                new Object[]{r(back), r(none), r(top), r(low), r(high), r(fastDrop), r(curveDrop)}
            );
        h.succeed();
    }

    private static String r(double d) {
        return String.format("%.2f", d);
    }
}
