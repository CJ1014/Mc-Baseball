package com.cj.mcbaseball.network;

import static com.cj.mcbaseball.network.LiveScheduleSyncPacket.LONG;
import static com.cj.mcbaseball.network.LiveScheduleSyncPacket.SHORT;
import static com.cj.mcbaseball.network.LiveScheduleSyncPacket.clip;

import com.cj.mcbaseball.client.ClientHooks;
import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LivePitch;
import com.cj.mcbaseball.live.model.LivePlayer;
import com.cj.mcbaseball.live.model.LiveProviderStatus;
import com.cj.mcbaseball.live.model.LiveWatchSnapshot;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Server -> client: the live game a nearby stadium is following, for the HUD.
 * {@code snapshot == null} means "you are no longer near a watching stadium / it stopped": hide the HUD.
 * Lineups are server-side only (for Phase 4 NPCs) and are not sent.
 */
public record LiveWatchSyncPacket(BlockPos controller, @Nullable LiveWatchSnapshot snapshot) {

    public static void encode(LiveWatchSyncPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.controller);
        LiveWatchSnapshot s = p.snapshot;
        b.writeBoolean(s != null);
        if (s == null) {
            return;
        }
        b.writeLong(s.gameId());
        b.writeEnum(s.status());
        b.writeLong(s.fetchedAtMillis());
        b.writeLong(s.serverNowMillis());
        b.writeVarLong(Math.max(0L, s.retryInMillis()));
        b.writeUtf(clip(s.message(), LONG), LONG);
        b.writeUtf(clip(s.provider(), SHORT), SHORT);
        b.writeVarInt(s.version());
        b.writeBoolean(s.state() != null);
        if (s.state() != null) {
            writeState(b, s.state());
        }
        writeLines(b, s.recentEvents(), MAX_RECENT);
        writeLines(b, s.debugLines(), MAX_DEBUG);
    }

    static final int MAX_RECENT = 8;
    static final int MAX_DEBUG = 40;

    private static void writeLines(FriendlyByteBuf b, List<String> lines, int max) {
        int n = Math.min(lines.size(), max);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            b.writeUtf(clip(lines.get(i), LONG), LONG);
        }
    }

    private static List<String> readLines(FriendlyByteBuf b, int max) {
        int n = b.readVarInt();
        if (n < 0 || n > max) {
            throw new IllegalArgumentException("Too many lines in live packet: " + n);
        }
        List<String> out = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(b.readUtf(LONG));
        }
        return out;
    }

    public static LiveWatchSyncPacket decode(FriendlyByteBuf b) {
        BlockPos pos = b.readBlockPos();
        if (!b.readBoolean()) {
            return new LiveWatchSyncPacket(pos, null);
        }
        long gameId = b.readLong();
        LiveProviderStatus status = b.readEnum(LiveProviderStatus.class);
        long fetchedAt = b.readLong();
        long serverNow = b.readLong();
        long retryIn = b.readVarLong();
        String message = b.readUtf(LONG);
        String provider = b.readUtf(SHORT);
        int version = b.readVarInt();
        LiveGameState state = b.readBoolean() ? readState(b) : null;
        List<String> recent = readLines(b, MAX_RECENT);
        List<String> debug = readLines(b, MAX_DEBUG);
        return new LiveWatchSyncPacket(pos, new LiveWatchSnapshot(gameId, state, status, fetchedAt, serverNow, retryIn, message, provider, version, recent, debug));
    }

    private static void writeState(FriendlyByteBuf b, LiveGameState s) {
        b.writeLong(s.gameId());
        b.writeEnum(s.status().state());
        b.writeUtf(clip(s.status().detailed(), SHORT), SHORT);
        b.writeUtf(clip(s.status().reason(), SHORT), SHORT);
        b.writeUtf(clip(s.status().code(), SHORT), SHORT);
        LiveScheduleSyncPacket.writeTeam(b, s.away());
        LiveScheduleSyncPacket.writeTeam(b, s.home());
        b.writeLong(s.startEpochMillis());
        b.writeInt(s.awayScore());
        b.writeInt(s.homeScore());
        b.writeInt(s.inning());
        b.writeUtf(clip(s.inningState(), SHORT), SHORT);
        b.writeInt(s.balls());
        b.writeInt(s.strikes());
        b.writeInt(s.outs());
        b.writeBoolean(s.betweenBatters());
        writePlayer(b, s.batter());
        writePlayer(b, s.pitcher());
        writePlayer(b, s.onDeck());
        writePlayer(b, s.runnerOnFirst());
        writePlayer(b, s.runnerOnSecond());
        writePlayer(b, s.runnerOnThird());
        LiveScheduleSyncPacket.writeTotals(b, s.awayTotals());
        LiveScheduleSyncPacket.writeTotals(b, s.homeTotals());
        LiveScheduleSyncPacket.writeInnings(b, s.awayInningRuns());
        LiveScheduleSyncPacket.writeInnings(b, s.homeInningRuns());
        writePitch(b, s.lastPitch());
        b.writeUtf(clip(s.lastPlay(), 512), 512);
        b.writeUtf(clip(s.venue(), LONG), LONG);
        b.writeUtf(clip(s.feedTimestamp(), SHORT), SHORT);
        b.writeVarInt(Math.max(0, s.suggestedPollSeconds()));
    }

    private static LiveGameState readState(FriendlyByteBuf b) {
        long gameId = b.readLong();
        LiveGameStatus status = new LiveGameStatus(b.readEnum(LiveGameStatus.State.class), b.readUtf(SHORT), b.readUtf(SHORT), b.readUtf(SHORT));
        return new LiveGameState(
            gameId,
            status,
            LiveScheduleSyncPacket.readTeam(b),
            LiveScheduleSyncPacket.readTeam(b),
            b.readLong(),
            b.readInt(),
            b.readInt(),
            b.readInt(),
            b.readUtf(SHORT),
            b.readInt(),
            b.readInt(),
            b.readInt(),
            b.readBoolean(),
            readPlayer(b),
            readPlayer(b),
            readPlayer(b),
            readPlayer(b),
            readPlayer(b),
            readPlayer(b),
            LiveScheduleSyncPacket.readTotals(b),
            LiveScheduleSyncPacket.readTotals(b),
            LiveScheduleSyncPacket.readInnings(b),
            LiveScheduleSyncPacket.readInnings(b),
            readPitch(b),
            b.readUtf(512),
            b.readUtf(LONG),
            b.readUtf(SHORT),
            b.readVarInt(),
            List.of(),
            List.of()
        );
    }

    private static void writePlayer(FriendlyByteBuf b, LivePlayer p) {
        b.writeVarInt(Math.max(0, p.id()));
        b.writeUtf(clip(p.fullName(), SHORT), SHORT);
        b.writeUtf(clip(p.shortName(), SHORT), SHORT);
        b.writeUtf(clip(p.jersey(), 8), 8);
        b.writeUtf(clip(p.position(), 8), 8);
        b.writeUtf(clip(p.batSide(), 8), 8);
        b.writeUtf(clip(p.pitchHand(), 8), 8);
        b.writeInt(p.battingOrder());
    }

    private static LivePlayer readPlayer(FriendlyByteBuf b) {
        return new LivePlayer(b.readVarInt(), b.readUtf(SHORT), b.readUtf(SHORT), b.readUtf(8), b.readUtf(8), b.readUtf(8), b.readUtf(8), b.readInt());
    }

    private static void writePitch(FriendlyByteBuf b, LivePitch p) {
        b.writeUtf(clip(p.id(), SHORT), SHORT);
        b.writeUtf(clip(p.typeCode(), 8), 8);
        b.writeUtf(clip(p.typeName(), SHORT), SHORT);
        b.writeDouble(p.mph());
        b.writeUtf(clip(p.call(), SHORT), SHORT);
        b.writeBoolean(p.isStrike());
        b.writeBoolean(p.isBall());
        b.writeBoolean(p.isInPlay());
        b.writeDouble(p.plateX());
        b.writeDouble(p.plateZ());
        b.writeDouble(p.zoneTop());
        b.writeDouble(p.zoneBottom());
        b.writeInt(p.ballsAfter());
        b.writeInt(p.strikesAfter());
        b.writeUtf(clip(p.callCode(), 8), 8);
    }

    private static LivePitch readPitch(FriendlyByteBuf b) {
        return new LivePitch(
            b.readUtf(SHORT), b.readUtf(8), b.readUtf(SHORT), b.readDouble(), b.readUtf(SHORT), b.readBoolean(), b.readBoolean(), b.readBoolean(),
            b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readInt(), b.readInt(), b.readUtf(8)
        );
    }

    public static void handle(LiveWatchSyncPacket p, Supplier<Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.receiveLiveWatch(p.controller, p.snapshot));
    }
}
