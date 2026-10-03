package com.cj.mcbaseball.live.recreation;

import com.cj.mcbaseball.live.model.LiveTeam;
import java.util.Map;

/**
 * Generic uniform colours for real teams, as indexes into the mod's {@code TeamColors} palette. Only plain
 * colour choices: no logos, no team artwork. Unknown teams get a stable colour from their id.
 */
public final class LiveTeamColors {

    static final int WHITE = 0;
    static final int GRAY = 1;
    private static final int[] FALLBACK = {3, 4, 5, 6, 9, 10, 11, 12, 13, 14, 2};
    private static final Map<String, Integer> MLB = Map.ofEntries(
        Map.entry("ARI", 4), Map.entry("AZ", 4), Map.entry("ATL", 13), Map.entry("BAL", 5), Map.entry("BOS", 3), Map.entry("CHC", 12),
        Map.entry("CWS", 2), Map.entry("CHW", 2), Map.entry("CIN", 3), Map.entry("CLE", 13), Map.entry("COL", 14), Map.entry("DET", 13),
        Map.entry("HOU", 5), Map.entry("KC", 12), Map.entry("LAA", 3), Map.entry("LAD", 12), Map.entry("MIA", 11), Map.entry("MIL", 13),
        Map.entry("MIN", 13), Map.entry("NYM", 12), Map.entry("NYY", 13), Map.entry("ATH", 9), Map.entry("OAK", 9), Map.entry("PHI", 3),
        Map.entry("PIT", 6), Map.entry("SD", 6), Map.entry("SF", 5), Map.entry("SEA", 10), Map.entry("STL", 3), Map.entry("TB", 13),
        Map.entry("TEX", 12), Map.entry("TOR", 12), Map.entry("WSH", 3), Map.entry("WSN", 3)
    );

    /** {primary, secondary} colour indexes: jersey and cap in the team colour, white pants at home, gray on the road. */
    public record Uniform(int primary, int secondary) {
    }

    private LiveTeamColors() {
    }

    static int teamColor(LiveTeam t) {
        Integer c = MLB.get(t.displayAbbr());
        return c != null ? c : FALLBACK[Math.floorMod(t.id() * 31 + t.displayAbbr().hashCode(), FALLBACK.length)];
    }

    /** Uniforms for both sides; if both teams share a colour, the road team wears gray. */
    public static Uniform[] pick(LiveTeam away, LiveTeam home) {
        int h = teamColor(home);
        int a = teamColor(away);
        Uniform homeU = new Uniform(h, WHITE);
        Uniform awayU = a == h ? new Uniform(GRAY, a) : new Uniform(a, GRAY);
        return new Uniform[]{awayU, homeU};
    }
}
