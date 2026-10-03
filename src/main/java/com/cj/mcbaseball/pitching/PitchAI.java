package com.cj.mcbaseball.pitching;

import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.Difficulty;
import com.cj.mcbaseball.team.NpcProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class PitchAI {
    public static PitchAI.Choice choose(BaseballGame g, NpcProfile p, @Nullable PitchType last, int sameTypeCount, int lastQuadrant) {
        Random r = g.rng;
        Difficulty d = g.difficulty();
        List<PitchType> options = new ArrayList<>(p.repertoire);
        double[] w = new double[options.size()];
        double total = 0.0;
        boolean behind = g.balls >= 2 && g.balls > g.strikes;

        for (int i = 0; i < options.size(); i++) {
            PitchType t = options.get(i);
            double wt = t.isFastball() ? (behind ? 6.0 : 3.5) : (double)(g.strikes == 2 ? 3 : 2);
            if (t == last && sameTypeCount >= 2) {
                wt = 0.0;
            }

            w[i] = wt;
            total += wt;
        }

        PitchType type = PitchType.FOUR_SEAM;
        double roll = r.nextDouble() * Math.max(total, 1.0E-6);

        for (int i = 0; i < options.size(); i++) {
            roll -= w[i];
            if (roll <= 0.0) {
                type = options.get(i);
                break;
            }
        }

        double zoneChance = behind ? 0.55 : (g.strikes <= g.balls && g.strikes != 2 ? 0.42 : 0.28);

        double edgeBias = switch (d) {
            case ROOKIE -> 0.25;
            case NORMAL -> 0.5;
            case ALL_STAR -> 0.7;
            case LEGEND -> 0.85;
        };
        double lat;
        double h;
        if (r.nextDouble() < zoneChance) {
            boolean edge = r.nextDouble() < edgeBias;
            lat = edge ? (double)(r.nextBoolean() ? 1 : -1) * (0.28 + r.nextDouble() * 0.17) : (r.nextDouble() - 0.5) * 0.5;
            h = edge ? (r.nextBoolean() ? 0.55 + r.nextDouble() * 0.15 : 1.1 + r.nextDouble() * 0.15) : 0.75 + r.nextDouble() * 0.35;
        } else {
            lat = (double)(r.nextBoolean() ? 1 : -1) * (0.6 + r.nextDouble() * 0.18);
            h = type.isFastball() ? 0.5 + r.nextDouble() * 1.0 : 0.25 + r.nextDouble() * 0.3;
        }

        int quad = (lat >= 0.0 ? 1 : 0) + (h >= 0.9 ? 2 : 0);
        if (quad == lastQuadrant && r.nextDouble() < 0.6) {
            lat = -lat;
            quad ^= 1;
        }
        double diffBonus = switch (d) {
            case ROOKIE -> -0.1;
            case NORMAL -> 0.0;
            case ALL_STAR -> 0.06;
            case LEGEND -> 0.12;
        };
        double quality = Mth.clamp(0.5 + (double)p.control / 99.0 * 0.4 + diffBonus + r.nextGaussian() * 0.12, 0.05, 1.0);
        return new PitchAI.Choice(type, lat, h, quality, quad);
    }

    public static Vec3 target(BaseballGame g, PitchAI.Choice c) {
        return g.geo.zonePoint(c.lateral(), c.height());
    }

    private PitchAI() {
    }

    public static record Choice(PitchType type, double lateral, double height, double quality, int quadrant) {
    }
}
