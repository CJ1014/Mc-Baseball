package com.cj.mcbaseball.live;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads real MLB Stats API responses saved under src/test/resources/live/mlb. */
public final class Fixtures {
    private Fixtures() {
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
