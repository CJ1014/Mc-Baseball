package com.cj.mcbaseball.live.model;

/** Health of the live data connection, shown to players. */
public enum LiveProviderStatus {
    /** First request still in flight, nothing to show yet. */
    LOADING,
    /** Data is current. */
    OK,
    /** Last refresh failed; showing the previous good data while retrying. */
    STALE,
    /** No data at all and the provider is failing; retrying with backoff. */
    UNAVAILABLE,
    /** Live mode switched off in the server config. */
    DISABLED
}
