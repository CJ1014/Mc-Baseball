package com.cj.mcbaseball.field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class FieldPlan {
    public final BlockPos ctrl;
    public final BlockPos home;
    public final Direction front;
    public final Direction d1;
    public final Direction d2;
    public final Direction u;
    public final Direction v;
    public final int y0;
    public final int bases;
    public final int fence;
    public final double mound;

    public FieldPlan(BlockPos ctrl, Direction front, FieldBuilder.Size size) {
        this.ctrl = ctrl;
        this.front = front;
        this.d1 = front.getOpposite();
        this.d2 = this.d1.getClockWise();
        double fx = (double)(this.d1.getStepX() + this.d2.getStepX());
        double fz = (double)(this.d1.getStepZ() + this.d2.getStepZ());
        double rx = -fz;
        this.u = (double)this.d1.getStepX() * rx + (double)this.d1.getStepZ() * fx > 0.0 ? this.d1 : this.d2;
        this.v = this.u == this.d1 ? this.d2 : this.d1;
        this.y0 = ctrl.getY();
        this.home = ctrl.relative(this.d1, 3).relative(this.d2, 3);
        this.bases = size.bases;
        this.fence = size.fence;
        this.mound = (double)Math.round((double)size.bases * 0.475);
    }

    public BlockPos at(int a, int b, int y) {
        return new BlockPos(
            this.home.getX() + this.u.getStepX() * a + this.v.getStepX() * b, y, this.home.getZ() + this.u.getStepZ() * a + this.v.getStepZ() * b
        );
    }

    public Direction dir(int da, int db) {
        if (da > 0) {
            return this.u;
        } else if (da < 0) {
            return this.u.getOpposite();
        } else {
            return db >= 0 ? this.v : this.v.getOpposite();
        }
    }
}
