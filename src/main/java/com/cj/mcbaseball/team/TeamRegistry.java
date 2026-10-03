package com.cj.mcbaseball.team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

public class TeamRegistry extends SavedData {
    private static final String ID = "mcbaseball_teams";
    public static final int MAX_TEAMS = 48;
    private final LinkedHashMap<UUID, TeamData> teams = new LinkedHashMap<>();

    public static TeamRegistry get(MinecraftServer server) {
        TeamRegistry reg = (TeamRegistry)server.overworld().getDataStorage().computeIfAbsent(TeamRegistry::load, TeamRegistry::new, "mcbaseball_teams");
        if (reg.teams.isEmpty()) {
            reg.add(TeamData.create("Sharks", "SHK", 12, 0));
            reg.add(TeamData.create("Tigers", "TGR", 5, 2));
        }

        return reg;
    }

    public Collection<TeamData> all() {
        return Collections.unmodifiableCollection(this.teams.values());
    }

    @Nullable
    public TeamData byId(@Nullable UUID id) {
        return id == null ? null : this.teams.get(id);
    }

    public boolean add(TeamData t) {
        if (this.teams.size() >= 48) {
            return false;
        } else {
            this.teams.put(t.id, t);
            this.setDirty();
            return true;
        }
    }

    public boolean remove(UUID id) {
        if (this.teams.size() <= 2) {
            return false;
        } else {
            boolean removed = this.teams.remove(id) != null;
            if (removed) {
                this.setDirty();
            }

            return removed;
        }
    }

    public List<TeamData> list() {
        return new ArrayList<>(this.teams.values());
    }

    public static TeamRegistry load(CompoundTag tag) {
        TeamRegistry r = new TeamRegistry();
        ListTag list = tag.getList("Teams", 10);

        for (int i = 0; i < list.size(); i++) {
            TeamData t = TeamData.load(list.getCompound(i));
            r.teams.put(t.id, t);
        }

        return r;
    }

    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();

        for (TeamData t : this.teams.values()) {
            list.add(t.save());
        }

        tag.put("Teams", list);
        return tag;
    }
}
