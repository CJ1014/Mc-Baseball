package com.cj.mcbaseball.network;

import com.cj.mcbaseball.client.ClientHooks;
import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LiveLineTotals;
import com.cj.mcbaseball.live.model.LiveProviderStatus;
import com.cj.mcbaseball.live.model.LiveSchedule;
import com.cj.mcbaseball.live.model.LiveTeam;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

/** Server -> client: one day's live schedule snapshot (games + connection status). */
public record LiveScheduleSyncPacket(LiveSchedule schedule) {
    static final int MAX_GAMES = 64;
    static final int MAX_INNINGS = 30;
    static final int SHORT = 64;
    static final int LONG = 256;

    public static void encode(LiveScheduleSyncPacket p, FriendlyByteBuf b) {
        LiveSchedule s = p.schedule;
        b.writeLong(s.epochDay());
        b.writeLong(s.todayEpochDay());
        b.writeEnum(s.status());
        b.writeLong(s.fetchedAtMillis());
        b.writeLong(s.serverNowMillis());
        b.writeVarLong(Math.max(0L, s.retryInMillis()));
        b.writeUtf(clip(s.message(), LONG), LONG);
        b.writeUtf(clip(s.provider(), SHORT), SHORT);
        b.writeVarInt(s.version());
        List<LiveGameSummary> games = s.games();
        int n = Math.min(games.size(), MAX_GAMES);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            writeGame(b, games.get(i));
        }
    }

    public static LiveScheduleSyncPacket decode(FriendlyByteBuf b) {
        long epochDay = b.readLong();
        long todayEpochDay = b.readLong();
        LiveProviderStatus status = b.readEnum(LiveProviderStatus.class);
        long fetchedAt = b.readLong();
        long serverNow = b.readLong();
        long retryIn = b.readVarLong();
        String message = b.readUtf(LONG);
        String provider = b.readUtf(SHORT);
        int version = b.readVarInt();
        int n = b.readVarInt();
        if (n < 0 || n > MAX_GAMES) {
            throw new IllegalArgumentException("Too many live games in packet: " + n);
        }
        List<LiveGameSummary> games = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            games.add(readGame(b));
        }
        return new LiveScheduleSyncPacket(new LiveSchedule(epochDay, todayEpochDay, games, status, fetchedAt, serverNow, retryIn, message, provider, version));
    }

    private static void writeGame(FriendlyByteBuf b, LiveGameSummary g) {
        b.writeLong(g.gameId());
        b.writeUtf(clip(g.officialDate(), SHORT), SHORT);
        writeTeam(b, g.away());
        writeTeam(b, g.home());
        b.writeEnum(g.status().state());
        b.writeUtf(clip(g.status().detailed(), SHORT), SHORT);
        b.writeUtf(clip(g.status().reason(), SHORT), SHORT);
        b.writeUtf(clip(g.status().code(), SHORT), SHORT);
        b.writeLong(g.startEpochMillis());
        b.writeBoolean(g.startTimeTbd());
        b.writeInt(g.awayScore());
        b.writeInt(g.homeScore());
        b.writeInt(g.inning());
        b.writeUtf(clip(g.inningState(), SHORT), SHORT);
        b.writeInt(g.balls());
        b.writeInt(g.strikes());
        b.writeInt(g.outs());
        writeTotals(b, g.awayTotals());
        writeTotals(b, g.homeTotals());
        writeInnings(b, g.awayInningRuns());
        writeInnings(b, g.homeInningRuns());
        b.writeUtf(clip(g.venue(), LONG), LONG);
        b.writeUtf(clip(g.description(), LONG), LONG);
        b.writeVarInt(Math.max(0, g.gameNumber()));
        b.writeBoolean(g.doubleHeader());
    }

    private static LiveGameSummary readGame(FriendlyByteBuf b) {
        long id = b.readLong();
        String date = b.readUtf(SHORT);
        LiveTeam away = readTeam(b);
        LiveTeam home = readTeam(b);
        LiveGameStatus status = new LiveGameStatus(b.readEnum(LiveGameStatus.State.class), b.readUtf(SHORT), b.readUtf(SHORT), b.readUtf(SHORT));
        long start = b.readLong();
        boolean tbd = b.readBoolean();
        int awayScore = b.readInt();
        int homeScore = b.readInt();
        int inning = b.readInt();
        String inningState = b.readUtf(SHORT);
        int balls = b.readInt();
        int strikes = b.readInt();
        int outs = b.readInt();
        LiveLineTotals awayTotals = readTotals(b);
        LiveLineTotals homeTotals = readTotals(b);
        List<Integer> awayInn = readInnings(b);
        List<Integer> homeInn = readInnings(b);
        String venue = b.readUtf(LONG);
        String description = b.readUtf(LONG);
        int gameNumber = b.readVarInt();
        boolean dh = b.readBoolean();
        return new LiveGameSummary(
            id, date, away, home, status, start, tbd, awayScore, homeScore, inning, inningState, balls, strikes, outs,
            awayTotals, homeTotals, awayInn, homeInn, venue, description, gameNumber, dh
        );
    }

    static void writeTeam(FriendlyByteBuf b, LiveTeam t) {
        b.writeVarInt(Math.max(0, t.id()));
        b.writeUtf(clip(t.name(), SHORT), SHORT);
        b.writeUtf(clip(t.abbreviation(), SHORT), SHORT);
        b.writeUtf(clip(t.clubName(), SHORT), SHORT);
        b.writeUtf(clip(t.locationName(), SHORT), SHORT);
    }

    static LiveTeam readTeam(FriendlyByteBuf b) {
        return new LiveTeam(b.readVarInt(), b.readUtf(SHORT), b.readUtf(SHORT), b.readUtf(SHORT), b.readUtf(SHORT));
    }

    static void writeTotals(FriendlyByteBuf b, LiveLineTotals t) {
        b.writeInt(t.runs());
        b.writeInt(t.hits());
        b.writeInt(t.errors());
        b.writeInt(t.leftOnBase());
    }

    static LiveLineTotals readTotals(FriendlyByteBuf b) {
        return new LiveLineTotals(b.readInt(), b.readInt(), b.readInt(), b.readInt());
    }

    static void writeInnings(FriendlyByteBuf b, List<Integer> runs) {
        int n = Math.min(runs.size(), MAX_INNINGS);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Integer r = runs.get(i);
            b.writeInt(r == null ? -1 : r);
        }
    }

    static List<Integer> readInnings(FriendlyByteBuf b) {
        int n = b.readVarInt();
        if (n < 0 || n > MAX_INNINGS) {
            throw new IllegalArgumentException("Too many innings in packet: " + n);
        }
        List<Integer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(b.readInt());
        }
        return out;
    }

    /** writeUtf throws if a string is longer than its limit; providers can send anything, so clip first. */
    static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static void handle(LiveScheduleSyncPacket p, Supplier<Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.receiveLiveSchedule(p.schedule));
    }
}
