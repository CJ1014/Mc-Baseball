package com.cj.mcbaseball.live.recreation;

import com.cj.mcbaseball.game.GameTeam;
import com.cj.mcbaseball.game.LineupSlot;
import com.cj.mcbaseball.game.Position;
import com.cj.mcbaseball.game.TeamSide;
import com.cj.mcbaseball.live.model.LivePlayer;
import com.cj.mcbaseball.live.model.LiveTeam;
import com.cj.mcbaseball.pitching.PitchType;
import com.cj.mcbaseball.team.NpcProfile;
import com.cj.mcbaseball.team.TeamData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A real team as the mod's lineup slots: one slot (and one NPC) per real player, the real batting order
 * (designated hitter included) and the real fielder at each position. The same slot is used whether the
 * player is batting or fielding.
 *
 * <p>The game's team swaps its {@code order} with {@link #applyTo}: the fielders while defending (what the
 * fielding AI walks), the batting order while batting (who sits in the dugout).
 */
final class LiveRoster {

    static final int MAX_BATTING = 12;

    final TeamSide side;
    final TeamData data;
    final Map<Integer, LineupSlot> byPlayer = new HashMap<>();
    final Map<LineupSlot, Integer> playerOf = new IdentityHashMap<>();
    final List<LineupSlot> batting = new ArrayList<>();
    final EnumMap<Position, LineupSlot> fielders = new EnumMap<>(Position.class);

    private LiveRoster(TeamSide side, TeamData data) {
        this.side = side;
        this.data = data;
    }

    static LiveRoster build(TeamSide side, LiveTeam team, LiveTeamColors.Uniform uniform, List<LivePlayer> lineup, LivePlayer currentPitcher) {
        UUID id = UUID.nameUUIDFromBytes(("live-team-" + team.id() + "-" + team.displayAbbr()).getBytes(StandardCharsets.UTF_8));
        LiveRoster r = new LiveRoster(side, new TeamData(id, team.displayFull(), team.displayAbbr(), uniform.primary(), uniform.secondary()));
        for (LivePlayer p : lineup) {
            if (r.batting.size() >= MAX_BATTING) {
                break;
            }
            Position pos = position(p.position());
            LineupSlot s = r.slot(p, pos);
            r.batting.add(s);
            if (pos != null && !r.fielders.containsKey(pos)) {
                r.fielders.put(pos, s);
            }
        }
        if (currentPitcher.known()) {
            r.fielders.put(Position.PITCHER, r.slot(currentPitcher, Position.PITCHER));
        }
        for (Position pos : Position.values()) {
            if (!r.fielders.containsKey(pos)) {
                r.fielders.put(pos, r.placeholder(pos));
            }
            r.data.roster.put(pos, r.fielders.get(pos).npc);
        }
        return r;
    }

    /** "CF" -> CENTER_FIELD; DH / PH / PR / unknown -> null (bats or runs only). */
    @Nullable
    static Position position(String abbr) {
        for (Position p : Position.values()) {
            if (p.abbr.equalsIgnoreCase(abbr)) {
                return p;
            }
        }
        return null;
    }

    /** Existing slot for this player, or a new one. Players who only bat get a placeholder position. */
    LineupSlot slot(LivePlayer p, @Nullable Position pos) {
        LineupSlot s = p.id() > 0 ? this.byPlayer.get(p.id()) : null;
        if (s != null) {
            return s;
        }
        s = new LineupSlot(this.side, pos == null ? Position.CENTER_FIELD : pos, profile(p, pos == Position.PITCHER), this.data.id);
        if (p.id() > 0) {
            this.byPlayer.put(p.id(), s);
            this.playerOf.put(s, p.id());
        }
        return s;
    }

    private LineupSlot placeholder(Position pos) {
        LivePlayer fake = new LivePlayer(0, this.data.abbreviation + " " + pos.abbr, this.data.abbreviation + " " + pos.abbr, "", pos.abbr, "", "", -1);
        return new LineupSlot(this.side, pos, profile(fake, pos == Position.PITCHER), this.data.id);
    }

    /** The slot for a batter, adding a pinch hitter into his batting-order spot if he's new. */
    LineupSlot batter(LivePlayer p) {
        LineupSlot existing = this.byPlayer.get(p.id());
        if (existing != null) {
            if (!this.batting.contains(existing)) {
                this.putInOrder(existing, p.battingOrder());
            }
            return existing;
        }
        LineupSlot s = this.slot(p, position(p.position()));
        this.putInOrder(s, p.battingOrder());
        return s;
    }

    /** A runner who isn't on the roster yet (pinch runner). */
    LineupSlot runner(LivePlayer p) {
        return this.batter(p);
    }

    private void putInOrder(LineupSlot s, int battingSlot) {
        if (battingSlot >= 1 && battingSlot <= this.batting.size()) {
            this.batting.set(battingSlot - 1, s);
        } else if (this.batting.size() < MAX_BATTING) {
            this.batting.add(s);
        }
    }

    /** Makes this player the pitcher; returns the slot of the pitcher he replaces (or null). */
    @Nullable
    LineupSlot setPitcher(LivePlayer p) {
        LineupSlot old = this.fielders.get(Position.PITCHER);
        LineupSlot s = this.slot(p, Position.PITCHER);
        if (s == old) {
            return null;
        }
        this.fielders.put(Position.PITCHER, s);
        return old;
    }

    boolean isPitcher(LivePlayer p) {
        Integer id = this.playerOf.get(this.fielders.get(Position.PITCHER));
        return id != null && id == p.id();
    }

    /** Puts the right players into the game's team for this half-inning. */
    void applyTo(GameTeam t, boolean defending) {
        t.order.clear();
        if (defending) {
            for (Position pos : Position.BATTING_ORDER) {
                t.order.add(this.fielders.get(pos));
            }
        } else {
            t.order.addAll(this.batting);
        }
        t.byPosition.clear();
        t.byPosition.putAll(this.fielders);
    }

    static NpcProfile profile(LivePlayer p, boolean pitcher) {
        int number = 0;
        try {
            number = p.jersey().isEmpty() ? 0 : Integer.parseInt(p.jersey().trim());
        } catch (NumberFormatException ignored) {
        }
        boolean batsRight = !"L".equals(p.batSide());
        boolean throwsRight = !"L".equals(p.pitchHand());
        int skin = Math.floorMod(p.id() * 7 + p.display().hashCode(), 12);
        String name = p.display().isEmpty() ? "Player" : p.display();
        return new NpcProfile(name, number, batsRight, throwsRight, 60, 60, 60, 65, 65, 60, pitcher ? 75 : 45, 70, 70, skin, EnumSet.of(PitchType.FOUR_SEAM));
    }
}
