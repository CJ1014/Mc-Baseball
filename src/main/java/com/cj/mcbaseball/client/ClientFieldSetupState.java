package com.cj.mcbaseball.client;

public final class ClientFieldSetupState {
    private static volatile int marker = -1;

    public static void setMarker(int m) {
        marker = m;
    }

    public static boolean isMarking() {
        return marker >= 0;
    }

    public static int marker() {
        return marker;
    }

    private ClientFieldSetupState() {
    }
}
