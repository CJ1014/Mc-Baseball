package com.cj.mcbaseball.live.mlb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.Fixtures;
import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameStatus.State;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class MlbStatusMapperTest {

    private static LiveGameStatus map(String abs, String coded, String code, String detailed, String reason) {
        JsonObject o = new JsonObject();
        if (abs != null) o.addProperty("abstractGameState", abs);
        if (coded != null) o.addProperty("codedGameState", coded);
        if (code != null) o.addProperty("statusCode", code);
        if (detailed != null) o.addProperty("detailedState", detailed);
        if (reason != null) o.addProperty("reason", reason);
        return MlbStatusMapper.map(o);
    }

    /** Every status MLB documents at /api/v1/gameStatus maps to a sensible state; only Writing/Unknown are UNKNOWN. */
    @Test
    void wholeCatalogueMaps() {
        JsonArray all = JsonParser.parseString(Fixtures.mlb("gameStatus_catalogue.json")).getAsJsonArray();
        assertTrue(all.size() > 200, "catalogue looks truncated");
        for (int i = 0; i < all.size(); i++) {
            JsonObject s = all.get(i).getAsJsonObject();
            LiveGameStatus st = MlbStatusMapper.map(s);
            String abs = s.get("abstractGameState").getAsString();
            String code = s.get("statusCode").getAsString();
            String where = code + " " + s.get("detailedState").getAsString();
            switch (abs) {
                case "Preview" -> assertEquals(LiveGameStatus.Section.UPCOMING, st.state().section(), where);
                case "Live" -> assertEquals(LiveGameStatus.Section.LIVE, st.state().section(), where);
                case "Final" -> assertTrue(st.state().isOver(), where);
                default -> assertEquals(State.UNKNOWN, st.state(), where);
            }
            if (!abs.equals("Other")) {
                assertNotEquals(State.UNKNOWN, st.state(), where);
            }
            assertTrue(!st.label().isEmpty(), where);
        }
    }

    @Test
    void trickyRealCodes() {
        assertEquals(State.SCHEDULED, map("Preview", "S", "S", "Scheduled", null).state());
        assertEquals(State.PREGAME, map("Preview", "P", "P", "Pre-Game", null).state());
        assertEquals(State.DELAYED_START, map("Preview", "P", "PR", "Delayed Start: Rain", "Rain").state());
        // "Scheduled: COVID-19" reuses coded state T (which otherwise means Suspended).
        assertEquals(State.SCHEDULED, map("Preview", "T", "T9", "Scheduled: COVID-19", "COVID-19").state());
        // Warmup is abstract "Live".
        assertEquals(State.WARMUP, map("Live", "P", "PW", "Warmup", null).state());
        assertEquals(State.LIVE, map("Live", "I", "I", "In Progress", null).state());
        assertEquals(State.REVIEW, map("Live", "I", "IH", "Instant Replay", "Review").state());
        assertEquals(State.DELAYED, map("Live", "I", "IR", "Delayed: Rain", "Rain").state());
        assertEquals(State.DELAYED, map("Live", "I", "IZ", "Delayed: About to Resume", "About to Resume").state());
        assertEquals(State.REVIEW, map("Live", "M", "MA", "Manager challenge: Tag play", "Tag play").state());
        assertEquals(State.SUSPENDED, map("Live", "U", "UR", "Suspended: Rain", "Rain").state());
        // Postponed / cancelled are abstract "Final".
        assertEquals(State.POSTPONED, map("Final", "D", "DR", "Postponed: Rain", "Rain").state());
        assertEquals(State.CANCELLED, map("Final", "C", "CR", "Cancelled: Rain", "Rain").state());
        assertEquals(State.FINAL, map("Final", "F", "FR", "Completed Early: Rain", "Rain").state());
        assertEquals(State.FINAL, map("Final", "O", "O", "Game Over", null).state());
        assertEquals(State.FORFEIT, map("Final", "Q", "Q", "Forfeit", null).state());
    }

    @Test
    void missingOrNewFieldsNeverThrow() {
        assertEquals(State.UNKNOWN, MlbStatusMapper.map(null).state());
        assertEquals(State.UNKNOWN, MlbStatusMapper.map(new JsonObject()).state());
        // No abstract state: inferred from coded letter.
        assertEquals(State.LIVE, map(null, "I", null, null, null).state());
        assertEquals(State.POSTPONED, map(null, "D", "DR", null, "Rain").state());
        // A brand new abstract state with an unknown letter.
        assertEquals(State.UNKNOWN, map("Hologram", "Z", "Z9", "Played on Mars", null).state());
        // Wrong JSON types.
        JsonObject weird = new JsonObject();
        weird.add("abstractGameState", new JsonArray());
        weird.addProperty("codedGameState", 7);
        assertEquals(State.UNKNOWN, MlbStatusMapper.map(weird).state());
    }

    @Test
    void labels() {
        assertEquals("RAIN DELAY", map("Live", "I", "IR", "Delayed: Rain", "Rain").label());
        assertEquals("POSTPONED: RAIN", map("Final", "D", "DR", "Postponed: Rain", "Rain").label());
        assertEquals("FINAL", map("Final", "F", "F", "Final", null).label());
        assertEquals("COMPLETED EARLY: RAIN", map("Final", "F", "FR", "Completed Early: Rain", "Rain").label());
        assertEquals("LIVE", map("Live", "I", "I", "In Progress", null).label());
        assertEquals("NOT STARTED", map("Preview", "S", "S", "Scheduled", null).label());
    }
}
