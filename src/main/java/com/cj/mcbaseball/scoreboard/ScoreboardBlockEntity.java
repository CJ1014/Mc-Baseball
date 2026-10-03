package com.cj.mcbaseball.scoreboard;

import com.cj.mcbaseball.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class ScoreboardBlockEntity extends BlockEntity {
    private ScoreboardBlockEntity.Snapshot snapshot = ScoreboardBlockEntity.Snapshot.EMPTY;
    @Nullable
    private BlockPos controller;
    private int scale = 1;
    private float yaw = Float.NaN;

    public int scale() {
        return this.scale;
    }

    public float yaw() {
        if (!Float.isNaN(this.yaw)) {
            return this.yaw;
        } else {
            BlockState s = this.getBlockState();
            return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? ((Direction)s.getValue(BlockStateProperties.HORIZONTAL_FACING)).toYRot() : 0.0F;
        }
    }

    public void setYaw(float y) {
        this.yaw = Mth.wrapDegrees(y);
        if (this.yaw < 0.0F) {
            this.yaw += 360.0F;
        }

        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void setScale(int s) {
        this.scale = Math.max(1, Math.min(10, s));
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition).inflate((double)(this.scale + 1));
    }

    public ScoreboardBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType)ModBlockEntities.SCOREBOARD.get(), pos, state);
    }

    public ScoreboardBlockEntity.Snapshot snapshot() {
        return this.snapshot;
    }

    @Nullable
    public BlockPos controller() {
        return this.controller;
    }

    public void link(BlockPos controllerPos) {
        this.controller = controllerPos.immutable();
        this.setChanged();
    }

    public void update(ScoreboardBlockEntity.Snapshot s) {
        if (!s.equals(this.snapshot)) {
            this.snapshot = s;
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Snap", this.snapshot.save());
        if (this.controller != null) {
            tag.put("Controller", NbtUtils.writeBlockPos(this.controller));
        }

        tag.putInt("Scale", this.scale);
        if (!Float.isNaN(this.yaw)) {
            tag.putFloat("Yaw", this.yaw);
        }
    }

    public void load(CompoundTag tag) {
        super.load(tag);
        this.snapshot = ScoreboardBlockEntity.Snapshot.load(tag.getCompound("Snap"));
        this.controller = tag.contains("Controller") ? NbtUtils.readBlockPos(tag.getCompound("Controller")) : null;
        this.scale = Math.max(1, tag.getInt("Scale"));
        this.yaw = tag.contains("Yaw") ? tag.getFloat("Yaw") : Float.NaN;
    }

    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static record Snapshot(
        boolean active,
        String away,
        String home,
        int awayRuns,
        int homeRuns,
        int inning,
        boolean top,
        int balls,
        int strikes,
        int outs,
        boolean fin,
        int awayColor,
        int homeColor
    ) {
        public static final ScoreboardBlockEntity.Snapshot EMPTY = new ScoreboardBlockEntity.Snapshot(
            false, "", "", 0, 0, 0, true, 0, 0, 0, false, 16777215, 16777215
        );

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putBoolean("A", this.active);
            t.putString("Aw", this.away);
            t.putString("Ho", this.home);
            t.putIntArray(
                "N",
                new int[]{
                    this.awayRuns,
                    this.homeRuns,
                    this.inning,
                    this.top ? 1 : 0,
                    this.balls,
                    this.strikes,
                    this.outs,
                    this.fin ? 1 : 0,
                    this.awayColor,
                    this.homeColor
                }
            );
            return t;
        }

        static ScoreboardBlockEntity.Snapshot load(CompoundTag t) {
            int[] n = t.getIntArray("N");
            return n.length < 10
                ? EMPTY
                : new ScoreboardBlockEntity.Snapshot(
                    t.getBoolean("A"), t.getString("Aw"), t.getString("Ho"), n[0], n[1], n[2], n[3] == 1, n[4], n[5], n[6], n[7] == 1, n[8], n[9]
                );
        }
    }
}
