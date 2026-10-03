package com.cj.mcbaseball.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class FieldLayout {
    public static final int MAX_WALL_POINTS = 64;
    private final EnumMap<FieldMarker, BlockPos> points = new EnumMap<>(FieldMarker.class);
    private final List<BlockPos> outfieldWall = new ArrayList<>();

    @Nullable
    public BlockPos get(FieldMarker m) {
        return this.points.get(m);
    }

    public boolean has(FieldMarker m) {
        return m.multiPoint() ? !this.outfieldWall.isEmpty() : this.points.containsKey(m);
    }

    public void set(FieldMarker m, BlockPos pos) {
        if (m.multiPoint()) {
            throw new IllegalArgumentException("use addWallPoint for " + m);
        } else {
            this.points.put(m, pos.immutable());
        }
    }

    public boolean addWallPoint(BlockPos pos) {
        if (this.outfieldWall.size() < 64 && !this.outfieldWall.contains(pos)) {
            this.outfieldWall.add(pos.immutable());
            return true;
        } else {
            return false;
        }
    }

    public void clear(FieldMarker m) {
        if (m.multiPoint()) {
            this.outfieldWall.clear();
        } else {
            this.points.remove(m);
        }
    }

    public List<BlockPos> outfieldWall() {
        return Collections.unmodifiableList(this.outfieldWall);
    }

    public List<FieldMarker> missingRequired() {
        List<FieldMarker> missing = new ArrayList<>();

        for (FieldMarker m : FieldMarker.values()) {
            if (m.required() && !this.has(m)) {
                missing.add(m);
            }
        }

        return missing;
    }

    public boolean isReady() {
        return this.missingRequired().isEmpty() && this.problems().isEmpty();
    }

    public List<Component> problems() {
        List<Component> out = new ArrayList<>();
        if (!this.missingRequired().isEmpty()) {
            return out;
        } else {
            Set<BlockPos> seen = new HashSet<>();

            for (FieldMarker m : FieldMarker.values()) {
                if (m.required() && !seen.add(this.points.get(m))) {
                    out.add(Component.translatable("mcbaseball.field.problem.duplicate", new Object[]{m.displayName()}));
                }
            }

            if (!out.isEmpty()) {
                return out;
            } else {
                Vec3 home = this.center(FieldMarker.HOME_PLATE);
                Vec3 mound = this.center(FieldMarker.PITCHERS_MOUND);
                Vec3 first = this.center(FieldMarker.FIRST_BASE);
                Vec3 second = this.center(FieldMarker.SECOND_BASE);
                Vec3 third = this.center(FieldMarker.THIRD_BASE);
                double homeToMound = flatDist(home, mound);
                if (homeToMound < 6.0) {
                    out.add(Component.translatable("mcbaseball.field.problem.mound_close"));
                }

                double toSecond = flatDist(home, second);
                if (toSecond <= homeToMound) {
                    out.add(Component.translatable("mcbaseball.field.problem.second_not_beyond_mound"));
                }

                Vec3 dir = flat(mound.subtract(home)).normalize();
                Vec3 right = new Vec3(-dir.z, 0.0, dir.x);
                if (flat(first.subtract(home)).dot(right) <= 0.0) {
                    out.add(Component.translatable("mcbaseball.field.problem.first_wrong_side"));
                }

                if (flat(third.subtract(home)).dot(right) >= 0.0) {
                    out.add(Component.translatable("mcbaseball.field.problem.third_wrong_side"));
                }

                return out;
            }
        }
    }

    @Nullable
    public Vec3 center(FieldMarker m) {
        BlockPos p = this.points.get(m);
        return p == null ? null : new Vec3((double)p.getX() + 0.5, (double)p.getY() + 1.0, (double)p.getZ() + 0.5);
    }

    private static Vec3 flat(Vec3 v) {
        return new Vec3(v.x, 0.0, v.z);
    }

    private static double flatDist(Vec3 a, Vec3 b) {
        return flat(a.subtract(b)).length();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag pts = new CompoundTag();
        this.points.forEach((m, px) -> pts.put(m.name(), NbtUtils.writeBlockPos(px)));
        tag.put("Points", pts);
        ListTag wall = new ListTag();

        for (BlockPos p : this.outfieldWall) {
            wall.add(NbtUtils.writeBlockPos(p));
        }

        tag.put("Wall", wall);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.points.clear();
        this.outfieldWall.clear();
        CompoundTag pts = tag.getCompound("Points");

        for (FieldMarker m : FieldMarker.values()) {
            if (!m.multiPoint() && pts.contains(m.name(), 10)) {
                this.points.put(m, NbtUtils.readBlockPos(pts.getCompound(m.name())));
            }
        }

        ListTag wall = tag.getList("Wall", 10);

        for (int i = 0; i < wall.size() && i < 64; i++) {
            this.outfieldWall.add(NbtUtils.readBlockPos(wall.getCompound(i)));
        }
    }
}
