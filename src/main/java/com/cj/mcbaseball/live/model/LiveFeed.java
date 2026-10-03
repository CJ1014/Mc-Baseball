package com.cj.mcbaseball.live.model;

import java.util.List;

/**
 * One live-feed response: the game's current state plus its full play-by-play, in at-bat order.
 *
 * @param awayPitcher the away team's current pitcher (known even while that team bats, unlike the state's pitcher)
 * @param homePitcher the home team's current pitcher
 */
public record LiveFeed(LiveGameState state, List<LivePlay> plays, LivePlayer awayPitcher, LivePlayer homePitcher) {
    public LiveFeed {
        plays = plays == null ? List.of() : List.copyOf(plays);
        awayPitcher = awayPitcher == null ? LivePlayer.NONE : awayPitcher;
        homePitcher = homePitcher == null ? LivePlayer.NONE : homePitcher;
    }

    public LiveFeed(LiveGameState state, List<LivePlay> plays) {
        this(state, plays, LivePlayer.NONE, LivePlayer.NONE);
    }
}
