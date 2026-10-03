package com.cj.mcbaseball.client.anim;

import com.cj.mcbaseball.anim.ThrowKind;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public final class ClientThrowAnims {
    private static final Map<Integer, ClientThrowAnims.Start> ACTIVE = new HashMap<>();

    public static void start(int entityId, ThrowKind kind) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            long back = (long)Math.round((float)kind.duration * kind.release);
            ACTIVE.put(entityId, new ClientThrowAnims.Start(kind, level.getGameTime() - back));
            if (ACTIVE.size() > 64) {
                ACTIVE.clear();
            }
        }
    }

    @Nullable
    public static ClientThrowAnims.Active current(Entity e) {
        ClientThrowAnims.Start s = ACTIVE.get(e.getId());
        if (s != null && e.level() != null) {
            float age = (float)(e.level().getGameTime() - s.tick) + Minecraft.getInstance().getFrameTime();
            if (age > (float)s.kind.duration) {
                ACTIVE.remove(e.getId());
                return null;
            } else {
                return new ClientThrowAnims.Active(s.kind, Math.max(0.0F, age / (float)s.kind.duration));
            }
        } else {
            return null;
        }
    }

    private ClientThrowAnims() {
    }

    public static record Active(ThrowKind kind, float progress) {
    }

    private static record Start(ThrowKind kind, long tick) {
    }
}
