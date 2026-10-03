package com.cj.mcbaseball.game;

import com.cj.mcbaseball.team.TeamData;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class GameTeam {
    public final TeamSide side;
    public final TeamData data;
    public final List<LineupSlot> order = new ArrayList<>();
    public final EnumMap<Position, LineupSlot> byPosition = new EnumMap<>(Position.class);
    public int runs;
    public int hits;
    public int errors;
    public final int[] runsByInning = new int[40];
    private int nextBatter;

    public GameTeam(TeamSide side, TeamData data) {
        this.side = side;
        this.data = data;

        for (Position p : Position.BATTING_ORDER) {
            LineupSlot s = new LineupSlot(side, p, data.roster.get(p), data.id);
            this.order.add(s);
            this.byPosition.put(p, s);
        }
    }

    @Nullable
    public LineupSlot at(Position p) {
        LineupSlot s = this.byPosition.get(p);
        return s != null && s.enabled ? s : null;
    }

    @Nullable
    public LineupSlot nextBatter() {
        for (int i = 0; i < this.order.size(); i++) {
            LineupSlot s = this.order.get(this.nextBatter % this.order.size());
            this.nextBatter = (this.nextBatter + 1) % this.order.size();
            if (s.enabled) {
                return s;
            }
        }

        return null;
    }

    public boolean hasAnyPlayer() {
        for (LineupSlot s : this.order) {
            if (s.enabled) {
                return true;
            }
        }

        return false;
    }

    public String abbr() {
        return this.data.abbreviation;
    }
}
