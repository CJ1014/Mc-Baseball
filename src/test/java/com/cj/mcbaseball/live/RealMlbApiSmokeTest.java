package com.cj.mcbaseball.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.mlb.MlbStatsApiProvider;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.net.LiveApiClient;
import com.cj.mcbaseball.live.net.LiveDataException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Hits the real MLB Stats API. Off by default (needs internet, data changes daily):
 * {@code ./gradlew test -Dmcbaseball.liveTests=true --tests '*RealMlbApiSmokeTest'}
 */
@EnabledIfSystemProperty(named = "mcbaseball.liveTests", matches = "true")
class RealMlbApiSmokeTest {

    @Test
    void todaysScheduleAndFeedEndpoints() throws Exception {
        ExecutorService io = Executors.newFixedThreadPool(2);
        ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
        try {
            LiveApiClient client = new LiveApiClient(io, sched, LiveApiClient.Settings.defaults("MCBaseball-test"), System::currentTimeMillis, null);
            MlbStatsApiProvider provider = new MlbStatsApiProvider(client, MlbStatsApiProvider.DEFAULT_BASE_URL, System.out::println);
            LocalDate today = LiveDates.baseballToday(Instant.now());
            List<LiveGameSummary> games = provider.getGamesForDate(today).get(30, TimeUnit.SECONDS);
            System.out.println("[smoke] " + today + ": " + games.size() + " games");
            for (LiveGameSummary g : games) {
                System.out.println("[smoke]   " + g.gameId() + " " + g.matchupAbbr() + " " + g.section() + " " + g.status().label() + " " + g.inningLabel()
                    + (g.showsScore() ? " " + g.awayScore() + "-" + g.homeScore() : ""));
                assertTrue(g.gameId() > 0);
                assertTrue(!g.away().displayAbbr().equals("???") && !g.home().displayAbbr().equals("???"), "abbreviations missing - hydrate=team dropped?");
            }
            if (!games.isEmpty()) {
                long pk = games.get(0).gameId();
                Optional<LiveGameSummary> one = provider.getGameInfo(pk).get(30, TimeUnit.SECONDS);
                assertTrue(one.isPresent());
                assertEquals(pk, one.get().gameId());
                // The live feed lives under v1.1; v1 is a 404. Phase 2 depends on this.
                assertNotNull(client.get("https://statsapi.mlb.com/api/v1.1/game/" + pk + "/feed/live", "feed", 0).get(30, TimeUnit.SECONDS));
                try {
                    client.get("https://statsapi.mlb.com/api/v1/game/" + pk + "/feed/live", "feed_v1", 0).get(30, TimeUnit.SECONDS);
                    System.out.println("[smoke] NOTE: v1 feed/live answered - MLB changed something");
                } catch (ExecutionException e) {
                    assertEquals(LiveDataException.Kind.NOT_FOUND, LiveDataException.from(e.getCause()).kind());
                }
            }
        } finally {
            io.shutdownNow();
            sched.shutdownNow();
        }
    }
}
