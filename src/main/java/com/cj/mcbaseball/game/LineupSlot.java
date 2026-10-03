package com.cj.mcbaseball.game;

import com.cj.mcbaseball.team.NpcProfile;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public final class LineupSlot {
    public final TeamSide side;
    public final Position position;
    public final NpcProfile npc;
    public final UUID teamId;
    @Nullable
    public UUID humanId;
    public String humanName = "";
    public boolean humanAbsent;
    public boolean humanReturning;
    public boolean enabled = true;
    @Nullable
    public UUID npcEntityId;

    public LineupSlot(TeamSide side, Position position, NpcProfile npc, UUID teamId) {
        this.side = side;
        this.position = position;
        this.npc = npc;
        this.teamId = teamId;
    }

    public boolean isHuman() {
        return this.humanId != null && !this.humanAbsent;
    }

    public boolean usesNpc() {
        return this.enabled && !this.isHuman();
    }

    public boolean batsRight() {
        return this.isHuman() || this.npc.batsRight;
    }

    public boolean throwsRight() {
        return this.isHuman() || this.npc.throwsRight;
    }

    public String displayName() {
        return this.isHuman() ? this.humanName : this.npc.displayName();
    }

    public String shortName() {
        if (this.isHuman()) {
            return this.humanName;
        } else {
            String n = this.npc.name;
            int i = n.lastIndexOf(32);
            return i >= 0 ? n.substring(i + 1) : n;
        }
    }

    public String statKey() {
        return this.humanId != null ? "p:" + this.humanId : "n:" + this.teamId + ":" + this.npc.number;
    }

    public String statName() {
        return this.humanId != null ? this.humanName : this.npc.displayName();
    }
}
