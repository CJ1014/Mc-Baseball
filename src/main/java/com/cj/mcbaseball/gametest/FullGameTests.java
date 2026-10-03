package com.cj.mcbaseball.gametest;

import com.cj.mcbaseball.MCBaseball;
import com.cj.mcbaseball.block.AngledBlockEntity;
import com.cj.mcbaseball.block.FieldControllerBlock;
import com.cj.mcbaseball.field.BuildJob;
import com.cj.mcbaseball.field.FieldBuilder;
import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.field.FieldLayout;
import com.cj.mcbaseball.field.FieldMarker;
import com.cj.mcbaseball.field.FieldPlan;
import com.cj.mcbaseball.field.StadiumBuilder;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.Difficulty;
import com.cj.mcbaseball.game.GameManager;
import com.cj.mcbaseball.game.GamePhase;
import com.cj.mcbaseball.game.GameTeam;
import com.cj.mcbaseball.game.LineupSlot;
import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import com.cj.mcbaseball.pitching.PitchingSystem;
import com.cj.mcbaseball.registry.ModBlocks;
import com.cj.mcbaseball.registry.ModEntities;
import com.cj.mcbaseball.scoreboard.ScoreboardBlockEntity;
import com.cj.mcbaseball.stats.StatLine;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@GameTestHolder("mcbaseball")
@PrefixGameTestTemplate(false)
public class FullGameTests {
    static final BlockPos HOME = new BlockPos(6, 2, 36);

    static FieldControllerBlockEntity buildField(GameTestHelper h) {
        for (int x = 0; x < 80; x++) {
            for (int z = 0; z < 72; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
            }
        }

        BlockPos first = new BlockPos(17, 2, 47);
        BlockPos second = new BlockPos(28, 2, 36);
        BlockPos third = new BlockPos(17, 2, 25);
        BlockPos mound = new BlockPos(16, 2, 36);
        h.setBlock(HOME, (Block)ModBlocks.HOME_PLATE.get());
        h.setBlock(first, (Block)ModBlocks.BASE.get());
        h.setBlock(second, (Block)ModBlocks.BASE.get());
        h.setBlock(third, (Block)ModBlocks.BASE.get());
        h.setBlock(mound, (Block)ModBlocks.PITCHERS_RUBBER.get());
        BlockPos ctrl = new BlockPos(2, 2, 2);
        h.setBlock(ctrl, (Block)ModBlocks.FIELD_CONTROLLER.get());
        FieldControllerBlockEntity be = (FieldControllerBlockEntity)h.getBlockEntity(ctrl);
        be.layout().set(FieldMarker.HOME_PLATE, h.absolutePos(HOME));
        be.layout().set(FieldMarker.FIRST_BASE, h.absolutePos(first));
        be.layout().set(FieldMarker.SECOND_BASE, h.absolutePos(second));
        be.layout().set(FieldMarker.THIRD_BASE, h.absolutePos(third));
        be.layout().set(FieldMarker.PITCHERS_MOUND, h.absolutePos(mound));
        return be;
    }

    @GameTest(
        template = "test_field",
        batch = "fullgame",
        timeoutTicks = 60000
    )
    public static void npcVsNpcGameFinishes(GameTestHelper h) {
        if (System.getenv("MCB_QUICK") != null) {
            h.succeed();
        } else {
            FieldControllerBlockEntity be = buildField(h);
            h.assertTrue(be.layout().isReady(), "field not ready: " + be.layout().problems());
            be.settings().innings = 9;
            be.settings().npcAutoFill = true;
            be.settings().difficulty = Difficulty.NORMAL;
            be.settings().extraInnings = false;
            GameManager.StartResult r = GameManager.tryStart(h.getLevel(), be, null);
            h.assertTrue(r.ok(), "start failed: " + r.message().getString());
            BaseballGame g = GameManager.at(h.getLevel(), be.getBlockPos());
            h.assertTrue(g != null, "no game");
            int[] last = new int[]{-1};
            h.onEachTick(() -> {
                if (g.tick % 400L == 0L && g.tick != (long)last[0]) {
                    last[0] = (int)g.tick;
                    MCBaseball.LOGGER.info("[GameTest] t={} {}", g.tick, g.debugLine());
                }
            });
            h.succeedWhen(
                () -> {
                    h.assertTrue(g.phase == GamePhase.GAME_OVER, "still playing: " + g.debugLine());
                    int pitches = 0;
                    int ab = 0;
                    int h1 = 0;
                    int so = 0;
                    int bb = 0;
                    int po = 0;

                    for (StatLine l : g.stats.lines.values()) {
                        pitches += l.pitches;
                        ab += l.ab;
                        h1 += l.h;
                        so += l.so;
                        bb += l.bb;
                        po += l.po;
                    }

                    MCBaseball.LOGGER
                        .info(
                            "[GameTest] FINAL {} {} - {} {} | innings={} pitches={} AB={} H={} K={} BB={} PO={} errors={}/{}",
                            new Object[]{
                                g.away.abbr(), g.away.runs, g.home.abbr(), g.home.runs, g.inning, pitches, ab, h1, so, bb, po, g.away.errors, g.home.errors
                            }
                        );
                    MCBaseball.LOGGER
                        .info(
                            "[GameTest] counters {} | game length {} ticks = {} real minutes",
                            new Object[]{g.counters, g.tick, String.format("%.1f", (double)g.tick / 1200.0)}
                        );
                    h.assertTrue(pitches > 20, "too few pitches: " + pitches);
                    h.assertTrue(ab >= 50, "too few at-bats: " + ab);
                    h.assertTrue(g.inning >= 9, "ended early at inning " + g.inning);
                    h.assertTrue(g.counters.getOrDefault("play_timeout", 0) == 0, "plays timed out: " + g.counters);
                    assertFair(h, g);
                    GameManager.remove(g);
                }
            );
        }
    }

    @GameTest(
        template = "test_park",
        batch = "builtfield",
        timeoutTicks = 30000
    )
    public static void builtFieldPlaysAGame(GameTestHelper h) {
        if (System.getenv("MCB_QUICK") != null) {
            h.succeed();
        } else {
            BlockPos ctrl = new BlockPos(10, 2, 10);
            h.setBlock(ctrl, (BlockState)((Block)ModBlocks.FIELD_CONTROLLER.get()).defaultBlockState().setValue(FieldControllerBlock.FACING, Direction.WEST));
            FieldControllerBlockEntity be = (FieldControllerBlockEntity)h.getBlockEntity(ctrl);
            Component msg = FieldBuilder.build(h.getLevel(), be, FieldBuilder.Size.STANDARD);
            MCBaseball.LOGGER.info("[GameTest] builder: {}", msg.getString());
            h.assertTrue(be.layout().isReady(), "built field not ready: missing=" + be.layout().missingRequired() + " problems=" + be.layout().problems());
            h.assertTrue(be.layout().outfieldWall().size() >= 10, "wall not marked");
            h.assertTrue(be.scoreboards().size() == 1, "scoreboard not linked");
            be.settings().innings = 3;
            be.settings().extraInnings = false;
            GameManager.StartResult r = GameManager.tryStart(h.getLevel(), be, null);
            h.assertTrue(r.ok(), "start failed: " + r.message().getString());
            BaseballGame g = GameManager.at(h.getLevel(), be.getBlockPos());
            h.succeedWhen(
                () -> {
                    h.assertTrue(g.phase == GamePhase.GAME_OVER, "still playing: " + g.debugLine());
                    MCBaseball.LOGGER
                        .info(
                            "[GameTest] BUILT FIELD FINAL {} {} - {} {} | {}", new Object[]{g.away.abbr(), g.away.runs, g.home.abbr(), g.home.runs, g.counters}
                        );
                    h.assertTrue(g.counters.getOrDefault("play_timeout", 0) == 0, "plays timed out: " + g.counters);
                    assertFair(h, g);
                    GameManager.remove(g);
                }
            );
        }
    }

    static void assertFair(GameTestHelper h, BaseballGame g) {
        h.assertTrue(g.counters.getOrDefault("pitch_batter_not_set", 0) == 0, "pitched to a batter who wasn't in the box: " + g.counters);
        h.assertTrue(g.counters.getOrDefault("pitch_runner_not_set", 0) == 0, "pitched while an NPC runner was off his base: " + g.counters);
    }

    @GameTest(
        template = "test_stadium",
        batch = "water",
        timeoutTicks = 400
    )
    public static void fieldStaysDryNextToWater(GameTestHelper h) {
        BlockPos ctrlRel = new BlockPos(24, 2, 24);
        h.setBlock(ctrlRel, (BlockState)((Block)ModBlocks.FIELD_CONTROLLER.get()).defaultBlockState().setValue(FieldControllerBlock.FACING, Direction.WEST));
        FieldControllerBlockEntity be = (FieldControllerBlockEntity)h.getBlockEntity(ctrlRel);
        FieldPlan plan = new FieldPlan(be.getBlockPos(), Direction.WEST, FieldBuilder.Size.SMALL);

        for (int a = -15; a <= -10; a++) {
            for (int b = 0; b <= 8; b++) {
                h.getLevel().setBlock(plan.at(a, b, plan.y0 - 1), Blocks.STONE.defaultBlockState(), 3);
                h.getLevel().setBlock(plan.at(a, b, plan.y0), Blocks.WATER.defaultBlockState(), 3);
            }
        }

        h.getLevel().setBlock(plan.at(4, 4, plan.y0), Blocks.WATER.defaultBlockState(), 3);

        for (int y = 0; y < 18; y++) {
            h.getLevel().setBlock(plan.at(-11, 3, plan.y0 + y), Blocks.STONE.defaultBlockState(), 3);
        }

        h.getLevel().setBlock(plan.at(-11, 3, plan.y0 + 18), Blocks.WATER.defaultBlockState(), 3);
        FieldBuilder.build(h.getLevel(), be, FieldBuilder.Size.SMALL);
        long start = h.getTick();
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() - start > 100L, "letting water try to flow");
            int wet = 0;

            for (int a = -9; a <= plan.fence; a++) {
                for (int bxx = -9; bxx <= plan.fence; bxx++) {
                    if (FieldBuilder.inFieldFootprint(plan, a, bxx)) {
                        for (int y = plan.y0; y <= plan.y0 + 2; y++) {
                            if (!h.getLevel().getFluidState(plan.at(a, bxx, y)).isEmpty()) {
                                wet++;
                            }
                        }
                    }
                }
            }

            int pond = 0;

            for (int a = -15; a <= -12; a++) {
                for (int bx = 0; bx <= 8; bx++) {
                    if (!h.getLevel().getFluidState(plan.at(a, bx, plan.y0)).isEmpty()) {
                        pond++;
                    }
                }
            }

            MCBaseball.LOGGER.info("[GameTest] WATER: {} wet blocks on the field, {} pond blocks left alone", wet, pond);
            h.assertTrue(wet == 0, wet + " water blocks flooded the field");
            h.assertTrue(pond > 0, "the pond outside the field should not be touched");
            BlockPos spill = plan.at(5, 5, plan.y0);
            h.getLevel().setBlock(spill, Blocks.WATER.defaultBlockState(), 3);
            GameManager.StartResult r = GameManager.tryStart(h.getLevel(), be, null);
            h.getLevel().setBlock(spill, Blocks.AIR.defaultBlockState(), 3);
            MCBaseball.LOGGER.info("[GameTest] WATER start check: ok={} message='{}'", r.ok(), r.message().getString());
            h.assertTrue(!r.ok() && r.message().getString().contains("water"), "game started with water on the infield");
        });
    }

    static void stadiumVariant(GameTestHelper h, String name, StadiumBuilder.Options opts) {
        PitchingSystem.DEBUG = name.equals("NO_STANDS");
        BlockPos kitPos = new BlockPos(24, 2, 24);
        h.setBlock(kitPos, (BlockState)((Block)ModBlocks.FIELD_CONTROLLER.get()).defaultBlockState().setValue(FieldControllerBlock.FACING, Direction.WEST));
        FieldControllerBlockEntity be = (FieldControllerBlockEntity)h.getBlockEntity(kitPos);

        for (int x = -40; x <= 150; x++) {
            for (int z = -40; z <= 150; z++) {
                for (int y = -3; y <= 50; y++) {
                    BlockPos wp = h.absolutePos(new BlockPos(x, y, z));
                    if (!h.getLevel().getFluidState(wp).isEmpty()) {
                        h.getLevel().setBlock(wp, Blocks.AIR.defaultBlockState(), 18);
                    }
                }
            }
        }

        BuildJob job = StadiumBuilder.job(h.getLevel(), be, FieldBuilder.Size.SMALL, null, opts);
        h.assertTrue(job != null, "stadium area not loaded");
        job.runAll();
        h.assertTrue(be.layout().isReady(), "stadium field not ready");
        FieldPlan plan = new FieldPlan(be.getBlockPos(), Direction.WEST, FieldBuilder.Size.SMALL);
        Map<String, Integer> fluids = new TreeMap<>();

        for (int x = -24; x <= 110; x++) {
            for (int z = -24; z <= 110; z++) {
                for (int yx = -2; yx <= 30; yx++) {
                    BlockPos wp = h.absolutePos(new BlockPos(x, yx, z));
                    FluidState fs = h.getLevel().getFluidState(wp);
                    if (!fs.isEmpty()) {
                        String k = "y" + (wp.getY() - plan.y0) + (FieldBuilder.inFieldFootprint(plan, 0, 0) ? "" : "");
                        fluids.merge(k + (fs.isSource() ? " src" : " flow"), 1, Integer::sum);
                    }
                }
            }
        }

        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        MCBaseball.LOGGER.info("[GameTest] FLUIDS {} (structure origin {}, home {}): {}", new Object[]{name, origin, plan.home, fluids});
        BlockPos hp = plan.home;
        StringBuilder col = new StringBuilder();

        for (int f = 1; f <= 8; f++) {
            BlockPos c = plan.at(f, f, plan.y0);
            col.append(" ")
                .append(f)
                .append(":")
                .append(ForgeRegistries.BLOCKS.getKey(h.getLevel().getBlockState(c).getBlock()))
                .append("/")
                .append(ForgeRegistries.BLOCKS.getKey(h.getLevel().getBlockState(c.above()).getBlock()));
        }

        MCBaseball.LOGGER.info("[GameTest] LANE {} home->mound blocks at y0/y0+1:{}", name, col);
        if (opts.extras()) {
            h.assertTrue(be.scoreboards().size() == 2, "video board not linked");
        }

        be.settings().innings = 3;
        be.settings().extraInnings = false;
        GameManager.StartResult r = GameManager.tryStart(h.getLevel(), be, null);
        h.assertTrue(r.ok(), "start failed: " + r.message().getString());
        BaseballGame g = GameManager.at(h.getLevel(), be.getBlockPos());
        h.succeedWhen(() -> {
            int calls = 0;

            for (Entry<String, Integer> e : g.counters.entrySet()) {
                if (e.getKey().startsWith("call_") || e.getKey().equals("in_play") || e.getKey().startsWith("foul")) {
                    calls += e.getValue();
                }
            }

            h.assertTrue(calls >= 30 || g.phase == GamePhase.GAME_OVER, "only " + calls + " pitches so far");
            int inPlay = g.counters.getOrDefault("in_play", 0);
            if (GameManager.get(g.id) != null) {
                MCBaseball.LOGGER.info("[GameTest] VARIANT {} | {}", name, g.counters);
                GameManager.remove(g);
            }

            assertFair(h, g);
            h.assertTrue(inPlay >= 5, name + ": balls are not being put in play: " + g.counters);
        });
    }

    @GameTest(
        template = "test_stadium",
        batch = "stadium",
        timeoutTicks = 20000
    )
    public static void stadiumFull(GameTestHelper h) {
        stadiumVariant(h, "FULL", StadiumBuilder.Options.ALL);
    }

    @GameTest(
        template = "test_stadium",
        batch = "stadium",
        timeoutTicks = 20000
    )
    public static void stadiumNoLights(GameTestHelper h) {
        stadiumVariant(h, "NO_LIGHTS", new StadiumBuilder.Options(true, false, true));
    }

    @GameTest(
        template = "test_stadium",
        batch = "stadium",
        timeoutTicks = 20000
    )
    public static void stadiumNoStands(GameTestHelper h) {
        stadiumVariant(h, "NO_STANDS", new StadiumBuilder.Options(false, true, true));
    }

    @GameTest(
        template = "test_field",
        batch = "calibration",
        timeoutTicks = 300
    )
    public static void npcSpeedCalibration(GameTestHelper h) {
        for (int x = 0; x < 80; x++) {
            for (int z = 30; z < 40; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
            }
        }

        BaseballPlayerEntity n = (BaseballPlayerEntity)((EntityType)ModEntities.BASEBALL_PLAYER.get()).create(h.getLevel());
        Vec3 start = h.absoluteVec(new Vec3(4.5, 2.0, 35.5));
        n.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
        h.getLevel().addFreshEntity(n);
        n.moveTo(h.absoluteVec(new Vec3(60.5, 2.0, 35.5)), 1.0);
        long[] t0 = new long[]{-1L};
        double[] x0 = new double[]{0.0};
        h.onEachTick(() -> {
            if (h.getTick() == 40L) {
                t0[0] = 40L;
                x0[0] = n.getX();
            }
        });
        h.runAtTickTime(
            120L,
            () -> {
                double measured = (n.getX() - x0[0]) / 80.0;
                MCBaseball.LOGGER
                    .info(
                        "[GameTest] NPC speed: measured {} b/t, model predicts {} b/t (attr {})",
                        new Object[]{measured, n.runSpeed(), n.getAttributeValue(Attributes.MOVEMENT_SPEED)}
                    );
                h.assertTrue(measured > 0.1, "NPC barely moved: " + measured);
                n.discard();
                h.succeed();
            }
        );
    }

    static void calib(GameTestHelper h, double scale) {
        if (System.getenv("MCB_CALIB") == null) {
            h.succeed();
        } else {
            BlockPos ctrl = new BlockPos(24, 2, 24);
            h.setBlock(ctrl, (BlockState)((Block)ModBlocks.FIELD_CONTROLLER.get()).defaultBlockState().setValue(FieldControllerBlock.FACING, Direction.WEST));
            FieldControllerBlockEntity be = (FieldControllerBlockEntity)h.getBlockEntity(ctrl);
            FieldBuilder.build(h.getLevel(), be, FieldBuilder.Size.STANDARD);
            be.settings().innings = 3;
            be.settings().extraInnings = false;
            GameManager.StartResult r = GameManager.tryStart(h.getLevel(), be, null);
            h.assertTrue(r.ok(), "start failed");
            BaseballGame g = GameManager.at(h.getLevel(), be.getBlockPos());
            g.npcBatErrScale = scale;
            h.succeedWhen(
                () -> {
                    h.assertTrue(g.phase == GamePhase.GAME_OVER, "playing: inning " + g.inning + " tick " + g.tick + " " + g.debugLine() + " " + g.counters);
                    if (GameManager.get(g.id) != null) {
                        int ab = 0;
                        int h1 = 0;
                        int so = 0;
                        int bb = 0;

                        for (StatLine l : g.stats.lines.values()) {
                            ab += l.ab;
                            h1 += l.h;
                            so += l.so;
                            bb += l.bb;
                        }

                        MCBaseball.LOGGER
                            .info(
                                "[GameTest] CALIB scale={} AB={} H={} K={} BB={} HR={}",
                                new Object[]{scale, ab, h1, so, bb, g.counters.getOrDefault("home_run", 0)}
                            );
                        GameManager.remove(g);
                    }
                }
            );
        }
    }

    @GameTest(
        template = "test_stadium",
        batch = "zcalib1",
        timeoutTicks = 60000
    )
    public static void calibA1(GameTestHelper h) {
        calib(h, 1.0);
    }

    @GameTest(
        template = "test_stadium",
        batch = "zcalib1",
        timeoutTicks = 60000
    )
    public static void calibA2(GameTestHelper h) {
        calib(h, 1.0);
    }

    @GameTest(
        template = "test_stadium",
        batch = "zcalib2",
        timeoutTicks = 60000
    )
    public static void calibA3(GameTestHelper h) {
        calib(h, 1.0);
    }

    @GameTest(
        template = "test_stadium",
        batch = "zcalib2",
        timeoutTicks = 60000
    )
    public static void calibA4(GameTestHelper h) {
        calib(h, 1.0);
    }

    @GameTest(
        template = "test_stadium",
        batch = "angles"
    )
    public static void rotationMathMatchesRenderers(GameTestHelper h) {
        for (int r = 0; r < 16; r++) {
            float yaw = (float)r * 22.5F;
            double ex = -Math.sin(Math.toRadians((double)yaw));
            double ez = Math.cos(Math.toRadians((double)yaw));
            Vector3f a = new Quaternionf().rotationY((float)Math.toRadians((double)(180.0F - yaw))).transform(new Vector3f(0.0F, 0.0F, -1.0F));
            Vector3f b = new Quaternionf().rotationY((float)Math.toRadians((double)(-yaw))).transform(new Vector3f(0.0F, 0.0F, 1.0F));
            h.assertTrue(Math.abs((double)a.x - ex) < 1.0E-4 && Math.abs((double)a.z - ez) < 1.0E-4, "model rotation wrong at yaw " + yaw + ": " + a);
            h.assertTrue(Math.abs((double)b.x - ex) < 1.0E-4 && Math.abs((double)b.z - ez) < 1.0E-4, "text rotation wrong at yaw " + yaw + ": " + b);
        }

        h.succeed();
    }

    static Vec3 front(float yaw) {
        return new Vec3(-Math.sin(Math.toRadians((double)yaw)), 0.0, Math.cos(Math.toRadians((double)yaw)));
    }

    static double alignment(float yaw, BlockPos from, BlockPos to) {
        Vec3 d = Vec3.atCenterOf(to).subtract(Vec3.atCenterOf(from));
        return front(yaw).dot(new Vec3(d.x, 0.0, d.z).normalize());
    }

    @GameTest(
        template = "test_stadium",
        batch = "aim",
        timeoutTicks = 2000
    )
    public static void fieldBlocksAimedAndDugoutsLinedUp(GameTestHelper h) {
        BlockPos ctrl = new BlockPos(24, 2, 24);
        h.setBlock(ctrl, (BlockState)((Block)ModBlocks.FIELD_CONTROLLER.get()).defaultBlockState().setValue(FieldControllerBlock.FACING, Direction.WEST));
        FieldControllerBlockEntity be = (FieldControllerBlockEntity)h.getBlockEntity(ctrl);
        FieldBuilder.build(h.getLevel(), be, FieldBuilder.Size.STANDARD);
        FieldLayout layout = be.layout();
        BlockPos home = layout.get(FieldMarker.HOME_PLATE);
        BlockPos mound = layout.get(FieldMarker.PITCHERS_MOUND);
        AngledBlockEntity plate = (AngledBlockEntity)h.getLevel().getBlockEntity(home);
        AngledBlockEntity rubber = (AngledBlockEntity)h.getLevel().getBlockEntity(mound);
        ScoreboardBlockEntity sb = (ScoreboardBlockEntity)h.getLevel().getBlockEntity(be.scoreboards().get(0));
        double ap = alignment(plate.yaw(), home, mound);
        double ar = alignment(rubber.yaw(), mound, home);
        double as = alignment(sb.yaw(), be.scoreboards().get(0), home);
        MCBaseball.LOGGER
            .info(
                "[GameTest] AIM plate yaw={} ({}), rubber yaw={} ({}), scoreboard yaw={} ({})",
                new Object[]{plate.yaw(), String.format("%.4f", ap), rubber.yaw(), String.format("%.4f", ar), sb.yaw(), String.format("%.4f", as)}
            );
        h.assertTrue(ap > 0.999, "home plate not pointing at the mound: " + ap);
        h.assertTrue(ar > 0.999, "rubber not pointing at home: " + ar);
        h.assertTrue(as > 0.999, "scoreboard not facing home: " + as);
        be.settings().innings = 3;
        GameManager.StartResult r = GameManager.tryStart(h.getLevel(), be, null);
        h.assertTrue(r.ok(), "start failed");
        BaseballGame g = GameManager.at(h.getLevel(), be.getBlockPos());
        long t0 = h.getTick();
        h.succeedWhen(
            () -> {
                h.assertTrue(h.getTick() - t0 > 40L, "settling");
                GameTeam team = g.offense();
                Vec3 line = g.geo.dugoutLine(team.side);
                int checked = 0;
                double worst = 0.0;

                for (int i = 0; i < team.order.size(); i++) {
                    LineupSlot s = team.order.get(i);
                    if (s != g.batter && !g.isOnBaseSlot(s) && g.npc(s) != null) {
                        double d = BaseballGame.horizDist(g.npc(s).position(), g.geo.dugoutSpot(team.side, i, team.order.size()));
                        worst = Math.max(worst, d);
                        checked++;
                    }
                }

                Vec3 s0 = g.geo.dugoutSpot(team.side, 0, 9);
                Vec3 s8 = g.geo.dugoutSpot(team.side, 8, 9);
                Vec3 rowDir = s8.subtract(s0).normalize();
                MCBaseball.LOGGER
                    .info(
                        "[GameTest] DUGOUT {} players checked, worst off-spot {}, row parallel to foul line {}",
                        new Object[]{checked, String.format("%.2f", worst), String.format("%.4f", Math.abs(rowDir.dot(line)))}
                    );
                h.assertTrue(checked >= 7, "not enough offense players in the dugout: " + checked);
                h.assertTrue(worst < 1.3, "a player isn't standing at their dugout spot: " + worst);
                h.assertTrue(Math.abs(rowDir.dot(line)) > 0.999, "dugout row isn't parallel to the foul line");
                GameManager.remove(g);
            }
        );
    }
}
