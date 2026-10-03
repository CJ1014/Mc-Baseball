package com.cj.mcbaseball.entity;

import com.cj.mcbaseball.MCBaseball;
import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.GameKit;
import com.cj.mcbaseball.game.GameManager;
import com.cj.mcbaseball.item.GloveItem;
import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import com.cj.mcbaseball.physics.BallMotion;
import com.cj.mcbaseball.physics.BallPhysics;
import com.cj.mcbaseball.pitching.PitchingSystem;
import com.cj.mcbaseball.registry.ModEntities;
import com.cj.mcbaseball.registry.ModItems;
import com.cj.mcbaseball.registry.ModSounds;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class BaseballEntity extends Entity implements ItemSupplier {
    public static final float SIZE = 0.25F;
    private static final EntityDataAccessor<Integer> DATA_MOTION = SynchedEntityData.defineId(BaseballEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_SPIN = SynchedEntityData.defineId(BaseballEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> DATA_GAME_BALL = SynchedEntityData.defineId(BaseballEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BOUNCES = SynchedEntityData.defineId(BaseballEntity.class, EntityDataSerializers.INT);
    private final BallPhysics.State phys = new BallPhysics.State();
    @Nullable
    private UUID throwerId;
    private int ticksSinceLaunch;
    private int restingTicks;
    @Nullable
    private UUID gameId;
    private int launchId;
    @Nullable
    private Vector3f lastSyncedSpin;
    private final BallPhysics.Listener serverListener = new BallPhysics.Listener() {
        @Override
        public boolean onSegment(BallPhysics.State s, Vec3 from, Vec3 to) {
            return BaseballEntity.this.handleEntityContact(from, to);
        }

        @Override
        public void onBounce(BallPhysics.State s, Direction face, BlockPos blockPos, double impactSpeed) {
            BaseballEntity.this.playBounceSound(blockPos, impactSpeed);
            BaseballGame g = BaseballEntity.this.game();
            if (g != null) {
                g.onBallBounce(BaseballEntity.this, s.pos);
            }
        }
    };
    public static int debugRemovals = 0;

    public BaseballEntity(EntityType<? extends BaseballEntity> type, Level level) {
        super(type, level);
    }

    public static BaseballEntity create(Level level, Vec3 center) {
        BaseballEntity ball = new BaseballEntity((EntityType<? extends BaseballEntity>)ModEntities.BASEBALL.get(), level);
        ball.setCenter(center);
        return ball;
    }

    public void launch(@Nullable Entity thrower, Vec3 velocity) {
        this.throwerId = thrower == null ? null : thrower.getUUID();
        this.launchId++;
        this.ticksSinceLaunch = 0;
        this.restingTicks = 0;
        this.phys.vel = velocity;
        this.phys.motion = BallMotion.FLYING;
        this.phys.bounces = 0;
        this.entityData.set(DATA_BOUNCES, 0);
        this.setSpin(Vec3.ZERO);
        this.setDeltaMovement(velocity);
        this.syncMotion();
    }

    public void setSpin(Vec3 spin) {
        this.phys.spin = spin;
        this.syncSpin(true);
    }

    private void syncSpin(boolean force) {
        Vector3f cur = (Vector3f)this.entityData.get(DATA_SPIN);
        Vec3 sp = this.phys.spin;
        double diff = Math.abs((double)cur.x() - sp.x) + Math.abs((double)cur.y() - sp.y) + Math.abs((double)cur.z() - sp.z);
        if (force || diff > 0.02 + 0.03 * sp.length()) {
            this.entityData.set(DATA_SPIN, new Vector3f((float)sp.x, (float)sp.y, (float)sp.z));
        }
    }

    public Vec3 getCenter() {
        return this.position().add(0.0, 0.125, 0.0);
    }

    public void setCenter(Vec3 c) {
        this.setPos(c.x, c.y - 0.125, c.z);
    }

    public BallMotion getMotion() {
        return BallMotion.byId((Integer)this.entityData.get(DATA_MOTION));
    }

    public int getBounces() {
        return this.phys.bounces;
    }

    @Nullable
    public UUID getThrowerId() {
        return this.throwerId;
    }

    public double getSpeed() {
        return this.getDeltaMovement().length();
    }

    public Vec3 getSpin() {
        return this.phys.spin;
    }

    public int launchId() {
        return this.launchId;
    }

    public void setGame(@Nullable UUID id) {
        this.gameId = id;
        this.entityData.set(DATA_GAME_BALL, id != null);
    }

    @Nullable
    public UUID gameId() {
        return this.gameId;
    }

    public boolean isGameBall() {
        return (Boolean)this.entityData.get(DATA_GAME_BALL);
    }

    public int syncedBounces() {
        return (Integer)this.entityData.get(DATA_BOUNCES);
    }

    @Nullable
    private BaseballGame game() {
        return this.gameId == null ? null : GameManager.get(this.gameId);
    }

    public void tick() {
        super.tick();
        this.ticksSinceLaunch++;
        this.phys.pos = this.getCenter();
        this.phys.vel = this.getDeltaMovement();
        this.phys.inWater = this.isInWater();
        if (this.level().isClientSide) {
            this.phys.motion = this.getMotion();
            if (this.tickCount % 10 == 0 || this.lastSyncedSpin != this.entityData.get(DATA_SPIN)) {
                Vector3f w = (Vector3f)this.entityData.get(DATA_SPIN);
                this.lastSyncedSpin = w;
                this.phys.spin = new Vec3((double)w.x(), (double)w.y(), (double)w.z());
            }
        }

        BallPhysics.tick(this.level(), this, this.phys, BallPhysics.Params.fromConfig(), this.level().isClientSide ? null : this.serverListener);
        if (!this.level().isClientSide && !this.isRemoved() && BallPhysics.unstick(this.level(), this, this.phys)) {
            MCBaseball.LOGGER.debug("[Baseball] ball was inside a block and was pushed out at {}", this.phys.pos);
        }

        if (!this.isRemoved()) {
            this.setCenter(this.phys.pos);
            this.setDeltaMovement(this.phys.vel);
            if (!this.level().isClientSide) {
                if (this.gameId != null && this.tickCount % 20 == 0 && this.game() == null) {
                    this.discard();
                    return;
                }

                if ((Integer)this.entityData.get(DATA_BOUNCES) != this.phys.bounces) {
                    this.entityData.set(DATA_BOUNCES, Math.min(this.phys.bounces, 1000));
                }

                this.syncMotion();
                this.syncSpin(false);
                this.tryScoopUp();
                this.tickIdleDespawn();
            }
        }
    }

    private void syncMotion() {
        if (this.getMotion() != this.phys.motion) {
            this.entityData.set(DATA_MOTION, this.phys.motion.ordinal());
        }
    }

    private void tickIdleDespawn() {
        int limit = (Integer)BaseballConfig.IDLE_DESPAWN_TICKS.get();
        if (this.phys.motion == BallMotion.RESTING) {
            if (this.gameId == null && limit > 0 && ++this.restingTicks > limit) {
                this.spawnAtLocation(new ItemStack((ItemLike)ModItems.BASEBALL.get()));
                this.discard();
            }
        } else {
            this.restingTicks = 0;
        }
    }

    private boolean isImmuneThrower(Entity e) {
        return this.throwerId != null && this.throwerId.equals(e.getUUID()) && this.ticksSinceLaunch < (Integer)BaseballConfig.THROWER_IMMUNITY_TICKS.get();
    }

    private boolean handleEntityContact(Vec3 from, Vec3 to) {
        double maxReach = (Double)BaseballConfig.BAREHAND_CATCH_RADIUS.get() + 1.0;
        AABB sweep = new AABB(from, to).inflate(maxReach + 0.125);
        List<Entity> nearby = this.level().getEntities(this, sweep, ex -> {
            if (ex instanceof LivingEntity le && le.isAlive() && !ex.isSpectator()) {
                return true;
            }

            return false;
        });
        if (nearby.isEmpty()) {
            return false;
        } else {
            Entity best = null;
            double bestDist = Double.MAX_VALUE;
            boolean bestIsCatchZoneOnly = false;

            for (Entity e : nearby) {
                if (!this.isImmuneThrower(e)) {
                    double reach = e instanceof Player p ? catchReach(p) : (e instanceof BaseballPlayerEntity npc ? npc.catchReach() : 0.0);
                    AABB body = e.getBoundingBox().inflate(0.125);
                    AABB catchZone = body.inflate(reach);
                    double d = sweepDistance(catchZone, from, to);
                    if (d < bestDist) {
                        bestDist = d;
                        best = e;
                        bestIsCatchZoneOnly = sweepDistance(body, from, to) == Double.MAX_VALUE;
                    }
                }
            }

            if (best == null) {
                return false;
            } else {
                if (best instanceof Player player && this.tryCatch(player)) {
                    return true;
                }

                if (best instanceof BaseballPlayerEntity npc && npc.tryCatchBall(this)) {
                    this.level()
                        .playSound(null, npc.getX(), npc.getY(), npc.getZ(), (SoundEvent)ModSounds.GLOVE_CATCH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                    this.discard();
                    return true;
                }

                if (bestIsCatchZoneOnly) {
                    return false;
                } else {
                    this.deflectOff(best);
                    BaseballGame g = this.game();
                    if (g != null) {
                        g.onBallDeflect(this, best);
                    }

                    return true;
                }
            }
        }
    }

    private static double sweepDistance(AABB box, Vec3 from, Vec3 to) {
        if (box.contains(from)) {
            return 0.0;
        } else {
            Optional<Vec3> hit = box.clip(from, to);
            return hit.<Double>map(h -> h.distanceToSqr(from)).orElse(Double.MAX_VALUE);
        }
    }

    private static double catchReach(Player p) {
        GloveItem.GloveStats glove = GloveItem.heldGlove(p);
        return (Double)BaseballConfig.BAREHAND_CATCH_RADIUS.get() + (glove == null ? 0.0 : (double)glove.catchRadiusBonus());
    }

    private boolean tryCatch(Player player) {
        BaseballGame g = this.game();
        if (this.gameId == null || g != null && g.canHumanCatch(player)) {
            GloveItem.GloveStats glove = GloveItem.heldGlove(player);
            double maxSpeed = (Double)BaseballConfig.BAREHAND_MAX_CATCH_SPEED.get() + (glove == null ? 0.0 : (double)glove.maxCatchSpeedBonus());
            double speed = this.phys.vel.length();
            if (speed > maxSpeed) {
                return false;
            } else {
                Vec3 toBall = this.phys.pos.subtract(player.getEyePosition());
                if (toBall.lengthSqr() > 1.0E-6 && player.getLookAngle().dot(toBall.normalize()) < 0.05) {
                    return false;
                } else {
                    boolean fromAir = this.phys.bounces == 0;
                    giveBall(player, this.gameId);
                    this.level()
                        .playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            (SoundEvent)ModSounds.GLOVE_CATCH.get(),
                            SoundSource.PLAYERS,
                            glove != null ? 1.0F : 0.6F,
                            glove != null ? 1.0F : 1.3F
                        );
                    this.discard();
                    if (g != null) {
                        g.onPossession(player, fromAir);
                    }

                    return true;
                }
            }
        } else {
            return false;
        }
    }

    private void tryScoopUp() {
        if (!(this.phys.vel.length() > (Double)BaseballConfig.PICKUP_MAX_SPEED.get())) {
            BaseballGame g = this.game();

            for (LivingEntity e : this.level()
                .getEntitiesOfClass(
                    LivingEntity.class,
                    this.getBoundingBox().inflate(0.55),
                    ex -> ex.isAlive() && !ex.isSpectator() && !this.isImmuneThrower(ex) && (ex instanceof Player || ex instanceof BaseballPlayerEntity)
                )) {
                if (!(e instanceof BaseballPlayerEntity npc)) {
                    Player p = (Player)e;
                    if (this.gameId == null || g != null && g.canHumanCatch(p)) {
                        giveBall(p, this.gameId);
                        this.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
                        this.discard();
                        if (g != null) {
                            g.onPossession(p, false);
                        }

                        return;
                    }
                    continue;
                }

                if (npc.tryCatchBall(this)) {
                    this.discard();
                    return;
                }
            }
        }
    }

    private static void giveBall(Player player, @Nullable UUID gameId) {
        ItemStack ball = new ItemStack((ItemLike)ModItems.BASEBALL.get());
        if (gameId != null) {
            GameKit.tagGameBall(ball, gameId);
        }

        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ball);
        } else if (!player.getInventory().add(ball)) {
            player.drop(ball, false);
        }
    }

    private void deflectOff(Entity e) {
        Vec3 away = this.phys.pos.subtract(e.getBoundingBox().getCenter());
        Vec3 n = away.lengthSqr() < 1.0E-6 ? this.phys.vel.normalize().reverse() : away.normalize();
        double into = this.phys.vel.dot(n);
        Vec3 v = into < 0.0 ? this.phys.vel.subtract(n.scale(1.3 * into)) : this.phys.vel;
        this.phys.vel = v.scale(0.3);
        this.phys.motion = BallMotion.FLYING;
        this.phys.bounces++;
        this.phys.spin = this.phys.spin.scale(0.3);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.7F, 1.6F);
    }

    private void playBounceSound(BlockPos pos, double impactSpeed) {
        if (!(impactSpeed < 0.04)) {
            float vol = (float)Math.min(1.0, impactSpeed * 1.5);
            this.level()
                .playSound(
                    null,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    (SoundEvent)ModSounds.BALL_BOUNCE.get(),
                    SoundSource.NEUTRAL,
                    vol,
                    0.9F + this.random.nextFloat() * 0.25F
                );
        }
    }

    protected void defineSynchedData() {
        this.entityData.define(DATA_MOTION, BallMotion.FLYING.ordinal());
        this.entityData.define(DATA_SPIN, new Vector3f());
        this.entityData.define(DATA_GAME_BALL, false);
        this.entityData.define(DATA_BOUNCES, 0);
    }

    protected void readAdditionalSaveData(CompoundTag tag) {
        this.phys.motion = BallMotion.byId(tag.getInt("BallMotion"));
        this.entityData.set(DATA_MOTION, this.phys.motion.ordinal());
        this.phys.bounces = tag.getInt("Bounces");
        if (tag.hasUUID("Thrower")) {
            this.throwerId = tag.getUUID("Thrower");
        }

        this.restingTicks = tag.getInt("RestingTicks");
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("BallMotion", this.phys.motion.ordinal());
        tag.putInt("Bounces", this.phys.bounces);
        if (this.throwerId != null) {
            tag.putUUID("Thrower", this.throwerId);
        }

        tag.putInt("RestingTicks", this.restingTicks);
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    public void remove(RemovalReason reason) {
        if (PitchingSystem.DEBUG && this.gameId != null && debugRemovals < 3 && this.tickCount < 30) {
            debugRemovals++;
            MCBaseball.LOGGER
                .info(
                    "[BallDebug] removed after {} ticks, reason={}, pos={}, vel={}, bounces={}",
                    new Object[]{this.tickCount, reason, this.position(), this.getDeltaMovement(), this.phys.bounces, new Throwable("removed by")}
                );
        }

        super.remove(reason);
    }

    public boolean shouldBeSaved() {
        return this.gameId == null && super.shouldBeSaved();
    }

    public ItemStack getItem() {
        return new ItemStack((ItemLike)ModItems.BASEBALL.get());
    }

    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return distSqr < 36864.0;
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isAttackable() {
        return false;
    }
}
