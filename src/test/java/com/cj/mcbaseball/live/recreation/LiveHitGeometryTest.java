package com.cj.mcbaseball.live.recreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.Fixtures;
import com.cj.mcbaseball.live.mlb.MlbLiveFeedParser;
import com.cj.mcbaseball.live.model.LiveFeed;
import com.cj.mcbaseball.live.model.LiveHit;
import com.cj.mcbaseball.live.model.LivePlay;
import com.cj.mcbaseball.live.model.LivePlayEvent;
import org.junit.jupiter.api.Test;

class LiveHitGeometryTest {

    private static LiveHit spray(double x, double y) {
        return new LiveHit(Double.NaN, Double.NaN, Double.NaN, "", "", "", x, y);
    }

    @Test
    void sprayChartDirections() {
        assertEquals(0.0, LiveHitGeometry.sprayAngle(spray(125.42, 50)), 1e-6);
        assertEquals(45.0, LiveHitGeometry.sprayAngle(spray(125.42 + 100, 198.27 - 100)), 1e-6);
        assertEquals(-45.0, LiveHitGeometry.sprayAngle(spray(125.42 - 100, 198.27 - 100)), 1e-6);
        assertEquals(0.0, LiveHitGeometry.sprayAngle(null));
    }

    @Test
    void fielderLocationWhenNoCoordinates() {
        LiveHit toSS = new LiveHit(Double.NaN, Double.NaN, Double.NaN, "ground_ball", "", "6", Double.NaN, Double.NaN);
        assertTrue(LiveHitGeometry.sprayAngle(toSS) < 0, "shortstop is on the left side");
        LiveHit toRF = new LiveHit(Double.NaN, Double.NaN, Double.NaN, "fly_ball", "", "9", Double.NaN, Double.NaN);
        assertTrue(LiveHitGeometry.sprayAngle(toRF) > 0);
    }

    @Test
    void realStatcastValuesWin() {
        LiveHit h = new LiveHit(104.3, 27.0, 401, "fly_ball", "hard", "8", 130, 40);
        assertEquals(104.3, LiveHitGeometry.exitMph(h));
        assertEquals(27.0, LiveHitGeometry.launchDegrees(h));
        LiveHit none = new LiveHit(Double.NaN, Double.NaN, Double.NaN, "ground_ball", "", "", Double.NaN, Double.NaN);
        assertTrue(LiveHitGeometry.launchDegrees(none) < 0, "grounders go down");
        assertTrue(Double.isFinite(LiveHitGeometry.exitMph(null)));
    }

    /** Real balls in play stay in fair territory (within the foul lines) unless they were fouls. */
    @Test
    void realBallsInPlayPointIntoTheField() throws Exception {
        LiveFeed feed = MlbLiveFeedParser.parseFeed(Fixtures.mlbGz("feeds/feed_849829_final.json.gz"), 849829L);
        int checked = 0;
        for (LivePlay play : feed.plays()) {
            for (LivePlayEvent e : play.events()) {
                LiveHit h = e.hit();
                if (e.kind() == LivePlayEvent.Kind.PITCH && e.pitch().isInPlay() && h != null && h.hasSpray()) {
                    double a = LiveHitGeometry.sprayAngle(h);
                    assertTrue(Math.abs(a) < 60.0, play.description() + " -> " + a);
                    checked++;
                }
            }
        }
        assertTrue(checked > 30, "checked " + checked);
    }
}
