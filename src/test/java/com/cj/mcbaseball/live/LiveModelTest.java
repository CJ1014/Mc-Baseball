package com.cj.mcbaseball.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LiveSchedule;
import com.cj.mcbaseball.live.model.LiveTeam;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveModelTest {

    @Test
    void ordinals() {
        String[] expect = {"1st", "2nd", "3rd", "4th", "9th", "10th", "11th", "12th", "13th", "21st", "22nd", "23rd", "101st", "111th"};
        int[] n = {1, 2, 3, 4, 9, 10, 11, 12, 13, 21, 22, 23, 101, 111};
        for (int i = 0; i < n.length; i++) {
            assertEquals(expect[i], LiveGameSummary.ordinal(n[i]));
        }
    }

    @Test
    void teamFallbacks() {
        assertEquals("NYY", new LiveTeam(147, "New York Yankees", "NYY", "Yankees", "Bronx").displayAbbr());
        assertEquals("RED", new LiveTeam(0, "", "", "Red Sox", "").displayAbbr());
        assertEquals("???", new LiveTeam(0, null, null, null, null).displayAbbr());
        assertEquals("NYY", new LiveTeam(0, "", "NYY", "", "").displayShort());
        assertEquals("Yankees", new LiveTeam(0, "", "", "Yankees", "").displayFull());
    }

    @Test
    void nullsNeverLeakOut() {
        LiveGameSummary g = new LiveGameSummary(1, null, null, null, null, 0, false, -1, -1, -1, null, -1, -1, -1, null, null, null, null, null, null, 1, false);
        assertEquals("??? @ ???", g.matchupAbbr());
        assertEquals(LiveGameStatus.State.UNKNOWN, g.status().state());
        assertEquals("", g.inningLabel());
        assertFalse(g.showsScore());
        LiveSchedule s = new LiveSchedule(0, 0, null, null, 0, 0, 0, null, null, 0);
        assertTrue(s.games().isEmpty());
        assertEquals(null, s.find(1));
    }

    @Test
    void inningLabels() {
        LiveGameSummary.ordinal(1);
        assertEquals("Mid 7th", withInning(7, "Middle").inningLabel());
        assertEquals("End 9th", withInning(9, "End").inningLabel());
        assertEquals("Top 12th", withInning(12, "Top").inningLabel());
        assertEquals("3rd", withInning(3, "Sideways").inningLabel());
    }

    private static LiveGameSummary withInning(int inning, String state) {
        return new LiveGameSummary(1, "", null, null, LiveGameStatus.of(LiveGameStatus.State.LIVE), 0, false, 0, 0, inning, state, 0, 0, 0,
            null, null, List.of(), List.of(), "", "", 1, false);
    }
}
