package com.cj.mcbaseball.field;

import com.cj.mcbaseball.block.StadiumKitBlock;
import com.cj.mcbaseball.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class StadiumKitBlockEntity extends BlockEntity {
    private int size = FieldBuilder.Size.STANDARD.ordinal();
    @Nullable
    private UUID owner;
    private long previewUntil;

    public StadiumKitBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType)ModBlockEntities.STADIUM_KIT.get(), pos, state);
    }

    public FieldBuilder.Size size() {
        return FieldBuilder.Size.byId(this.size);
    }

    public void setSize(int s) {
        this.size = FieldBuilder.Size.byId(s).ordinal();
        this.showOutline();
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void setOwner(Player p) {
        this.owner = p.getUUID();
        this.setChanged();
    }

    public boolean canBuild(Player p) {
        return this.owner == null || this.owner.equals(p.getUUID()) || p.hasPermissions(2);
    }

    public void showOutline() {
        if (this.level != null) {
            this.previewUntil = this.level.getGameTime() + 900L;
        }
    }

    public FieldPlan plan() {
        return new FieldPlan(this.worldPosition, (Direction)this.getBlockState().getValue(StadiumKitBlock.FACING), this.size());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StadiumKitBlockEntity be) {
        if (level instanceof ServerLevel sl && level.getGameTime() <= be.previewUntil && level.getGameTime() % 15L == 0L) {
            FieldPlan p = be.plan();
            int R = p.fence;
            int side = 18;
            double y = (double)p.y0 + 1.3;
            List<double[]> pts = new ArrayList<>();

            for (int t = 0; t <= R; t += 2) {
                pts.add(new double[]{(double)t, 0.0});
                pts.add(new double[]{0.0, (double)t});
            }

            double outer = (double)R + 1.25 + 8.0 + 2.0;

            for (double deg = 0.0; deg <= 90.0; deg += 120.0 / outer) {
                double r = Math.toRadians(deg);
                pts.add(new double[]{outer * Math.cos(r), outer * Math.sin(r)});
            }

            for (int t = 0; t <= (int)((double)R * 0.8); t += 2) {
                pts.add(new double[]{(double)(-side), (double)t});
                pts.add(new double[]{(double)t, (double)(-side)});
            }

            for (double deg = 180.0; deg <= 270.0; deg += 8.0) {
                double r = Math.toRadians(deg);
                pts.add(new double[]{(double)side * Math.cos(r), (double)side * Math.sin(r)});
            }

            for (ServerPlayer pl : sl.players()) {
                if (!(pl.blockPosition().distSqr(pos) > 48400.0)) {
                    for (double[] pt : pts) {
                        BlockPos bp = p.at((int)Math.round(pt[0]), (int)Math.round(pt[1]), p.y0);
                        sl.sendParticles(pl, ParticleTypes.END_ROD, true, (double)bp.getX() + 0.5, y, (double)bp.getZ() + 0.5, 1, 0.0, 0.3, 0.0, 0.0);
                    }
                }
            }

            return;
        }
    }

    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Size", this.size);
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
    }

    public void load(CompoundTag tag) {
        super.load(tag);
        this.size = FieldBuilder.Size.byId(tag.getInt("Size")).ordinal();
        this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
