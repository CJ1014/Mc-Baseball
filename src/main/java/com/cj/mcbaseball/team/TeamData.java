package com.cj.mcbaseball.team;

import com.cj.mcbaseball.game.Position;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;

public final class TeamData {
    public final UUID id;
    public String name;
    public String abbreviation;
    public int primaryColor;
    public int secondaryColor;
    public final EnumMap<Position, NpcProfile> roster = new EnumMap<>(Position.class);

    public TeamData(UUID id, String name, String abbreviation, int primary, int secondary) {
        this.id = id;
        this.name = name;
        this.abbreviation = abbreviation;
        this.primaryColor = primary;
        this.secondaryColor = secondary;
    }

    public static TeamData create(String name, String abbr, int primary, int secondary) {
        TeamData t = new TeamData(UUID.randomUUID(), name, abbr, primary, secondary);
        t.generateRoster();
        return t;
    }

    public void generateRoster() {
        Random r = new Random(this.id.getMostSignificantBits() ^ this.id.getLeastSignificantBits());
        List<Integer> used = new ArrayList<>();
        this.roster.clear();

        for (Position p : Position.values()) {
            this.roster.put(p, NpcProfile.generate(r, p, used));
        }
    }

    public int primaryRgb() {
        return TeamColors.rgb(this.primaryColor);
    }

    public int secondaryRgb() {
        return TeamColors.rgb(this.secondaryColor);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", this.id);
        t.putString("Name", this.name);
        t.putString("Abbr", this.abbreviation);
        t.putInt("Primary", this.primaryColor);
        t.putInt("Secondary", this.secondaryColor);
        ListTag list = new ListTag();

        for (Position p : Position.values()) {
            NpcProfile np = this.roster.get(p);
            if (np != null) {
                CompoundTag e = np.save();
                e.putString("Pos", p.name());
                list.add(e);
            }
        }

        t.put("Roster", list);
        return t;
    }

    public static TeamData load(CompoundTag t) {
        TeamData d = new TeamData(t.getUUID("Id"), t.getString("Name"), t.getString("Abbr"), t.getInt("Primary"), t.getInt("Secondary"));
        ListTag list = t.getList("Roster", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);

            try {
                d.roster.put(Position.valueOf(e.getString("Pos")), NpcProfile.load(e));
            } catch (IllegalArgumentException var6) {
            }
        }

        if (d.roster.size() < Position.values().length) {
            d.generateRoster();
        }

        return d;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(this.save());
    }

    public static TeamData read(FriendlyByteBuf buf) {
        CompoundTag t = buf.readNbt();
        return load(t == null ? new CompoundTag() : t);
    }
}
