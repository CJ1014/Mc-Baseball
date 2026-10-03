package com.cj.mcbaseball.network;

import com.cj.mcbaseball.client.ClientGameState;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.LineupSlot;
import com.cj.mcbaseball.game.PlayerRole;
import com.cj.mcbaseball.game.Runner;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent.Context;

public record GameHudPacket(
    boolean active,
    String awayAbbr,
    String homeAbbr,
    int awayColor,
    int homeColor,
    int awayRuns,
    int homeRuns,
    int inning,
    boolean top,
    int balls,
    int strikes,
    int outs,
    int bases,
    int phase,
    int role,
    int pitchType,
    double homeX,
    double homeY,
    double homeZ,
    double fwdX,
    double fwdZ,
    boolean showZone,
    boolean showLanding,
    boolean assist,
    String batter,
    String pitcher,
    BlockPos controller,
    Component coach,
    double[] basePos
) {
    public static GameHudPacket inactive() {
        return new GameHudPacket(
            false,
            "",
            "",
            0,
            0,
            0,
            0,
            0,
            true,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            false,
            false,
            false,
            "",
            "",
            BlockPos.ZERO,
            Component.empty(),
            new double[9]
        );
    }

    public static GameHudPacket of(BaseballGame g, PlayerRole role, UUID viewer, Component coach) {
        int bases = 0;

        for (int b = 1; b <= 3; b++) {
            if (g.onBase[b] != null) {
                bases |= 1 << b - 1;
            }
        }

        if (g.play != null) {
            bases = 0;

            for (Runner r : g.play.runners) {
                if (r.active() && r.base >= 1 && r.base <= 3) {
                    bases |= 1 << r.base - 1;
                }
            }
        }

        double[] bp = new double[9];

        for (int bx = 1; bx <= 3; bx++) {
            Vec3 v = g.geo.base(bx);
            bp[(bx - 1) * 3] = v.x;
            bp[(bx - 1) * 3 + 1] = v.y;
            bp[(bx - 1) * 3 + 2] = v.z;
        }

        LineupSlot ps = g.pitcherSlot();
        return new GameHudPacket(
            true,
            g.away.abbr(),
            g.home.abbr(),
            g.away.data.primaryRgb(),
            g.home.data.primaryRgb(),
            g.away.runs,
            g.home.runs,
            g.inning,
            g.top,
            g.balls,
            g.strikes,
            Math.min(3, g.outs),
            bases,
            g.phase.ordinal(),
            role.ordinal(),
            g.humanPitchType(viewer).ordinal(),
            g.geo.home.x,
            g.geo.groundY,
            g.geo.home.z,
            g.geo.forward.x,
            g.geo.forward.z,
            g.settings.showStrikeZone,
            g.settings.showLandingMarker,
            g.settings.battingAssist,
            g.batter == null ? "" : g.batter.displayName(),
            ps == null ? "" : ps.displayName(),
            g.controllerPos,
            coach,
            bp
        );
    }

    public Vec3 base(int b) {
        if (b != 4 && b != 0) {
            int i = (b - 1) * 3;
            return new Vec3(this.basePos[i], this.basePos[i + 1], this.basePos[i + 2]);
        } else {
            return new Vec3(this.homeX, this.homeY, this.homeZ);
        }
    }

    public static void encode(GameHudPacket p, FriendlyByteBuf b) {
        b.writeBoolean(p.active);
        if (p.active) {
            b.writeUtf(p.awayAbbr, 8);
            b.writeUtf(p.homeAbbr, 8);
            b.writeInt(p.awayColor);
            b.writeInt(p.homeColor);
            b.writeVarInt(p.awayRuns);
            b.writeVarInt(p.homeRuns);
            b.writeVarInt(p.inning);
            b.writeBoolean(p.top);
            b.writeByte(p.balls);
            b.writeByte(p.strikes);
            b.writeByte(p.outs);
            b.writeByte(p.bases);
            b.writeByte(p.phase);
            b.writeByte(p.role);
            b.writeByte(p.pitchType);
            b.writeDouble(p.homeX);
            b.writeDouble(p.homeY);
            b.writeDouble(p.homeZ);
            b.writeDouble(p.fwdX);
            b.writeDouble(p.fwdZ);
            b.writeBoolean(p.showZone);
            b.writeBoolean(p.showLanding);
            b.writeBoolean(p.assist);
            b.writeUtf(p.batter, 64);
            b.writeUtf(p.pitcher, 64);
            b.writeBlockPos(p.controller);
            b.writeComponent(p.coach);

            for (double d : p.basePos) {
                b.writeDouble(d);
            }
        }
    }

    public static GameHudPacket decode(FriendlyByteBuf b) {
        if (!b.readBoolean()) {
            return inactive();
        } else {
            String aw = b.readUtf(8);
            String ho = b.readUtf(8);
            int ac = b.readInt();
            int hc = b.readInt();
            int ar = b.readVarInt();
            int hr = b.readVarInt();
            int inn = b.readVarInt();
            boolean top = b.readBoolean();
            int balls = b.readByte();
            int strikes = b.readByte();
            int outs = b.readByte();
            int bases = b.readByte();
            int phase = b.readByte();
            int role = b.readByte();
            int pt = b.readByte();
            double hx = b.readDouble();
            double hy = b.readDouble();
            double hz = b.readDouble();
            double fx = b.readDouble();
            double fz = b.readDouble();
            boolean zone = b.readBoolean();
            boolean landing = b.readBoolean();
            boolean assist = b.readBoolean();
            String batter = b.readUtf(64);
            String pitcher = b.readUtf(64);
            BlockPos ctrl = b.readBlockPos();
            Component coach = b.readComponent();
            double[] bp = new double[9];

            for (int i = 0; i < 9; i++) {
                bp[i] = b.readDouble();
            }

            return new GameHudPacket(
                true,
                aw,
                ho,
                ac,
                hc,
                ar,
                hr,
                inn,
                top,
                balls,
                strikes,
                outs,
                bases,
                phase,
                role,
                pt,
                hx,
                hy,
                hz,
                fx,
                fz,
                zone,
                landing,
                assist,
                batter,
                pitcher,
                ctrl,
                coach,
                bp
            );
        }
    }

    public static void handle(GameHudPacket p, Supplier<Context> ctx) {
        ClientGameState.update(p);
    }
}
