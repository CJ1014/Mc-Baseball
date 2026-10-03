package com.cj.mcbaseball.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

public class CareerStats extends SavedData {
    private static final String ID = "mcbaseball_stats";
    private final Map<String, StatLine> lines = new HashMap<>();
    private final Map<String, CompoundTag> lastGames = new HashMap<>();

    public static CareerStats get(MinecraftServer server) {
        return (CareerStats)server.overworld().getDataStorage().computeIfAbsent(CareerStats::load, CareerStats::new, "mcbaseball_stats");
    }

    public void record(GameStats game) {
        for (Entry<String, StatLine> e : game.lines.entrySet()) {
            StatLine career = this.lines.computeIfAbsent(e.getKey(), k -> new StatLine());
            career.name = e.getValue().name;
            StatLine one = e.getValue();
            one.games = 1;
            career.add(one);
        }

        this.setDirty();
    }

    @Nullable
    public StatLine get(String key) {
        return this.lines.get(key);
    }

    public void setLastGame(String fieldKey, CompoundTag summary) {
        this.lastGames.put(fieldKey, summary);
        this.setDirty();
    }

    @Nullable
    public CompoundTag lastGame(String fieldKey) {
        return this.lastGames.get(fieldKey);
    }

    public static CareerStats load(CompoundTag tag) {
        CareerStats s = new CareerStats();
        CompoundTag l = tag.getCompound("Lines");

        for (String k : l.getAllKeys()) {
            s.lines.put(k, StatLine.load(l.getCompound(k)));
        }

        CompoundTag g = tag.getCompound("LastGames");

        for (String k : g.getAllKeys()) {
            s.lastGames.put(k, g.getCompound(k));
        }

        return s;
    }

    public CompoundTag save(CompoundTag tag) {
        CompoundTag l = new CompoundTag();
        this.lines.forEach((k, v) -> l.put(k, v.save()));
        tag.put("Lines", l);
        CompoundTag g = new CompoundTag();
        this.lastGames.forEach(g::put);
        tag.put("LastGames", g);
        return tag;
    }
}
