package com.cj.mcbaseball.game;

import com.cj.mcbaseball.MCBaseball;
import com.cj.mcbaseball.batting.BattingSystem;
import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.entity.BaseballEntity;
import com.cj.mcbaseball.fielding.FieldingSystem;
import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import com.cj.mcbaseball.npc.NpcAnim;
import com.cj.mcbaseball.npc.ai.NpcDirector;
import com.cj.mcbaseball.physics.BallMotion;
import com.cj.mcbaseball.pitching.PitchRecord;
import com.cj.mcbaseball.pitching.PitchType;
import com.cj.mcbaseball.pitching.PitchingSystem;
import com.cj.mcbaseball.registry.ModEntities;
import com.cj.mcbaseball.registry.ModItems;
import com.cj.mcbaseball.registry.ModSounds;
import com.cj.mcbaseball.stats.CareerStats;
import com.cj.mcbaseball.stats.GameStats;
import com.cj.mcbaseball.stats.StatLine;
import com.cj.mcbaseball.team.TeamData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BaseballGame {
    public final UUID id = UUID.randomUUID();
    public final ServerLevel level;
    public final BlockPos controllerPos;
    public final FieldGeometry geo;
    public final GameSettings settings;
    public final GameTeam home;
    public final GameTeam away;
    public final Random rng = new Random();
    public final GameStats stats = new GameStats();
    public final NpcDirector ai;
    public final FieldingSystem fielding;
    public final GameBroadcaster net;
    public GamePhase phase = GamePhase.WAITING;
    public int phaseTicks;
    public long tick;
    public int inning = 1;
    public boolean top = true;
    public int balls;
    public int strikes;
    public int outs;
    public final LineupSlot[] onBase = new LineupSlot[4];
    @Nullable
    public LineupSlot batter;
    @Nullable
    public PitchRecord pitch;
    @Nullable
    public PlayTracker play;
    @Nullable
    public BaseballEntity ball;
    @Nullable
    public LineupSlot holder;
    @Nullable
    private Vec3 lastHolderPos;
    private BaseballGame.NextStep afterPause = BaseballGame.NextStep.NEXT_PITCH;
    private int pauseTicks;
    public final Map<UUID, PitchType> humanPitchType = new HashMap<>();
    @Nullable
    public BattingSystem.PendingContact pendingContact;
    @Nullable
    public BattingSystem.NpcSwing npcSwing;
    public long humanPitcherReadySince;
    public double npcBatErrScale = 1.0;
    @Nullable
    public String winnerName;
    private boolean ended;
    public final Map<String, Integer> counters = new TreeMap<>();
    /** Live Mode: when set, real-world data decides pitches, swings and calls (see LiveGameDirector). */
    @Nullable
    public LiveGameDirector director;

    public void count(String k) {
        this.counters.merge(k, 1, Integer::sum);
    }

    public BaseballGame(ServerLevel level, BlockPos controllerPos, FieldGeometry geo, GameSettings settings, TeamData homeTeam, TeamData awayTeam) {
        this.level = level;
        this.controllerPos = controllerPos;
        this.geo = geo;
        this.settings = settings.copy();
        this.home = new GameTeam(TeamSide.HOME, homeTeam);
        this.away = new GameTeam(TeamSide.AWAY, awayTeam);
        this.ai = new NpcDirector(this);
        this.fielding = new FieldingSystem(this);
        this.net = new GameBroadcaster(this);
    }

    public GameTeam offense() {
        return this.top ? this.away : this.home;
    }

    public GameTeam defense() {
        return this.top ? this.home : this.away;
    }

    public GameTeam team(TeamSide s) {
        return s == TeamSide.HOME ? this.home : this.away;
    }

    public boolean isDefense(@Nullable LineupSlot s) {
        return s != null && s.side == this.defense().side;
    }

    public boolean isOffense(@Nullable LineupSlot s) {
        return s != null && s.side == this.offense().side;
    }

    public Difficulty difficulty() {
        return this.settings.difficulty;
    }

    public List<LineupSlot> allSlots() {
        List<LineupSlot> l = new ArrayList<>(this.home.order);
        l.addAll(this.away.order);
        return l;
    }

    @Nullable
    public LineupSlot pitcherSlot() {
        LineupSlot p = this.defense().at(Position.PITCHER);
        if (p != null) {
            return p;
        } else {
            for (Position pos : Position.HUMAN_PRIORITY) {
                LineupSlot s = this.defense().at(pos);
                if (s != null) {
                    return s;
                }
            }

            return null;
        }
    }

    @Nullable
    public LivingEntity actor(@Nullable LineupSlot s) {
        if (s == null || !s.enabled) {
            return null;
        } else if (s.isHuman()) {
            ServerPlayer p = this.level.getServer().getPlayerList().getPlayer(s.humanId);
            return p != null && p.level() == this.level && p.isAlive() ? p : null;
        } else if (s.npcEntityId == null) {
            return null;
        } else {
            if (this.level.getEntity(s.npcEntityId) instanceof BaseballPlayerEntity b && b.isAlive()) {
                return b;
            }

            return null;
        }
    }

    @Nullable
    public BaseballPlayerEntity npc(@Nullable LineupSlot s) {
        return this.actor(s) instanceof BaseballPlayerEntity b ? b : null;
    }

    @Nullable
    public LineupSlot slotOf(@Nullable Entity e) {
        if (e == null) {
            return null;
        } else if (e instanceof BaseballPlayerEntity b) {
            LineupSlot s = b.slot();
            return s != null && this.id.equals(b.gameId()) ? s : null;
        } else {
            if (e instanceof Player p) {
                for (LineupSlot s : this.allSlots()) {
                    if (p.getUUID().equals(s.humanId) && !s.humanAbsent) {
                        return s;
                    }
                }
            }

            return null;
        }
    }

    public boolean isParticipant(UUID player) {
        for (LineupSlot s : this.allSlots()) {
            if (player.equals(s.humanId)) {
                return true;
            }
        }

        return false;
    }

    public void begin() {
        ChunkKeeper.keepField(this.level, this.geo);
        this.ensureActors();
        this.positionForHalfInning();
        this.setPhase(GamePhase.PREGAME);
        this.net.call(Component.translatable("mcbaseball.call.play_ball").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}));
        this.playSound((SoundEvent)ModSounds.GAME_START.get(), this.geo.home, 1.5F);

        for (LineupSlot s : this.allSlots()) {
            if (s.isHuman() && this.actor(s) instanceof ServerPlayer sp) {
                this.net
                    .personal(
                        sp,
                        GameMessages.Kind.INFO,
                        Component.translatable("mcbaseball.info.you_play", new Object[]{s.position.displayName(), this.team(s.side).data.name})
                            .withStyle(ChatFormatting.AQUA)
                    );
            }
        }
    }

    private void setPhase(GamePhase p) {
        this.phase = p;
        this.phaseTicks = 0;
        this.net.dirty();
    }

    public void tick() {
        if (!this.ended) {
            this.tick++;
            this.phaseTicks++;
            if (this.tick % 10L == 0L) {
                this.checkHumans();
            }

            if (this.tick % 20L == 1L) {
                ChunkKeeper.keepField(this.level, this.geo);
            }

            if (this.ball != null && this.ball.isAlive() && this.tick % 2L == 0L) {
                ChunkKeeper.keepBall(this.level, this.ball.position());
            }

            switch (this.phase) {
                case PREGAME:
                    if (this.phaseTicks >= (Integer)BaseballConfig.PREGAME_TICKS.get()) {
                        this.startHalfInning();
                    }
                    break;
                case PITCHING:
                    this.tickPitching();
                    break;
                case BALL_IN_PLAY:
                    this.tickPlay();
                    break;
                case PLAY_OVER:
                    if (this.phaseTicks >= this.pauseTicks) {
                        this.doNextStep();
                    }
                    break;
                case SIDE_CHANGE:
                    if (this.phaseTicks >= (Integer)BaseballConfig.SIDE_CHANGE_TICKS.get()) {
                        this.startHalfInning();
                    }
                    break;
                case GAME_OVER:
                    if (this.phaseTicks >= (Integer)BaseballConfig.GAME_OVER_CLEANUP_TICKS.get()) {
                        GameManager.remove(this);
                    }
            }

            if (this.holder != null) {
                LivingEntity h = this.actor(this.holder);
                if (h != null) {
                    this.lastHolderPos = h.position();
                }
            }

            this.ai.tick();
            this.net.tick();
        }
    }

    public void cleanup() {
        if (!this.ended) {
            this.ended = true;
            this.removeBall();

            for (LineupSlot s : this.allSlots()) {
                if (s.npcEntityId != null) {
                    Entity e = this.level.getEntity(s.npcEntityId);
                    if (e != null) {
                        e.discard();
                    }
                }

                if (s.humanId != null) {
                    ServerPlayer p = this.level.getServer().getPlayerList().getPlayer(s.humanId);
                    if (p != null) {
                        GameKit.cleanup(p, this.id);
                    }
                }
            }

            this.net.sendInactive();
        }
    }

    public void ensureActors() {
        for (LineupSlot s : this.allSlots()) {
            if (s.humanReturning) {
                s.humanReturning = false;
                s.humanAbsent = false;
                s.enabled = true;
            }

            if (s.enabled) {
                if (s.isHuman()) {
                    if (s.npcEntityId != null) {
                        Entity e = this.level.getEntity(s.npcEntityId);
                        if (e != null) {
                            e.discard();
                        }

                        s.npcEntityId = null;
                    }
                } else if (this.npc(s) == null) {
                    this.spawnNpc(s, this.geo.dugout(s.side));
                }
            }
        }
    }

    private void spawnNpc(LineupSlot s, Vec3 at) {
        BaseballPlayerEntity e = (BaseballPlayerEntity)((EntityType)ModEntities.BASEBALL_PLAYER.get()).create(this.level);
        if (e != null) {
            e.setup(this, s, this.team(s.side).data);
            Vec3 g = this.ground(at);
            e.moveTo(g.x, g.y, g.z, FieldGeometry.yawToward(g, this.geo.home), 0.0F);
            this.level.addFreshEntity(e);
            s.npcEntityId = e.getUUID();
        }
    }

    public Vec3 ground(Vec3 p) {
        MutableBlockPos m = new MutableBlockPos();
        int x = (int)Math.floor(p.x);
        int z = (int)Math.floor(p.z);
        int y0 = (int)Math.floor(p.y);

        for (int y = y0 + 3; y >= y0 - 6; y--) {
            m.set(x, y, z);
            VoxelShape shape = this.level.getBlockState(m).getCollisionShape(this.level, m);
            if (!shape.isEmpty()) {
                double top = (double)y + shape.max(Axis.Y);
                if (this.level.noCollision(new AABB(p.x - 0.3, top + 0.01, p.z - 0.3, p.x + 0.3, top + 1.8, p.z + 0.3))) {
                    return new Vec3(p.x, top, p.z);
                }
            }
        }

        return p;
    }

    public void place(@Nullable LineupSlot s, Vec3 at, Vec3 facing) {
        LivingEntity a = this.actor(s);
        if (a != null) {
            Vec3 g = this.ground(at);
            float yaw = FieldGeometry.yawToward(g, facing);
            if (a instanceof ServerPlayer p) {
                p.teleportTo(this.level, g.x, g.y, g.z, yaw, 0.0F);
            } else if (a instanceof BaseballPlayerEntity n) {
                n.stopMoving();
                n.moveTo(g.x, g.y, g.z, yaw, 0.0F);
                n.setYHeadRot(yaw);
                n.setYBodyRot(yaw);
            }
        }
    }

    private void positionForHalfInning() {
        for (LineupSlot s : this.defense().order) {
            if (s.enabled) {
                this.place(s, this.geo.spot(s.position), this.geo.home);
                LivingEntity a = this.actor(s);
                if (a instanceof ServerPlayer p) {
                    GameKit.ensureGlove(p, this.id, s.position == Position.CATCHER);
                    GameKit.gloveToOffhand(p);
                }

                if (a instanceof BaseballPlayerEntity n) {
                    n.clearHands();
                    n.holdGlove(s.position == Position.CATCHER);
                    n.setAnim(NpcAnim.NONE);
                }
            }
        }

        int i = 0;
        Vec3 dug = this.geo.dugout(this.offense().side);
        Vec3 along = this.geo.forward;

        for (LineupSlot sx : this.offense().order) {
            if (sx.enabled) {
                int idx = this.offense().order.indexOf(sx);
                this.place(sx, this.geo.dugoutSpot(this.offense().side, idx, this.offense().order.size()), this.geo.dugoutFacing(this.offense().side));
                i++;
                if (this.actor(sx) instanceof BaseballPlayerEntity n) {
                    n.clearHands();
                    n.setAnim(NpcAnim.NONE);
                }
            }
        }
    }

    /** Live Mode: defense to their spots, offense to the dugout (same as a normal half-inning start). */
    public void livePositionForHalfInning() {
        this.positionForHalfInning();
    }

    /** Live Mode: bring this exact batter to the plate (instead of the next one in the game's own order). */
    public void liveSetBatter(LineupSlot next) {
        if (this.batter != null && this.batter != next && !this.isOnBase(this.batter) && this.isOffense(this.batter)) {
            GameTeam bt = this.team(this.batter.side);
            int idx = Math.max(0, bt.order.indexOf(this.batter));
            this.place(this.batter, this.geo.dugoutSpot(this.batter.side, idx, Math.max(1, bt.order.size())), this.geo.dugoutFacing(this.batter.side));
            if (this.actor(this.batter) instanceof BaseballPlayerEntity old) {
                old.clearHands();
                old.setAnim(NpcAnim.NONE);
            }
        }
        this.batter = next;
        this.ensureActors();
        this.place(next, this.geo.batterBox(next.batsRight()), this.geo.mound);
        if (this.actor(next) instanceof BaseballPlayerEntity n) {
            n.clearHands();
            n.holdBat();
            n.setAnim(NpcAnim.BAT_STANCE);
        }
        this.net.dirty();
    }

    private void startHalfInning() {
        this.outs = 0;
        Arrays.fill(this.onBase, null);
        this.ensureActors();
        this.positionForHalfInning();
        this.net
            .call(
                Component.translatable(this.top ? "mcbaseball.call.top" : "mcbaseball.call.bottom", new Object[]{this.inning})
                    .withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD})
            );
        this.setupAtBat();
    }

    private void setupAtBat() {
        this.ensureActors();
        if (this.batter != null && !this.isOnBase(this.batter) && this.isOffense(this.batter)) {
            GameTeam bt = this.team(this.batter.side);
            this.place(
                this.batter, this.geo.dugoutSpot(this.batter.side, bt.order.indexOf(this.batter), bt.order.size()), this.geo.dugoutFacing(this.batter.side)
            );
            if (this.actor(this.batter) instanceof BaseballPlayerEntity n) {
                n.clearHands();
            }
        }

        this.batter = this.offense().nextBatter();
        this.balls = 0;
        this.strikes = 0;
        if (this.batter == null) {
            this.gameOver();
        } else {
            this.place(this.batter, this.geo.batterBox(this.batter.batsRight()), this.geo.mound);
            LivingEntity a = this.actor(this.batter);
            if (a instanceof ServerPlayer p) {
                GameKit.ensureBat(p, this.id);
            }

            if (a instanceof BaseballPlayerEntity n) {
                n.holdBat();
                n.setAnim(NpcAnim.BAT_STANCE);
            }

            this.net.info(Component.translatable("mcbaseball.info.now_batting", new Object[]{this.batter.displayName()}).withStyle(ChatFormatting.GRAY));
            this.preparePitch();
        }
    }

    public static double horizDist(Vec3 a, Vec3 b) {
        return horiz(a, b);
    }

    static double horiz(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public boolean batterSet() {
        if (this.batter == null) {
            return false;
        } else {
            if (this.actor(this.batter) instanceof BaseballPlayerEntity bn && horiz(bn.position(), this.geo.batterBox(this.batter.batsRight())) > 0.35) {
                return false;
            }

            for (int b = 1; b <= 3; b++) {
                LineupSlot r = this.onBase[b];
                if (r != null && this.actor(r) instanceof BaseballPlayerEntity rn && horiz(rn.position(), this.geo.base(b)) > 1.6) {
                    return false;
                }
            }

            return true;
        }
    }

    public void snapToPositions() {
        this.count("snap_to_positions");
        if (this.batter != null
            && this.actor(this.batter) instanceof BaseballPlayerEntity bn
            && horiz(bn.position(), this.geo.batterBox(this.batter.batsRight())) > 0.35) {
            this.place(this.batter, this.geo.batterBox(this.batter.batsRight()), this.geo.mound);
            bn.holdBat();
            bn.setAnim(NpcAnim.BAT_STANCE);
        }

        for (int b = 1; b <= 3; b++) {
            LineupSlot r = this.onBase[b];
            if (r != null) {
                LivingEntity var4 = this.actor(r);
                if (var4 instanceof BaseballPlayerEntity) {
                    BaseballPlayerEntity rn = (BaseballPlayerEntity)var4;
                    if (horiz(rn.position(), this.geo.base(b)) > 1.6) {
                        this.place(r, this.geo.base(b), this.geo.base(Math.min(b + 1, 4)));
                    }
                }
            }
        }
    }

    public boolean isOnBaseSlot(LineupSlot s) {
        return this.isOnBase(s);
    }

    private boolean isOnBase(LineupSlot s) {
        for (int b = 1; b <= 3; b++) {
            if (this.onBase[b] == s) {
                return true;
            }
        }

        return false;
    }

    public void preparePitch() {
        this.ensureActors();
        this.removeBall();
        this.pitch = null;
        this.play = null;
        this.pendingContact = null;
        this.npcSwing = null;
        LineupSlot ps = this.pitcherSlot();
        this.giveBallTo(ps);

        for (int b = 1; b <= 3; b++) {
            LineupSlot r = this.onBase[b];
            if (r != null) {
                LivingEntity a = this.actor(r);
                if (a instanceof ServerPlayer) {
                    ServerPlayer p = (ServerPlayer)a;
                    if (!this.geo.touching(p.position(), b)) {
                        this.place(r, this.geo.base(b), this.geo.base(b + 1));
                    }
                }
            }
        }

        if (this.batter != null
            && this.actor(this.batter) instanceof ServerPlayer bp
            && horiz(bp.position(), this.geo.batterBox(this.batter.batsRight())) > 1.0) {
            this.place(this.batter, this.geo.batterBox(this.batter.batsRight()), this.geo.mound);
        }

        this.ai.resetForPitch();
        this.humanPitcherReadySince = this.tick;
        this.setPhase(GamePhase.PITCHING);
    }

    public void giveBallTo(@Nullable LineupSlot s) {
        this.removeBall();
        this.takeBallFromEveryone();
        this.holder = s;
        LivingEntity a = this.actor(s);
        if (a instanceof ServerPlayer p) {
            GameKit.giveGameBall(p, this.id);
        } else if (a instanceof BaseballPlayerEntity n) {
            n.holdBall(true);
        }

        if (a != null) {
            this.lastHolderPos = a.position();
        }

        this.net.dirty();
    }

    public void clearHolders() {
        this.takeBallFromEveryone();
    }

    public boolean isPausedForNextPitch() {
        return this.phase == GamePhase.PLAY_OVER && this.afterPause == BaseballGame.NextStep.NEXT_PITCH;
    }

    private void takeBallFromEveryone() {
        for (LineupSlot s : this.allSlots()) {
            LivingEntity a = this.actor(s);
            if (a instanceof ServerPlayer p) {
                GameKit.removeGameBalls(p, this.id);
            } else if (a instanceof BaseballPlayerEntity) {
                BaseballPlayerEntity n = (BaseballPlayerEntity)a;
                if (n.getMainHandItem().getItem() == ModItems.BASEBALL.get()) {
                    n.holdBall(false);
                }
            }
        }

        this.holder = null;
    }

    public void removeBall() {
        if (this.ball != null && this.ball.isAlive()) {
            this.ball.discard();
        }

        this.ball = null;
    }

    public void setLooseBall(BaseballEntity b) {
        this.ball = b;
        this.holder = null;
        this.net.dirty();
    }

    public void onPossession(LivingEntity e, boolean fromAir) {
        LineupSlot s = this.slotOf(e);
        if (s != null) {
            this.ball = null;
            this.holder = s;
            this.lastHolderPos = e.position();
            if (e instanceof BaseballPlayerEntity n) {
                n.holdBall(true);
            }

            this.net.dirty();
            if (this.phase == GamePhase.PITCHING && this.pitch != null && !this.pitch.contact) {
                this.pitch.catcherCaught = true;
            } else {
                PlayTracker p = this.play;
                if (this.phase == GamePhase.BALL_IN_PLAY && p != null) {
                    if (p.holderSince != s) {
                        p.holderSince = s;
                        p.holderSinceTick = this.tick;
                    }

                    p.throwTargetBase = -1;
                    if (p.batted
                        && p.inFlight
                        && fromAir
                        && s.position == Position.CATCHER
                        && this.tick - p.startTick <= 6L
                        && this.geo.planeDistance(e.position()) < 0.5) {
                        this.count("foul_tip");
                        this.play = null;
                        this.removeBall();
                        this.strikes++;
                        if (this.strikes >= 3) {
                            this.stats.of(this.batter).ab++;
                            this.stats.of(this.batter).so++;
                            this.stats.of(this.pitch != null ? this.pitch.pitcher : this.pitcherSlot()).pk++;
                            this.stats.of(s).po++;
                            this.outs++;
                            this.net
                                .call(Component.translatable("mcbaseball.call.strikeout").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}));
                            this.playSound((SoundEvent)ModSounds.STRIKEOUT.get(), this.geo.home, 1.0F);
                            this.pause(
                                (Integer)BaseballConfig.PLAY_OVER_TICKS.get(),
                                this.outs >= 3 ? BaseballGame.NextStep.SIDE_CHANGE : BaseballGame.NextStep.NEXT_BATTER
                            );
                        } else {
                            this.net.call(Component.translatable("mcbaseball.call.foul_tip", new Object[]{this.strikes}).withStyle(ChatFormatting.YELLOW));
                            this.pause(25, BaseballGame.NextStep.NEXT_PITCH);
                        }
                    } else {
                        if (p.batted && p.inFlight && fromAir && this.isDefense(s)) {
                            p.inFlight = false;
                            p.caughtInAir = true;
                            boolean foulTerritory = !this.geo.isFairDirection(e.position());
                            p.fair = FairState.FAIR;
                            Runner br = p.batterRunner();
                            if (br != null) {
                                this.recordOut(br, s, false);
                            }

                            this.net
                                .call(
                                    Component.translatable(foulTerritory ? "mcbaseball.call.foul_out" : "mcbaseball.call.fly_out")
                                        .withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
                                );

                            for (Runner r : p.runners) {
                                if (!r.isBatter() && r.active()) {
                                    LivingEntity ra = this.actor(r.slot);
                                    boolean onStart = ra != null && this.geo.touching(ra.position(), r.startBase);
                                    if (!onStart || r.base > r.startBase) {
                                        r.mustTagUp = true;
                                        r.base = r.startBase;
                                        r.target = r.startBase;
                                    }
                                }
                            }
                        } else if (p.batted && p.inFlight) {
                            p.inFlight = false;
                        }

                        this.ai.onPossession(s);
                    }
                }
            }
        }
    }

    public boolean canHumanCatch(Player p) {
        LineupSlot s = this.slotOf(p);
        if (s == null || !this.isDefense(s)) {
            return false;
        } else {
            return this.phase != GamePhase.PITCHING ? this.phase == GamePhase.BALL_IN_PLAY : this.pitch != null && s.position == Position.CATCHER;
        }
    }

    public void onBallThrown(LineupSlot thrower, BaseballEntity b, int targetBase) {
        this.setLooseBall(b);
        if (this.play != null) {
            this.play.lastThrower = thrower;
            this.play.lastThrowTick = this.tick;
            this.play.throwTargetBase = targetBase;
        }

        this.stats.of(thrower);
    }

    public void onBallBounce(BaseballEntity b, Vec3 pos) {
        if (b == this.ball) {
            PlayTracker p = this.play;
            if (p != null && this.phase == GamePhase.BALL_IN_PLAY) {
                p.throwTargetBase = -1;
                if (p.batted && p.inFlight) {
                    p.inFlight = false;
                    if (p.fair == FairState.UNDECIDED && this.geo.pastBases(pos)) {
                        if (this.geo.isFairDirection(pos)) {
                            p.fair = FairState.FAIR;
                        } else {
                            this.foulBall(p);
                        }
                    }
                }
            }
        }
    }

    public void onBallDeflect(BaseballEntity b, Entity hit) {
        if (b == this.ball) {
            if (this.phase == GamePhase.PITCHING && this.pitch != null && !this.pitch.contact && !this.pitch.resolved && this.actor(this.batter) == hit) {
                this.pitch.hbp = true;
                PitchingSystem.resolvePitch(this);
            } else {
                if (this.phase == GamePhase.PITCHING && this.pitch != null && !this.pitch.contact) {
                    LineupSlot hs = this.slotOf(hit);
                    String who = hs == null ? hit.getType().toShortString() : hs.side + "_" + hs.position.abbr;
                    this.count("pitch_hit_" + who);
                    if (this.counters.getOrDefault("pitch_hit_" + who, 0) <= 2) {
                        MCBaseball.LOGGER
                            .info(
                                "[PitchDebug] pitch deflected off {} at field(fwd,up,lat)=({},{},{})",
                                new Object[]{
                                    who,
                                    String.format("%.2f", this.geo.planeDistance(hit.position())),
                                    String.format("%.2f", hit.getY() - this.geo.groundY),
                                    String.format("%.2f", this.geo.lateral(hit.position()))
                                }
                            );
                    }
                }

                if (this.play != null && this.play.batted && this.play.inFlight) {
                    this.play.inFlight = false;
                }
            }
        }
    }

    private void tickPitching() {
        PitchingSystem.tick(this);
    }

    public void applyCount(PitchingSystem.Call call) {
        LineupSlot ps = this.pitch != null ? this.pitch.pitcher : this.pitcherSlot();
        this.count("call_" + call.name());
        switch (call) {
            case BALL:
                this.balls++;
                if (this.balls >= 4) {
                    this.stats.of(this.batter).bb++;
                    this.stats.of(ps).pbb++;
                    this.net.call(Component.translatable("mcbaseball.call.walk").withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}));
                    this.forceAdvance(this.batter);
                    this.pause((Integer)BaseballConfig.PLAY_OVER_TICKS.get(), this.walkOffCheck(BaseballGame.NextStep.NEXT_BATTER));
                    return;
                }

                this.net.call(Component.translatable("mcbaseball.call.ball", new Object[]{this.balls}).withStyle(ChatFormatting.GREEN));
                break;
            case HIT_BY_PITCH:
                this.net.call(Component.translatable("mcbaseball.call.hbp").withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}));
                this.forceAdvance(this.batter);
                this.pause((Integer)BaseballConfig.PLAY_OVER_TICKS.get(), this.walkOffCheck(BaseballGame.NextStep.NEXT_BATTER));
                return;
            case STRIKE_LOOKING:
            case STRIKE_SWINGING:
                this.strikes++;
                if (this.strikes >= 3) {
                    this.stats.of(this.batter).ab++;
                    this.stats.of(this.batter).so++;
                    this.stats.of(ps).pk++;
                    this.stats.of(this.defense().at(Position.CATCHER)).po++;
                    this.outs++;
                    this.net
                        .call(
                            Component.translatable(call == PitchingSystem.Call.STRIKE_LOOKING ? "mcbaseball.call.strikeout_looking" : "mcbaseball.call.strikeout")
                                .withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
                        );
                    this.playSound((SoundEvent)ModSounds.STRIKEOUT.get(), this.geo.home, 1.0F);
                    this.pause(
                        (Integer)BaseballConfig.PLAY_OVER_TICKS.get(), this.outs >= 3 ? BaseballGame.NextStep.SIDE_CHANGE : BaseballGame.NextStep.NEXT_BATTER
                    );
                    return;
                }

                this.net
                    .call(
                        Component.translatable(
                                call == PitchingSystem.Call.STRIKE_LOOKING ? "mcbaseball.call.strike_looking" : "mcbaseball.call.strike_swinging",
                                new Object[]{this.strikes}
                            )
                            .withStyle(ChatFormatting.YELLOW)
                    );
        }

        this.pause(20, BaseballGame.NextStep.NEXT_PITCH);
    }

    private void forceAdvance(@Nullable LineupSlot b) {
        if (b != null) {
            if (this.onBase[1] != null) {
                if (this.onBase[2] != null) {
                    if (this.onBase[3] != null) {
                        this.scoreRun(this.onBase[3], b);
                    }

                    this.onBase[3] = this.onBase[2];
                }

                this.onBase[2] = this.onBase[1];
            }

            this.onBase[1] = b;

            for (int i = 1; i <= 3; i++) {
                if (this.onBase[i] != null) {
                    this.place(this.onBase[i], this.geo.base(i), this.geo.base(i + 1));
                }
            }

            if (this.actor(b) instanceof BaseballPlayerEntity n) {
                n.clearHands();
                n.setAnim(NpcAnim.NONE);
            }
        }
    }

    public void startBattedPlay(double exitMph, double launchDeg, Vec3 contactPoint) {
        this.count("in_play");
        PlayTracker p = new PlayTracker(true, this.tick, this.outs, this.batter);
        p.exitMph = exitMph;
        p.launchDeg = launchDeg;
        p.contactPoint = contactPoint;
        Runner br = new Runner(this.batter, 0);
        br.target = 1;
        p.runners.add(br);

        for (int b = 1; b <= 3; b++) {
            if (this.onBase[b] != null) {
                Runner r = new Runner(this.onBase[b], b);
                r.stealing = this.ai.isStealing(this.onBase[b]);
                r.target = b;
                p.runners.add(r);
            }
        }

        this.play = p;
        if (this.pitch != null) {
            this.pitch.contact = true;
            this.pitch.resolved = true;
        }

        this.holder = null;
        if (this.actor(this.batter) instanceof BaseballPlayerEntity n) {
            n.clearHands();
            n.setAnim(NpcAnim.NONE);
        }

        this.setPhase(GamePhase.BALL_IN_PLAY);
        this.ai.onBallInPlay();
    }

    public void startNonBattedPlay() {
        PlayTracker p = new PlayTracker(false, this.tick, this.outs, this.batter);

        for (int b = 1; b <= 3; b++) {
            if (this.onBase[b] != null) {
                Runner r = new Runner(this.onBase[b], b);
                r.stealing = this.ai.isStealing(this.onBase[b]);
                r.target = r.stealing ? b + 1 : b;
                p.runners.add(r);
            }
        }

        this.play = p;
        this.setPhase(GamePhase.BALL_IN_PLAY);
        this.ai.onBallInPlay();
    }

    private void tickPlay() {
        PlayTracker p = this.play;
        if (p == null) {
            this.pause(20, BaseballGame.NextStep.NEXT_PITCH);
        } else if (p.endAt >= 0L) {
            if (this.tick >= p.endAt) {
                this.finishPlay();
            }
        } else {
            this.updateBallState(p);
            if (this.phase == GamePhase.BALL_IN_PLAY && this.play == p && p.endAt < 0L) {
                this.updateRunners(p);
                this.checkOuts(p);
                if (this.outs >= 3) {
                    this.finishPlay();
                } else {
                    this.checkSettled(p);
                    if (this.play == p && this.tick - p.startTick > (long)((Integer)BaseballConfig.PLAY_TIMEOUT_TICKS.get()).intValue()) {
                        this.count("play_timeout");
                        MCBaseball.LOGGER.warn("[Baseball] play timed out: {} | {}", this.debugLine(), this.playDebug(p));
                        this.finishPlay();
                    }
                }
            }
        }
    }

    @Nullable
    private Vec3 ballOrHolderPos() {
        if (this.ball != null && this.ball.isAlive()) {
            return this.ball.getCenter();
        } else {
            LivingEntity h = this.actor(this.holder);
            return h != null ? h.position() : this.lastHolderPos;
        }
    }

    private void updateBallState(PlayTracker p) {
        if (this.ball != null && this.ball.isAlive() && (this.ball.getY() < this.geo.groundY - 4.0 || this.geo.distFromHome(this.ball.getCenter()) > 400.0)
            )
         {
            this.count("ball_out_of_play");
            MCBaseball.LOGGER.warn("[Baseball] ball out of play: {}", this.playDebug(p));
            this.removeBall();
            this.giveBallTo(this.pitcherSlot());
        }

        if (p.batted && !p.caughtInAir) {
            Vec3 pos = this.ballOrHolderPos();
            if (pos != null) {
                boolean loose = this.ball != null && this.ball.isAlive();
                if (loose && p.inFlight && this.geo.beyondFence(pos)) {
                    if (this.geo.isFairDirection(pos)) {
                        this.homeRun(p);
                    } else {
                        this.foulBall(p);
                    }
                } else {
                    if (p.fair == FairState.UNDECIDED && !p.inFlight) {
                        boolean decideNow = this.geo.planeDistance(pos) < -0.3
                            || this.geo.pastBases(pos)
                            || this.holder != null
                            || loose && this.ball.getMotion() == BallMotion.RESTING;
                        if (decideNow) {
                            if (!(this.geo.planeDistance(pos) >= -0.3) || !this.geo.isFairDirection(pos)) {
                                this.foulBall(p);
                                return;
                            }

                            p.fair = FairState.FAIR;
                        }
                    }

                    if (p.fair == FairState.FAIR && !p.inFlight && loose && this.geo.beyondFence(pos)) {
                        this.groundRuleDouble(p);
                    }
                }
            }
        }
    }

    private void updateRunners(PlayTracker p) {
        for (Runner r : p.runners) {
            if (r.active()) {
                LivingEntity a = this.actor(r.slot);
                if (a != null) {
                    Vec3 feet = a.position();
                    if (r.mustTagUp) {
                        if (r.startBase >= 1 && this.geo.touching(feet, r.startBase)) {
                            r.mustTagUp = false;
                            r.base = r.startBase;
                        }
                    } else {
                        int next = r.base + 1;
                        if (next <= 4 && this.geo.touching(feet, next) && (p.batted || r.stealing || next <= r.startBase + 1)) {
                            r.base = next;
                            if (r.isBatter() && next == 1) {
                                r.overrunImmuneUntil = this.tick + 60L;
                            }

                            if (next == 4) {
                                r.scored = true;
                                r.scoredTick = this.tick;
                                this.net
                                    .info(Component.translatable("mcbaseball.info.scores", new Object[]{r.slot.displayName()}).withStyle(ChatFormatting.GREEN));
                                if (this.actor(r.slot) instanceof BaseballPlayerEntity n) {
                                    n.moveTo(this.geo.dugout(r.slot.side), 0.7);
                                }
                            }

                            this.net.dirty();
                        }
                    }
                }
            }
        }
    }

    public boolean isSafe(Runner r, Vec3 feet) {
        if (r.mustTagUp) {
            return false;
        } else {
            return r.base >= 1 && r.base <= 3 && this.geo.touching(feet, r.base)
                ? true
                : r.isBatter()
                    && r.base == 1
                    && this.tick < r.overrunImmuneUntil
                    && FieldGeometry.flatDist(feet, this.geo.base(2)) > FieldGeometry.flatDist(feet, this.geo.base(1));
        }
    }

    private void checkOuts(PlayTracker p) {
        if (this.holder != null && this.isDefense(this.holder)) {
            LivingEntity h = this.actor(this.holder);
            if (h != null) {
                Vec3 hf = h.position();
                int hb = this.geo.baseAt(hf);
                double reachBase = (Double)BaseballConfig.TAG_REACH.get();

                for (Runner r : p.runners) {
                    if (r.active()) {
                        if (hb > 0) {
                            boolean force = p.isForced(r) && r.base < r.startBase + 1 && hb == r.startBase + 1;
                            boolean appeal = r.mustTagUp && r.startBase >= 1 && hb == r.startBase;
                            if (force || appeal) {
                                this.recordOut(r, this.holder, true);
                                this.net
                                    .call(
                                        Component.translatable(appeal ? "mcbaseball.call.doubled_off" : "mcbaseball.call.force_out")
                                            .withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
                                    );
                                continue;
                            }
                        }

                        LivingEntity ra = this.actor(r.slot);
                        if (ra != null && ra != h) {
                            double reach = reachBase - (this.tick < r.slideUntil ? (Double)BaseballConfig.SLIDE_TAG_REDUCTION.get() : 0.0);
                            if (FieldGeometry.flatDist(ra.position(), hf) <= reach
                                && Math.abs(ra.getY() - hf.y) < 2.0
                                && !this.isSafe(r, ra.position())) {
                                this.recordOut(r, this.holder, false);
                                this.net
                                    .call(
                                        Component.translatable("mcbaseball.call.tag_out").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
                                    );
                            }

                            if (this.outs >= 3) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    public void recordOut(Runner r, @Nullable LineupSlot by, boolean force) {
        if (!r.out) {
            r.out = true;
            this.outs++;
            PlayTracker p = this.play;
            if (p != null) {
                p.outsOnPlay++;
                if (this.outs >= 3) {
                    p.thirdOutTick = this.tick;
                    p.forceThirdOut = force || r.isBatter() && r.base < 1;
                }

                if (by != null) {
                    this.stats.of(by).po++;
                    if (p.lastThrower != null && p.lastThrower != by && p.lastThrowTick >= p.startTick) {
                        this.stats.of(p.lastThrower).a++;
                    }
                }
            }

            if (this.actor(r.slot) instanceof BaseballPlayerEntity n) {
                n.moveTo(this.geo.dugout(r.slot.side), 0.7);
            }

            this.net.dirty();
        }
    }

    private void checkSettled(PlayTracker p) {
        boolean held = this.holder != null && this.isDefense(this.holder);
        boolean allSafe = true;

        for (Runner r : p.runners) {
            if (r.active()) {
                LivingEntity a = this.actor(r.slot);
                if (a != null) {
                    if (!this.isSafe(r, a.position())) {
                        allSafe = false;
                        break;
                    }

                    if (r.slot.usesNpc() && r.target != r.base) {
                        allSafe = false;
                        break;
                    }
                }
            }
        }

        boolean decided = !p.batted || p.fair == FairState.FAIR;
        if (allSafe && decided) {
            p.settledTicks++;
        } else {
            p.settledTicks = 0;
        }

        boolean stuck = held && p.holderSince == this.holder && this.tick - p.holderSinceTick > 200L;
        if (held && p.settledTicks >= 20 || p.settledTicks >= 50 || stuck) {
            this.finishPlay();
        }
    }

    private void homeRun(PlayTracker p) {
        this.count("home_run");
        p.homeRun = true;
        p.fair = FairState.FAIR;
        p.inFlight = false;

        for (Runner r : p.runners) {
            if (r.active()) {
                r.scored = true;
                r.scoredTick = this.tick;
                r.base = 4;
            }
        }

        this.removeBall();
        this.net.call(Component.translatable("mcbaseball.call.home_run").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}));
        this.playSound((SoundEvent)ModSounds.HOME_RUN.get(), this.geo.home, 2.0F);

        for (int i = 0; i < 40; i++) {
            Vec3 c = this.geo.home.add(this.rng.nextGaussian() * 3.0, 2.0 + this.rng.nextDouble() * 4.0, this.rng.nextGaussian() * 3.0);
            this.level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0.05);
        }

        for (Runner rx : p.runners) {
            if (this.actor(rx.slot) instanceof BaseballPlayerEntity n) {
                n.setAnim(NpcAnim.CHEER);
            }
        }

        p.endAt = this.tick + 70L;
    }

    private void groundRuleDouble(PlayTracker p) {
        p.groundRuleDouble = true;

        for (Runner r : p.runners) {
            if (r.active()) {
                r.mustTagUp = false;
                r.base = r.isBatter() ? 2 : Math.max(r.base, r.startBase + 2);
                if (r.base >= 4) {
                    r.base = 4;
                    r.scored = true;
                    r.scoredTick = this.tick;
                }
            }
        }

        this.removeBall();
        this.net.call(Component.translatable("mcbaseball.call.ground_rule_double").withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}));
        p.endAt = this.tick + 30L;
    }

    private void foulBall(PlayTracker p) {
        this.count("foul");
        p.fair = FairState.FOUL;
        this.removeBall();
        if (this.strikes < 2) {
            this.strikes++;
        }

        this.net.call(Component.translatable("mcbaseball.call.foul").withStyle(ChatFormatting.YELLOW));
        this.play = null;
        this.pause(30, BaseballGame.NextStep.NEXT_PITCH);
    }

    private void finishPlay() {
        PlayTracker p = this.play;
        if (p != null) {
            this.play = null;
            int runs = 0;

            for (Runner r : p.runners) {
                if (r.scored) {
                    boolean counts = p.thirdOutTick < 0L || !p.forceThirdOut && r.scoredTick < p.thirdOutTick;
                    if (counts) {
                        runs++;
                        LineupSlot rbiTo = p.batted && p.errors == 0 && p.outsOnPlay < 2 ? p.batter : null;
                        this.scoreRun(r.slot, rbiTo);
                    }
                }
            }

            Arrays.fill(this.onBase, null);
            if (this.outs < 3) {
                List<Runner> order = new ArrayList<>(p.runners);
                order.sort((a, bx) -> Integer.compare(bx.startBase, a.startBase));

                for (Runner rx : order) {
                    if (rx.active()) {
                        int b = rx.mustTagUp ? rx.startBase : rx.base;
                        if (b != 0) {
                            while (b > 0 && this.onBase[b] != null) {
                                b--;
                            }

                            if (b == 0) {
                                this.recordOutQuiet(rx);
                            } else {
                                this.onBase[b] = rx.slot;
                                rx.base = b;
                            }
                        } else if (p.batted) {
                            this.recordOutQuiet(rx);
                        }
                    }
                }
            }

            if (p.batted) {
                this.creditBatter(p, runs);
            }

            for (Runner rxx : p.runners) {
                if ((rxx.out || rxx.scored) && (rxx.slot != this.batter || rxx.out || rxx.scored)) {
                    this.place(rxx.slot, this.geo.dugout(rxx.slot.side), this.geo.home);
                }
            }

            for (int b = 1; b <= 3; b++) {
                if (this.onBase[b] != null && this.actor(this.onBase[b]) instanceof ServerPlayer) {
                    this.place(this.onBase[b], this.geo.base(b), this.geo.base(b + 1));
                }
            }

            this.removeBall();
            BaseballGame.NextStep next;
            if (this.outs >= 3) {
                next = BaseballGame.NextStep.SIDE_CHANGE;
            } else if (p.batted) {
                next = BaseballGame.NextStep.NEXT_BATTER;
            } else {
                next = BaseballGame.NextStep.NEXT_PITCH;
            }

            this.pause((Integer)BaseballConfig.PLAY_OVER_TICKS.get(), this.walkOffCheck(next));
        }
    }

    private void recordOutQuiet(Runner r) {
        if (!r.out) {
            r.out = true;
            this.outs++;
        }
    }

    private void creditBatter(PlayTracker p, int runs) {
        LineupSlot bs = p.batter;
        StatLine bl = this.stats.of(bs);
        Runner br = p.batterRunner();
        if (p.homeRun) {
            bl.ab++;
            bl.h++;
            bl.hr++;
            bl.rbi += runs;
            this.offense().hits++;
        } else if (!p.caughtInAir) {
            bl.ab++;
            if (br != null && !br.out) {
                boolean fieldersChoice = p.outsOnPlay > 0;
                if (!fieldersChoice && p.errors <= 0) {
                    bl.h++;
                    this.offense().hits++;
                    int bases = Math.max(1, Math.min(3, br.base));
                    if (!p.groundRuleDouble) {
                        this.net
                            .call(Component.translatable("mcbaseball.call.hit." + bases).withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}));
                    }
                } else {
                    this.net
                        .call(Component.translatable(p.errors > 0 ? "mcbaseball.call.error" : "mcbaseball.call.fielders_choice").withStyle(ChatFormatting.AQUA));
                }
            }
        } else {
            boolean sacFly = runs > 0 && p.outsAtStart < 2;
            if (!sacFly) {
                bl.ab++;
            }
        }
    }

    private void scoreRun(LineupSlot runner, @Nullable LineupSlot rbiTo) {
        GameTeam t = this.offense();
        t.runs++;
        t.runsByInning[Math.min(this.inning - 1, t.runsByInning.length - 1)]++;
        this.stats.of(runner).r++;
        if (rbiTo != null) {
            this.stats.of(rbiTo).rbi++;
        }

        this.stats.of(this.pitch != null ? this.pitch.pitcher : this.pitcherSlot()).ra++;
        this.net.dirty();
    }

    public void pause(int ticks, BaseballGame.NextStep next) {
        this.pauseTicks = ticks;
        this.afterPause = next;
        this.setPhase(GamePhase.PLAY_OVER);
    }

    private BaseballGame.NextStep walkOffCheck(BaseballGame.NextStep next) {
        if (!this.top && this.inning >= this.settings.innings && this.home.runs > this.away.runs) {
            this.net.call(Component.translatable("mcbaseball.call.walk_off").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}));
            return BaseballGame.NextStep.GAME_OVER;
        } else {
            return next;
        }
    }

    private void doNextStep() {
        switch (this.afterPause) {
            case NEXT_PITCH:
                this.preparePitch();
                break;
            case NEXT_BATTER:
                this.setupAtBat();
                break;
            case SIDE_CHANGE:
                this.sideChange();
                break;
            case GAME_OVER:
                this.gameOver();
        }
    }

    private void sideChange() {
        this.removeBall();
        this.takeBallFromEveryone();
        if (this.top) {
            if (this.inning >= this.settings.innings && this.home.runs > this.away.runs) {
                this.gameOver();
                return;
            }

            this.top = false;
        } else {
            if (this.inning >= this.settings.innings && this.home.runs != this.away.runs) {
                this.gameOver();
                return;
            }

            if (this.inning >= this.settings.innings && !this.settings.extraInnings) {
                this.gameOver();
                return;
            }

            if (this.inning >= 25) {
                this.gameOver();
                return;
            }

            this.top = true;
            this.inning++;
        }

        this.outs = 0;
        this.balls = 0;
        this.strikes = 0;
        Arrays.fill(this.onBase, null);
        this.batter = null;
        this.net.call(Component.translatable("mcbaseball.call.side_retired").withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}));
        this.setPhase(GamePhase.SIDE_CHANGE);
    }

    public void gameOver() {
        if (this.phase != GamePhase.GAME_OVER) {
            this.removeBall();
            this.takeBallFromEveryone();
            GameTeam winner = this.home.runs > this.away.runs ? this.home : (this.away.runs > this.home.runs ? this.away : null);
            this.winnerName = winner == null ? null : winner.data.name;

            for (LineupSlot s : this.allSlots()) {
                BaseballPlayerEntity n = this.npc(s);
                if (n != null) {
                    n.stopMoving();
                    n.setAnim(winner != null && s.side == winner.side ? NpcAnim.CHEER : NpcAnim.NONE);
                }
            }

            this.playSound((SoundEvent)ModSounds.GAME_END.get(), this.geo.home, 1.5F);
            CompoundTag summary = this.buildSummary();
            CareerStats cs = CareerStats.get(this.level.getServer());
            cs.record(this.stats);
            cs.setLastGame(fieldKey(this.level, this.controllerPos), summary);
            this.net.sendGameOver(summary);
            this.setPhase(GamePhase.GAME_OVER);
        }
    }

    public static String fieldKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.toShortString();
    }

    public CompoundTag buildSummary() {
        CompoundTag t = new CompoundTag();
        t.putString("Home", this.home.data.name);
        t.putString("Away", this.away.data.name);
        t.putString("HomeAbbr", this.home.abbr());
        t.putString("AwayAbbr", this.away.abbr());
        t.putInt("HomeRuns", this.home.runs);
        t.putInt("AwayRuns", this.away.runs);
        t.putInt("HomeHits", this.home.hits);
        t.putInt("AwayHits", this.away.hits);
        t.putInt("Innings", this.inning);
        t.putString("Winner", this.winnerName == null ? "" : this.winnerName);
        ListTag lines = new ListTag();

        for (StatLine l : this.stats.lines.values()) {
            lines.add(l.save());
        }

        t.put("Lines", lines);
        return t;
    }

    public void onHumanLogout(UUID id) {
        for (LineupSlot s : this.allSlots()) {
            if (id.equals(s.humanId)) {
                s.humanAbsent = true;
                s.humanReturning = false;
                if (!this.settings.npcAutoFill) {
                    s.enabled = false;
                }
            }
        }

        if (this.holder != null && this.holder.humanAbsent) {
            this.holder = null;
            if (this.lastHolderPos != null && this.phase == GamePhase.BALL_IN_PLAY) {
                BaseballEntity b = BaseballEntity.create(this.level, this.lastHolderPos.add(0.0, 0.5, 0.0));
                b.setGame(this.id);
                b.launch(null, Vec3.ZERO);
                this.level.addFreshEntity(b);
                this.setLooseBall(b);
            }
        }
    }

    public void onHumanLogin(UUID id) {
        for (LineupSlot s : this.allSlots()) {
            if (id.equals(s.humanId) && s.humanAbsent) {
                s.humanReturning = true;
            }
        }
    }

    private void checkHumans() {
        if (this.holder != null
            && this.holder.isHuman()
            && this.actor(this.holder) instanceof ServerPlayer p
            && !GameKit.hasGameBall(p, this.id)
            && this.ball == null
            && (this.phase != GamePhase.PITCHING || this.pitch == null)) {
            this.holder = null;
            BaseballEntity b = BaseballEntity.create(this.level, p.position().add(0.0, 0.6, 0.0));
            b.setGame(this.id);
            b.launch(null, Vec3.ZERO);
            this.level.addFreshEntity(b);
            this.setLooseBall(b);
        }
    }

    public void playSound(SoundEvent s, Vec3 at, float vol) {
        this.level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, 1.0F);
    }

    public PitchType humanPitchType(UUID player) {
        return this.humanPitchType.getOrDefault(player, PitchType.FOUR_SEAM);
    }

    public String playDebug(PlayTracker p) {
        StringBuilder sb = new StringBuilder("fair=" + p.fair + " inFlight=" + p.inFlight + " caught=" + p.caughtInAir);
        if (this.ball != null && this.ball.isAlive()) {
            sb.append(" ballAge=")
                .append(this.ball.tickCount)
                .append(" abs=")
                .append(this.ball.blockPosition().toShortString())
                .append(" entTicking=")
                .append(this.level.isPositionEntityTicking(this.ball.blockPosition()))
                .append(" ball@")
                .append(this.fmt(this.ball.getCenter()))
                .append(" v=")
                .append(String.format("%.3f", this.ball.getSpeed()))
                .append(" ")
                .append(this.ball.getMotion());
        }

        LivingEntity h = this.actor(this.holder);
        if (h != null) {
            sb.append(" holder@").append(this.fmt(h.position()));
        }

        for (Runner r : p.runners) {
            LivingEntity a = this.actor(r.slot);
            sb.append(" | R")
                .append(r.startBase)
                .append(" base=")
                .append(r.base)
                .append(" tgt=")
                .append(r.target)
                .append(r.out ? " OUT" : "")
                .append(r.scored ? " SCORED" : "")
                .append(r.mustTagUp ? " TAGUP" : "")
                .append(a == null ? " noactor" : " @" + this.fmt(a.position()) + (this.isSafe(r, a.position()) ? " safe" : ""));
        }

        return sb.toString();
    }

    private String fmt(Vec3 v) {
        Vec3 rel = v.subtract(this.geo.home);
        return String.format("(%.1f,%.1f,%.1f)", rel.dot(this.geo.forward), v.y - this.geo.groundY, rel.dot(this.geo.right));
    }

    public String debugLine() {
        return String.format(
            "%s %s %s%d  %s %d-%d  B%d S%d O%d  bases[%s%s%s]  holder=%s ball=%s",
            this.id.toString().substring(0, 8),
            this.phase,
            this.top ? "T" : "B",
            this.inning,
            this.away.abbr() + "-" + this.home.abbr(),
            this.away.runs,
            this.home.runs,
            this.balls,
            this.strikes,
            this.outs,
            this.onBase[1] != null ? "1" : "-",
            this.onBase[2] != null ? "2" : "-",
            this.onBase[3] != null ? "3" : "-",
            this.holder == null ? "none" : this.holder.position.abbr + "/" + this.holder.side,
            this.ball != null && this.ball.isAlive()
        );
    }

    public static enum NextStep {
        NEXT_PITCH,
        NEXT_BATTER,
        SIDE_CHANGE,
        GAME_OVER;
    }
}
