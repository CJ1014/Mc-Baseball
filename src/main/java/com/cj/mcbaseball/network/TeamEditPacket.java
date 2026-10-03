package com.cj.mcbaseball.network;

import com.cj.mcbaseball.item.UniformArmorItem;
import com.cj.mcbaseball.registry.ModItems;
import com.cj.mcbaseball.team.TeamColors;
import com.cj.mcbaseball.team.TeamData;
import com.cj.mcbaseball.team.TeamRegistry;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent.Context;

public record TeamEditPacket(int action, String name, String abbr, int primary, int secondary, UUID team) {
    public static final int CREATE = 0;
    public static final int DELETE = 1;
    public static final int UNIFORM = 2;
    public static final int REQUEST = 3;

    public static void encode(TeamEditPacket p, FriendlyByteBuf b) {
        b.writeByte(p.action);
        b.writeUtf(p.name, 24);
        b.writeUtf(p.abbr, 4);
        b.writeByte(p.primary);
        b.writeByte(p.secondary);
        b.writeUUID(p.team);
    }

    public static TeamEditPacket decode(FriendlyByteBuf b) {
        return new TeamEditPacket(b.readByte(), b.readUtf(24), b.readUtf(4), b.readByte(), b.readByte(), b.readUUID());
    }

    public static void handle(TeamEditPacket p, Supplier<Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) {
            TeamRegistry reg = TeamRegistry.get(sp.server);
            switch (p.action) {
                case 0:
                    String name = clean(p.name, 20);
                    String abbr = clean(p.abbr, 3).toUpperCase(Locale.ROOT);
                    if (!name.isEmpty() && !abbr.isEmpty()) {
                        if (!reg.add(
                            TeamData.create(name, abbr, Math.floorMod(p.primary, TeamColors.RGB.length), Math.floorMod(p.secondary, TeamColors.RGB.length))
                        )) {
                            sp.displayClientMessage(Component.translatable("mcbaseball.team.too_many").withStyle(ChatFormatting.RED), true);
                        } else {
                            sp.displayClientMessage(Component.translatable("mcbaseball.team.created", new Object[]{name}).withStyle(ChatFormatting.GREEN), true);
                        }
                    } else {
                        sp.displayClientMessage(Component.translatable("mcbaseball.team.need_name").withStyle(ChatFormatting.RED), true);
                    }
                    break;
                case 1:
                    if (!reg.remove(p.team)) {
                        sp.displayClientMessage(Component.translatable("mcbaseball.team.keep_two").withStyle(ChatFormatting.RED), true);
                    }
                    break;
                case 2:
                    TeamData t = reg.byId(p.team);
                    if (t != null) {
                        give(sp, (Item)ModItems.BASEBALL_CAP.get(), t.primaryRgb());
                        give(sp, (Item)ModItems.BASEBALL_JERSEY.get(), t.primaryRgb());
                        give(sp, (Item)ModItems.BASEBALL_PANTS.get(), t.secondaryRgb());
                        give(sp, (Item)ModItems.CLEATS.get(), 1973794);
                        sp.displayClientMessage(Component.translatable("mcbaseball.team.uniform", new Object[]{t.name}).withStyle(ChatFormatting.GREEN), true);
                    }
            }

            ModNetwork.toPlayer(sp, new TeamsSyncPacket(reg.list()));
        }
    }

    private static void give(ServerPlayer sp, Item item, int rgb) {
        ItemStack s = new ItemStack(item);
        if (item instanceof UniformArmorItem u) {
            u.setColor(s, rgb);
        }

        if (!sp.getInventory().add(s)) {
            sp.drop(s, false);
        }
    }

    private static String clean(String s, int max) {
        String t = s.replaceAll("[^A-Za-z0-9 '\\-]", "").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
