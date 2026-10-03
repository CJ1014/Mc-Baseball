package com.cj.mcbaseball.live.model;

import java.util.List;

/** One live-feed response: the game's current state plus its full play-by-play, in at-bat order. */
public record LiveFeed(LiveGameState state, List<LivePlay> plays) {
    public LiveFeed {
        plays = plays == null ? List.of() : List.copyOf(plays);
    }
}
