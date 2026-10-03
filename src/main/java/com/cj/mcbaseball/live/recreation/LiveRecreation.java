package com.cj.mcbaseball.live.recreation;

import com.cj.mcbaseball.MCBaseball;
import com.cj.mcbaseball.anim.ThrowKind;
import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.entity.BaseballEntity;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.ChunkKeeper;
import com.cj.mcbaseball.game.FieldGeometry;
import com.cj.mcbaseball.game.GameManager;
import com.cj.mcbaseball.game.GamePhase;
import com.cj.mcbaseball.game.GameSettings;
import com.cj.mcbaseball.game.GameTeam;
import com.cj.mcbaseball.game.LineupSlot;
import com.cj.mcbaseball.game.LiveGameDirector;
import com.cj.mcbaseball.game.Position;
import com.cj.mcbaseball.game.TeamSide;
import com.cj.mcbaseball.live.model.LiveFeed;
import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveHit;
import com.cj.mcbaseball.live.model.LivePitch;
import com.cj.mcbaseball.live.model.LivePlay;
import com.cj.mcbaseball.live.model.LivePlayer;
import com.cj.mcbaseball.live.session.LiveBaseballSession;
import com.cj.mcbaseball.live.session.LiveEvent;
import com.cj.mcbaseball.live.session.RecreationState;
import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import com.cj.mcbaseball.npc.NpcAnim;
import com.cj.mcbaseball.physics.AimSolver;
import com.cj.mcbaseball.physics.BallPhysics;
import com.cj.mcbaseball.physics.BaseballUnits;
import com.cj.mcbaseball.pitching.PitchRecord;
import com.cj.mcbaseball.pitching.PitchType;
import com.cj.mcbaseball.pitching.PitchingSystem;
import com.cj.mcbaseball.registry.ModSounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Recreates a real game with the mod's own NPCs on one field (Phase 4).
 *
 * <p>Owns a {@link BaseballGame} built from the real rosters and attaches itself as its
 * {@link LiveGameDirector}. The game animates and simulates everything (positions, windup, pitch flight,
 * catcher, swings, sounds, scoreboards); this class decides what the real game decided: which pitch, how
 * fast, where it crosses the plate, what the batter does with it, the call, the count, who bats and who
 * pitches. Events are pulled from the {@link LiveBaseballSession} one at a time, only when the previous one
 * has finished on the field, so NPC animation sets the pace.
 *
 * <p>Phase 4 covers pitches and the batter. Balls put in play fly with the real batted-ball data when the feed
 * has it and the nearest fielder runs at them; the official result (runners, outs, runs) is then applied.
 * Believable fielding plays for each result are Phase 5.
 *
 * <p>Server thread only.
 */
public final class LiveRecreation implements LiveGameDirector {

    /** Ticks between the end of one pitch and the next windup, normal / catching up. */
    static final int READY_TICKS_CATCH_UP = 8;
    static final int AFTER_PITCH_TICKS = 18;
    static final int AFTER_PITCH_TICKS_CATCH_UP = 6;
    /** Queue length at which the field hurries (shorter pauses; never skips anything). */
    static final int CATCH_UP_QUEUE = 4;
    static final int DECOR_LIFETIME = 120;

    record Plan(LiveEvent.Pitch event, PitchType type, double breakScale, double mph, LiveSwing swing, PitchCoordinateMapper.ZonePoint zone) {
    }

    private record Decor(BaseballEntity ball, long expires) {
    }

    private final ServerLevel level;
    private final BlockPos controller;
    private final FieldGeometry geo;
    private final GameSettings settings;
    private final long gameId;
    @Nullable
    private BaseballGame game;
    @Nullable
    private LiveRoster away;
    @Nullable
    private LiveRoster home;
    @Nullable
    private LiveBaseballSession session;
    @Nullable
    private Plan plan;
    private boolean windup;
    private long releaseTick;
    private long swingTick = -1L;
    private long contactTick = -1L;
    @Nullable
    private Vec3 contactPoint;
    private long busyUntil;
    private final List<Decor> decor = new ArrayList<>();
    private boolean finished;
    private long removeAt = Long.MAX_VALUE;
    private boolean closed;
    private long nowMillis;

    public LiveRecreation(ServerLevel level, BlockPos controller, FieldGeometry geo, GameSettings settings, long gameId) {
        this.level = level;
        this.controller = controller.immutable();
        this.geo = geo;
        this.settings = settings;
        this.gameId = gameId;
    }

    public long gameId() {
        return this.gameId;
    }

    public boolean isClosed() {
        return this.closed;
    }

    @Nullable
    public BaseballGame game() {
        return this.game;
    }

    /** Called every server tick (after the games have ticked). */
    public void tick(LiveBaseballSession s, long nowMillis) {
        if (this.closed) {
            return;
        }
        this.session = s;
        this.nowMillis = nowMillis;
        if (this.game == null) {
            LiveFeed feed = s.lastFeed();
            if (s.isSynced() && feed != null) {
                this.build(feed);
            }
            return;
        }
        BaseballGame g = this.game;
        if (GameManager.get(g.id) == null) {
            // Removed from outside (server stopping, controller broken...).
            this.closed = true;
            return;
        }
        this.tickDecor(g);
        if (this.finished) {
            if (g.tick >= this.removeAt) {
                this.close();
            }
            return;
        }
        this.tickSwingAndContact(g);
        if (this.plan == null && g.phase == GamePhase.PITCHING && g.pitch == null && g.tick >= this.busyUntil) {
            LiveEvent e = s.nextForPlayback(nowMillis);
            if (e != null) {
                this.start(g, s, e);
            }
        }
    }

    /** Ends the recreation: NPCs and balls are removed with the game. */
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        for (Decor d : this.decor) {
            d.ball.discard();
        }
        this.decor.clear();
        if (this.game != null) {
            this.game.director = null;
            GameManager.remove(this.game);
        }
    }

    // ------------------------------------------------------------------ setup

    private void build(LiveFeed feed) {
        LiveGameState s = feed.state();
        LiveTeamColors.Uniform[] u = LiveTeamColors.pick(s.away(), s.home());
        this.away = LiveRoster.build(TeamSide.AWAY, s.away(), u[0], s.awayLineup(), feed.awayPitcher());
        this.home = LiveRoster.build(TeamSide.HOME, s.home(), u[1], s.homeLineup(), feed.homePitcher());
        BaseballGame g = new BaseballGame(this.level, this.controller, this.geo, this.settings, this.home.data, this.away.data);
        g.director = this;
        this.game = g;
        RecreationState rec = this.session.recreation();
        g.inning = Math.max(1, rec.inning);
        g.top = rec.top;
        g.balls = rec.balls;
        g.strikes = rec.strikes;
        g.outs = Math.min(2, rec.outs);
        if (s.isBreak()) {
            // Between half-innings MLB already lists the next half's batter and pitcher: set up that half,
            // or the due-up hitter would be put in the other team's lineup.
            if (g.top) {
                g.top = false;
            } else {
                g.inning++;
                g.top = true;
            }
            g.balls = 0;
            g.strikes = 0;
            g.outs = 0;
        }
        g.away.runs = rec.awayScore;
        g.home.runs = rec.homeScore;
        copyInnings(s.awayInningRuns(), g.away);
        copyInnings(s.homeInningRuns(), g.home);
        g.away.hits = Math.max(0, s.awayTotals().hits());
        g.home.hits = Math.max(0, s.homeTotals().hits());
        this.applyOrders(g);
        GameManager.registerLive(g);
        ChunkKeeper.keepField(this.level, this.geo);
        g.ensureActors();
        g.livePositionForHalfInning();
        this.syncRunners(g, false);
        if (rec.pitcher.known()) {
            this.ensurePitcher(g, rec.pitcher);
        }
        LivePlayer batter = rec.batter.known() ? rec.batter : s.batter();
        if (batter.known()) {
            this.ensureBatter(g, batter);
        }
        g.preparePitch();
        MCBaseball.LOGGER.info("[MCBaseball Live] NPC recreation of {} @ {} started at {}", s.away().displayAbbr(), s.home().displayAbbr(),
            this.controller.toShortString());
    }

    private static void copyInnings(List<Integer> runs, GameTeam t) {
        Arrays.fill(t.runsByInning, 0);
        for (int i = 0; i < runs.size() && i < t.runsByInning.length; i++) {
            t.runsByInning[i] = Math.max(0, runs.get(i));
        }
    }

    private LiveRoster offenseRoster(BaseballGame g) {
        return g.top ? this.away : this.home;
    }

    private LiveRoster defenseRoster(BaseballGame g) {
        return g.top ? this.home : this.away;
    }

    private void applyOrders(BaseballGame g) {
        this.home.applyTo(g.home, g.top);
        this.away.applyTo(g.away, !g.top);
    }

    // ------------------------------------------------------------------ events

    private void start(BaseballGame g, LiveBaseballSession s, LiveEvent e) {
        if (e instanceof LiveEvent.Pitch p) {
            LivePlay play = p.play();
            if (play.inning() > 0 && (play.inning() != g.inning || play.isTop() != g.top)) {
                // Missed half-inning event (e.g. joined right at the change): switch sides first.
                this.halfInning(g, play.inning(), play.isTop(), 0);
            }
            this.ensurePitcher(g, play.pitcher());
            this.ensureBatter(g, play.batter());
            this.syncCount(g);
            this.plan = this.makePlan(p);
            this.windup = false;
            this.swingTick = -1L;
            this.contactTick = -1L;
            return;
        }
        s.markPlayed(e);
        if (e instanceof LiveEvent.HalfInning h) {
            this.halfInning(g, h.inning(), h.top(), 70);
        } else if (e instanceof LiveEvent.Action a) {
            if (a.event().isDowntime()) {
                return;
            }
            if (a.event().isSubstitution() && "P".equals(a.event().position()) && a.event().player().known()) {
                this.ensurePitcher(g, a.event().player());
            }
            this.syncRunners(g, true);
            g.outs = Math.min(2, s.recreation().outs);
            this.pauseFor(g, a.event().isSubstitution() ? 40 : 50);
        } else if (e instanceof LiveEvent.AtBatResult r) {
            this.applyResult(g, s, r.play());
        } else if (e instanceof LiveEvent.Status st) {
            if (st.to().state().isOver()) {
                this.finish(g, s);
            }
        } else if (e instanceof LiveEvent.CallChanged || e instanceof LiveEvent.ResultChanged) {
            this.syncCount(g);
            this.syncScore(g, s.recreation());
            this.pauseFor(g, 20);
        }
    }

    private void halfInning(BaseballGame g, int inning, boolean top, int pause) {
        g.removeBall();
        g.inning = Math.max(1, inning);
        g.top = top;
        g.outs = 0;
        g.balls = 0;
        g.strikes = 0;
        Arrays.fill(g.onBase, null);
        g.batter = null;
        this.applyOrders(g);
        g.ensureActors();
        g.livePositionForHalfInning();
        if (pause > 0) {
            this.pauseFor(g, pause);
        } else {
            g.preparePitch();
        }
    }

    private void applyResult(BaseballGame g, LiveBaseballSession s, LivePlay play) {
        RecreationState rec = s.recreation();
        this.syncScore(g, rec);
        g.outs = Math.min(2, rec.outs);
        g.balls = 0;
        g.strikes = 0;
        this.syncRunners(g, true);
        BaseballPlayerEntity batter = g.npc(g.batter);
        boolean batterOn = g.batter != null && this.isOnBase(g, g.batter);
        if (batter != null && !batterOn) {
            batter.clearHands();
            batter.moveTo(this.geo.dugout(g.batter.side), 0.7);
        }
        int pause = 45;
        switch (play.eventType()) {
            case "home_run" -> {
                g.playSound((SoundEvent) ModSounds.HOME_RUN.get(), this.geo.home, 2.0F);
                for (int i = 0; i < 40; i++) {
                    Vec3 c = this.geo.home.add(g.rng.nextGaussian() * 3.0, 2.0 + g.rng.nextDouble() * 4.0, g.rng.nextGaussian() * 3.0);
                    this.level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0.05);
                }
                if (batter != null) {
                    batter.setAnim(NpcAnim.CHEER);
                }
                pause = 110;
            }
            case "strikeout", "strikeout_double_play", "strikeout_triple_play" ->
                g.playSound((SoundEvent) ModSounds.STRIKEOUT.get(), this.geo.home, 1.0F);
            case "single", "double", "triple" -> pause = 60;
            default -> {
            }
        }
        if (rec.outs >= 3) {
            // The next half-inning event repositions everyone; the third out is shown until then.
            g.outs = 2;
        }
        this.pauseFor(g, pause);
    }

    private void finish(BaseballGame g, LiveBaseballSession s) {
        RecreationState rec = s.recreation();
        this.syncScore(g, rec);
        g.removeBall();
        TeamSide winner = rec.homeScore > rec.awayScore ? TeamSide.HOME : rec.awayScore > rec.homeScore ? TeamSide.AWAY : null;
        for (LineupSlot slot : g.allSlots()) {
            BaseballPlayerEntity n = g.npc(slot);
            if (n != null) {
                n.stopMoving();
                n.clearHands();
                n.setAnim(winner != null && slot.side == winner ? NpcAnim.CHEER : NpcAnim.NONE);
            }
        }
        g.playSound((SoundEvent) ModSounds.GAME_END.get(), this.geo.home, 1.5F);
        this.finished = true;
        this.removeAt = g.tick + BaseballConfig.GAME_OVER_CLEANUP_TICKS.get();
        g.pause(Integer.MAX_VALUE / 2, BaseballGame.NextStep.NEXT_PITCH);
    }

    private void pauseFor(BaseballGame g, int ticks) {
        this.busyUntil = g.tick + ticks;
        g.pause(ticks, BaseballGame.NextStep.NEXT_PITCH);
    }

    private boolean catchingUp() {
        return this.session != null && this.session.queue().size() >= CATCH_UP_QUEUE;
    }

    // ------------------------------------------------------------------ people

    private void ensureBatter(BaseballGame g, LivePlayer p) {
        if (!p.known()) {
            return;
        }
        if (this.defenseRoster(g).byPlayer.containsKey(p.id())) {
            // He plays for the fielding team: our half-inning is stale. Never put him in the other lineup.
            MCBaseball.LOGGER.warn("[MCBaseball Live] Batter {} is on the fielding team ({} {}); skipped", p.display(), g.top ? "top" : "bottom", g.inning);
            return;
        }
        LineupSlot slot = this.offenseRoster(g).batter(p);
        this.applyOrders(g);
        if (g.batter != slot) {
            g.liveSetBatter(slot);
        }
    }

    /** Pitching changes happen here, between pitches only, never while a ball is in the air. */
    private void ensurePitcher(BaseballGame g, LivePlayer p) {
        if (!p.known()) {
            return;
        }
        LiveRoster d = this.defenseRoster(g);
        if (this.offenseRoster(g).byPlayer.containsKey(p.id())) {
            MCBaseball.LOGGER.warn("[MCBaseball Live] Pitcher {} is on the batting team ({} {}); skipped", p.display(), g.top ? "top" : "bottom", g.inning);
            return;
        }
        LineupSlot old = d.setPitcher(p);
        if (old == null) {
            return;
        }
        this.applyOrders(g);
        g.ensureActors();
        LineupSlot now = d.fielders.get(Position.PITCHER);
        BaseballPlayerEntity leaving = g.npc(old);
        if (leaving != null) {
            leaving.clearHands();
            leaving.moveTo(this.geo.dugout(old.side), 0.6);
        }
        g.place(now, this.geo.spot(Position.PITCHER), this.geo.home);
        BaseballPlayerEntity arriving = g.npc(now);
        if (arriving != null) {
            arriving.clearHands();
            arriving.holdGlove(false);
        }
        if (g.pitch == null && (g.holder == old || g.holder == null)) {
            g.giveBallTo(now);
        }
    }

    private boolean isOnBase(BaseballGame g, LineupSlot s) {
        for (int b = 1; b <= 3; b++) {
            if (g.onBase[b] == s) {
                return true;
            }
        }
        return false;
    }

    /** Puts the runners the real game has on base onto the bases (walking/running there if {@code animate}). */
    private void syncRunners(BaseballGame g, boolean animate) {
        RecreationState rec = this.session.recreation();
        LiveRoster off = this.offenseRoster(g);
        LineupSlot[] next = new LineupSlot[4];
        String[] names = {null, "1B", "2B", "3B"};
        for (int b = 1; b <= 3; b++) {
            LivePlayer p = rec.bases.get(names[b]);
            if (p != null && p.known()) {
                next[b] = off.runner(p);
            }
        }
        this.applyOrders(g);
        g.ensureActors();
        for (int b = 1; b <= 3; b++) {
            LineupSlot prev = g.onBase[b];
            if (prev != null && !contains(next, prev) && prev != g.batter) {
                BaseballPlayerEntity n = g.npc(prev);
                if (n != null) {
                    n.moveTo(this.geo.dugout(prev.side), 0.7);
                }
            }
        }
        System.arraycopy(next, 0, g.onBase, 0, 4);
        for (int b = 1; b <= 3; b++) {
            if (next[b] == null) {
                continue;
            }
            BaseballPlayerEntity n = g.npc(next[b]);
            if (n != null) {
                n.clearHands();
                if (animate) {
                    n.moveTo(this.geo.base(b), 1.0);
                } else {
                    g.place(next[b], this.geo.base(b), this.geo.base(Math.min(b + 1, 4)));
                }
            }
        }
        g.net.dirty();
    }

    private static boolean contains(LineupSlot[] a, LineupSlot s) {
        for (LineupSlot x : a) {
            if (x == s) {
                return true;
            }
        }
        return false;
    }

    private void syncCount(BaseballGame g) {
        RecreationState rec = this.session.recreation();
        g.balls = Math.max(0, Math.min(3, rec.balls));
        g.strikes = Math.max(0, Math.min(2, rec.strikes));
        g.net.dirty();
    }

    private void syncScore(BaseballGame g, RecreationState rec) {
        int da = rec.awayScore - g.away.runs;
        int dh = rec.homeScore - g.home.runs;
        int idx = Math.max(0, Math.min(g.inning - 1, g.away.runsByInning.length - 1));
        if (da > 0) {
            g.away.runsByInning[idx] += da;
        }
        if (dh > 0) {
            g.home.runsByInning[idx] += dh;
        }
        g.away.runs = rec.awayScore;
        g.home.runs = rec.homeScore;
        g.net.dirty();
    }

    // ------------------------------------------------------------------ pitches

    Plan makePlan(LiveEvent.Pitch e) {
        LivePitch p = e.event().pitch();
        LivePitchTypes.Visual v = LivePitchTypes.of(p.typeCode());
        LiveSwing swing = LiveSwing.of(p);
        double mph = p.mph() > 0 ? p.mph() : (v.type().minMph + v.type().maxMph) / 2.0;
        return new Plan(e, v.type(), v.breakScale(), mph, swing, PitchCoordinateMapper.forPitch(p, swing));
    }

    /** NPC pitcher: windup and release of the real pitch. */
    @Override
    public void tickPitching(BaseballGame g) {
        Plan pl = this.plan;
        if (pl == null || g.pitch != null || this.finished) {
            return;
        }
        LineupSlot ps = g.pitcherSlot();
        BaseballPlayerEntity pn = g.npc(ps);
        if (pn == null || ps == null) {
            return;
        }
        if (g.holder != ps) {
            if (g.ball == null) {
                g.giveBallTo(ps);
            }
            return;
        }
        if (!g.batterSet()) {
            g.snapToPositions();
            return;
        }
        int ready = this.catchingUp() ? READY_TICKS_CATCH_UP : BaseballConfig.PITCH_READY_TICKS.get();
        if (g.phaseTicks < ready) {
            return;
        }
        if (!this.windup) {
            this.windup = true;
            this.releaseTick = g.tick + 14L;
            pn.setAnim(NpcAnim.forThrow(ThrowKind.of(pl.type())));
            pn.lookAtPos(this.geo.home.add(0.0, 1.0, 0.0));
            return;
        }
        if (g.tick >= this.releaseTick) {
            PitchingSystem.releaseAt(g, ps, pn, pl.type(), this.target(g, pl), pl.mph(), pl.breakScale() * BaseballConfig.PITCH_BREAK_SCALE.get(), 0.0);
        }
    }

    private Vec3 target(BaseballGame g, Plan pl) {
        if (pl.swing() == LiveSwing.HIT_BY_PITCH) {
            LivingEntity b = g.actor(g.batter);
            double lat = b != null ? this.geo.lateral(b.position()) * 0.85 : 0.0;
            return this.geo.zonePoint(lat, 0.95);
        }
        return this.geo.zonePoint(pl.zone().lateral(), pl.zone().height());
    }

    /** NPC batter: plan exactly what the real batter did. */
    @Override
    public void onPitchReleased(BaseballGame g, PitchRecord pr) {
        Plan pl = this.plan;
        if (pl == null || g.ball == null || !pl.swing().swings()) {
            return;
        }
        BaseballEntity b = g.ball;
        AimSolver.Crossing c = AimSolver.toPlane(
            b.getCenter(), b.getDeltaMovement(), b.getSpin(), BallPhysics.Params.fromConfig(), this.geo.home.add(this.geo.forward.scale(0.35)),
            this.geo.forward.reverse(), 80
        );
        if (c == null) {
            return;
        }
        this.contactTick = g.tick + Math.max(1L, Math.round(c.ticks()));
        this.contactPoint = c.point();
        this.swingTick = Math.max(g.tick + 1L, this.contactTick - (pl.swing().isBunt() ? 6L : 2L));
    }

    private void tickSwingAndContact(BaseballGame g) {
        Plan pl = this.plan;
        PitchRecord pr = g.pitch;
        if (pl == null || pr == null || pr.resolved) {
            return;
        }
        if (this.swingTick >= 0L && g.tick >= this.swingTick) {
            this.swingTick = -1L;
            pr.swung = true;
            LivingEntity batter = g.actor(g.batter);
            if (batter instanceof BaseballPlayerEntity n) {
                n.setAnim(pl.swing().isBunt() ? NpcAnim.BUNT : NpcAnim.SWING);
            }
            if (batter != null) {
                this.level.playSound(null, batter.getX(), batter.getY(), batter.getZ(), (SoundEvent) ModSounds.BAT_SWING.get(), SoundSource.PLAYERS,
                    pl.swing().isBunt() ? 0.2F : 0.8F, 1.0F);
            }
        }
        if (this.contactTick >= 0L && g.tick >= this.contactTick && this.contactPoint != null) {
            this.contactTick = -1L;
            switch (pl.swing()) {
                case FOUL_TIP -> this.level.playSound(null, this.contactPoint.x, this.contactPoint.y, this.contactPoint.z,
                    (SoundEvent) ModSounds.BAT_HIT.get(), SoundSource.PLAYERS, 0.35F, 1.3F);
                case FOUL, BUNT_FOUL -> {
                    this.hitBall(g, this.foulVelocity(pl), 6.0);
                    this.endPitchAfterContact(g, pr, this.catchingUp() ? AFTER_PITCH_TICKS_CATCH_UP : 30);
                }
                case IN_PLAY -> {
                    Vec3 vel = this.inPlayVelocity(pl);
                    Vec3 spin = BallPhysics.backspin(vel, vel.y > 0.0 ? 9.0 : -3.0);
                    BaseballEntity ball = this.hitBall(g, vel, spin);
                    int flight = this.sendFielder(g, pl, ball, vel, spin);
                    LivingEntity batter = g.actor(g.batter);
                    if (batter instanceof BaseballPlayerEntity n) {
                        n.clearHands();
                        n.moveTo(this.geo.base(1), 1.0);
                    }
                    this.endPitchAfterContact(g, pr, Math.max(40, Math.min(110, flight + 30)));
                }
                default -> {
                }
            }
        }
    }

    private BaseballEntity hitBall(BaseballGame g, Vec3 vel, double backspin) {
        return this.hitBall(g, vel, BallPhysics.backspin(vel, backspin));
    }

    /** The pitched ball is replaced by a batted ball that only looks real: the game's rules ignore it. */
    private BaseballEntity hitBall(BaseballGame g, Vec3 vel, Vec3 spin) {
        g.removeBall();
        BaseballEntity b = BaseballEntity.create(this.level, this.contactPoint);
        b.setGame(g.id);
        LivingEntity batter = g.actor(g.batter);
        b.launch(batter, vel);
        b.setSpin(spin);
        this.level.addFreshEntity(b);
        this.decor.add(new Decor(b, g.tick + DECOR_LIFETIME));
        this.level.playSound(null, this.contactPoint.x, this.contactPoint.y, this.contactPoint.z, (SoundEvent) ModSounds.BAT_HIT.get(),
            SoundSource.PLAYERS, 1.0F, 1.0F);
        return b;
    }

    private void endPitchAfterContact(BaseballGame g, PitchRecord pr, int pause) {
        pr.contact = true;
        pr.resolved = true;
        this.finishPitch(g, pause);
    }

    /** Catcher has it / it hit the batter: the real call stands. */
    @Override
    public void onPitchResolved(BaseballGame g) {
        this.finishPitch(g, this.catchingUp() ? AFTER_PITCH_TICKS_CATCH_UP : AFTER_PITCH_TICKS);
    }

    private void finishPitch(BaseballGame g, int pause) {
        Plan pl = this.plan;
        this.plan = null;
        this.swingTick = -1L;
        this.contactTick = -1L;
        if (pl != null && this.session != null) {
            this.session.markPlayed(pl.event());
        }
        this.syncCount(g);
        this.pauseFor(g, pause);
    }

    /** Fouls go back or to the side into foul territory; the feed rarely has data for them. */
    private Vec3 foulVelocity(Plan pl) {
        long h = pl.event().event().playId().hashCode() * 2654435761L;
        double u = ((h >>> 8) & 0xFFFF) / 65535.0;
        double side = ((h >>> 30) & 1) == 0 ? 1.0 : -1.0;
        double spray = side * (100.0 + u * 60.0);
        double launch = 20.0 + ((h >>> 40) & 0xFF) / 255.0 * 35.0;
        return this.velocity(pl.swing().isBunt() ? 25.0 : 70.0, launch, spray);
    }

    /** Real exit velocity, launch angle and direction when the feed has them (see LiveHitGeometry). */
    Vec3 inPlayVelocity(Plan pl) {
        LiveHit hit = pl.event().event().hit();
        return this.velocity(LiveHitGeometry.exitMph(hit), LiveHitGeometry.launchDegrees(hit), LiveHitGeometry.sprayAngle(hit));
    }

    private Vec3 velocity(double exitMph, double launchDeg, double sprayDeg) {
        double s = Math.toRadians(sprayDeg);
        double l = Math.toRadians(launchDeg);
        Vec3 dirH = this.geo.forward.scale(Math.cos(s)).add(this.geo.right.scale(Math.sin(s)));
        double speed = BaseballUnits.blocksPerTickFromDisplayMph(exitMph) * BaseballConfig.EXIT_VELOCITY_SCALE.get();
        return dirH.scale(Math.cos(l) * speed).add(0.0, Math.sin(l) * speed, 0.0);
    }

    /** The real fielder the ball went to (or the closest one) runs to where it will come down. Returns flight ticks. */
    private int sendFielder(BaseballGame g, Plan pl, BaseballEntity ball, Vec3 vel, Vec3 spin) {
        BallPhysics.Prediction land = BallPhysics.predictLanding(ball.getCenter(), vel, spin, BallPhysics.Params.fromConfig(), this.geo.groundY, 200);
        Vec3 spot = land != null ? land.point() : ball.getCenter().add(vel.scale(20));
        LiveHit hit = pl.event().event().hit();
        Position pos = hit == null ? null : byNumber(hit.location());
        LineupSlot fielder = pos == null ? null : g.defense().at(pos);
        if (fielder == null) {
            double best = Double.MAX_VALUE;
            for (LineupSlot s : g.defense().order) {
                LivingEntity a = g.actor(s);
                if (a != null && s.position != Position.CATCHER && a.position().distanceToSqr(spot) < best) {
                    best = a.position().distanceToSqr(spot);
                    fielder = s;
                }
            }
        }
        BaseballPlayerEntity n = g.npc(fielder);
        if (n != null) {
            n.moveTo(spot, 1.0);
        }
        for (LineupSlot s : g.defense().order) {
            BaseballPlayerEntity f = g.npc(s);
            if (f != null) {
                f.lookAtPos(spot);
            }
        }
        return land != null ? land.ticks() : 40;
    }

    @Nullable
    static Position byNumber(String n) {
        return switch (n) {
            case "1" -> Position.PITCHER;
            case "2" -> Position.CATCHER;
            case "3" -> Position.FIRST_BASE;
            case "4" -> Position.SECOND_BASE;
            case "5" -> Position.THIRD_BASE;
            case "6" -> Position.SHORTSTOP;
            case "7" -> Position.LEFT_FIELD;
            case "8" -> Position.CENTER_FIELD;
            case "9" -> Position.RIGHT_FIELD;
            default -> null;
        };
    }

    private void tickDecor(BaseballGame g) {
        Iterator<Decor> it = this.decor.iterator();
        while (it.hasNext()) {
            Decor d = it.next();
            if (!d.ball.isAlive() || g.tick >= d.expires) {
                d.ball.discard();
                it.remove();
            }
        }
    }

    /** Lines for the debug panel. */
    public List<String> debugLines() {
        List<String> l = new ArrayList<>();
        BaseballGame g = this.game;
        if (g == null) {
            l.add("NPCs: waiting for first data");
            return l;
        }
        LineupSlot ps = g.pitcherSlot();
        l.add("NPCs: phase " + g.phase + (this.plan != null ? "  throwing " + this.plan.event().label() + " (" + this.plan.swing() + ", "
            + (this.plan.zone().fromData() ? "real location" : "no location in feed") + ")" : "") + (this.finished ? "  FINAL" : ""));
        l.add("NPCs: pitcher " + (ps == null ? "-" : ps.displayName()) + "   batter " + (g.batter == null ? "-" : g.batter.displayName()));
        return l;
    }
}
