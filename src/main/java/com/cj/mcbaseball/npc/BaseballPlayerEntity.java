package com.cj.mcbaseball.npc;

import com.cj.mcbaseball.entity.BaseballEntity;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.GameManager;
import com.cj.mcbaseball.game.LineupSlot;
import com.cj.mcbaseball.item.UniformArmorItem;
import com.cj.mcbaseball.registry.ModItems;
import com.cj.mcbaseball.team.NpcProfile;
import com.cj.mcbaseball.team.TeamData;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class BaseballPlayerEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(BaseballPlayerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANIM_START = SynchedEntityData.defineId(BaseballPlayerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(BaseballPlayerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_NUMBER = SynchedEntityData.defineId(BaseballPlayerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_NUMBER_COLOR = SynchedEntityData.defineId(
        BaseballPlayerEntity.class, EntityDataSerializers.INT
    );
    public static final double SPEED_FACTOR = 2.16;
    @Nullable
    private UUID gameId;
    @Nullable
    private LineupSlot slot;
    @Nullable
    private Vec3 moveTarget;
    private double moveSpeed = 1.0;
    @Nullable
    private Vec3 lastPathTarget;
    @Nullable
    private Vec3 lookPos;

    public BaseballPlayerEntity(EntityType<? extends BaseballPlayerEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
    }

    public static Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.38)
            .add(Attributes.FOLLOW_RANGE, 128.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void setup(BaseballGame game, LineupSlot slot, TeamData team) {
        this.gameId = game.id;
        this.slot = slot;
        NpcProfile p = slot.npc;
        this.entityData.set(DATA_SKIN, p.skin);
        this.entityData.set(DATA_NUMBER, p.number);
        this.entityData.set(DATA_NUMBER_COLOR, team.secondaryRgb());
        this.setCustomName(Component.literal(p.displayName()));
        this.setCustomNameVisible(false);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.33 + (double)p.speed / 99.0 * 0.05);
        this.dress(team);
    }

    public void dress(TeamData team) {
        this.setItemSlot(EquipmentSlot.HEAD, dyed(((Item)ModItems.BASEBALL_CAP.get()).getDefaultInstance(), team.primaryRgb()));
        this.setItemSlot(EquipmentSlot.CHEST, dyed(((Item)ModItems.BASEBALL_JERSEY.get()).getDefaultInstance(), team.primaryRgb()));
        this.setItemSlot(EquipmentSlot.LEGS, dyed(((Item)ModItems.BASEBALL_PANTS.get()).getDefaultInstance(), team.secondaryRgb()));
        this.setItemSlot(EquipmentSlot.FEET, dyed(((Item)ModItems.CLEATS.get()).getDefaultInstance(), 1973794));

        for (EquipmentSlot s : EquipmentSlot.values()) {
            this.setDropChance(s, 0.0F);
        }
    }

    private static ItemStack dyed(ItemStack stack, int rgb) {
        if (stack.getItem() instanceof UniformArmorItem u) {
            u.setColor(stack, rgb);
        }

        return stack;
    }

    @Nullable
    public UUID gameId() {
        return this.gameId;
    }

    @Nullable
    public LineupSlot slot() {
        return this.slot;
    }

    public void moveTo(Vec3 target, double speed) {
        this.moveTarget = target;
        this.moveSpeed = speed;
    }

    public void stopMoving() {
        this.moveTarget = null;
        this.lastPathTarget = null;
        this.getNavigation().stop();
    }

    @Nullable
    public Vec3 moveTarget() {
        return this.moveTarget;
    }

    public void lookAtPos(@Nullable Vec3 pos) {
        this.lookPos = pos;
    }

    public double runSpeed() {
        double a = this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return a * a * 2.16;
    }

    public void setAnim(NpcAnim anim) {
        this.entityData.set(DATA_ANIM, anim.ordinal());
        this.entityData.set(DATA_ANIM_START, this.tickCount);
    }

    public NpcAnim anim() {
        return NpcAnim.byId((Integer)this.entityData.get(DATA_ANIM));
    }

    public int animAge() {
        return this.tickCount - (Integer)this.entityData.get(DATA_ANIM_START);
    }

    public int animStart() {
        return (Integer)this.entityData.get(DATA_ANIM_START);
    }

    public int skin() {
        return (Integer)this.entityData.get(DATA_SKIN);
    }

    public int jerseyNumber() {
        return (Integer)this.entityData.get(DATA_NUMBER);
    }

    public int numberColor() {
        return (Integer)this.entityData.get(DATA_NUMBER_COLOR);
    }

    public void holdBall(boolean ball) {
        this.setItemSlot(EquipmentSlot.MAINHAND, ball ? new ItemStack((ItemLike)ModItems.BASEBALL.get()) : ItemStack.EMPTY);
    }

    public void holdBat() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((ItemLike)ModItems.WOODEN_BAT.get()));
    }

    public void holdGlove(boolean catcher) {
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(catcher ? (ItemLike)ModItems.CATCHERS_MITT.get() : (ItemLike)ModItems.BASEBALL_GLOVE.get()));
    }

    public void clearHands() {
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    public double catchReach() {
        return this.slot == null ? 0.5 : 0.55 + (double)this.slot.npc.fielding / 99.0 * 0.45;
    }

    public boolean tryCatchBall(BaseballEntity ball) {
        BaseballGame g = this.gameId == null ? null : GameManager.get(this.gameId);
        return g != null && g.fielding.npcTryCatch(this, ball);
    }

    public void startSlide(Vec3 dir) {
        this.setAnim(NpcAnim.SLIDE);
        Vec3 d = new Vec3(dir.x, 0.0, dir.z).normalize();
        this.setDeltaMovement(d.scale(0.55).add(0.0, 0.05, 0.0));
        this.hasImpulse = true;
    }

    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (this.tickCount % 40 != 0 || (this.gameId == null || GameManager.get(this.gameId) != null) && (this.gameId != null || this.tickCount <= 600)) {
                NpcAnim a = this.anim();
                if (a.duration() > 0 && this.animAge() > a.duration()) {
                    this.setAnim(NpcAnim.NONE);
                }

                if (a != NpcAnim.SLIDE) {
                    if (this.moveTarget != null) {
                        double dx = this.moveTarget.x - this.getX();
                        double dz = this.moveTarget.z - this.getZ();
                        double d2 = dx * dx + dz * dz;
                        if (d2 < 0.12) {
                            this.getNavigation().stop();
                            this.lastPathTarget = null;
                        } else if (d2 < 9.0) {
                            this.getNavigation().stop();
                            this.getMoveControl().setWantedPosition(this.moveTarget.x, this.moveTarget.y, this.moveTarget.z, this.moveSpeed);
                            this.lastPathTarget = null;
                        } else if (this.lastPathTarget == null
                            || this.lastPathTarget.distanceToSqr(this.moveTarget) > 1.0
                            || this.getNavigation().isDone()
                            || this.tickCount % 20 == 0) {
                            this.getNavigation().moveTo(this.moveTarget.x, this.moveTarget.y, this.moveTarget.z, this.moveSpeed);
                            this.lastPathTarget = this.moveTarget;
                        }
                    }

                    if (this.lookPos != null) {
                        this.getLookControl().setLookAt(this.lookPos.x, this.lookPos.y, this.lookPos.z, 30.0F, 30.0F);
                    }
                }
            } else {
                this.discard();
            }
        }
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ANIM, 0);
        this.entityData.define(DATA_ANIM_START, 0);
        this.entityData.define(DATA_SKIN, 0);
        this.entityData.define(DATA_NUMBER, 0);
        this.entityData.define(DATA_NUMBER_COLOR, 16777215);
    }

    protected void registerGoals() {
    }

    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? super.hurt(source, amount) : false;
    }

    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public boolean shouldBeSaved() {
        return false;
    }

    public boolean canBeLeashed(Player player) {
        return false;
    }

    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    public boolean isPushedByFluid() {
        return false;
    }

    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
