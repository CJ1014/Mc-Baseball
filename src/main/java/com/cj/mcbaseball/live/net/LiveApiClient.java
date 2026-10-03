package com.cj.mcbaseball.live.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.zip.GZIPInputStream;
import javax.annotation.Nullable;

/**
 * Non-blocking HTTP GET for live data. Never call {@code join()}/{@code get()} on its futures from
 * the server or render thread; chain callbacks and hop threads instead.
 *
 * <ul>
 *   <li>All I/O runs on the supplied executor; retries are <em>scheduled</em>, never slept.</li>
 *   <li>Connect + request timeouts on every call.</li>
 *   <li>Retries timeouts, connection failures, 5xx and 429 with exponential backoff + jitter, honouring Retry-After.</li>
 *   <li>Identical concurrent requests share one HTTP call (in-flight coalescing).</li>
 *   <li>Successful bodies are cached; callers pass how fresh they need the data to be.</li>
 *   <li>gzip responses are decoded (live feeds are ~10x smaller compressed).</li>
 * </ul>
 */
public final class LiveApiClient {

    /** Decompressed bodies larger than this are rejected (a full extra-innings live feed is a few MB). */
    static final int MAX_BODY_BYTES = 48 * 1024 * 1024;
    static final long MAX_RETRY_AFTER_MILLIS = 120_000L;

    public record Settings(Duration connectTimeout, Duration requestTimeout, int maxRetries, long baseBackoffMillis, long maxBackoffMillis, String userAgent) {
        public static Settings defaults(String userAgent) {
            return new Settings(Duration.ofSeconds(5), Duration.ofSeconds(10), 2, 1_000L, 30_000L, userAgent);
        }
    }

    /** Debug hook: sees every successful raw response body. Called on an I/O thread. */
    public interface Recorder {
        void record(String name, String url, String body);
    }

    private record Cached(String body, long fetchedAt) {
    }

    private final HttpClient http;
    private final ScheduledExecutorService scheduler;
    private final Settings settings;
    private final LongSupplier clock;
    @Nullable
    private final Recorder recorder;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();
    private volatile boolean closed;
    private final AtomicLong requestCount = new AtomicLong();

    public LiveApiClient(Executor ioExecutor, ScheduledExecutorService scheduler, Settings settings, LongSupplier clock, @Nullable Recorder recorder) {
        this.http = HttpClient.newBuilder()
            .executor(ioExecutor)
            .connectTimeout(settings.connectTimeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(ProxySelector.getDefault())
            .build();
        this.scheduler = scheduler;
        this.settings = settings;
        this.clock = clock;
        this.recorder = recorder;
    }

    /**
     * @param url          absolute URL
     * @param recordName   short label for debug recordings, e.g. "schedule_2026-10-03"
     * @param maxAgeMillis a cached body younger than this is returned without a request (0 = always fetch)
     */
    public CompletableFuture<String> get(String url, String recordName, long maxAgeMillis) {
        if (this.closed) {
            return CompletableFuture.failedFuture(new LiveDataException(LiveDataException.Kind.CANCELLED, "client closed"));
        }
        if (maxAgeMillis > 0) {
            Cached c = this.cache.get(url);
            if (c != null && this.clock.getAsLong() - c.fetchedAt < maxAgeMillis) {
                return CompletableFuture.completedFuture(c.body);
            }
        }
        CompletableFuture<String> mine = new CompletableFuture<>();
        CompletableFuture<String> existing = this.inFlight.putIfAbsent(url, mine);
        if (existing != null) {
            return existing;
        }
        mine.whenComplete((r, e) -> this.inFlight.remove(url, mine));
        this.attempt(url, recordName, 0, mine);
        return mine;
    }

    /** Last good body for a URL regardless of age, for "show stale data while reconnecting". */
    public Optional<String> lastGood(String url) {
        Cached c = this.cache.get(url);
        return c == null ? Optional.empty() : Optional.of(c.body);
    }

    public void invalidate(String url) {
        this.cache.remove(url);
    }

    /** Total HTTP requests actually sent (including retries). For tests and the debug panel. */
    public long requestCount() {
        return this.requestCount.get();
    }

    public boolean isClosed() {
        return this.closed;
    }

    /** Fails every pending request with CANCELLED and refuses new ones. Executors are owned by the caller. */
    public void close() {
        this.closed = true;
        for (CompletableFuture<String> f : this.inFlight.values()) {
            f.completeExceptionally(new LiveDataException(LiveDataException.Kind.CANCELLED, "client closed"));
        }
        this.inFlight.clear();
        this.cache.clear();
    }

    private void attempt(String url, String recordName, int attemptNo, CompletableFuture<String> result) {
        if (this.closed || result.isDone()) {
            result.completeExceptionally(new LiveDataException(LiveDataException.Kind.CANCELLED, "client closed"));
            return;
        }
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(url))
                .timeout(this.settings.requestTimeout())
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .header("User-Agent", this.settings.userAgent())
                .GET()
                .build();
        } catch (IllegalArgumentException e) {
            result.completeExceptionally(new LiveDataException(LiveDataException.Kind.HTTP_ERROR, "bad URL: " + url, e));
            return;
        }
        this.requestCount.incrementAndGet();
        CompletableFuture<HttpResponse<byte[]>> send;
        try {
            send = this.http.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (RuntimeException e) {
            this.retryOrFail(url, recordName, attemptNo, result, classify(e));
            return;
        }
        send.whenComplete((resp, err) -> {
            if (result.isDone()) {
                return;
            }
            if (err != null) {
                this.retryOrFail(url, recordName, attemptNo, result, classify(err));
                return;
            }
            LiveDataException failure = checkStatus(resp);
            if (failure != null) {
                this.retryOrFail(url, recordName, attemptNo, result, failure);
                return;
            }
            String body;
            try {
                body = decode(resp);
            } catch (IOException e) {
                this.retryOrFail(url, recordName, attemptNo, result,
                    new LiveDataException(LiveDataException.Kind.INVALID_RESPONSE, "could not decode body: " + e.getMessage(), e));
                return;
            }
            this.cache.put(url, new Cached(body, this.clock.getAsLong()));
            if (this.recorder != null) {
                try {
                    this.recorder.record(recordName, url, body);
                } catch (RuntimeException ignored) {
                }
            }
            result.complete(body);
        });
    }

    private void retryOrFail(String url, String recordName, int attemptNo, CompletableFuture<String> result, LiveDataException failure) {
        if (this.closed) {
            result.completeExceptionally(new LiveDataException(LiveDataException.Kind.CANCELLED, "client closed"));
            return;
        }
        if (!failure.retryable() || attemptNo >= this.settings.maxRetries()) {
            result.completeExceptionally(failure);
            return;
        }
        long delay = backoffMillis(attemptNo, failure.retryAfterMillis(), this.settings.baseBackoffMillis(), this.settings.maxBackoffMillis());
        try {
            this.scheduler.schedule(() -> this.attempt(url, recordName, attemptNo + 1, result), delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            result.completeExceptionally(new LiveDataException(LiveDataException.Kind.CANCELLED, "shutting down", e));
        }
    }

    /** base * 2^attempt, +/-25% jitter, capped, but never shorter than the server's Retry-After. */
    static long backoffMillis(int attemptNo, long retryAfterMillis, long base, long max) {
        long exp = base << Math.min(attemptNo, 16);
        double jitter = 0.75 + ThreadLocalRandom.current().nextDouble() * 0.5;
        long d = Math.min(max, (long) (exp * jitter));
        return Math.max(d, Math.min(retryAfterMillis, MAX_RETRY_AFTER_MILLIS));
    }

    @Nullable
    static LiveDataException checkStatus(HttpResponse<?> resp) {
        int code = resp.statusCode();
        if (code >= 200 && code < 300) {
            return null;
        }
        if (code == 429 || code == 503) {
            long ra = parseRetryAfter(resp.headers().firstValue("Retry-After").orElse(""));
            return new LiveDataException(LiveDataException.Kind.RATE_LIMITED, "HTTP " + code, null, code, ra);
        }
        if (code >= 500) {
            return new LiveDataException(LiveDataException.Kind.SERVER_ERROR, "HTTP " + code, null, code, 0L);
        }
        if (code == 404) {
            return new LiveDataException(LiveDataException.Kind.NOT_FOUND, "HTTP 404", null, code, 0L);
        }
        return new LiveDataException(LiveDataException.Kind.HTTP_ERROR, "HTTP " + code, null, code, 0L);
    }

    static long parseRetryAfter(String v) {
        try {
            long s = Long.parseLong(v.trim());
            return s <= 0 ? 0L : Math.min(s * 1000L, MAX_RETRY_AFTER_MILLIS);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    static String decode(HttpResponse<byte[]> resp) throws IOException {
        byte[] raw = resp.body() == null ? new byte[0] : resp.body();
        String enc = resp.headers().firstValue("Content-Encoding").orElse("").toLowerCase(Locale.ROOT);
        if (enc.contains("gzip")) {
            raw = gunzip(raw);
        } else if (raw.length > MAX_BODY_BYTES) {
            throw new IOException("body too large");
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    static byte[] gunzip(byte[] in) throws IOException {
        try (InputStream gz = new GZIPInputStream(new ByteArrayInputStream(in))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, in.length * 6));
            byte[] buf = new byte[16384];
            int n;
            while ((n = gz.read(buf)) > 0) {
                if (out.size() + n > MAX_BODY_BYTES) {
                    throw new IOException("body too large");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    static LiveDataException classify(Throwable t) {
        Throwable cur = t;
        for (int i = 0; cur != null && i < 8; i++) {
            if (cur instanceof LiveDataException lde) {
                return lde;
            }
            if (cur instanceof HttpConnectTimeoutException || cur instanceof HttpTimeoutException) {
                return new LiveDataException(LiveDataException.Kind.TIMEOUT, "timed out", cur);
            }
            if (cur instanceof CancellationException) {
                return new LiveDataException(LiveDataException.Kind.CANCELLED, "cancelled", cur);
            }
            if (cur instanceof ConnectException || cur instanceof IOException) {
                return new LiveDataException(LiveDataException.Kind.CONNECTION, cur.getClass().getSimpleName()
                    + (cur.getMessage() == null ? "" : ": " + cur.getMessage()), cur);
            }
            cur = cur.getCause();
        }
        return new LiveDataException(LiveDataException.Kind.CONNECTION, t.getClass().getSimpleName(), t);
    }
}
