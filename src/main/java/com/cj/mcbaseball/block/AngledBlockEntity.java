package com.cj.mcbaseball.block;

import com.cj.mcbaseball.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class AngledBlockEntity extends BlockEntity {
    private float yaw = Float.NaN;

    public AngledBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType)ModBlockEntities.ANGLED.get(), pos, state);
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

    public static float snap(float y) {
        return (float)Math.round(y / 22.5F) * 22.5F;
    }

    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!Float.isNaN(this.yaw)) {
            tag.putFloat("Yaw", this.yaw);
        }
    }

    public void load(CompoundTag tag) {
        super.load(tag);
        this.yaw = tag.contains("Yaw") ? tag.getFloat("Yaw") : Float.NaN;
    }

    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
