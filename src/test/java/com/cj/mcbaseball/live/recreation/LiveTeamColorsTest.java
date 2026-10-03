package com.cj.mcbaseball.live.recreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.cj.mcbaseball.live.model.LiveTeam;
import org.junit.jupiter.api.Test;

class LiveTeamColorsTest {

    @Test
    void homeWhiteRoadGrayAndClashes() {
        LiveTeam nyy = new LiveTeam(147, "New York Yankees", "NYY", "Yankees", "Bronx");
        LiveTeam tb = new LiveTeam(139, "Tampa Bay Rays", "TB", "Rays", "Tampa Bay");
        LiveTeam lad = new LiveTeam(119, "Los Angeles Dodgers", "LAD", "Dodgers", "Los Angeles");
        LiveTeamColors.Uniform[] u = LiveTeamColors.pick(lad, nyy);
        assertEquals(LiveTeamColors.WHITE, u[1].secondary());
        assertEquals(LiveTeamColors.GRAY, u[0].secondary());
        // NYY and TB share navy: the road team switches to gray jerseys so the sides stay readable.
        LiveTeamColors.Uniform[] clash = LiveTeamColors.pick(tb, nyy);
        assertEquals(LiveTeamColors.GRAY, clash[0].primary());
        assertNotEquals(clash[0].primary(), clash[1].primary());
    }

    @Test
    void unknownTeamsGetStableColour() {
        LiveTeam x = new LiveTeam(9999, "Mystery Club", "MYS", "Mystery", "Nowhere");
        assertEquals(LiveTeamColors.teamColor(x), LiveTeamColors.teamColor(x));
    }
}
