package com.cj.mcbaseball.field;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum FieldMarker {
    HOME_PLATE(true),
    FIRST_BASE(true),
    SECOND_BASE(true),
    THIRD_BASE(true),
    PITCHERS_MOUND(true),
    LEFT_FOUL_POLE(false),
    RIGHT_FOUL_POLE(false),
    HOME_DUGOUT(false),
    AWAY_DUGOUT(false),
    OUTFIELD_WALL(false);

    private final boolean required;

    private FieldMarker(boolean required) {
        this.required = required;
    }

    public boolean required() {
        return this.required;
    }

    public boolean multiPoint() {
        return this == OUTFIELD_WALL;
    }

    public String key() {
        return "mcbaseball.marker." + this.name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable(this.key());
    }

    public static FieldMarker byId(int id) {
        FieldMarker[] v = values();
        return id >= 0 && id < v.length ? v[id] : HOME_PLATE;
    }
}
