package com.cj.mcbaseball.game;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public final class GameSettings {
    public static final int[] INNING_CHOICES = new int[]{3, 5, 7, 9};
    public int innings = 9;
    public boolean npcAutoFill = true;
    public Difficulty difficulty = Difficulty.NORMAL;
    public boolean extraInnings = true;
    public boolean showStrikeZone = true;
    public boolean showLandingMarker = true;
    public boolean battingAssist = true;
    public boolean simpleBatting = true;
    @Nullable
    public UUID homeTeam;
    @Nullable
    public UUID awayTeam;

    public GameSettings copy() {
        GameSettings s = new GameSettings();
        s.load(this.save());
        return s;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Innings", this.innings);
        t.putBoolean("AutoFill", this.npcAutoFill);
        t.putInt("Difficulty", this.difficulty.ordinal());
        t.putBoolean("Extra", this.extraInnings);
        t.putBoolean("Zone", this.showStrikeZone);
        t.putBoolean("Landing", this.showLandingMarker);
        t.putBoolean("Assist", this.battingAssist);
        t.putBoolean("SimpleBat", this.simpleBatting);
        if (this.homeTeam != null) {
            t.putUUID("Home", this.homeTeam);
        }

        if (this.awayTeam != null) {
            t.putUUID("Away", this.awayTeam);
        }

        return t;
    }

    public void load(CompoundTag t) {
        if (t.contains("Innings")) {
            this.innings = clampInnings(t.getInt("Innings"));
        }

        if (t.contains("AutoFill")) {
            this.npcAutoFill = t.getBoolean("AutoFill");
        }

        if (t.contains("Difficulty")) {
            this.difficulty = Difficulty.byId(t.getInt("Difficulty"));
        }

        if (t.contains("Extra")) {
            this.extraInnings = t.getBoolean("Extra");
        }

        if (t.contains("Zone")) {
            this.showStrikeZone = t.getBoolean("Zone");
        }

        if (t.contains("Landing")) {
            this.showLandingMarker = t.getBoolean("Landing");
        }

        if (t.contains("Assist")) {
            this.battingAssist = t.getBoolean("Assist");
        }

        if (t.contains("SimpleBat")) {
            this.simpleBatting = t.getBoolean("SimpleBat");
        }

        this.homeTeam = t.hasUUID("Home") ? t.getUUID("Home") : null;
        this.awayTeam = t.hasUUID("Away") ? t.getUUID("Away") : null;
    }

    public static int clampInnings(int n) {
        for (int c : INNING_CHOICES) {
            if (c == n) {
                return n;
            }
        }

        return 9;
    }
}
