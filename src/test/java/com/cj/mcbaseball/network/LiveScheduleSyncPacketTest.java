package com.cj.mcbaseball.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cj.mcbaseball.live.Fixtures;
import com.cj.mcbaseball.live.mlb.MlbScheduleParser;
import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LiveProviderStatus;
import com.cj.mcbaseball.live.model.LiveSchedule;
import com.cj.mcbaseball.live.model.LiveTeam;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class LiveScheduleSyncPacketTest {

    private static LiveSchedule roundTrip(LiveSchedule s) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        LiveScheduleSyncPacket.encode(new LiveScheduleSyncPacket(s), buf);
        LiveSchedule out = LiveScheduleSyncPacket.decode(buf).schedule();
        assertEquals(0, buf.readableBytes(), "decoder left bytes unread");
        return out;
    }

    @Test
    void realScheduleSurvivesTheWire() throws Exception {
        List<LiveGameSummary> games = MlbScheduleParser.parse(Fixtures.mlb("schedule_2026-10-03_mixed.json"));
        LiveSchedule s = new LiveSchedule(20729, 20729, games, LiveProviderStatus.OK, 111L, 222L, 0L, "", "MLB Stats API", 7);
        assertEquals(s, roundTrip(s));

        List<LiveGameSummary> big = MlbScheduleParser.parse(Fixtures.mlb("schedule_2025-04-05_postponed.json"));
        LiveSchedule s2 = new LiveSchedule(1, 2, big, LiveProviderStatus.STALE, 1L, 2L, 3000L, "timed out", "MLB Stats API", 3);
        assertEquals(s2, roundTrip(s2));
    }

    @Test
    void oversizedProviderStringsAreClippedNotFatal() {
        String huge = "X".repeat(5000);
        LiveGameSummary g = new LiveGameSummary(1, huge, new LiveTeam(1, huge, huge, huge, huge), LiveTeam.UNKNOWN,
            new LiveGameStatus(LiveGameStatus.State.DELAYED, huge, huge, huge), 0, false, 1, 2, 3, huge, 0, 0, 0, null, null,
            java.util.Collections.nCopies(100, 1), List.of(), huge, huge, 1, false);
        LiveSchedule s = new LiveSchedule(1, 1, List.of(g), LiveProviderStatus.OK, 0, 0, 0, huge, huge, 1);
        LiveSchedule out = roundTrip(s);
        assertEquals(64, out.games().get(0).away().name().length());
        assertEquals(256, out.games().get(0).venue().length());
        assertEquals(LiveScheduleSyncPacket.MAX_INNINGS, out.games().get(0).awayInningRuns().size());
    }

    @Test
    void corruptGameCountIsRejected() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(1);
        buf.writeLong(1);
        buf.writeEnum(LiveProviderStatus.OK);
        buf.writeLong(0);
        buf.writeLong(0);
        buf.writeVarLong(0);
        buf.writeUtf("");
        buf.writeUtf("");
        buf.writeVarInt(1);
        buf.writeVarInt(1_000_000);
        assertThrows(IllegalArgumentException.class, () -> LiveScheduleSyncPacket.decode(buf));
    }
}
