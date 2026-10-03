package com.cj.mcbaseball.game;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum Position {
    PITCHER("P", 0.0, 0.0, true),
    CATCHER("C", 0.0, 0.0, true),
    FIRST_BASE("1B", 0.88, 0.6, true),
    SECOND_BASE("2B", 1.2, 0.32, true),
    THIRD_BASE("3B", 0.88, -0.6, true),
    SHORTSTOP("SS", 1.2, -0.32, true),
    LEFT_FIELD("LF", 2.05, -0.95, false),
    CENTER_FIELD("CF", 2.45, 0.0, false),
    RIGHT_FIELD("RF", 2.05, 0.95, false);

    public final String abbr;
    public final double x;
    public final double y;
    public final boolean infield;
    public static final Position[] BATTING_ORDER = new Position[]{
        CENTER_FIELD, SHORTSTOP, FIRST_BASE, THIRD_BASE, LEFT_FIELD, RIGHT_FIELD, SECOND_BASE, CATCHER, PITCHER
    };
    public static final Position[] HUMAN_PRIORITY = new Position[]{
        PITCHER, SHORTSTOP, CENTER_FIELD, SECOND_BASE, THIRD_BASE, FIRST_BASE, LEFT_FIELD, RIGHT_FIELD, CATCHER
    };

    private Position(String abbr, double x, double y, boolean infield) {
        this.abbr = abbr;
        this.x = x;
        this.y = y;
        this.infield = infield;
    }

    public Component displayName() {
        return Component.translatable("mcbaseball.position." + this.name().toLowerCase(Locale.ROOT));
    }

    public boolean outfield() {
        return !this.infield;
    }

    public static Position byId(int id) {
        Position[] v = values();
        return id >= 0 && id < v.length ? v[id] : PITCHER;
    }
}
