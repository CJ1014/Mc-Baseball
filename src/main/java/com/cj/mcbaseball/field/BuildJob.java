package com.cj.mcbaseball.field;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class BuildJob {
    final ServerLevel level;
    private final int minA;
    private final int maxA;
    private final int minB;
    private final int maxB;
    private int a;
    private int b;
    private final BuildJob.ColumnPainter painter;
    private final Runnable finish;
    @Nullable
    private final UUID player;
    private final String nameKey;
    private boolean done;
    private int lastPct = -1;
    public final BlockPos anchor;

    public BuildJob(
        ServerLevel level,
        BlockPos anchor,
        int minA,
        int maxA,
        int minB,
        int maxB,
        BuildJob.ColumnPainter painter,
        Runnable finish,
        @Nullable ServerPlayer player,
        String nameKey
    ) {
        this.level = level;
        this.anchor = anchor;
        this.minA = minA;
        this.maxA = maxA;
        this.minB = minB;
        this.maxB = maxB;
        this.a = minA;
        this.b = minB;
        this.painter = painter;
        this.finish = finish;
        this.player = player == null ? null : player.getUUID();
        this.nameKey = nameKey;
    }

    public boolean isDone() {
        return this.done;
    }

    public boolean tick(int budget) {
        if (this.done) {
            return true;
        } else {
            while (budget > 0 && this.a <= this.maxA) {
                budget -= Math.max(1, this.painter.paint(this.a, this.b));
                if (++this.b > this.maxB) {
                    this.b = this.minB;
                    this.a++;
                }
            }

            int pct = (int)(100.0 * (double)(this.a - this.minA) / (double)Math.max(1, this.maxA - this.minA + 1));
            if (pct / 5 != this.lastPct / 5) {
                this.lastPct = pct;
                ServerPlayer p = this.player == null ? null : this.level.getServer().getPlayerList().getPlayer(this.player);
                if (p != null) {
                    p.displayClientMessage(Component.translatable(this.nameKey, new Object[]{Math.min(pct, 99)}).withStyle(ChatFormatting.YELLOW), true);
                }
            }

            if (this.a > this.maxA) {
                this.finish.run();
                this.done = true;
            }

            return this.done;
        }
    }

    public void runAll() {
        while (!this.tick(1073741823)) {
        }
    }

    public interface ColumnPainter {
        int paint(int var1, int var2);
    }
}
