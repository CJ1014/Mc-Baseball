package com.cj.mcbaseball.live;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads real MLB Stats API responses saved under src/test/resources/live/mlb. */
public final class Fixtures {
    private Fixtures() {
    }

    /** Gzipped fixture, e.g. "feeds/feed_849829_final.json.gz". */
    public static String mlbGz(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/live/mlb/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("missing fixture " + name);
            }
            return new String(new java.util.zip.GZIPInputStream(in).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String mlb(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/live/mlb/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
