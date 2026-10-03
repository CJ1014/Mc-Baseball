package com.cj.mcbaseball.live.recorded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.Fixtures;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordedFeedProviderTest {

    @Test
    void findsFixtureSequenceAndReplaysItInOrder() throws Exception {
        Map<Long, List<Path>> found = RecordedGames.scan(Fixtures.recordedDir());
        assertEquals(20, found.get(849828L).size());
        RecordedFeedProvider p = new RecordedFeedProvider(found, Runnable::run);
        long id = RecordedFeedProvider.idFor(849828L);
        assertEquals(-849828L, id);
        assertEquals("20261003_210650", p.getLiveFeed(id).get(5, TimeUnit.SECONDS).state().feedTimestamp());
        assertEquals("20261003_210744", p.getLiveFeed(id).get(5, TimeUnit.SECONDS).state().feedTimestamp());
        for (int i = 0; i < 30; i++) {
            p.getLiveFeed(id).get(5, TimeUnit.SECONDS);
        }
        assertEquals("20261003_211836", p.getLiveFeed(id).get(5, TimeUnit.SECONDS).state().feedTimestamp(), "stays on the last snapshot");
        p.rewind(id);
        assertEquals("20261003_210650", p.getLiveFeed(id).get(5, TimeUnit.SECONDS).state().feedTimestamp());

        List<LiveGameSummary> s = p.summaries();
        assertEquals(1, s.size());
        assertEquals(id, s.get(0).gameId());
        assertEquals("ATL @ LAD", s.get(0).matchupAbbr());
        assertTrue(s.get(0).description().startsWith("RECORDED - 20 snapshots"));
    }

    @Test
    void readsDebugRecorderLayoutAndSkipsJunk(@TempDir Path dir) throws Exception {
        Path day = Files.createDirectories(dir.resolve("2026-10-03"));
        String feed = Fixtures.mlbGz("feeds/feed_849829_final.json.gz");
        Files.writeString(day.resolve("210000-001_feed_849829.json"), feed);
        Files.writeString(day.resolve("210010-001_feed_849829.json"), feed);
        Files.writeString(day.resolve("210005-001_schedule_2026-10-03.json"), "{}");
        Files.writeString(day.resolve("notes.txt"), "hi");
        Map<Long, List<Path>> found = RecordedGames.scan(dir);
        assertEquals(1, found.size());
        assertEquals(2, found.get(849829L).size());
        assertTrue(RecordedGames.scan(dir.resolve("missing")).isEmpty());

        Files.writeString(day.resolve("210020-001_feed_123.json"), "not json");
        RecordedFeedProvider p = new RecordedFeedProvider(RecordedGames.scan(dir), Runnable::run);
        assertEquals(1, p.summaries().size(), "unreadable recording left out of the list");
        assertThrows(ExecutionException.class, () -> p.getLiveFeed(-123L).get(5, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class, () -> p.getLiveFeed(-999L).get(5, TimeUnit.SECONDS));
    }
}
