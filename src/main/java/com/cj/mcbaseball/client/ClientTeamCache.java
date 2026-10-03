package com.cj.mcbaseball.client;

import com.cj.mcbaseball.team.TeamData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public final class ClientTeamCache {
    private static List<TeamData> teams = new ArrayList<>();
    private static int version;

    public static void set(List<TeamData> list) {
        teams = new ArrayList<>(list);
        version++;
    }

    public static List<TeamData> teams() {
        return teams;
    }

    public static int version() {
        return version;
    }

    @Nullable
    public static TeamData byId(@Nullable UUID id) {
        if (id == null) {
            return null;
        } else {
            for (TeamData t : teams) {
                if (t.id.equals(id)) {
                    return t;
                }
            }

            return null;
        }
    }

    public static int indexOf(@Nullable UUID id) {
        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).id.equals(id)) {
                return i;
            }
        }

        return -1;
    }

    private ClientTeamCache() {
    }
}
