package com.cj.mcbaseball.live.model;

import javax.annotation.Nullable;

/**
 * What a stadium watching a real game knows right now, as sent to clients near it.
 *
 * @param state           latest good data, or null if none has arrived yet
 * @param fetchedAtMillis server time of the last successful update (0 = never)
 * @param serverNowMillis server time this snapshot was built
 * @param retryInMillis   when STALE/UNAVAILABLE: time until the next attempt
 */
public record LiveWatchSnapshot(
    long gameId,
    @Nullable LiveGameState state,
    LiveProviderStatus status,
    long fetchedAtMillis,
    long serverNowMillis,
    long retryInMillis,
    String message,
    String provider,
    int version
) {
    public LiveWatchSnapshot {
        status = status == null ? LiveProviderStatus.LOADING : status;
        message = message == null ? "" : message;
        provider = provider == null ? "" : provider;
    }
}
