package com.cj.mcbaseball.live.model;

import java.util.List;

/**
 * What the server knows about one day's schedule, as sent to clients.
 *
 * @param epochDay        the baseball date (LocalDate.toEpochDay)
 * @param todayEpochDay   the server's current baseball "today", so a client that asked for "today" knows which snapshot answers it
 * @param games           games sorted for display (may be stale if {@link #status} is STALE)
 * @param fetchedAtMillis server clock time the games were last fetched successfully (0 = never)
 * @param serverNowMillis server clock time this snapshot was built, so clients can compute ages without trusting their own clock
 * @param retryInMillis   when status is STALE/UNAVAILABLE: time until the next attempt
 * @param message         short technical reason for the last failure ("" when OK)
 * @param provider        provider display name, e.g. "MLB Stats API"
 * @param version         bumps whenever anything in this snapshot changes
 */
public record LiveSchedule(
    long epochDay,
    long todayEpochDay,
    List<LiveGameSummary> games,
    LiveProviderStatus status,
    long fetchedAtMillis,
    long serverNowMillis,
    long retryInMillis,
    String message,
    String provider,
    int version
) {
    public LiveSchedule {
        games = games == null ? List.of() : List.copyOf(games);
        status = status == null ? LiveProviderStatus.LOADING : status;
        message = message == null ? "" : message;
        provider = provider == null ? "" : provider;
    }

    public List<LiveGameSummary> inSection(LiveGameStatus.Section section) {
        return this.games.stream().filter(g -> g.section() == section).toList();
    }

    public LiveGameSummary find(long gameId) {
        for (LiveGameSummary g : this.games) {
            if (g.gameId() == gameId) {
                return g;
            }
        }
        return null;
    }
}
