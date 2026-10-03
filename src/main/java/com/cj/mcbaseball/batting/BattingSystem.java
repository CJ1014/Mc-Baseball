package com.cj.mcbaseball.batting;

import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.entity.BaseballEntity;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.Difficulty;
import com.cj.mcbaseball.game.FieldGeometry;
import com.cj.mcbaseball.game.GameMessages;
import com.cj.mcbaseball.game.GamePhase;
import com.cj.mcbaseball.game.LineupSlot;
import com.cj.mcbaseball.item.BatItem;
import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import com.cj.mcbaseball.npc.NpcAnim;
import com.cj.mcbaseball.physics.AimSolver;
import com.cj.mcbaseball.physics.BallPhysics;
import com.cj.mcbaseball.physics.BaseballUnits;
import com.cj.mcbaseball.pitching.PitchRecord;
import com.cj.mcbaseball.registry.ModItems;
import com.cj.mcbaseball.registry.ModSounds;
import com.cj.mcbaseball.team.NpcProfile;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class BattingSystem {
    public static final double NPC_ERROR_SCALE = 1.0;

    public static void humanSwing(BaseballGame g, ServerPlayer p, boolean bunt) {
        LineupSlot s = g.slotOf(p);
        if (s != null && s == g.batter && g.phase == GamePhase.PITCHING && g.pitch != null) {
            if (p.getMainHandItem().getItem() instanceof BatItem bat) {
                double aimY = g.settings.simpleBatting ? Double.NaN : aimFromLook(g, p);
                int lag = Mth.clamp((int)Math.ceil((double)p.latency / 50.0), 0, (Integer)BaseballConfig.MAX_LAG_COMPENSATION_TICKS.get());
                swing(g, p, bat.stats(), true, bunt, aimY, lag, 69.0, g.settings.simpleBatting ? 1.6 : 1.0);
            }
        }
    }

    public static void npcSwing(BaseballGame g, BattingSystem.NpcSwing plan) {
        LivingEntity a = g.actor(g.batter);
        if (a != null && g.batter != null) {
            NpcProfile np = g.batter.npc;
            double batMph = 60.5 + (double)np.power / 99.0 * 14.0;
            BatItem bat = (BatItem)ModItems.WOODEN_BAT.get();
            swing(g, a, bat.stats(), g.batter.batsRight(), plan.bunt(), plan.aimY(), 0, batMph, 1.0);
        }
    }

    public static double aimFromLook(BaseballGame g, ServerPlayer p) {
        FieldGeometry geo = g.geo;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double ln = look.dot(geo.right);
        double en = geo.lateral(eye);
        double t = Math.abs(ln) > 0.001 ? -en / ln : -1.0;
        double y;
        if (t > 0.0 && t < 25.0) {
            y = eye.y + look.y * t;
        } else {
            double horiz = Math.sqrt(look.x * look.x + look.z * look.z);
            y = eye.y + (horiz < 0.001 ? -1.0 : look.y / horiz * 4.0);
        }

        return Mth.clamp(y, geo.groundY + 0.1, geo.groundY + 2.2);
    }

    private static void swing(
        BaseballGame g, LivingEntity batter, BatItem.BatStats bat, boolean batsRight, boolean bunt, double aimY, int lag, double baseBatMph, double windowScale
    ) {
        PitchRecord pr = g.pitch;
        if (pr != null && !pr.swung && !pr.resolved && !pr.contact) {
            pr.swung = true;
            g.level
                .playSound(
                    null,
                    batter.getX(),
                    batter.getY(),
                    batter.getZ(),
                    (SoundEvent)ModSounds.BAT_SWING.get(),
                    SoundSource.PLAYERS,
                    bunt ? 0.2F : 0.8F,
                    1.0F
                );
            if (batter instanceof BaseballPlayerEntity n) {
                n.setAnim(bunt ? NpcAnim.BUNT : NpcAnim.SWING);
            }

            FieldGeometry geo = g.geo;
            double start = (double)(g.tick - (long)lag);
            double ideal = bunt ? 0.0 : 2.0 / (double)bat.swingSpeed();
            double crossTick;
            Vec3 crossPt;
            if (pr.contactCrossed && pr.contactPoint != null) {
                crossTick = pr.contactTick;
                crossPt = pr.contactPoint;
            } else {
                BaseballEntity b = g.ball;
                if (b == null || !b.isAlive()) {
                    return;
                }

                Vec3 planePt = geo.home.add(geo.forward.scale(0.35));
                AimSolver.Crossing c = AimSolver.toPlane(
                    b.getCenter(), b.getDeltaMovement(), b.getSpin(), BallPhysics.Params.fromConfig(), planePt, geo.forward.reverse(), 40
                );
                if (c == null) {
                    return;
                }

                crossTick = (double)g.tick + c.ticks();
                crossPt = c.point();
            }

            if (Double.isNaN(aimY)) {
                aimY = crossPt.y - 0.04 + g.rng.nextGaussian() * 0.05;
            }

            double timing = crossTick - (start + ideal);
            double dv = crossPt.y - aimY;
            double window = (Double)BaseballConfig.SWING_TIMING_WINDOW.get() * windowScale;
            double hwin = (Double)BaseballConfig.SWING_HEIGHT_WINDOW.get();
            Random r = g.rng;
            double backspinRate = 0.0;
            double sidespin = 0.0;
            double hand = batsRight ? 1.0 : -1.0;
            double exit;
            double launch;
            double spray;
            double q;
            String label;
            if (bunt) {
                if (crossTick < start - 2.0 || crossTick > start + 14.0 || Math.abs(dv) > 0.35) {
                    feedback(g, batter, "mcbaseball.feedback.miss", null);
                    return;
                }

                q = 0.3;
                exit = 14.0 + r.nextDouble() * 10.0;
                launch = -6.0 + r.nextDouble() * 14.0;
                spray = r.nextGaussian() * 14.0;
                backspinRate = 2.0;
                label = "mcbaseball.feedback.bunt";
            } else {
                if (Math.abs(timing) > window || Math.abs(dv) > hwin) {
                    String key = timing > window
                        ? "mcbaseball.feedback.very_early"
                        : (timing < -window ? "mcbaseball.feedback.very_late" : (dv > 0.0 ? "mcbaseball.feedback.under" : "mcbaseball.feedback.over"));
                    feedback(g, batter, key, null);
                    return;
                }

                double u = Mth.clamp(dv / hwin, -1.0, 1.0);
                double barrelWidth = 0.45 + 0.55 * (double)bat.sweetSpot();
                double qBarrel = Mth.clamp(1.0 - sq(Math.abs(timing) / window / barrelWidth), 0.0, 1.0);
                double q0 = 0.2 * (double)bat.power();
                q = q0 * (0.35 + 0.65 * qBarrel) * (1.0 - 0.9 * u * u);
                double batMph = baseBatMph * (double)bat.swingSpeed() * (1.0 - 0.15 * sq(Math.abs(timing) / window));
                exit = (q * pr.mph + (1.0 + q) * batMph) * (1.0 - 0.5 * u * u) * (0.6 + 0.4 * qBarrel);
                launch = 10.0 + u * 52.0 + r.nextGaussian() * 3.0;
                spray = -hand * timing * 30.0 + r.nextGaussian() * 8.0;
                backspinRate = Mth.clamp(3.0 + 20.0 * u, -14.0, 22.0);
                sidespin = Mth.clamp(hand * timing * 1.6, -4.0, 4.0);
                if ((qBarrel < 0.22 || Math.abs(u) > 0.88) && r.nextDouble() < 0.6) {
                    spray = 180.0 + r.nextGaussian() * 35.0;
                    launch = 25.0 + r.nextDouble() * 40.0;
                    exit *= 0.55;
                    backspinRate = 15.0;
                }

                exit = Mth.clamp(exit, 15.0, 122.0);
                launch = Mth.clamp(launch, -35.0, 78.0);
                q = qBarrel * (1.0 - u * u);
                double at = Math.abs(timing);
                label = at < 0.3
                    ? "mcbaseball.feedback.perfect"
                    : (
                        at < 0.8
                            ? "mcbaseball.feedback.good"
                            : (
                                at < 1.5
                                    ? (timing > 0.0 ? "mcbaseball.feedback.early" : "mcbaseball.feedback.late")
                                    : (timing > 0.0 ? "mcbaseball.feedback.very_early" : "mcbaseball.feedback.very_late")
                            )
                    );
            }

            double s = Math.toRadians(spray);
            double l = Math.toRadians(launch);
            Vec3 dirH = geo.forward.scale(Math.cos(s)).add(geo.right.scale(Math.sin(s)));
            double speed = BaseballUnits.blocksPerTickFromDisplayMph(exit) * (Double)BaseballConfig.EXIT_VELOCITY_SCALE.get();
            Vec3 vel = dirH.scale(Math.cos(l) * speed).add(0.0, Math.sin(l) * speed, 0.0);
            Vec3 spin = BallPhysics.backspin(vel, backspinRate).add(0.0, sidespin, 0.0);
            g.pendingContact = new BattingSystem.PendingContact(
                Math.max(g.tick, (long)Math.ceil(crossTick)), crossPt, vel, spin, exit, launch, batter, label, !bunt && exit >= 85.0, q
            );
            applyPending(g);
        }
    }

    public static void applyPending(BaseballGame g) {
        BattingSystem.PendingContact pc = g.pendingContact;
        if (pc != null && g.tick >= pc.applyTick()) {
            g.pendingContact = null;
            PitchRecord pr = g.pitch;
            if (pr != null && !pr.resolved) {
                g.clearHolders();
                BaseballEntity b = g.ball;
                if (b != null && b.isAlive()) {
                    b.setCenter(pc.point());
                } else {
                    b = BaseballEntity.create(g.level, pc.point());
                    b.setGame(g.id);
                    g.level.addFreshEntity(b);
                }

                b.launch(pc.batter(), pc.velocity());
                b.setSpin(pc.spin());
                g.setLooseBall(b);
                float vol = (float)Mth.clamp(pc.exitMph() / 90.0, 0.3, 1.5);
                g.level
                    .playSound(
                        null,
                        pc.point().x,
                        pc.point().y,
                        pc.point().z,
                        (SoundEvent)ModSounds.BAT_HIT.get(),
                        SoundSource.PLAYERS,
                        vol,
                        (float)(0.85 + pc.quality() * 0.3)
                    );
                feedback(g, pc.batter(), pc.feedbackKey(), pc.showExit() ? pc.exitMph() : null);
                g.startBattedPlay(pc.exitMph(), pc.launchDeg(), pc.point());
            }
        }
    }

    private static void feedback(BaseballGame g, LivingEntity batter, String key, @Nullable Double exit) {
        if (batter instanceof ServerPlayer p) {
            g.net.personal(p, GameMessages.Kind.TIMING, Component.translatable(key));
            if (exit != null) {
                g.net
                    .personal(
                        p,
                        GameMessages.Kind.EXIT_VELO,
                        Component.translatable("mcbaseball.feedback.exit_velo", new Object[]{Math.round(exit)}).withStyle(ChatFormatting.WHITE)
                    );
            }
        }
    }

    public static void planNpcSwing(BaseballGame g, PitchRecord pr) {
        if (g.director != null) {
            // Live Mode: the batter does what the real batter did.
            g.director.onPitchReleased(g, pr);
            return;
        }
        LineupSlot bs = g.batter;
        if (bs != null && bs.usesNpc() && g.ball != null) {
            BaseballEntity b = g.ball;
            NpcProfile np = bs.npc;
            Difficulty d = g.difficulty();
            FieldGeometry geo = g.geo;
            Random r = g.rng;
            BallPhysics.Params params = BallPhysics.Params.fromConfig();
            AimSolver.Crossing home = AimSolver.toPlane(b.getCenter(), b.getDeltaMovement(), b.getSpin(), params, geo.home, geo.forward.reverse(), 80);
            AimSolver.Crossing contact = AimSolver.toPlane(
                b.getCenter(), b.getDeltaMovement(), b.getSpin(), params, geo.home.add(geo.forward.scale(0.35)), geo.forward.reverse(), 80
            );
            if (home != null && contact != null) {
                double eyeErr = 0.22 * (1.3 - (double)np.eye / 99.0 * 0.6) * (d.swingHeightSigma / 0.3) * g.npcBatErrScale;
                Vec3 perceived = home.point().add(geo.right.scale(r.nextGaussian() * eyeErr)).add(0.0, r.nextGaussian() * eyeErr, 0.0);
                boolean looksStrike = geo.inStrikeZone(perceived);
                double swingP = looksStrike ? (g.strikes == 2 ? 0.9 : 0.7) : d.chaseRate * (1.35 - (double)np.eye / 99.0 * 0.7) * (g.strikes == 2 ? 1.5 : 1.0);
                if (g.balls == 0 && g.strikes == 0) {
                    swingP *= 0.65;
                }

                if (g.balls == 3 && g.strikes == 0) {
                    swingP *= 0.3;
                } else if (g.balls == 3 && g.strikes < 2) {
                    swingP *= looksStrike ? 0.85 : 0.5;
                }

                if (!(r.nextDouble() >= swingP)) {
                    boolean bunt = g.strikes < 2 && g.outs < 2 && (g.onBase[1] != null || g.onBase[2] != null) && np.power < 52 && r.nextDouble() < 0.12;
                    double skill = 1.3 - (double)np.contact / 99.0 * 0.6;
                    double timingErr = r.nextGaussian() * d.swingTimingSigma * skill * g.npcBatErrScale;
                    double aimErr = r.nextGaussian() * d.swingHeightSigma * skill;
                    double ideal = bunt ? 0.0 : 2.0;
                    long swingTick = Math.round((double)g.tick + contact.ticks() - ideal + timingErr);
                    if (bunt) {
                        swingTick = g.tick + Math.max(1L, (long)(contact.ticks() * 0.4));
                    }

                    g.npcSwing = new BattingSystem.NpcSwing(Math.max(g.tick + 1L, swingTick), contact.point().y + aimErr, bunt);
                }
            }
        }
    }

    private static double sq(double x) {
        return x * x;
    }

    private BattingSystem() {
    }

    public static record NpcSwing(long tick, double aimY, boolean bunt) {
    }

    public static record PendingContact(
        long applyTick,
        Vec3 point,
        Vec3 velocity,
        Vec3 spin,
        double exitMph,
        double launchDeg,
        LivingEntity batter,
        String feedbackKey,
        boolean showExit,
        double quality
    ) {
    }
}
