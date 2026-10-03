package com.cj.mcbaseball.live.net;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

/**
 * Developer feature (off by default): saves every raw API response so a bug seen during a live game
 * can be reproduced later from the exact same data. Capped per session so it can't fill a disk.
 *
 * <p>Layout: {@code <dir>/<yyyy-MM-dd>/<HHmmss-SSS>_<name>.json}
 */
public final class ResponseRecorder implements LiveApiClient.Recorder {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss-SSS");

    private final Path dir;
    private final int maxFiles;
    private final AtomicInteger written = new AtomicInteger();
    private volatile boolean warned;

    public ResponseRecorder(Path dir, int maxFiles) {
        this.dir = dir;
        this.maxFiles = maxFiles;
    }

    @Override
    public void record(String name, String url, String body) {
        int n = this.written.incrementAndGet();
        if (n > this.maxFiles) {
            if (n == this.maxFiles + 1) {
                LOGGER.warn("[MCBaseball Live] Debug recording stopped after {} files this session", this.maxFiles);
            }
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String safe = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        Path file = this.dir.resolve(DAY.format(now)).resolve(TIME.format(now) + "_" + safe + ".json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (!this.warned) {
                this.warned = true;
                LOGGER.warn("[MCBaseball Live] Could not write debug recording to {}: {}", file, e.toString());
            }
        }
    }

    public Path dir() {
        return this.dir;
    }
}
