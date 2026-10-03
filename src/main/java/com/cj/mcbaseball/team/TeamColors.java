package com.cj.mcbaseball.team;

public final class TeamColors {
    public static final String[] NAMES = new String[]{
        "White", "Gray", "Black", "Red", "Maroon", "Orange", "Gold", "Yellow", "Lime", "Green", "Teal", "Sky Blue", "Blue", "Navy", "Purple", "Pink"
    };
    public static final int[] RGB = new int[]{
        15921906, 9342606, 1973794, 13116974, 8003116, 15759898, 14725162, 16048205, 8311624, 3050302, 2006172, 7258608, 2381768, 1780314, 8077237, 15764160
    };

    public static int rgb(int index) {
        return RGB[Math.floorMod(index, RGB.length)];
    }

    public static String name(int index) {
        return NAMES[Math.floorMod(index, NAMES.length)];
    }

    private TeamColors() {
    }
}
