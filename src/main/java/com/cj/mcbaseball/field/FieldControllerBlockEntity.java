package com.cj.mcbaseball.field;

import com.cj.mcbaseball.block.AngledBlockEntity;
import com.cj.mcbaseball.block.FieldControllerBlock;
import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.game.FieldGeometry;
import com.cj.mcbaseball.game.GameSettings;
import com.cj.mcbaseball.game.TeamSide;
import com.cj.mcbaseball.registry.ModBlockEntities;
import com.cj.mcbaseball.scoreboard.ScoreboardBlockEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class FieldControllerBlockEntity extends BlockEntity {
    private final FieldLayout layout = new FieldLayout();
    @Nullable
    private UUID ownerId;
    private String ownerName = "";
    private final GameSettings settings = new GameSettings();
    private final LinkedHashMap<UUID, FieldControllerBlockEntity.Signup> signups = new LinkedHashMap<>();
    private final List<BlockPos> scoreboards = new ArrayList<>();
    private boolean gameActive;
    private final Set<UUID> spectators = new HashSet<>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    public boolean isSpectator(UUID id) {
        return this.spectators.contains(id);
    }

    public Set<UUID> spectators() {
        return Collections.unmodifiableSet(this.spectators);
    }

    public void watch(Player p) {
        this.signups.remove(p.getUUID());
        this.spectators.add(p.getUUID());
        this.sync();
    }

    public GameSettings settings() {
        return this.settings;
    }

    public Map<UUID, FieldControllerBlockEntity.Signup> signups() {
        return Collections.unmodifiableMap(this.signups);
    }

    public boolean isGameActive() {
        return this.gameActive;
    }

    public void setGameActive(boolean active) {
        this.gameActive = active;
        this.sync();
    }

    public void markSettingsChanged() {
        this.sync();
    }

    public void signup(Player p, TeamSide side) {
        this.spectators.remove(p.getUUID());
        FieldControllerBlockEntity.Signup old = this.signups.get(p.getUUID());
        this.signups.put(p.getUUID(), new FieldControllerBlockEntity.Signup(side, old == null ? -1 : old.position(), p.getGameProfile().getName()));
        this.sync();
    }

    public void leave(UUID id) {
        if (this.signups.remove(id) != null) {
            this.sync();
        }
    }

    public void setPositionPref(UUID id, int pos) {
        FieldControllerBlockEntity.Signup old = this.signups.get(id);
        if (old != null) {
            this.signups.put(id, new FieldControllerBlockEntity.Signup(old.side(), pos >= 0 && pos <= 8 ? pos : -1, old.name()));
            this.sync();
        }
    }

    public void clearSignups() {
        this.signups.clear();
        this.sync();
    }

    public List<BlockPos> scoreboards() {
        return Collections.unmodifiableList(this.scoreboards);
    }

    public void linkScoreboard(BlockPos pos) {
        if (!this.scoreboards.contains(pos) && this.scoreboards.size() < 16) {
            this.scoreboards.add(pos.immutable());
            this.setChanged();
        }
    }

    public void unlinkScoreboard(BlockPos pos) {
        if (this.scoreboards.remove(pos)) {
            this.setChanged();
        }
    }

    private void sync() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            LOADED.computeIfAbsent(this.level.dimension(), k -> new HashSet<>()).add(this.worldPosition);
        }
    }

    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) {
            Set<BlockPos> set = LOADED.get(this.level.dimension());
            if (set != null) {
                set.remove(this.worldPosition);
            }
        }
    }

    @Nullable
    public static BlockPos nearestLoaded(Level level, BlockPos from, double maxDist) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set == null) {
            return null;
        } else {
            BlockPos best = null;
            double bestD = maxDist * maxDist;

            for (BlockPos p : set) {
                double d = p.distSqr(from);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }

            return best;
        }
    }

    public FieldControllerBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType)ModBlockEntities.FIELD_CONTROLLER.get(), pos, state);
    }

    public FieldLayout layout() {
        return this.layout;
    }

    public String ownerName() {
        return this.ownerName;
    }

    public void setOwner(Player p) {
        this.ownerId = p.getUUID();
        this.ownerName = p.getGameProfile().getName();
        this.setChanged();
    }

    public boolean canEdit(Player p) {
        return this.ownerId == null || this.ownerId.equals(p.getUUID()) || p.hasPermissions(2);
    }

    public FieldControllerBlockEntity.MarkResult applyMarker(FieldMarker marker, BlockPos target, ServerPlayer player) {
        if (!this.canEdit(player)) {
            return FieldControllerBlockEntity.MarkResult.NO_PERMISSION;
        } else {
            int max = (Integer)BaseballConfig.FIELD_MAX_RADIUS.get();
            if (target.distSqr(this.worldPosition) > (double)max * (double)max) {
                return FieldControllerBlockEntity.MarkResult.TOO_FAR;
            } else {
                if (marker.multiPoint()) {
                    if (!this.layout.addWallPoint(target)) {
                        return FieldControllerBlockEntity.MarkResult.WALL_FULL;
                    }
                } else {
                    this.layout.set(marker, target);
                }

                this.onLayoutChanged();
                return FieldControllerBlockEntity.MarkResult.OK;
            }
        }
    }

    public boolean clearMarker(FieldMarker marker, ServerPlayer player) {
        if (!this.canEdit(player)) {
            return false;
        } else {
            this.layout.clear(marker);
            this.onLayoutChanged();
            return true;
        }
    }

    public Component autoDetect(ServerPlayer player) {
        if (!this.canEdit(player)) {
            return Component.translatable("mcbaseball.field.no_permission", new Object[]{this.ownerName}).withStyle(ChatFormatting.RED);
        } else {
            FieldAutoDetector.Result r = FieldAutoDetector.scan(
                this.level, this.worldPosition, (Integer)BaseballConfig.AUTO_DETECT_RADIUS.get(), (Integer)BaseballConfig.AUTO_DETECT_VERTICAL.get()
            );
            if (r.home() == null) {
                return Component.translatable("mcbaseball.field.autodetect.no_home").withStyle(ChatFormatting.RED);
            } else {
                int found = 0;
                found += this.setIfFound(FieldMarker.HOME_PLATE, r.home());
                found += this.setIfFound(FieldMarker.PITCHERS_MOUND, r.mound());
                found += this.setIfFound(FieldMarker.FIRST_BASE, r.first());
                found += this.setIfFound(FieldMarker.SECOND_BASE, r.second());
                found += this.setIfFound(FieldMarker.THIRD_BASE, r.third());
                this.onLayoutChanged();
                MutableComponent msg = Component.translatable("mcbaseball.field.autodetect.found", new Object[]{found, 5});
                return msg.withStyle(found == 5 ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
            }
        }
    }

    private int setIfFound(FieldMarker m, @Nullable BlockPos pos) {
        if (pos == null) {
            return 0;
        } else {
            this.layout.set(m, pos);
            return 1;
        }
    }

    public void relayout() {
        this.onLayoutChanged();
    }

    public void aimFieldBlocks() {
        if (this.level != null && !this.level.isClientSide) {
            BlockPos home = this.layout.get(FieldMarker.HOME_PLATE);
            BlockPos mound = this.layout.get(FieldMarker.PITCHERS_MOUND);
            if (home != null) {
                Vec3 h = Vec3.atCenterOf(home);
                if (mound != null) {
                    Vec3 m = Vec3.atCenterOf(mound);
                    if (this.level.getBlockEntity(home) instanceof AngledBlockEntity a) {
                        a.setYaw(FieldGeometry.yawToward(h, m));
                    }

                    if (this.level.getBlockEntity(mound) instanceof AngledBlockEntity a) {
                        a.setYaw(FieldGeometry.yawToward(m, h));
                    }
                }

                for (BlockPos sb : this.scoreboards) {
                    if (this.level.getBlockEntity(sb) instanceof ScoreboardBlockEntity b) {
                        b.setYaw(FieldGeometry.yawToward(Vec3.atCenterOf(sb), h));
                    }
                }
            }
        }
    }

    private void onLayoutChanged() {
        this.setChanged();
        this.aimFieldBlocks();
        if (this.level != null && !this.level.isClientSide) {
            BlockState state = this.getBlockState();
            boolean ready = this.layout.isReady();
            if (state.hasProperty(FieldControllerBlock.READY) && (Boolean)state.getValue(FieldControllerBlock.READY) != ready) {
                this.level.setBlock(this.worldPosition, (BlockState)state.setValue(FieldControllerBlock.READY, ready), 3);
            } else {
                this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
            }
        }
    }

    public Component statusLine() {
        return this.layout.isReady()
            ? Component.translatable("mcbaseball.field.ready").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})
            : Component.translatable("mcbaseball.field.incomplete").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD});
    }

    @Nullable
    public Component missingLine() {
        List<FieldMarker> missing = this.layout.missingRequired();
        if (missing.isEmpty()) {
            return null;
        } else {
            MutableComponent c = Component.translatable("mcbaseball.field.missing").withStyle(ChatFormatting.GRAY);

            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) {
                    c.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }

                c.append(missing.get(i).displayName().copy().withStyle(ChatFormatting.WHITE));
            }

            return c;
        }
    }

    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag specs = new ListTag();

        for (UUID u : this.spectators) {
            specs.add(NbtUtils.createUUID(u));
        }

        tag.put("Spectators", specs);
        tag.put("Layout", this.layout.save());
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }

        tag.putString("OwnerName", this.ownerName);
        tag.put("Settings", this.settings.save());
        tag.putBoolean("GameActive", this.gameActive);
        ListTag su = new ListTag();
        this.signups.forEach((id, v) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.putInt("Side", v.side().ordinal());
            e.putInt("Pos", v.position());
            e.putString("Name", v.name());
            su.add(e);
        });
        tag.put("Signups", su);
        ListTag sb = new ListTag();

        for (BlockPos p : this.scoreboards) {
            sb.add(NbtUtils.writeBlockPos(p));
        }

        tag.put("Scoreboards", sb);
    }

    public void load(CompoundTag tag) {
        super.load(tag);
        this.spectators.clear();

        for (Tag t : tag.getList("Spectators", 11)) {
            this.spectators.add(NbtUtils.loadUUID(t));
        }

        this.layout.load(tag.getCompound("Layout"));
        this.ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.ownerName = tag.getString("OwnerName");
        if (tag.contains("Settings")) {
            this.settings.load(tag.getCompound("Settings"));
        }

        this.gameActive = tag.getBoolean("GameActive");
        this.signups.clear();
        ListTag su = tag.getList("Signups", 10);

        for (int i = 0; i < su.size(); i++) {
            CompoundTag e = su.getCompound(i);
            if (e.hasUUID("Id")) {
                this.signups
                    .put(e.getUUID("Id"), new FieldControllerBlockEntity.Signup(TeamSide.byId(e.getInt("Side")), e.getInt("Pos"), e.getString("Name")));
            }
        }

        this.scoreboards.clear();
        ListTag sb = tag.getList("Scoreboards", 10);

        for (int ix = 0; ix < sb.size(); ix++) {
            this.scoreboards.add(NbtUtils.readBlockPos(sb.getCompound(ix)));
        }
    }

    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static enum MarkResult {
        OK,
        TOO_FAR,
        NO_PERMISSION,
        WALL_FULL;
    }

    public static record Signup(TeamSide side, int position, String name) {
    }
}
