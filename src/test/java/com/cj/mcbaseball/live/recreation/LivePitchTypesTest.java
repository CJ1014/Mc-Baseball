package com.cj.mcbaseball.live.recreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.Fixtures;
import com.cj.mcbaseball.live.mlb.MlbLiveFeedParser;
import com.cj.mcbaseball.live.model.LiveFeed;
import com.cj.mcbaseball.live.model.LivePlay;
import com.cj.mcbaseball.live.model.LivePlayEvent;
import com.cj.mcbaseball.pitching.PitchType;
import org.junit.jupiter.api.Test;

class LivePitchTypesTest {

    @Test
    void knownCodes() {
        assertEquals(PitchType.FOUR_SEAM, LivePitchTypes.of("FF").type());
        assertEquals(PitchType.SINKER, LivePitchTypes.of("SI").type());
        assertEquals(PitchType.SLIDER, LivePitchTypes.of("SL").type());
        assertEquals(PitchType.CURVEBALL, LivePitchTypes.of("KC").type());
        assertEquals(PitchType.CHANGEUP, LivePitchTypes.of("CH").type());
        assertEquals(PitchType.FOUR_SEAM, LivePitchTypes.of("ff").type(), "case-insensitive");
        // Relatives share a mod type but break differently.
        assertTrue(LivePitchTypes.of("FC").breakScale() < LivePitchTypes.of("SL").breakScale());
        assertTrue(LivePitchTypes.of("ST").breakScale() > LivePitchTypes.of("SL").breakScale());
        assertTrue(LivePitchTypes.of("FS").breakScale() > LivePitchTypes.of("CH").breakScale());
        assertFalse(LivePitchTypes.of("FF").generic());
    }

    @Test
    void unknownCodesNeverFail() {
        assertSame(LivePitchTypes.GENERIC, LivePitchTypes.of("QQ"));
        assertSame(LivePitchTypes.GENERIC, LivePitchTypes.of(""));
        assertSame(LivePitchTypes.GENERIC, LivePitchTypes.of(null));
        assertEquals(0.0, LivePitchTypes.GENERIC.breakScale());
    }

    @Test
    void everyPitchTypeInARealGameIsKnown() throws Exception {
        LiveFeed feed = MlbLiveFeedParser.parseFeed(Fixtures.mlbGz("feeds/feed_849829_final.json.gz"), 849829L);
        for (LivePlay play : feed.plays()) {
            for (LivePlayEvent e : play.events()) {
                if (e.kind() == LivePlayEvent.Kind.PITCH && !e.pitch().typeCode().isEmpty()) {
                    assertFalse(LivePitchTypes.of(e.pitch().typeCode()).generic(), "unmapped real pitch type " + e.pitch().typeCode());
                }
            }
        }
    }
}
