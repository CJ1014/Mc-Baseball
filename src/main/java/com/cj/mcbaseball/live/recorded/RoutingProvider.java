package com.cj.mcbaseball.live.recorded;

import com.cj.mcbaseball.live.LiveBaseballProvider;
import com.cj.mcbaseball.live.model.LiveFeed;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Live provider for real game ids, recorded provider for negative (recorded) ids. */
public final class RoutingProvider implements LiveBaseballProvider {

    private final LiveBaseballProvider live;
    private final LiveBaseballProvider recorded;

    public RoutingProvider(LiveBaseballProvider live, LiveBaseballProvider recorded) {
        this.live = live;
        this.recorded = recorded;
    }

    @Override
    public String id() {
        return this.live.id();
    }

    @Override
    public String displayName() {
        return this.live.displayName();
    }

    @Override
    public CompletableFuture<List<LiveGameSummary>> getGamesForDate(LocalDate date) {
        return this.live.getGamesForDate(date);
    }

    @Override
    public CompletableFuture<Optional<LiveGameSummary>> getGameInfo(long gameId) {
        return gameId < 0 ? this.recorded.getGameInfo(gameId) : this.live.getGameInfo(gameId);
    }

    @Override
    public CompletableFuture<LiveFeed> getLiveFeed(long gameId) {
        return gameId < 0 ? this.recorded.getLiveFeed(gameId) : this.live.getLiveFeed(gameId);
    }

    @Override
    public void close() {
        this.live.close();
        this.recorded.close();
    }
}
