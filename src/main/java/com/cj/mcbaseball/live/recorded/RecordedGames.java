package com.cj.mcbaseball.live.recorded;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Finds saved live-feed sequences on disk, for replaying known games through Live Mode (developer test mode).
 *
 * <p>Recognised layouts (both can sit anywhere under the folder, up to 4 levels deep):
 * <ul>
 *   <li>Files written by the debug recorder: {@code .../<date>/<time>_feed_<gamePk>.json}</li>
 *   <li>A folder named {@code <gamePk>_<anything>} holding {@code .json} / {@code .json.gz} snapshots
 *       (the layout of the recorded test fixtures)</li>
 * </ul>
 * Snapshots of one game are played in file-name order (both layouts use time-stamped names).
 */
public final class RecordedGames {

    private static final Pattern RECORDER_FILE = Pattern.compile(".*feed_(\\d+)\\.json(\\.gz)?$");
    private static final Pattern GAME_DIR = Pattern.compile("^(\\d+)_.*");

    private RecordedGames() {
    }

    /** gamePk -> snapshot files in play order. Missing folder = no games. */
    public static Map<Long, List<Path>> scan(Path dir) {
        Map<Long, List<Path>> out = new TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.walk(dir, 4)) {
            files.filter(Files::isRegularFile).forEach(f -> {
                String name = f.getFileName().toString();
                Long pk = null;
                Matcher m = RECORDER_FILE.matcher(name);
                if (m.matches()) {
                    pk = Long.parseLong(m.group(1));
                } else if (name.endsWith(".json") || name.endsWith(".json.gz")) {
                    Path parent = f.getParent();
                    Matcher d = parent == null ? null : GAME_DIR.matcher(parent.getFileName().toString());
                    if (d != null && d.matches()) {
                        pk = Long.parseLong(d.group(1));
                    }
                }
                if (pk != null && pk > 0) {
                    out.computeIfAbsent(pk, k -> new ArrayList<>()).add(f);
                }
            });
        } catch (IOException | RuntimeException e) {
            return out;
        }
        out.values().forEach(l -> l.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString())));
        return out;
    }

    public static String read(Path file) throws IOException {
        try (InputStream raw = Files.newInputStream(file);
             InputStream in = file.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
