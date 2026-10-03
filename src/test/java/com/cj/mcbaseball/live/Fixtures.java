package com.cj.mcbaseball.live;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads real MLB Stats API responses saved under src/test/resources/live/mlb. */
public final class Fixtures {
    private Fixtures() {
    }

    /** The recorded ATL @ LAD bottom-of-the-4th sequence, parsed, in order (20 snapshots). */
    public static java.util.List<com.cj.mcbaseball.live.model.LiveFeed> recordedBottom4th() {
        try {
            java.nio.file.Path dir = java.nio.file.Path.of(Fixtures.class.getResource("/live/mlb/recorded/849828_atl-lad_bot4").toURI());
            java.util.List<com.cj.mcbaseball.live.model.LiveFeed> out = new java.util.ArrayList<>();
            try (var files = java.nio.file.Files.list(dir)) {
                for (java.nio.file.Path f : files.filter(x -> x.toString().endsWith(".json.gz")).sorted().toList()) {
                    out.add(com.cj.mcbaseball.live.mlb.MlbLiveFeedParser.parseFeed(mlbGz("recorded/849828_atl-lad_bot4/" + f.getFileName()), 849828L));
                }
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static java.nio.file.Path recordedDir() {
        try {
            return java.nio.file.Path.of(Fixtures.class.getResource("/live/mlb/recorded").toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
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
