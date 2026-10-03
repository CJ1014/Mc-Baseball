package com.cj.mcbaseball.live.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the real HTTP stack against a local server that misbehaves on purpose. */
class LiveApiClientTest {

    /** What the fake server does for request number n (0-based). */
    private record Reply(int status, String body, boolean gzip, long delayMillis, String retryAfter) {
        static Reply ok(String body) {
            return new Reply(200, body, false, 0, null);
        }
    }

    private HttpServer server;
    private ExecutorService serverPool;
    private ExecutorService io;
    private ScheduledExecutorService scheduler;
    private final AtomicInteger hits = new AtomicInteger();
    private volatile IntFunction<Reply> script = n -> Reply.ok("{\"ok\":true}");
    private final AtomicLong fakeNow = new AtomicLong(1_000_000L);

    @BeforeEach
    void start() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.serverPool = Executors.newCachedThreadPool();
        this.server.setExecutor(this.serverPool);
        this.server.createContext("/", ex -> {
            Reply r = this.script.apply(this.hits.getAndIncrement());
            try {
                if (r.delayMillis > 0) {
                    Thread.sleep(r.delayMillis);
                }
                byte[] body = r.body.getBytes(StandardCharsets.UTF_8);
                if (r.gzip) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    try (GZIPOutputStream gz = new GZIPOutputStream(bo)) {
                        gz.write(body);
                    }
                    body = bo.toByteArray();
                    ex.getResponseHeaders().add("Content-Encoding", "gzip");
                }
                if (r.retryAfter != null) {
                    ex.getResponseHeaders().add("Retry-After", r.retryAfter);
                }
                ex.sendResponseHeaders(r.status, body.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body);
                }
            } catch (InterruptedException | IOException ignored) {
            } finally {
                ex.close();
            }
        });
        this.server.start();
        this.io = Executors.newFixedThreadPool(2);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void stop() {
        this.server.stop(0);
        this.serverPool.shutdownNow();
        this.io.shutdownNow();
        this.scheduler.shutdownNow();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + this.server.getAddress().getPort() + path;
    }

    private LiveApiClient client(int retries, Duration timeout) {
        return this.client(retries, timeout, null);
    }

    private LiveApiClient client(int retries, Duration timeout, LiveApiClient.Recorder recorder) {
        LiveApiClient.Settings s = new LiveApiClient.Settings(Duration.ofSeconds(2), timeout, retries, 50L, 400L, "test");
        return new LiveApiClient(this.io, this.scheduler, s, this.fakeNow::get, recorder);
    }

    private static LiveDataException failure(CompletableFuture<String> f) throws Exception {
        try {
            f.get(10, TimeUnit.SECONDS);
            fail("expected failure");
            return null;
        } catch (ExecutionException e) {
            return LiveDataException.from(e.getCause());
        }
    }

    @Test
    void plainAndGzipBodies() throws Exception {
        this.script = n -> n == 0 ? Reply.ok("{\"plain\":1}") : new Reply(200, "{\"zipped\":\"ñ\"}", true, 0, null);
        LiveApiClient c = this.client(0, Duration.ofSeconds(2));
        assertEquals("{\"plain\":1}", c.get(this.url("/a"), "a", 0).get(5, TimeUnit.SECONDS));
        assertEquals("{\"zipped\":\"ñ\"}", c.get(this.url("/b"), "b", 0).get(5, TimeUnit.SECONDS));
    }

    @Test
    void neverBlocksTheCaller() throws Exception {
        this.script = n -> new Reply(200, "{}", false, 1500, null);
        LiveApiClient c = this.client(0, Duration.ofSeconds(5));
        long t0 = System.nanoTime();
        CompletableFuture<String> f = c.get(this.url("/slow"), "slow", 0);
        long tookMs = (System.nanoTime() - t0) / 1_000_000L;
        assertFalse(f.isDone());
        assertTrue(tookMs < 200, "get() blocked for " + tookMs + "ms");
        assertEquals("{}", f.get(5, TimeUnit.SECONDS));
    }

    @Test
    void retriesServerErrorsThenSucceeds() throws Exception {
        this.script = n -> n < 2 ? new Reply(500, "oops", false, 0, null) : Reply.ok("{\"third\":true}");
        LiveApiClient c = this.client(2, Duration.ofSeconds(2));
        assertEquals("{\"third\":true}", c.get(this.url("/x"), "x", 0).get(10, TimeUnit.SECONDS));
        assertEquals(3, this.hits.get());
        assertEquals(3, c.requestCount());
    }

    @Test
    void givesUpAfterMaxRetries() throws Exception {
        this.script = n -> new Reply(502, "bad gateway", false, 0, null);
        LiveApiClient c = this.client(2, Duration.ofSeconds(2));
        LiveDataException e = failure(c.get(this.url("/x"), "x", 0));
        assertEquals(LiveDataException.Kind.SERVER_ERROR, e.kind());
        assertEquals(502, e.httpStatus());
        assertEquals(3, this.hits.get());
    }

    @Test
    void notFoundIsNotRetried() throws Exception {
        this.script = n -> new Reply(404, "nope", false, 0, null);
        LiveApiClient c = this.client(3, Duration.ofSeconds(2));
        LiveDataException e = failure(c.get(this.url("/x"), "x", 0));
        assertEquals(LiveDataException.Kind.NOT_FOUND, e.kind());
        assertFalse(e.retryable());
        assertEquals(1, this.hits.get());
    }

    @Test
    void rateLimitHonoursRetryAfter() throws Exception {
        this.script = n -> n == 0 ? new Reply(429, "slow down", false, 0, "1") : Reply.ok("{}");
        LiveApiClient c = this.client(1, Duration.ofSeconds(2));
        long t0 = System.nanoTime();
        assertEquals("{}", c.get(this.url("/x"), "x", 0).get(10, TimeUnit.SECONDS));
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(ms >= 950, "retried after only " + ms + "ms despite Retry-After: 1");
    }

    @Test
    void requestTimeout() throws Exception {
        this.script = n -> new Reply(200, "{}", false, 3000, null);
        LiveApiClient c = this.client(0, Duration.ofMillis(300));
        LiveDataException e = failure(c.get(this.url("/x"), "x", 0));
        assertEquals(LiveDataException.Kind.TIMEOUT, e.kind());
        assertTrue(e.retryable());
    }

    @Test
    void connectionRefused() throws Exception {
        int deadPort;
        try (ServerSocket s = new ServerSocket(0)) {
            deadPort = s.getLocalPort();
        }
        LiveApiClient c = this.client(1, Duration.ofSeconds(2));
        LiveDataException e = failure(c.get("http://127.0.0.1:" + deadPort + "/x", "x", 0));
        assertEquals(LiveDataException.Kind.CONNECTION, e.kind());
    }

    @Test
    void badUrlFailsInsteadOfThrowing() throws Exception {
        LiveApiClient c = this.client(0, Duration.ofSeconds(2));
        LiveDataException e = failure(c.get("ht!tp://not a url", "x", 0));
        assertFalse(e.retryable());
    }

    @Test
    void corruptGzipIsAnInvalidResponse() throws Exception {
        this.script = n -> n == 0 ? new Reply(200, "x", false, 0, null) : Reply.ok("{}");
        // Send "gzip" header with a non-gzip body by hand.
        this.server.removeContext("/");
        this.server.createContext("/", ex -> {
            this.hits.incrementAndGet();
            byte[] body = "definitely not gzip".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Encoding", "gzip");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
            ex.close();
        });
        LiveApiClient c = this.client(0, Duration.ofSeconds(2));
        assertEquals(LiveDataException.Kind.INVALID_RESPONSE, failure(c.get(this.url("/x"), "x", 0)).kind());
    }

    @Test
    void concurrentIdenticalRequestsShareOneCall() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        this.script = n -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            return Reply.ok("{\"shared\":1}");
        };
        LiveApiClient c = this.client(0, Duration.ofSeconds(5));
        List<CompletableFuture<String>> fs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            fs.add(c.get(this.url("/same"), "same", 0));
        }
        assertSame(fs.get(0), fs.get(9));
        release.countDown();
        for (CompletableFuture<String> f : fs) {
            assertEquals("{\"shared\":1}", f.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, this.hits.get());
    }

    @Test
    void cacheRespectsMaxAge() throws Exception {
        this.script = n -> Reply.ok("{\"n\":" + n + "}");
        LiveApiClient c = this.client(0, Duration.ofSeconds(2));
        assertEquals("{\"n\":0}", c.get(this.url("/c"), "c", 10_000).get(5, TimeUnit.SECONDS));
        this.fakeNow.addAndGet(5_000);
        assertEquals("{\"n\":0}", c.get(this.url("/c"), "c", 10_000).get(5, TimeUnit.SECONDS));
        assertEquals(1, this.hits.get());
        this.fakeNow.addAndGet(6_000);
        assertEquals("{\"n\":1}", c.get(this.url("/c"), "c", 10_000).get(5, TimeUnit.SECONDS));
        assertEquals("{\"n\":2}", c.get(this.url("/c"), "c", 0).get(5, TimeUnit.SECONDS));
        assertTrue(c.lastGood(this.url("/c")).isPresent());
    }

    @Test
    void closeCancelsPendingAndRefusesNew() throws Exception {
        this.script = n -> new Reply(200, "{}", false, 3000, null);
        LiveApiClient c = this.client(0, Duration.ofSeconds(10));
        CompletableFuture<String> pending = c.get(this.url("/x"), "x", 0);
        c.close();
        assertEquals(LiveDataException.Kind.CANCELLED, failure(pending).kind());
        assertEquals(LiveDataException.Kind.CANCELLED, failure(c.get(this.url("/y"), "y", 0)).kind());
    }

    @Test
    void closeStopsScheduledRetries() throws Exception {
        this.script = n -> new Reply(500, "down", false, 0, null);
        LiveApiClient.Settings s = new LiveApiClient.Settings(Duration.ofSeconds(2), Duration.ofSeconds(2), 5, 300L, 300L, "test");
        LiveApiClient c = new LiveApiClient(this.io, this.scheduler, s, this.fakeNow::get, null);
        CompletableFuture<String> f = c.get(this.url("/x"), "x", 0);
        Thread.sleep(150);
        c.close();
        assertEquals(LiveDataException.Kind.CANCELLED, failure(f).kind());
        int after = this.hits.get();
        Thread.sleep(800);
        assertEquals(after, this.hits.get(), "a retry fired after close()");
    }

    @Test
    void recorderSavesRawBodies(@TempDir Path dir) throws Exception {
        ResponseRecorder rec = new ResponseRecorder(dir, 2);
        this.script = n -> Reply.ok("{\"rec\":" + n + "}");
        LiveApiClient c = this.client(0, Duration.ofSeconds(2), rec);
        for (int i = 0; i < 3; i++) {
            c.get(this.url("/r"), "schedule/2026-10-03?x", 0).get(5, TimeUnit.SECONDS);
        }
        try (var files = Files.walk(dir)) {
            List<Path> saved = files.filter(Files::isRegularFile).toList();
            assertEquals(2, saved.size(), "recorder cap not applied");
            assertTrue(saved.get(0).getFileName().toString().endsWith("_schedule_2026-10-03_x.json"));
        }
    }

    @Test
    void backoffGrowsAndHonoursRetryAfter() {
        long a = LiveApiClient.backoffMillis(0, 0, 1000, 30_000);
        long b = LiveApiClient.backoffMillis(3, 0, 1000, 30_000);
        long capped = LiveApiClient.backoffMillis(20, 0, 1000, 30_000);
        assertTrue(a >= 750 && a <= 1250, "first backoff " + a);
        assertTrue(b >= 6000 && b <= 10000, "fourth backoff " + b);
        assertTrue(capped <= 30_000);
        assertEquals(45_000, LiveApiClient.backoffMillis(0, 45_000, 1000, 30_000));
        assertEquals(LiveApiClient.MAX_RETRY_AFTER_MILLIS, LiveApiClient.backoffMillis(0, 10_000_000, 1000, 30_000));
        assertEquals(0, LiveApiClient.parseRetryAfter("Wed, 21 Oct 2015 07:28:00 GMT"));
        assertEquals(5000, LiveApiClient.parseRetryAfter(" 5 "));
    }
}
