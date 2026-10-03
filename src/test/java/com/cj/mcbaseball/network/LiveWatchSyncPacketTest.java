package com.cj.mcbaseball.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.Fixtures;
import com.cj.mcbaseball.live.mlb.MlbLiveFeedParser;
import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveProviderStatus;
import com.cj.mcbaseball.live.model.LiveWatchSnapshot;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class LiveWatchSyncPacketTest {

    private static LiveWatchSyncPacket roundTrip(LiveWatchSyncPacket p) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        LiveWatchSyncPacket.encode(p, buf);
        LiveWatchSyncPacket out = LiveWatchSyncPacket.decode(buf);
        assertEquals(0, buf.readableBytes());
        return out;
    }

    @Test
    void realStatesSurviveTheWire() throws Exception {
        for (String f : new String[]{"recorded/849828_atl-lad_bot4/20261003_211803.json.gz", "recorded/849828_atl-lad_bot4/20261003_211836.json.gz",
            "feeds/feed_849829_final.json.gz", "feeds/feed_849835_pregame.json.gz"}) {
            long pk = f.contains("849829") ? 849829L : f.contains("849835") ? 849835L : 849828L;
            LiveGameState s = MlbLiveFeedParser.parse(Fixtures.mlbGz(f), pk);
            LiveWatchSnapshot snap = new LiveWatchSnapshot(pk, s, LiveProviderStatus.STALE, 10L, 20L, 5000L, "timed out", "MLB Stats API", 4,
                List.of("Ball (1-0) - 96.4 Four-Seam Fastball", "Max Muncy lines out."), List.of("Game ID: " + pk, "Queued: 0"));
            LiveWatchSyncPacket out = roundTrip(new LiveWatchSyncPacket(new BlockPos(1, 2, 3), snap));
            assertEquals(new BlockPos(1, 2, 3), out.controller());
            // Lineups stay on the server.
            LiveGameState expected = new LiveGameState(s.gameId(), s.status(), s.away(), s.home(), s.startEpochMillis(), s.awayScore(), s.homeScore(),
                s.inning(), s.inningState(), s.balls(), s.strikes(), s.outs(), s.betweenBatters(), s.batter(), s.pitcher(), s.onDeck(), s.runnerOnFirst(),
                s.runnerOnSecond(), s.runnerOnThird(), s.awayTotals(), s.homeTotals(), s.awayInningRuns(), s.homeInningRuns(), s.lastPitch(),
                s.lastPlay(), s.venue(), s.feedTimestamp(), s.suggestedPollSeconds(), List.of(), List.of());
            assertEquals(new LiveWatchSnapshot(pk, expected, LiveProviderStatus.STALE, 10L, 20L, 5000L, "timed out", "MLB Stats API", 4,
                List.of("Ball (1-0) - 96.4 Four-Seam Fastball", "Max Muncy lines out."), List.of("Game ID: " + pk, "Queued: 0")), out.snapshot(), f);
        }
    }

    @Test
    void clearAndNoStateYet() {
        assertNull(roundTrip(new LiveWatchSyncPacket(BlockPos.ZERO, null)).snapshot());
        LiveWatchSnapshot loading = new LiveWatchSnapshot(5L, null, LiveProviderStatus.UNAVAILABLE, 0, 0, 1000, "refused", "x", 1, null, null);
        LiveWatchSnapshot out = roundTrip(new LiveWatchSyncPacket(BlockPos.ZERO, loading)).snapshot();
        assertNull(out.state());
        assertEquals(loading, out);
        assertTrue(out.retryInMillis() > 0);
    }
}
