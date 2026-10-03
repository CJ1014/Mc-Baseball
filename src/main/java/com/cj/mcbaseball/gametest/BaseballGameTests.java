package com.cj.mcbaseball.gametest;

import com.cj.mcbaseball.entity.BaseballEntity;
import com.cj.mcbaseball.field.FieldAutoDetector;
import com.cj.mcbaseball.field.FieldLayout;
import com.cj.mcbaseball.field.FieldMarker;
import com.cj.mcbaseball.physics.BallMotion;
import com.cj.mcbaseball.physics.BallPhysics;
import com.cj.mcbaseball.registry.ModBlocks;
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
public class BaseballGameTests {
    private static final String LANE = "test_lane";

    private static void floor(GameTestHelper h, int x0, int x1, int z0, int z1, Block block) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                h.setBlock(new BlockPos(x, 1, z), block);
            }
        }
    }

    private static BaseballEntity spawnBall(GameTestHelper h, Vec3 relCenter, Vec3 vel) {
        BaseballEntity ball = BaseballEntity.create(h.getLevel(), h.absoluteVec(relCenter));
        ball.launch(null, vel);
        h.getLevel().addFreshEntity(ball);
        return ball;
    }

    @GameTest(
        template = "test_lane",
        timeoutTicks = 400
    )
    public static void ballSettlesOnGround(GameTestHelper h) {
        floor(h, 0, 47, 0, 11, Blocks.STONE);
        BaseballEntity ball = spawnBall(h, new Vec3(3.0, 5.0, 6.0), new Vec3(0.35, 0.2, 0.0));
        double floorTop = h.absoluteVec(new Vec3(0.0, 2.0, 0.0)).y;
        h.succeedWhen(() -> {
            h.assertTrue(ball.isAlive(), "ball vanished");
            h.assertTrue(ball.getMotion() == BallMotion.RESTING, "ball not resting yet: " + ball.getMotion());
            h.assertTrue(ball.getY() >= floorTop - 0.01, "ball sank into floor: y=" + ball.getY() + " floorTop=" + floorTop);
            h.assertTrue(ball.getBounces() >= 1, "ball never bounced");
        });
    }

    @GameTest(
        template = "test_lane",
        timeoutTicks = 100
    )
    public static void fastBallDoesNotTunnel(GameTestHelper h) {
        floor(h, 0, 47, 0, 11, Blocks.STONE);

        for (int y = 2; y <= 6; y++) {
            for (int z = 0; z <= 11; z++) {
                h.setBlock(new BlockPos(20, y, z), Blocks.STONE);
            }
        }

        double wallX = h.absoluteVec(new Vec3(20.0, 0.0, 0.0)).x;
        BaseballEntity ball = spawnBall(h, new Vec3(3.0, 4.0, 6.0), new Vec3(3.0, 0.0, 0.0));
        h.succeedWhen(() -> {
            h.assertTrue(ball.isAlive(), "ball vanished");
            h.assertTrue(ball.getX() < wallX, "ball tunnelled through the wall: x=" + ball.getX());
            h.assertTrue(ball.getBounces() >= 1, "ball hasn't hit the wall yet");
            h.assertTrue(ball.getDeltaMovement().x < 0.0, "ball didn't rebound");
        });
    }

    @GameTest(
        template = "test_lane",
        timeoutTicks = 400
    )
    public static void iceRollsFartherThanStone(GameTestHelper h) {
        floor(h, 0, 47, 0, 4, Blocks.STONE);
        floor(h, 0, 47, 7, 11, Blocks.PACKED_ICE);
        double y = 2.135;
        BaseballEntity stone = spawnBall(h, new Vec3(2.0, y, 2.0), new Vec3(0.4, 0.0, 0.0));
        BaseballEntity ice = spawnBall(h, new Vec3(2.0, y, 9.0), new Vec3(0.4, 0.0, 0.0));
        double startX = h.absoluteVec(new Vec3(2.0, 0.0, 0.0)).x;
        h.succeedWhen(() -> {
            h.assertTrue(stone.getMotion() == BallMotion.RESTING, "stone ball still moving");
            double ds = stone.getX() - startX;
            double di = ice.getX() - startX;
            h.assertTrue(ds > 2.0, "stone ball barely rolled: " + ds);
            h.assertTrue(di > ds * 2.0, "ice ball should roll much farther: ice=" + di + " stone=" + ds);
        });
    }

    @GameTest(
        template = "test_lane",
        timeoutTicks = 20
    )
    public static void autoDetectAssignsBases(GameTestHelper h) {
        floor(h, 0, 47, 0, 11, Blocks.GRASS_BLOCK);
        BlockPos home = new BlockPos(2, 2, 6);
        BlockPos mound = new BlockPos(10, 2, 6);
        BlockPos second = new BlockPos(18, 2, 6);
        BlockPos first = new BlockPos(10, 2, 11);
        BlockPos third = new BlockPos(10, 2, 1);
        h.setBlock(home, (Block)ModBlocks.HOME_PLATE.get());
        h.setBlock(mound, (Block)ModBlocks.PITCHERS_RUBBER.get());
        h.setBlock(second, (Block)ModBlocks.BASE.get());
        h.setBlock(first, (Block)ModBlocks.BASE.get());
        h.setBlock(third, (Block)ModBlocks.BASE.get());
        FieldAutoDetector.Result r = FieldAutoDetector.scan(h.getLevel(), h.absolutePos(new BlockPos(6, 2, 6)), 20, 4);
        h.assertTrue(h.absolutePos(home).equals(r.home()), "home wrong: " + r.home());
        h.assertTrue(h.absolutePos(mound).equals(r.mound()), "mound wrong: " + r.mound());
        h.assertTrue(h.absolutePos(second).equals(r.second()), "second wrong: " + r.second());
        h.assertTrue(h.absolutePos(first).equals(r.first()), "first wrong: " + r.first());
        h.assertTrue(h.absolutePos(third).equals(r.third()), "third wrong: " + r.third());
        FieldLayout layout = new FieldLayout();
        layout.set(FieldMarker.HOME_PLATE, r.home());
        layout.set(FieldMarker.PITCHERS_MOUND, r.mound());
        layout.set(FieldMarker.FIRST_BASE, r.first());
        layout.set(FieldMarker.SECOND_BASE, r.second());
        layout.set(FieldMarker.THIRD_BASE, r.third());
        h.assertTrue(layout.isReady(), "detected field should be READY but problems=" + layout.problems());
        layout.set(FieldMarker.FIRST_BASE, r.third());
        layout.set(FieldMarker.THIRD_BASE, r.first());
        h.assertTrue(!layout.isReady() && !layout.problems().isEmpty(), "swapped bases were not detected");
        h.succeed();
    }

    @GameTest(
        template = "test_lane",
        timeoutTicks = 200
    )
    public static void landingPredictionMatchesFlight(GameTestHelper h) {
        floor(h, 0, 47, 0, 11, Blocks.STONE);
        Vec3 start = new Vec3(2.0, 3.0, 6.0);
        Vec3 vel = new Vec3(0.55, 0.45, 0.0);
        double groundY = h.absoluteVec(new Vec3(0.0, 2.0, 0.0)).y;
        BallPhysics.Prediction p = BallPhysics.predictLanding(h.absoluteVec(start), vel, Vec3.ZERO, BallPhysics.Params.fromConfig(), groundY, 200);
        h.assertTrue(p != null, "no prediction");
        BaseballEntity ball = spawnBall(h, start, vel);
        h.runAtTickTime(h.getTick() + (long)p.ticks(), () -> {
            double err = ball.getCenter().subtract(p.point()).horizontalDistance();
            h.assertTrue(err < 1.0, "prediction off by " + err + " blocks");
            h.succeed();
        });
    }
}
