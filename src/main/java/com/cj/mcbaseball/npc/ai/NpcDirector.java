package com.cj.mcbaseball.npc.ai;

import com.cj.mcbaseball.anim.ThrowKind;
import com.cj.mcbaseball.batting.BattingSystem;
import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.entity.BaseballEntity;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.Difficulty;
import com.cj.mcbaseball.game.FairState;
import com.cj.mcbaseball.game.FieldGeometry;
import com.cj.mcbaseball.game.GameTeam;
import com.cj.mcbaseball.game.LineupSlot;
import com.cj.mcbaseball.game.PlayTracker;
import com.cj.mcbaseball.game.Position;
import com.cj.mcbaseball.game.Runner;
import com.cj.mcbaseball.item.BatItem;
import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import com.cj.mcbaseball.npc.NpcAnim;
import com.cj.mcbaseball.physics.BallPhysics;
import com.cj.mcbaseball.pitching.PitchAI;
import com.cj.mcbaseball.pitching.PitchRecord;
import com.cj.mcbaseball.pitching.PitchType;
import com.cj.mcbaseball.pitching.PitchingSystem;
import com.cj.mcbaseball.registry.ModSounds;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class NpcDirector {
    private static final double HUMAN_SPEED = 0.28;
    private final BaseballGame g;
    private final Set<LineupSlot> stealers = new HashSet<>();
    @Nullable
    private LineupSlot chaser;
    @Nullable
    private Vec3 chasePoint;
    private long reactAt;
    private long replanAt;
    private long holderDecideAt;
    private final Map<Integer, LineupSlot> cover = new HashMap<>();
    private long pitchDecisionAt = -1L;
    @Nullable
    private PitchAI.Choice windup;
    private long windupRelease;
    @Nullable
    private PitchType lastType;
    private int sameTypeCount;
    private int lastQuadrant = -1;
    private boolean pickoffTried;
    private boolean batterSettled;

    public NpcDirector(BaseballGame g) {
        this.g = g;
    }

    public void resetForPitch() {
        this.stealers.clear();
        this.windup = null;
        this.pitchDecisionAt = -1L;
        this.pickoffTried = false;
        this.chaser = null;
        this.g.fielding.clearPending();
        Vec3 home = this.g.geo.home;

        for (LineupSlot s : this.g.defense().order) {
            BaseballPlayerEntity n = this.g.npc(s);
            if (n != null) {
                n.moveTo(this.g.geo.spot(s.position), 0.75);
                n.lookAtPos(home.add(0.0, 1.0, 0.0));
                n.setAnim(s.position == Position.CATCHER ? NpcAnim.CATCH_READY : NpcAnim.NONE);
                if (s != this.g.holder) {
                    n.holdBall(false);
                }

                n.holdGlove(s.position == Position.CATCHER);
            }
        }

        for (int b = 1; b <= 3; b++) {
            BaseballPlayerEntity n = this.g.npc(this.g.onBase[b]);
            if (n != null) {
                n.clearHands();
                n.setAnim(NpcAnim.NONE);
                n.moveTo(this.g.geo.base(b), 0.7);
                n.lookAtPos(this.g.geo.mound.add(0.0, 1.5, 0.0));
            }
        }

        BaseballPlayerEntity bn = this.g.npc(this.g.batter);
        this.batterSettled = false;
        if (bn != null) {
            bn.holdBat();
            Vec3 box = this.g.geo.batterBox(this.g.batter.batsRight());
            if (BaseballGame.horizDist(bn.position(), box) > 0.6) {
                bn.setAnim(NpcAnim.NONE);
                bn.moveTo(box, 0.8);
            } else {
                bn.stopMoving();
                bn.setAnim(NpcAnim.BAT_STANCE);
            }

            bn.lookAtPos(this.g.geo.mound.add(0.0, 1.5, 0.0));
        }
    }

    private void settleForPitch() {
        if (this.g.pitch == null && this.g.batter != null) {
            BaseballPlayerEntity bn = this.g.npc(this.g.batter);
            if (bn != null) {
                Vec3 box = this.g.geo.batterBox(this.g.batter.batsRight());
                double d = BaseballGame.horizDist(bn.position(), box);
                if (d > 0.6) {
                    if (bn.getNavigation().isDone()) {
                        bn.moveTo(box, 0.8);
                    }

                    this.batterSettled = false;
                } else if (!this.batterSettled) {
                    this.g.place(this.g.batter, box, this.g.geo.mound);
                    bn.holdBat();
                    bn.setAnim(NpcAnim.BAT_STANCE);
                    bn.lookAtPos(this.g.geo.mound.add(0.0, 1.5, 0.0));
                    this.batterSettled = true;
                }
            }

            for (int b = 1; b <= 3; b++) {
                BaseballPlayerEntity rn = this.g.npc(this.g.onBase[b]);
                if (rn != null && BaseballGame.horizDist(rn.position(), this.g.geo.base(b)) > 1.0 && rn.getNavigation().isDone()) {
                    rn.moveTo(this.g.geo.base(b), 0.8);
                }
            }

            if (this.g.phaseTicks > 100 && !this.g.batterSet()) {
                this.g.snapToPositions();
            }

            if (this.g.tick % 20L == 0L) {
                GameTeam team = this.g.offense();

                for (int i = 0; i < team.order.size(); i++) {
                    LineupSlot s = team.order.get(i);
                    if (s != this.g.batter && !this.g.isOnBaseSlot(s)) {
                        BaseballPlayerEntity n = this.g.npc(s);
                        if (n != null) {
                            Vec3 spot = this.g.geo.dugoutSpot(team.side, i, team.order.size());
                            if (BaseballGame.horizDist(n.position(), spot) > 1.2) {
                                if (n.getNavigation().isDone()) {
                                    n.moveTo(spot, 0.6);
                                }
                            } else {
                                n.lookAtPos(this.g.geo.dugoutFacing(team.side));
                            }
                        }
                    }
                }
            }
        }
    }

    public void onPitchReleased(PitchRecord pr) {
        BattingSystem.planNpcSwing(this.g, pr);

        for (int b = 1; b <= 2; b++) {
            LineupSlot r = this.g.onBase[b];
            if (r != null && r.usesNpc() && this.g.onBase[b + 1] == null) {
                double prob = Math.max(0.0, (double)(r.npc.speed - 58) / 41.0)
                    * 0.18
                    * (b == 2 ? 0.4 : 1.0)
                    * (this.g.balls < 3 ? 1.0 : 0.5)
                    * (0.6 + this.g.difficulty().judgment * 0.4);
                if (this.g.rng.nextDouble() < prob) {
                    this.stealers.add(r);
                    BaseballPlayerEntity n = this.g.npc(r);
                    if (n != null) {
                        n.moveTo(this.g.geo.base(b + 1), 1.0);
                    }
                }
            }
        }
    }

    public boolean isStealing(LineupSlot s) {
        return this.stealers.contains(s);
    }

    public boolean anyStealing() {
        return !this.stealers.isEmpty();
    }

    public void clearSteals() {
        this.stealers.clear();
    }

    public void completeStealsOnPassedBall() {
        for (int b = 3; b >= 1; b--) {
            LineupSlot r = this.g.onBase[b];
            if (r != null && this.stealers.contains(r) && b < 3 && this.g.onBase[b + 1] == null) {
                this.g.onBase[b + 1] = r;
                this.g.onBase[b] = null;
                this.g.net.info(Component.translatable("mcbaseball.info.stolen_base", new Object[]{r.displayName()}));
            }
        }

        this.stealers.clear();
    }

    public void onBallInPlay() {
        this.chaser = null;
        this.chasePoint = null;
        this.reactAt = this.g.tick + (long)this.g.difficulty().reactionTicks;
        this.replanAt = 0L;
        this.holderDecideAt = -1L;
    }

    public void onPossession(LineupSlot s) {
        this.chaser = null;
        this.chasePoint = null;
        int extra = s.position.outfield() ? 4 : 2;
        this.holderDecideAt = this.g.tick + (long)(this.g.difficulty().reactionTicks / 2) + (long)extra;
    }

    public void tick() {
        switch (this.g.phase) {
            case PITCHING:
                this.tickPitching();
                break;
            case BALL_IN_PLAY:
                if (this.g.tick % 3L == 0L) {
                    this.tickLooks();
                    this.tickDefense();
                    this.tickOffense();
                }

                this.g.fielding.tick();
        }
    }

    private void tickLooks() {
        Vec3 target = this.ballPos();
        if (target != null) {
            for (LineupSlot s : this.g.allSlots()) {
                BaseballPlayerEntity n = this.g.npc(s);
                if (n != null) {
                    n.lookAtPos(target);
                }
            }
        }
    }

    @Nullable
    private Vec3 ballPos() {
        if (this.g.ball != null && this.g.ball.isAlive()) {
            return this.g.ball.getCenter();
        } else {
            LivingEntity h = this.g.actor(this.g.holder);
            return h == null ? null : h.position().add(0.0, 1.2, 0.0);
        }
    }

    private void tickPitching() {
        this.settleForPitch();
        LineupSlot ps = this.g.pitcherSlot();
        BaseballPlayerEntity pn = this.g.npc(ps);
        if (this.g.pitch == null && pn != null && this.g.holder == ps && ps != null) {
            if (this.g.phaseTicks >= (Integer)BaseballConfig.PITCH_READY_TICKS.get()) {
                if (this.windup == null && !this.g.batterSet()) {
                    this.pitchDecisionAt = -1L;
                } else if (this.windup != null) {
                    if (this.g.tick >= this.windupRelease) {
                        PitchAI.Choice c = this.windup;
                        this.windup = null;
                        PitchingSystem.releasePitch(this.g, ps, pn, c.type(), PitchAI.target(this.g, c), c.quality(), ps.npc.velocity, ps.npc.stuff);
                        this.sameTypeCount = c.type() == this.lastType ? this.sameTypeCount + 1 : 1;
                        this.lastType = c.type();
                        this.lastQuadrant = c.quadrant();
                    }
                } else {
                    if (this.pitchDecisionAt < 0L) {
                        int wait = 8 + this.g.rng.nextInt(25);
                        this.pitchDecisionAt = this.g.tick + (long)wait;
                    }

                    if (this.g.tick >= this.pitchDecisionAt) {
                        if (this.g.batter == null
                            || !this.g.batter.isHuman()
                            || !(this.g.actor(this.g.batter) instanceof ServerPlayer bp)
                            || bp.isUsingItem() && bp.getUseItem().getItem() instanceof BatItem
                            || this.g.tick >= this.pitchDecisionAt + 80L) {
                            if (this.pickoffTried || !this.tryPickoff(ps, pn)) {
                                this.pickoffTried = true;
                                this.windup = PitchAI.choose(this.g, ps.npc, this.lastType, this.sameTypeCount, this.lastQuadrant);
                                this.windupRelease = this.g.tick + 14L;
                                pn.setAnim(NpcAnim.forThrow(ThrowKind.of(this.windup.type())));
                                pn.lookAtPos(this.g.geo.home.add(0.0, 1.0, 0.0));
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean tryPickoff(LineupSlot ps, BaseballPlayerEntity pn) {
        if (this.g.difficulty() == Difficulty.ROOKIE) {
            return false;
        } else {
            for (int b = 1; b <= 2; b++) {
                LineupSlot r = this.g.onBase[b];
                if (r != null && r.isHuman()) {
                    LivingEntity ra = this.g.actor(r);
                    if (ra != null) {
                        double lead = FieldGeometry.flatDist(ra.position(), this.g.geo.base(b));
                        if (!(lead < 3.0) && !(this.g.rng.nextDouble() > 0.4 * this.g.difficulty().judgment)) {
                            LineupSlot recv = this.g.defense().at(b == 1 ? Position.FIRST_BASE : Position.SHORTSTOP);
                            LivingEntity rv = this.g.actor(recv);
                            if (recv != null && rv != null) {
                                this.pickoffTried = true;
                                this.g.startNonBattedPlay();
                                this.cover.clear();
                                this.cover.put(b, recv);
                                BaseballPlayerEntity rn = this.g.npc(recv);
                                if (rn != null) {
                                    rn.moveTo(this.g.geo.base(b), 1.0);
                                }

                                this.g.fielding.npcThrow(ps, this.g.geo.base(b).add(0.0, 1.2, 0.0), b);
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }
    }

    private void tickDefense() {
        PlayTracker p = this.g.play;
        if (p != null) {
            if (this.g.holder != null && this.g.isDefense(this.g.holder)) {
                this.assignCoverage(null);
                this.handleHolder(p);
            } else {
                BaseballEntity b = this.g.ball;
                if (b != null && b.isAlive()) {
                    if (this.g.tick >= this.reactAt) {
                        if (p.throwTargetBase >= 0) {
                            this.assignCoverage(null);
                            Vec3 tb = this.g.geo.base(p.throwTargetBase);
                            Vec3 bc = b.getCenter();
                            boolean movingAway = b.getDeltaMovement().dot(FieldGeometry.flat(bc.subtract(tb))) > 0.0;
                            if (!movingAway || !(FieldGeometry.flatDist(bc, tb) > 4.0)) {
                                return;
                            }

                            p.throwTargetBase = -1;
                        }

                        if (this.g.tick >= this.replanAt || this.chaser == null) {
                            this.plan(b);
                            this.replanAt = this.g.tick + 8L;
                        }

                        BaseballPlayerEntity cn = this.g.npc(this.chaser);
                        if (cn != null && this.chasePoint != null) {
                            cn.moveTo(this.chasePoint, 1.0);
                        }

                        this.assignCoverage(this.chaser);
                        this.backup();
                    }
                }
            }
        }
    }

    private double speedOf(@Nullable LivingEntity e) {
        return e instanceof BaseballPlayerEntity n ? n.runSpeed() : 0.28;
    }

    private void plan(BaseballEntity b) {
        BallPhysics.Params params = BallPhysics.Params.fromConfig();
        LineupSlot best = null;
        InterceptPlanner.Intercept bestI = null;
        double bestT = Double.MAX_VALUE;
        Vec3 bc = b.getCenter();
        double homeDist = this.g.geo.distFromHome(bc);

        for (LineupSlot s : this.g.defense().order) {
            LivingEntity a = this.g.actor(s);
            if (a != null) {
                InterceptPlanner.Intercept it = InterceptPlanner.plan(
                    bc, b.getDeltaMovement(), b.getSpin(), b.getMotion(), this.g.geo.groundY, a.position(), this.speedOf(a), s.isHuman() ? 0 : 1, params
                );
                double t = (double)it.ticks();
                if (s.position == Position.CATCHER && homeDist > this.g.geo.baseDist * 0.6) {
                    t += 15.0;
                }

                if (s.position == Position.PITCHER && homeDist > this.g.geo.baseDist * 1.1) {
                    t += 8.0;
                }

                if (t < bestT) {
                    bestT = t;
                    best = s;
                    bestI = it;
                }
            }
        }

        this.chaser = best;
        this.chasePoint = bestI == null ? null : bestI.point();
        if (best != null && best.isHuman() && this.chasePoint != null) {
            this.chaser = best;
        }
    }

    private void assignCoverage(@Nullable LineupSlot chasing) {
        Vec3 bp = this.ballPos();
        if (bp != null) {
            boolean rightSide = this.g.geo.angleOf(bp) > 0.0;
            GameTeam d = this.g.defense();
            LineupSlot p = d.at(Position.PITCHER);
            LineupSlot c = d.at(Position.CATCHER);
            LineupSlot b1 = d.at(Position.FIRST_BASE);
            LineupSlot b2 = d.at(Position.SECOND_BASE);
            LineupSlot ss = d.at(Position.SHORTSTOP);
            LineupSlot b3 = d.at(Position.THIRD_BASE);
            this.cover.clear();
            this.cover.put(1, this.pick(chasing, b1, p, b2));
            this.cover.put(2, rightSide ? this.pick(chasing, ss, b2, p) : this.pick(chasing, b2, ss, p));
            LineupSlot c2 = this.cover.get(2);
            this.cover.put(3, this.pick(chasing, b3, c2 == ss ? p : ss, p));
            this.cover.put(4, this.pick(chasing, c, p, null));

            for (Entry<Integer, LineupSlot> e : this.cover.entrySet()) {
                LineupSlot s = e.getValue();
                if (s != null && s != chasing && s != this.g.holder) {
                    BaseballPlayerEntity n = this.g.npc(s);
                    if (n != null && !this.g.fielding.hasPendingThrow(s)) {
                        n.moveTo(this.g.geo.base(e.getKey()), 1.0);
                    }
                }
            }
        }
    }

    @Nullable
    private LineupSlot pick(@Nullable LineupSlot chasing, @Nullable LineupSlot... prefs) {
        for (LineupSlot s : prefs) {
            if (s != null && s != chasing && s != this.g.holder) {
                return s;
            }
        }

        return null;
    }

    @Nullable
    public LineupSlot coverer(int base) {
        return this.cover.get(base);
    }

    @Nullable
    public LineupSlot chaser() {
        return this.chaser;
    }

    private void backup() {
        if (this.chasePoint != null && this.chaser != null) {
            boolean deep = this.g.geo.distFromHome(this.chasePoint) > this.g.geo.baseDist * 1.4;
            LineupSlot backer = null;
            double best = Double.MAX_VALUE;

            for (LineupSlot s : this.g.defense().order) {
                if (s != this.chaser && !this.cover.containsValue(s) && s.usesNpc() && deep == s.position.outfield()) {
                    LivingEntity a = this.g.actor(s);
                    if (a != null) {
                        double dd = FieldGeometry.flatDist(a.position(), this.chasePoint);
                        if (dd < best) {
                            best = dd;
                            backer = s;
                        }
                    }
                }
            }

            BaseballPlayerEntity n = this.g.npc(backer);
            if (n != null) {
                Vec3 away = FieldGeometry.flat(this.chasePoint.subtract(this.g.geo.home)).normalize().scale(4.0);
                n.moveTo(this.chasePoint.add(away), 0.9);
            }
        }
    }

    private void handleHolder(PlayTracker p) {
        LineupSlot hs = this.g.holder;
        BaseballPlayerEntity h = this.g.npc(hs);
        if (hs != null && h != null && !this.g.fielding.hasPendingThrow(hs)) {
            if (this.holderDecideAt >= 0L && this.g.tick < this.holderDecideAt) {
                h.stopMoving();
            } else {
                Difficulty d = this.g.difficulty();
                NpcDirector.Option best = null;

                for (Runner r : p.runners) {
                    if (r.active()) {
                        LivingEntity ra = this.g.actor(r.slot);
                        if (ra != null) {
                            int tb;
                            boolean force;
                            if (r.mustTagUp) {
                                tb = r.startBase;
                                force = true;
                            } else if (p.isForced(r) && r.base < r.startBase + 1) {
                                tb = r.startBase + 1;
                                force = true;
                            } else {
                                if (this.g.isSafe(r, ra.position())) {
                                    continue;
                                }

                                tb = this.heading(r, ra);
                                force = false;
                            }

                            if (tb >= 1 && tb <= 4) {
                                Vec3 basePos = this.g.geo.base(tb);
                                double runnerT = FieldGeometry.flatDist(ra.position(), basePos) / this.speedOf(ra);
                                double perceived = runnerT * (1.0 + this.g.rng.nextGaussian() * 0.25 * (1.0 - d.judgment));
                                double toRunner = FieldGeometry.flatDist(h.position(), ra.position());
                                if (!force && toRunner < 5.0 && !this.g.isSafe(r, ra.position())) {
                                    double score = (double)(100 + tb);
                                    if (best == null || score > best.score) {
                                        best = new NpcDirector.Option(tb, false, true, r, score);
                                    }
                                } else {
                                    double self = FieldGeometry.flatDist(h.position(), basePos);
                                    LineupSlot recv = this.cover.get(tb);
                                    boolean run = self < 6.0 || recv == null || recv == hs;
                                    double ballT;
                                    if (run) {
                                        ballT = self / h.runSpeed() + (double)(force ? 0 : 2);
                                    } else {
                                        LivingEntity rv = this.g.actor(recv);
                                        if (rv == null) {
                                            continue;
                                        }

                                        double recvT = FieldGeometry.flatDist(rv.position(), basePos) / this.speedOf(rv);
                                        double throwT = self / this.g.fielding.npcThrowSpeed(hs) + 6.0;
                                        ballT = Math.max(recvT, throwT) + (double)(force ? 0 : 3);
                                    }

                                    if (ballT < perceived) {
                                        double score = (double)(tb * 10 + (force ? 5 : 0));
                                        if (best == null || score > best.score) {
                                            best = new NpcDirector.Option(tb, force, run, null, score);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (best != null) {
                    if (best.chaseRunner != null) {
                        LivingEntity ra = this.g.actor(best.chaseRunner.slot);
                        if (ra != null) {
                            h.moveTo(ra.position(), 1.0);
                        }
                    } else if (best.run) {
                        h.moveTo(this.g.geo.base(best.base), 1.0);
                    } else {
                        this.throwTo(hs, h, best.base);
                    }
                } else {
                    Runner lead = null;

                    for (Runner rx : p.runners) {
                        if (rx.active()) {
                            LivingEntity ra = this.g.actor(rx.slot);
                            if (ra != null && !this.g.isSafe(rx, ra.position()) && (lead == null || rx.base > lead.base)) {
                                lead = rx;
                            }
                        }
                    }

                    if (lead != null) {
                        LivingEntity la = this.g.actor(lead.slot);
                        int tbx = la == null ? lead.base + 1 : this.heading(lead, la);
                        if (tbx >= 1 && tbx <= 4) {
                            LineupSlot recvx = this.cover.get(tbx);
                            if (recvx != null && recvx != hs && FieldGeometry.flatDist(h.position(), this.g.geo.base(tbx)) > 8.0) {
                                this.throwTo(hs, h, tbx);
                                return;
                            }
                        }

                        h.stopMoving();
                    } else {
                        double dHome = this.g.geo.distFromHome(h.position());
                        LineupSlot ps = this.g.pitcherSlot();
                        if (dHome > this.g.geo.baseDist * 1.3 && ps != null && ps != hs && this.g.actor(ps) != null) {
                            this.throwToSlot(hs, h, ps);
                        } else {
                            h.stopMoving();
                        }
                    }
                }
            }
        }
    }

    private void throwTo(LineupSlot hs, BaseballPlayerEntity h, int base) {
        LineupSlot recv = this.cover.get(base);
        LivingEntity rv = this.g.actor(recv);
        Vec3 basePos = this.g.geo.base(base);
        Vec3 target = rv != null && FieldGeometry.flatDist(rv.position(), basePos) < 3.0
            ? rv.position().add(0.0, 1.2, 0.0)
            : basePos.add(0.0, 1.2, 0.0);
        this.g.fielding.npcThrow(hs, target, base);
        this.holderDecideAt = this.g.tick + 20L;
    }

    private void throwToSlot(LineupSlot hs, BaseballPlayerEntity h, LineupSlot to) {
        LivingEntity rv = this.g.actor(to);
        if (rv != null) {
            this.g.fielding.npcThrow(hs, rv.position().add(0.0, 1.2, 0.0), -1);
            this.holderDecideAt = this.g.tick + 20L;
        }
    }

    private int heading(Runner r, LivingEntity ra) {
        if (r.slot.usesNpc()) {
            return Math.max(1, r.target);
        } else {
            int cur = Math.max(1, r.base);
            int next = Math.min(4, r.base + 1);
            double dCur = FieldGeometry.flatDist(ra.position(), this.g.geo.base(cur));
            double dNext = FieldGeometry.flatDist(ra.position(), this.g.geo.base(next));
            return dNext < dCur ? next : cur;
        }
    }

    private void tickOffense() {
        PlayTracker p = this.g.play;
        if (p != null) {
            Difficulty d = this.g.difficulty();

            for (Runner r : p.runners) {
                if (r.active() && r.slot.usesNpc()) {
                    BaseballPlayerEntity a = this.g.npc(r.slot);
                    if (a != null) {
                        Vec3 feet = a.position();
                        if (r.mustTagUp) {
                            r.target = r.startBase;
                            a.moveTo(this.g.geo.base(r.startBase), 1.0);
                        } else {
                            boolean onBase = r.base >= 1 && r.base <= 3 && this.g.geo.touching(feet, r.base);
                            boolean flyInAir = p.batted && p.inFlight && !p.caughtInAir && p.fair != FairState.FOUL;
                            Vec3 dest;
                            if (p.isForced(r) && r.base < r.startBase + 1) {
                                r.target = r.startBase + 1;
                                dest = this.g.geo.base(r.target);
                            } else if (flyInAir && !r.isBatter() && this.g.outs < 2) {
                                r.target = r.base;
                                double frac = r.base == 2 ? 0.35 : 0.45;
                                dest = this.g.geo.base(r.base).add(this.g.geo.base(r.base + 1).subtract(this.g.geo.base(r.base)).scale(frac));
                            } else if (!p.batted && !r.stealing) {
                                r.target = r.base;
                                dest = this.g.geo.base(r.base);
                            } else if (!onBase && r.target > r.base) {
                                if (!p.isForced(r) && this.obviouslyOut(r, a)) {
                                    r.target = r.base;
                                }

                                dest = this.g.geo.base(r.target);
                            } else {
                                r.target = this.shouldAdvance(p, r, a, d) ? r.base + 1 : r.base;
                                dest = this.g.geo.base(r.target);
                            }

                            if (r.target > 4) {
                                r.target = 4;
                            }

                            a.moveTo(dest, 1.0);
                            if (r.target > r.base && this.g.tick >= r.slideUntil) {
                                Vec3 tb = this.g.geo.base(r.target);
                                double dist = FieldGeometry.flatDist(feet, tb);
                                if (dist < 2.2 && dist > 1.0 && this.threatNear(tb)) {
                                    r.slideUntil = this.g.tick + 16L;
                                    a.startSlide(tb.subtract(feet));
                                    this.g
                                        .level
                                        .playSound(
                                            null, a.getX(), a.getY(), a.getZ(), (SoundEvent)ModSounds.SLIDE.get(), SoundSource.PLAYERS, 1.0F, 1.0F
                                        );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean shouldAdvance(PlayTracker p, Runner r, BaseballPlayerEntity a, Difficulty d) {
        int next = r.base + 1;
        if (next > 4) {
            return false;
        } else if (p.batted && p.fair == FairState.UNDECIDED && !p.isForced(r)) {
            return false;
        } else {
            for (Runner o : p.runners) {
                if (o != r && o.active()) {
                    if (o.base == next && o.target <= next) {
                        return false;
                    }

                    if (o.target == next && o.base < next) {
                        return false;
                    }
                }
            }

            Vec3 bp = this.ballPos();
            if (p.batted
                && !p.caughtInAir
                && !r.isBatter()
                && r.base >= 2
                && this.g.outs < 2
                && bp != null
                && this.g.geo.distFromHome(bp) < this.g.geo.baseDist * 1.25
                && !p.isForced(r)) {
                return false;
            } else {
                Vec3 basePos = this.g.geo.base(next);
                double runnerT = FieldGeometry.flatDist(a.position(), basePos) / a.runSpeed();
                double ballT = this.defenseTimeTo(basePos);
                double margin = (this.g.outs == 2 ? 1.05 : 1.25) + this.g.rng.nextGaussian() * 0.25 * (1.0 - d.judgment);
                return runnerT * margin < ballT;
            }
        }
    }

    private boolean obviouslyOut(Runner r, BaseballPlayerEntity a) {
        Vec3 tb = this.g.geo.base(r.target);
        double runnerT = FieldGeometry.flatDist(a.position(), tb) / a.runSpeed();
        double ballT = this.defenseTimeTo(tb);
        double backT = FieldGeometry.flatDist(a.position(), this.g.geo.base(Math.max(1, r.base))) / a.runSpeed();
        return ballT < runnerT * 0.6 && backT < runnerT && r.base >= 1;
    }

    private double defenseTimeTo(Vec3 basePos) {
        LivingEntity h = this.g.actor(this.g.holder);
        if (h != null && this.g.isDefense(this.g.holder)) {
            double throwSpeed = this.g.holder.usesNpc() ? this.g.fielding.npcThrowSpeed(this.g.holder) : 1.1;
            return FieldGeometry.flatDist(h.position(), basePos) / throwSpeed + 6.0;
        } else {
            Vec3 bp = this.ballPos();
            if (bp == null) {
                return 999.0;
            } else {
                double fieldT = 999.0;

                for (LineupSlot s : this.g.defense().order) {
                    LivingEntity a = this.g.actor(s);
                    if (a != null) {
                        fieldT = Math.min(fieldT, FieldGeometry.flatDist(a.position(), bp) / this.speedOf(a));
                    }
                }

                return fieldT + (double)this.g.difficulty().reactionTicks + FieldGeometry.flatDist(bp, basePos) / 1.15 + 6.0;
            }
        }
    }

    private boolean threatNear(Vec3 base) {
        LivingEntity h = this.g.actor(this.g.holder);
        if (h != null && this.g.isDefense(this.g.holder) && FieldGeometry.flatDist(h.position(), base) < 10.0) {
            return true;
        } else {
            Vec3 bp = this.g.ball != null && this.g.ball.isAlive() ? this.g.ball.getCenter() : null;
            return bp != null && FieldGeometry.flatDist(bp, base) < 10.0;
        }
    }

    private static record Option(int base, boolean force, boolean run, @Nullable Runner chaseRunner, double score) {
    }
}
