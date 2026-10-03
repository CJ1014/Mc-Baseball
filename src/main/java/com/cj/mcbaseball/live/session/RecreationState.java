package com.cj.mcbaseball.live.session;

import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LivePitch;
import com.cj.mcbaseball.live.model.LivePlay;
import com.cj.mcbaseball.live.model.LivePlayer;
import com.cj.mcbaseball.live.model.LiveRunner;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The game as the Minecraft recreation has shown it so far: starts from the real state when watching
 * begins, then changes only through events that have been played. Lags the real game by the queue.
 */
public final class RecreationState {

    public int inning;
    public boolean top;
    public int balls;
    public int strikes;
    public int outs;
    public int awayScore;
    public int homeScore;
    /** base ("1B", "2B", "3B") -> runner */
    public final Map<String, LivePlayer> bases = new TreeMap<>();
    public LivePlayer batter = LivePlayer.NONE;
    public LivePlayer pitcher = LivePlayer.NONE;
    public LivePitch lastPitch = LivePitch.NONE;
    public String lastPlay = "";
    private final Set<String> appliedRunners = new HashSet<>();

    public void reset(LiveGameState s) {
        this.inning = Math.max(0, s.inning());
        this.top = s.isTop();
        this.balls = Math.max(0, s.balls());
        this.strikes = Math.max(0, s.strikes());
        this.outs = Math.max(0, s.outs());
        this.awayScore = Math.max(0, s.awayScore());
        this.homeScore = Math.max(0, s.homeScore());
        this.bases.clear();
        if (s.runnerOnFirst().known()) {
            this.bases.put("1B", s.runnerOnFirst());
        }
        if (s.runnerOnSecond().known()) {
            this.bases.put("2B", s.runnerOnSecond());
        }
        if (s.runnerOnThird().known()) {
            this.bases.put("3B", s.runnerOnThird());
        }
        this.batter = s.batter();
        this.pitcher = s.pitcher();
        this.lastPitch = s.lastPitch();
        this.lastPlay = s.lastPlay();
        this.appliedRunners.clear();
    }

    public void apply(LiveEvent e) {
        if (e instanceof LiveEvent.HalfInning h) {
            this.inning = h.inning();
            this.top = h.top();
            this.outs = 0;
            this.balls = 0;
            this.strikes = 0;
            this.bases.clear();
            this.batter = LivePlayer.NONE;
        } else if (e instanceof LiveEvent.Pitch p) {
            this.batter = p.play().batter();
            this.pitcher = p.play().pitcher();
            this.lastPitch = p.event().pitch();
            if (p.event().ballsAfter() >= 0 && p.event().strikesAfter() >= 0) {
                this.balls = p.event().ballsAfter();
                this.strikes = p.event().strikesAfter();
            }
        } else if (e instanceof LiveEvent.CallChanged c) {
            if (c.event().ballsAfter() >= 0 && c.event().strikesAfter() >= 0) {
                this.balls = c.event().ballsAfter();
                this.strikes = c.event().strikesAfter();
            }
        } else if (e instanceof LiveEvent.Action a) {
            LivePlay play = a.play();
            int outsOnBases = 0;
            for (int i = 0; i < play.runners().size(); i++) {
                LiveRunner r = play.runners().get(i);
                if (r.eventIndex() == a.event().index() && this.move(play, i, r, true)) {
                    outsOnBases++;
                }
            }
            if (a.event().isSubstitution() && "P".equals(a.event().position()) && a.event().player().known()) {
                this.pitcher = a.event().player();
            }
            // The event's own out count is authoritative; counting outs on the bases is only a fallback.
            this.outs = a.event().outsAfter() >= 0 ? a.event().outsAfter() : Math.min(3, this.outs + outsOnBases);
        } else if (e instanceof LiveEvent.AtBatResult r) {
            LivePlay play = r.play();
            for (int i = 0; i < play.runners().size(); i++) {
                this.move(play, i, play.runners().get(i), false);
            }
            this.scoresFrom(play);
            if (play.outsAfter() >= 0) {
                this.outs = play.outsAfter();
            }
            this.balls = 0;
            this.strikes = 0;
            this.lastPlay = play.description();
            if (this.outs >= 3) {
                this.bases.clear();
            }
        } else if (e instanceof LiveEvent.ResultChanged rc) {
            this.scoresFrom(rc.play());
            this.lastPlay = rc.play().description();
        }
    }

    private void scoresFrom(LivePlay play) {
        if (play.awayScore() >= 0 && play.homeScore() >= 0) {
            this.awayScore = play.awayScore();
            this.homeScore = play.homeScore();
        }
    }

    /** Moves one runner (once per play+index); returns true if the runner was put out. */
    private boolean move(LivePlay play, int idx, LiveRunner r, boolean countRuns) {
        if (!this.appliedRunners.add(play.atBatIndex() + ":" + idx)) {
            return false;
        }
        int id = r.runner().id();
        this.bases.values().removeIf(v -> v.id() == id);
        if (r.isOut()) {
            return true;
        }
        switch (r.endBase()) {
            case "1B", "2B", "3B" -> this.bases.put(r.endBase(), r.runner());
            case "score" -> {
                if (countRuns) {
                    if (play.isTop()) {
                        this.awayScore++;
                    } else {
                        this.homeScore++;
                    }
                }
            }
            default -> {
            }
        }
        return false;
    }

    public int basesMask() {
        return (this.bases.containsKey("1B") ? 1 : 0) | (this.bases.containsKey("2B") ? 2 : 0) | (this.bases.containsKey("3B") ? 4 : 0);
    }

    /**
     * The real game's state as the recreation has shown it: score, inning, count, outs, runners, batter,
     * pitcher, last pitch and play come from what was played; names, teams and status from the real feed.
     */
    public LiveGameState view(LiveGameState real) {
        return new LiveGameState(
            real.gameId(), real.status(), real.away(), real.home(), real.startEpochMillis(), this.awayScore, this.homeScore,
            this.inning, this.top ? "Top" : "Bottom", this.balls, this.strikes, this.outs, !this.batter.known(),
            this.batter.known() ? this.batter : real.batter(), this.pitcher.known() ? this.pitcher : real.pitcher(), LivePlayer.NONE,
            this.bases.getOrDefault("1B", LivePlayer.NONE), this.bases.getOrDefault("2B", LivePlayer.NONE), this.bases.getOrDefault("3B", LivePlayer.NONE),
            real.awayTotals(), real.homeTotals(), real.awayInningRuns(), real.homeInningRuns(), this.lastPitch, this.lastPlay, real.venue(),
            real.feedTimestamp(), real.suggestedPollSeconds(), real.awayLineup(), real.homeLineup()
        );
    }

    public String summary(String awayAbbr, String homeAbbr) {
        StringBuilder b = new StringBuilder();
        b.append(this.top ? "Top " : "Bot ").append(LiveGameSummary.ordinal(Math.max(1, this.inning)))
            .append(", ").append(this.outs).append(" out, ").append(this.balls).append('-').append(this.strikes)
            .append(", ").append(awayAbbr).append(' ').append(this.awayScore).append(' ').append(homeAbbr).append(' ').append(this.homeScore);
        if (!this.bases.isEmpty()) {
            b.append(", on ").append(String.join("/", this.bases.keySet()));
        }
        return b.toString();
    }
}
