package com.cj.mcbaseball.live.net;

/** Any failure getting or understanding live data. {@link #retryable()} drives backoff vs. giving up. */
public class LiveDataException extends Exception {

    public enum Kind {
        /** Connect timeout / request timeout. */
        TIMEOUT(true),
        /** DNS failure, refused connection, reset, TLS failure... */
        CONNECTION(true),
        /** HTTP 429 or 503: back off harder. */
        RATE_LIMITED(true),
        /** Other HTTP 5xx. */
        SERVER_ERROR(true),
        /** HTTP 404 - the game/date does not exist. */
        NOT_FOUND(false),
        /** Other HTTP 4xx. */
        HTTP_ERROR(false),
        /** Body was not JSON, or not shaped like the provider's documents at all. */
        INVALID_RESPONSE(true),
        /** Client/session shut down while the request was pending. */
        CANCELLED(false);

        private final boolean retryable;

        Kind(boolean retryable) {
            this.retryable = retryable;
        }
    }

    private final Kind kind;
    private final int httpStatus;
    private final long retryAfterMillis;

    public LiveDataException(Kind kind, String message) {
        this(kind, message, null, 0, 0L);
    }

    public LiveDataException(Kind kind, String message, Throwable cause) {
        this(kind, message, cause, 0, 0L);
    }

    public LiveDataException(Kind kind, String message, Throwable cause, int httpStatus, long retryAfterMillis) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.retryAfterMillis = retryAfterMillis;
    }

    public Kind kind() {
        return this.kind;
    }

    public boolean retryable() {
        return this.kind.retryable;
    }

    public int httpStatus() {
        return this.httpStatus;
    }

    /** Server-requested wait (Retry-After), 0 if none. */
    public long retryAfterMillis() {
        return this.retryAfterMillis;
    }

    /** Unwraps CompletionException/ExecutionException chains into a LiveDataException. */
    public static LiveDataException from(Throwable t) {
        Throwable cur = t;
        for (int i = 0; cur != null && i < 8; i++) {
            if (cur instanceof LiveDataException lde) {
                return lde;
            }
            if (cur instanceof java.util.concurrent.CancellationException) {
                return new LiveDataException(Kind.CANCELLED, "cancelled", cur);
            }
            cur = cur.getCause();
        }
        String msg = t == null ? "unknown error" : t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        return new LiveDataException(Kind.CONNECTION, msg, t);
    }

    /** One short line for status displays. */
    public String shortReason() {
        return switch (this.kind) {
            case TIMEOUT -> "timed out";
            case CONNECTION -> "connection failed";
            case RATE_LIMITED -> "rate limited (HTTP " + this.httpStatus + ")";
            case SERVER_ERROR -> "provider error (HTTP " + this.httpStatus + ")";
            case NOT_FOUND -> "not found (HTTP 404)";
            case HTTP_ERROR -> "HTTP " + this.httpStatus;
            case INVALID_RESPONSE -> "invalid response";
            case CANCELLED -> "cancelled";
        };
    }
}
