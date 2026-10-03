package com.cj.mcbaseball.fielding;

import com.cj.mcbaseball.anim.ThrowKind;
import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.entity.BaseballEntity;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.FieldGeometry;
import com.cj.mcbaseball.game.GameKit;
import com.cj.mcbaseball.game.GameManager;
import com.cj.mcbaseball.game.GamePhase;
import com.cj.mcbaseball.game.LineupSlot;
import com.cj.mcbaseball.game.PlayTracker;
import com.cj.mcbaseball.game.Position;
import com.cj.mcbaseball.game.Runner;
import com.cj.mcbaseball.item.BaseballItem;
import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import com.cj.mcbaseball.npc.NpcAnim;
import com.cj.mcbaseball.physics.AimSolver;
import com.cj.mcbaseball.physics.BallPhysics;
import com.cj.mcbaseball.physics.BaseballUnits;
import com.cj.mcbaseball.pitching.PitchRecord;
import com.cj.mcbaseball.pitching.PitchingSystem;
import com.cj.mcbaseball.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class FieldingSystem {
    private final BaseballGame g;
    private final List<FieldingSystem.PendingThrow> pending = new ArrayList<>();
    private final Map<Integer, Integer> attempts = new HashMap<>();
    private final Map<UUID, Boolean> wasSprinting = new HashMap<>();

    public FieldingSystem(BaseballGame g) {
        this.g = g;
    }

    public boolean hasPendingThrow(LineupSlot s) {
        for (FieldingSystem.PendingThrow t : this.pending) {
            if (t.thrower == s) {
                return true;
            }
        }

        return false;
    }

    public void clearPending() {
        this.pending.clear();
    }

    public boolean npcTryCatch(BaseballPlayerEntity npc, BaseballEntity ball) {
        LineupSlot s = npc.slot();
        if (s == null || !this.g.isDefense(s) || ball != this.g.ball) {
            return false;
        } else if (this.g.phase == GamePhase.PITCHING) {
            PitchRecord pr = this.g.pitch;
            if (s.position == Position.CATCHER && pr != null && !pr.contact && !pr.resolved) {
                PitchingSystem.trackNow(this.g);
                if (!pr.crossed) {
                    return false;
                } else {
                    pr.catcherCaught = true;
                    this.g.ball = null;
                    this.g.holder = s;
                    npc.holdBall(true);
                    return true;
                }
            } else {
                return false;
            }
        } else if (this.g.phase != GamePhase.BALL_IN_PLAY) {
            return false;
        } else {
            double speed = ball.getSpeed();
            boolean catcher = s.position == Position.CATCHER;
            double maxSpeed = 1.3 + (double)s.npc.fielding / 99.0 * 1.5 + (catcher ? 0.8 : 0.0);
            if (speed > maxSpeed) {
                return false;
            } else {
                boolean scoop = speed < (Double)BaseballConfig.PICKUP_MAX_SPEED.get() * 2.0;
                int key = scoop ? -1 - (int)(this.g.tick / 20L) : ball.launchId() * 100 + Math.min(ball.getBounces(), 99);
                Integer prev = this.attempts.get(npc.getId());
                if (prev != null && prev == key) {
                    return false;
                } else {
                    this.attempts.put(npc.getId(), key);
                    double rel = this.g.difficulty().catchReliability * (0.97 + (double)s.npc.fielding / 99.0 * 0.03);
                    if (scoop) {
                        rel = Math.max(rel, 0.985);
                    }

                    if (this.g.rng.nextDouble() > rel) {
                        PlayTracker p = this.g.play;
                        if (p != null) {
                            p.errors++;
                        }

                        this.g.stats.of(s).e++;
                        this.g.defense().errors++;
                        this.g.net.info(Component.translatable("mcbaseball.info.error", new Object[]{s.displayName()}).withStyle(ChatFormatting.RED));
                        return false;
                    } else {
                        this.g.onPossession(npc, ball.getBounces() == 0);
                        return true;
                    }
                }
            }
        }
    }

    public void npcThrow(LineupSlot thrower, Vec3 target, int targetBase) {
        BaseballPlayerEntity n = this.g.npc(thrower);
        if (n != null && !this.hasPendingThrow(thrower)) {
            n.stopMoving();
            n.lookAtPos(target);
            n.setAnim(NpcAnim.forThrow(ThrowKind.fielder(thrower.position.abbr, n.position().distanceTo(target))));
            this.pending.add(new FieldingSystem.PendingThrow(thrower, target, targetBase, this.g.tick + 5L));
        }
    }

    public double npcThrowSpeed(LineupSlot s) {
        int arm = s.usesNpc() ? s.npc.arm : 70;
        double mph = Mth.lerp((double)arm / 99.0, (Double)BaseballConfig.NPC_THROW_MIN_MPH.get(), (Double)BaseballConfig.NPC_THROW_MAX_MPH.get());
        return BaseballUnits.blocksPerTickFromDisplayMph(mph);
    }

    public void tick() {
        if (this.pending.isEmpty()) {
            this.tickHumanSlides();
        } else {
            Iterator<FieldingSystem.PendingThrow> it = this.pending.iterator();

            while (it.hasNext()) {
                FieldingSystem.PendingThrow t = it.next();
                if (this.g.tick >= t.releaseTick) {
                    it.remove();
                    if (this.g.holder == t.thrower && this.g.phase == GamePhase.BALL_IN_PLAY) {
                        BaseballPlayerEntity n = this.g.npc(t.thrower);
                        if (n != null) {
                            Vec3 start = n.getEyePosition().add(t.target.subtract(n.getEyePosition()).normalize().scale(0.5)).subtract(0.0, 0.2, 0.0);
                            double dist = t.target.distanceTo(start);
                            double speed = Math.min(this.npcThrowSpeed(t.thrower), 0.42 + dist * 0.055);
                            double errScale = dist * this.g.difficulty().throwError * (1.3 - (double)t.thrower.npc.arm / 99.0 * 0.5);
                            Vec3 aim = t.target
                                .add(
                                    this.g.rng.nextGaussian() * errScale, this.g.rng.nextGaussian() * errScale * 0.6, this.g.rng.nextGaussian() * errScale
                                );
                            Vec3 v = AimSolver.solve(start, aim, speed, vel -> BallPhysics.backspin(vel, 7.0), BallPhysics.Params.fromConfig());
                            this.g.clearHolders();
                            BaseballEntity ball = BaseballEntity.create(this.g.level, start);
                            ball.setGame(this.g.id);
                            ball.launch(n, v);
                            ball.setSpin(BallPhysics.backspin(v, 7.0));
                            this.g.level.addFreshEntity(ball);
                            this.g.onBallThrown(t.thrower, ball, t.targetBase);
                            this.g.level.playSound(null, n.getX(), n.getY(), n.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 0.7F);
                        }
                    }
                }
            }

            this.tickHumanSlides();
        }
    }

    public static FieldingSystem.Release humanRelease(ServerPlayer p, ItemStack stack, int held) {
        UUID gid = GameKit.gameBallId(stack);
        if (gid == null) {
            return FieldingSystem.Release.NOT_GAME;
        } else {
            BaseballGame g = GameManager.get(gid);
            if (g == null) {
                stack.removeTagKey("mcbaseballGame");
                return FieldingSystem.Release.NOT_GAME;
            } else {
                LineupSlot s = g.slotOf(p);
                if (s != null && g.holder == s) {
                    boolean pickoff = false;
                    if (g.phase == GamePhase.PITCHING && g.pitch == null && s == g.pitcherSlot()) {
                        PitchingSystem.HumanResult r = PitchingSystem.humanRelease(g, p, s, held);
                        if (r == PitchingSystem.HumanResult.PITCHED) {
                            return FieldingSystem.Release.PITCHED;
                        }

                        if (r == PitchingSystem.HumanResult.TOO_EARLY) {
                            return FieldingSystem.Release.KEEP;
                        }

                        if (g.phaseTicks < (Integer)BaseballConfig.PITCH_READY_TICKS.get()) {
                            return FieldingSystem.Release.KEEP;
                        }

                        pickoff = true;
                    }

                    if (g.phase != GamePhase.BALL_IN_PLAY && !pickoff) {
                        return FieldingSystem.Release.KEEP;
                    } else {
                        if (pickoff) {
                            g.startNonBattedPlay();
                        }

                        float charge = BaseballItem.chargeFraction(held);
                        double speed = BaseballItem.throwSpeed(charge);
                        Vec3 look = p.getLookAngle();
                        Vec3 eye = p.getEyePosition();
                        g.clearHolders();
                        BaseballEntity ball = BaseballEntity.create(g.level, eye.add(look.scale(0.35)));
                        ball.setGame(g.id);
                        ball.launch(p, look.scale(speed));
                        ball.setSpin(BallPhysics.backspin(look, 7.0));
                        g.level.addFreshEntity(ball);
                        g.onBallThrown(s, ball, baseInLook(g, eye, look));
                        return FieldingSystem.Release.THROWN;
                    }
                } else {
                    return FieldingSystem.Release.KEEP;
                }
            }
        }
    }

    private static int baseInLook(BaseballGame g, Vec3 eye, Vec3 look) {
        int best = -1;
        double bestDot = Math.cos(Math.toRadians(15.0));

        for (int b = 1; b <= 4; b++) {
            Vec3 to = g.geo.base(b).add(0.0, 1.0, 0.0).subtract(eye).normalize();
            double d = to.dot(look);
            if (d > bestDot) {
                bestDot = d;
                best = b;
            }
        }

        return best;
    }

    public boolean humanTag(ServerPlayer p, Entity target) {
        LineupSlot s = this.g.slotOf(p);
        PlayTracker play = this.g.play;
        if (s != null && this.g.holder == s && play != null && this.g.phase == GamePhase.BALL_IN_PLAY) {
            LineupSlot ts = this.g.slotOf(target);
            Runner r = ts == null ? null : play.of(ts);
            if (r != null && r.active()) {
                double reach = (Double)BaseballConfig.TAG_REACH.get()
                    + 1.4
                    - (this.g.tick < r.slideUntil ? (Double)BaseballConfig.SLIDE_TAG_REDUCTION.get() : 0.0);
                if (!(FieldGeometry.flatDist(p.position(), target.position()) > reach) && !this.g.isSafe(r, target.position())) {
                    this.g.recordOut(r, s, false);
                    this.g.net.call(Component.translatable("mcbaseball.call.tag_out").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}));
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void tickHumanSlides() {
        PlayTracker play = this.g.play;
        if (play != null) {
            for (Runner r : play.runners) {
                if (r.active() && r.slot.isHuman()) {
                    LivingEntity sprinting = this.g.actor(r.slot);
                    if (sprinting instanceof ServerPlayer) {
                        ServerPlayer p = (ServerPlayer)sprinting;
                        boolean sprintingx = p.isSprinting();
                        boolean was = this.wasSprinting.getOrDefault(p.getUUID(), false);
                        this.wasSprinting.put(p.getUUID(), sprintingx);
                        if (was && p.isShiftKeyDown() && this.g.tick >= r.slideUntil) {
                            int next = Math.min(4, r.base + 1);
                            double d = FieldGeometry.flatDist(p.position(), this.g.geo.base(next));
                            if (!(d > 4.5)) {
                                Vec3 dir = FieldGeometry.flat(this.g.geo.base(next).subtract(p.position())).normalize();
                                p.setDeltaMovement(dir.scale(0.75).add(0.0, 0.05, 0.0));
                                p.hurtMarked = true;
                                r.slideUntil = this.g.tick + 14L;
                                this.g
                                    .level
                                    .playSound(null, p.getX(), p.getY(), p.getZ(), (SoundEvent)ModSounds.SLIDE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                                this.g.level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.4, 0.05, 0.4, 0.02);
                            }
                        }
                    }
                }
            }
        }
    }

    @Nullable
    public static BaseballGame gameOf(ServerPlayer p) {
        return GameManager.forPlayer(p.getUUID());
    }

    private static record PendingThrow(LineupSlot thrower, Vec3 target, int targetBase, long releaseTick) {
    }

    public static enum Release {
        NOT_GAME,
        PITCHED,
        THROWN,
        KEEP;
    }
}
