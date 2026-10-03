package com.cj.mcbaseball.pitching;

import com.cj.mcbaseball.MCBaseball;
import com.cj.mcbaseball.anim.ThrowKind;
import com.cj.mcbaseball.batting.BattingSystem;
import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.entity.BaseballEntity;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.FieldGeometry;
import com.cj.mcbaseball.game.GameKit;
import com.cj.mcbaseball.game.GameMessages;
import com.cj.mcbaseball.game.LineupSlot;
import com.cj.mcbaseball.game.Position;
import com.cj.mcbaseball.network.ThrowAnimPacket;
import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import com.cj.mcbaseball.npc.NpcAnim;
import com.cj.mcbaseball.physics.AimSolver;
import com.cj.mcbaseball.physics.BallPhysics;
import com.cj.mcbaseball.physics.BaseballUnits;
import com.cj.mcbaseball.registry.ModSounds;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public final class PitchingSystem {
    public static boolean DEBUG = false;
    public static final double METER_SWEET = 0.88;

    public static double meterValue(int held) {
        double t = (double)held / (double)((Integer)BaseballConfig.PITCH_METER_TICKS.get()).intValue();
        return t <= 1.0 ? t : Math.max(0.0, 2.0 - t);
    }

    public static double meterQuality(double m) {
        return Mth.clamp(1.0 - Math.abs(m - 0.88) / 0.45, 0.0, 1.0);
    }

    public static void releasePitch(
        BaseballGame g, LineupSlot ps, LivingEntity pitcher, PitchType type, Vec3 target, double quality, int velocityRating, int stuffRating
    ) {
        FieldGeometry geo = g.geo;
        Vec3 toHome = geo.forward.reverse();
        Vec3 armSide = ps.throwsRight() ? geo.right.reverse() : geo.right;
        Vec3 release = new Vec3(pitcher.getX(), pitcher.getEyeY() - 0.25, pitcher.getZ())
            .add(armSide.scale(0.35))
            .add(toHome.scale(0.6));
        double mph = Mth.lerp((double)velocityRating / 99.0, (double)type.minMph, (double)type.maxMph)
            * (0.94 + 0.06 * quality)
            * (Double)BaseballConfig.PITCH_SPEED_SCALE.get();
        double speed = BaseballUnits.blocksPerTickFromDisplayMph(mph);
        double breakScale = (Double)BaseballConfig.PITCH_BREAK_SCALE.get() * (0.8 + (double)stuffRating / 99.0 * 0.4);
        Vec3 brk = armSide.scale(type.armSide * breakScale).add(0.0, type.vertical * breakScale, 0.0);
        double miss = (1.0 - quality) * (Double)BaseballConfig.PITCH_MAX_MISS.get();
        Vec3 aim = target.add(geo.right.scale(g.rng.nextGaussian() * miss * 0.7)).add(0.0, g.rng.nextGaussian() * miss * 0.6, 0.0);
        BallPhysics.Params params = BallPhysics.Params.fromConfig();
        Function<Vec3, Vec3> spinFor = vel -> BallPhysics.spinForAccel(vel, brk, params);
        Vec3 v = AimSolver.solve(release, aim, speed, spinFor, params);
        g.clearHolders();
        BaseballEntity ball = BaseballEntity.create(g.level, release);
        ball.setGame(g.id);
        ball.launch(pitcher, v);
        ball.setSpin(spinFor.apply(v));
        g.level.addFreshEntity(ball);
        g.setLooseBall(ball);
        if (g.batter != null
            && g.actor(g.batter) instanceof BaseballPlayerEntity bn
            && BaseballGame.horizDist(bn.position(), g.geo.batterBox(g.batter.batsRight())) > 0.8) {
            g.count("pitch_batter_not_set");
        }

        for (int b = 1; b <= 3; b++) {
            LineupSlot r = g.onBase[b];
            if (r != null) {
                LivingEntity var30 = g.actor(r);
                if (var30 instanceof BaseballPlayerEntity) {
                    BaseballPlayerEntity rn = (BaseballPlayerEntity)var30;
                    if (BaseballGame.horizDist(rn.position(), g.geo.base(b)) > 2.0) {
                        g.count("pitch_runner_not_set");
                    }
                }
            }
        }

        if (DEBUG && g.counters.getOrDefault("dbg_release", 0) < 4) {
            g.count("dbg_release");
            MCBaseball.LOGGER
                .info(
                    "[PitchDebug] RELEASE by {} {} pitcher@field(fwd,up,lat)=({},{},{}) release fwd={} vel(fwd,up,lat)=({},{},{}) target fwd={} moundFwd={}",
                    new Object[]{
                        ps.side,
                        ps.position.abbr,
                        String.format("%.2f", geo.planeDistance(pitcher.position())),
                        String.format("%.2f", pitcher.getY() - geo.groundY),
                        String.format("%.2f", geo.lateral(pitcher.position())),
                        String.format("%.2f", geo.planeDistance(release)),
                        String.format("%.3f", v.dot(geo.forward)),
                        String.format("%.3f", v.y),
                        String.format("%.3f", v.dot(geo.right)),
                        String.format("%.2f", geo.planeDistance(target)),
                        String.format("%.2f", geo.planeDistance(geo.mound))
                    }
                );
        }

        if (pitcher instanceof ServerPlayer sp) {
            ThrowAnimPacket.broadcast(sp, ThrowKind.of(type));
        }

        PitchRecord pr = new PitchRecord(type, mph, g.tick, ps, g.batter);
        pr.lastCenter = release;
        pr.lastHomeDist = geo.planeDistance(release);
        pr.lastContactDist = pr.lastHomeDist - 0.35;
        g.pitch = pr;
        g.stats.of(ps).pitches++;
        if (pitcher instanceof BaseballPlayerEntity n) {
            n.holdBall(false);
        }

        g.ai.onPitchReleased(pr);
        g.net.dirty();
    }

    public static PitchingSystem.HumanResult humanRelease(BaseballGame g, ServerPlayer p, LineupSlot s, int held) {
        FieldGeometry geo = g.geo;
        Vec3 look = p.getLookAngle();
        double towardHome = look.dot(geo.forward.reverse());
        if (towardHome < 0.5) {
            return PitchingSystem.HumanResult.NOT_A_PITCH;
        } else if (g.phaseTicks >= (Integer)BaseballConfig.PITCH_READY_TICKS.get() && g.batter != null && g.batterSet()) {
            Vec3 eye = p.getEyePosition();
            double lf = look.dot(geo.forward);
            double t = -geo.planeDistance(eye) / lf;
            Vec3 hit = eye.add(look.scale(t));
            double lat = Mth.clamp(geo.lateral(hit), -1.6, 1.6);
            double h = Mth.clamp(hit.y - geo.groundY, 0.1, 2.3);
            Vec3 target = geo.zonePoint(lat, h);
            double m = meterValue(held);
            double q = meterQuality(m);
            PitchType type = g.humanPitchType(p.getUUID());
            releasePitch(g, s, p, type, target, q, 75, 70);
            String key = q > 0.9
                ? "mcbaseball.feedback.release_perfect"
                : (q > 0.6 ? "mcbaseball.feedback.release_good" : (q > 0.3 ? "mcbaseball.feedback.release_off" : "mcbaseball.feedback.release_wild"));
            g.net.personal(p, GameMessages.Kind.TIMING, Component.translatable(key));
            return PitchingSystem.HumanResult.PITCHED;
        } else {
            g.net.personal(p, GameMessages.Kind.HINT, Component.translatable("mcbaseball.hint.wait_batter"));
            return PitchingSystem.HumanResult.TOO_EARLY;
        }
    }

    public static void tick(BaseballGame g) {
        PitchRecord pr = g.pitch;
        if (pr == null) {
            tickWaiting(g);
        } else if (!pr.resolved) {
            track(g, pr);
            if (g.npcSwing != null && g.tick >= g.npcSwing.tick()) {
                BattingSystem.NpcSwing plan = g.npcSwing;
                g.npcSwing = null;
                BattingSystem.npcSwing(g, plan);
            }

            BattingSystem.applyPending(g);
            if (!pr.contact && !pr.resolved && g.pitch == pr) {
                npcCatcher(g, pr);
                int lag = (Integer)BaseballConfig.MAX_LAG_COMPENSATION_TICKS.get();
                boolean ballGone = g.ball == null || !g.ball.isAlive();
                if (pr.crossed) {
                    boolean done = pr.catcherCaught || ballGone || (double)g.tick > pr.crossTick + 20.0;
                    if (done && (double)g.tick >= pr.crossTick + (double)lag + 1.0 && g.pendingContact == null) {
                        resolvePitch(g);
                    }
                } else if (pr.catcherCaught && g.tick >= pr.releaseTick + (long)lag + 30L) {
                    resolvePitch(g);
                } else if (g.tick > pr.releaseTick + 80L || ballGone && !pr.catcherCaught && g.pendingContact == null) {
                    resolvePitch(g);
                }
            }
        }
    }

    private static void tickWaiting(BaseballGame g) {
        LineupSlot ps = g.pitcherSlot();
        if (ps != null && ps.isHuman() && g.holder == ps) {
            long idle = g.tick - g.humanPitcherReadySince;
            if (idle == 900L && g.actor(ps) instanceof ServerPlayer p) {
                g.net.personal(p, GameMessages.Kind.HINT, Component.translatable("mcbaseball.hint.pitch_soon"));
            }

            if (idle > 1200L && g.actor(ps) instanceof ServerPlayer p) {
                PitchingSystem.GameKitBridge.removeBall(g, p);
                releasePitch(g, ps, p, PitchType.FOUR_SEAM, g.geo.zonePoint(0.0, 0.9), 0.6, 60, 50);
            }
        }
    }

    public static void trackNow(BaseballGame g) {
        if (g.pitch != null) {
            track(g, g.pitch);
        }
    }

    private static void track(BaseballGame g, PitchRecord pr) {
        BaseballEntity b = g.ball;
        if (b != null && b.isAlive() && !pr.contact) {
            FieldGeometry geo = g.geo;
            Vec3 c = b.getCenter();
            double dHome = geo.planeDistance(c);
            double dContact = dHome - 0.35;
            if (pr.lastCenter != null) {
                if (!pr.contactCrossed && pr.lastContactDist > 0.0 && dContact <= 0.0) {
                    double f = pr.lastContactDist / (pr.lastContactDist - dContact);
                    pr.contactCrossed = true;
                    pr.contactPoint = pr.lastCenter.add(c.subtract(pr.lastCenter).scale(f));
                    pr.contactTick = (double)(g.tick - 1L) + f;
                }

                if (!pr.crossed && pr.lastHomeDist > 0.0 && dHome <= 0.0) {
                    double f = pr.lastHomeDist / (pr.lastHomeDist - dHome);
                    pr.crossed = true;
                    pr.crossPoint = pr.lastCenter.add(c.subtract(pr.lastCenter).scale(f));
                    pr.crossTick = (double)(g.tick - 1L) + f;
                    pr.inZone = geo.inStrikeZone(pr.crossPoint);
                    Component speed = Component.literal(Math.round(pr.mph) + " MPH ")
                        .withStyle(ChatFormatting.WHITE)
                        .append(pr.type.displayName().copy().withStyle(ChatFormatting.GRAY));
                    g.net.toRoles(GameMessages.Kind.PITCH_SPEED, speed);
                }
            }

            pr.lastCenter = c;
            pr.lastHomeDist = dHome;
            pr.lastContactDist = dContact;
        }
    }

    private static void npcCatcher(BaseballGame g, PitchRecord pr) {
        BaseballEntity b = g.ball;
        if (b != null && b.isAlive() && pr.crossed && !pr.catcherCaught) {
            LineupSlot cs = g.defense().at(Position.CATCHER);
            if (cs != null && cs.usesNpc()) {
                LivingEntity ca = g.actor(cs);
                if (ca != null) {
                    Vec3 c = b.getCenter();
                    double dc = g.geo.planeDistance(c) - g.geo.planeDistance(ca.position());
                    if (!(dc > 0.4)) {
                        double lat = Math.abs(g.geo.lateral(c) - g.geo.lateral(ca.position()));
                        double h = c.y - ca.getY();
                        if (lat < 1.8 && h > -0.4 && h < 2.7) {
                            pr.catcherCaught = true;
                            b.discard();
                            g.ball = null;
                            g.holder = cs;
                            if (ca instanceof BaseballPlayerEntity n) {
                                n.holdBall(true);
                            }

                            g.level
                                .playSound(
                                    null, ca.getX(), ca.getY(), ca.getZ(), (SoundEvent)ModSounds.GLOVE_CATCH.get(), SoundSource.PLAYERS, 1.2F, 0.9F
                                );
                        }
                    }
                }
            }
        }
    }

    public static void resolvePitch(BaseballGame g) {
        PitchRecord pr = g.pitch;
        if (pr != null && !pr.resolved && !pr.contact) {
            pr.resolved = true;
            if (!pr.crossed && pr.lastCenter != null) {
                pr.inZone = false;
            }

            PitchingSystem.Call call = pr.hbp
                ? PitchingSystem.Call.HIT_BY_PITCH
                : (pr.swung ? PitchingSystem.Call.STRIKE_SWINGING : (pr.inZone ? PitchingSystem.Call.STRIKE_LOOKING : PitchingSystem.Call.BALL));
            if (DEBUG && !pr.crossed && g.ball != null && g.ball.isAlive() && g.counters.getOrDefault("dbg_dead", 0) < 2) {
                g.count("dbg_dead");
                BaseballEntity b = g.ball;
                StringBuilder sb = new StringBuilder();
                BlockPos c = b.blockPosition();

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockState st = g.level.getBlockState(c.offset(dx, dy, dz));
                            if (!st.isAir()) {
                                sb.append(" [")
                                    .append(dx)
                                    .append(',')
                                    .append(dy)
                                    .append(',')
                                    .append(dz)
                                    .append(' ')
                                    .append(ForgeRegistries.BLOCKS.getKey(st.getBlock()))
                                    .append(']');
                            }
                        }
                    }
                }

                for (Entity e : g.level.getEntities(b, b.getBoundingBox().inflate(2.5))) {
                    sb.append(" {entity ")
                        .append(e.getType().toShortString())
                        .append(" @ ")
                        .append(String.format("%.1f,%.1f,%.1f", e.getX() - b.getX(), e.getY() - b.getY(), e.getZ() - b.getZ()))
                        .append('}');
                }

                MCBaseball.LOGGER
                    .info("[PitchDebug] DEAD BALL at {} motion={} bounces={} around:{}", new Object[]{b.blockPosition(), b.getMotion(), b.getBounces(), sb});
            }

            if (DEBUG && g.counters.getOrDefault("dbg_pitch", 0) < 6) {
                g.count("dbg_pitch");
                MCBaseball.LOGGER
                    .info(
                        "[PitchDebug] call={} crossed={} contactCrossed={} inZone={} swung={} caught={} cross={} lastCenter={} relLast(fwd,up,lat)={} age={}",
                        new Object[]{
                            call,
                            pr.crossed,
                            pr.contactCrossed,
                            pr.inZone,
                            pr.swung,
                            pr.catcherCaught,
                            pr.crossPoint,
                            pr.lastCenter,
                            pr.lastCenter == null
                                ? "-"
                                : String.format(
                                    "(%.2f,%.2f,%.2f)",
                                    g.geo.planeDistance(pr.lastCenter),
                                    pr.lastCenter.y - g.geo.groundY,
                                    g.geo.lateral(pr.lastCenter)
                                ),
                            g.tick - pr.releaseTick
                        }
                    );
            }

            boolean caught = pr.catcherCaught;
            LineupSlot catcher = g.defense().at(Position.CATCHER);
            g.removeBall();
            g.giveBallTo(catcher != null ? catcher : g.pitcherSlot());
            if (g.actor(catcher) instanceof BaseballPlayerEntity n) {
                n.setAnim(NpcAnim.CATCH_READY);
            }

            g.applyCount(call);
            if (g.ai.anyStealing()) {
                if (g.isPausedForNextPitch() && g.outs < 3) {
                    if (caught) {
                        g.startNonBattedPlay();
                    } else {
                        g.ai.completeStealsOnPassedBall();
                    }
                }

                if (!g.isPausedForNextPitch() || !caught) {
                    g.ai.clearSteals();
                }
            }
        }
    }

    private PitchingSystem() {
    }

    public static enum Call {
        BALL,
        STRIKE_LOOKING,
        STRIKE_SWINGING,
        HIT_BY_PITCH;
    }

    private static final class GameKitBridge {
        static void removeBall(BaseballGame g, ServerPlayer p) {
            GameKit.removeGameBalls(p, g.id);
        }
    }

    public static enum HumanResult {
        PITCHED,
        TOO_EARLY,
        NOT_A_PITCH;
    }
}
