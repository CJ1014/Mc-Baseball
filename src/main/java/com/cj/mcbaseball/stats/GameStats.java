package com.cj.mcbaseball.stats;

import com.cj.mcbaseball.game.LineupSlot;
import com.cj.mcbaseball.game.TeamSide;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public final class GameStats {
    public final Map<String, StatLine> lines = new LinkedHashMap<>();

    public StatLine of(@Nullable LineupSlot s) {
        return s == null ? new StatLine() : this.lines.computeIfAbsent(s.statKey(), k -> {
            StatLine l = new StatLine();
            l.name = s.statName();
            l.team = s.side == TeamSide.HOME ? 0 : 1;
            return l;
        });
    }
}
