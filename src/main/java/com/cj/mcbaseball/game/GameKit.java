package com.cj.mcbaseball.game;

import com.cj.mcbaseball.item.BatItem;
import com.cj.mcbaseball.item.GloveItem;
import com.cj.mcbaseball.registry.ModItems;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

public final class GameKit {
    private static final String TAG_KIT = "mcbaseballKit";
    private static final String TAG_GAME_BALL = "mcbaseballGame";

    public static void tagGameBall(ItemStack stack, UUID gameId) {
        stack.getOrCreateTag().putUUID("mcbaseballGame", gameId);
    }

    @Nullable
    public static UUID gameBallId(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.hasUUID("mcbaseballGame") ? t.getUUID("mcbaseballGame") : null;
    }

    public static boolean isKit(ItemStack stack, UUID gameId) {
        CompoundTag t = stack.getTag();
        return t == null
            ? false
            : t.hasUUID("mcbaseballKit") && t.getUUID("mcbaseballKit").equals(gameId)
                || t.hasUUID("mcbaseballGame") && t.getUUID("mcbaseballGame").equals(gameId);
    }

    public static void giveGameBall(ServerPlayer p, UUID gameId) {
        removeGameBalls(p, gameId);
        ItemStack ball = new ItemStack((ItemLike)ModItems.BASEBALL.get());
        tagGameBall(ball, gameId);
        insertPreferMainHand(p, ball);
    }

    public static boolean hasGameBall(ServerPlayer p, UUID gameId) {
        Inventory inv = p.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (gameId.equals(gameBallId(inv.getItem(i)))) {
                return true;
            }
        }

        return gameId.equals(gameBallId(p.containerMenu.getCarried()));
    }

    public static void removeGameBalls(ServerPlayer p, UUID gameId) {
        Inventory inv = p.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (gameId.equals(gameBallId(inv.getItem(i)))) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    public static void ensureBat(ServerPlayer p, UUID gameId) {
        Inventory inv = p.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof BatItem) {
                return;
            }
        }

        ItemStack bat = new ItemStack((ItemLike)ModItems.WOODEN_BAT.get());
        bat.getOrCreateTag().putUUID("mcbaseballKit", gameId);
        insertPreferMainHand(p, bat);
    }

    public static void ensureGlove(ServerPlayer p, UUID gameId, boolean catcher) {
        Inventory inv = p.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof GloveItem) {
                return;
            }
        }

        ItemStack glove = new ItemStack(catcher ? (ItemLike)ModItems.CATCHERS_MITT.get() : (ItemLike)ModItems.BASEBALL_GLOVE.get());
        glove.getOrCreateTag().putUUID("mcbaseballKit", gameId);
        if (p.getOffhandItem().isEmpty()) {
            p.setItemInHand(InteractionHand.OFF_HAND, glove);
        } else if (!inv.add(glove)) {
            p.drop(glove, false);
        }
    }

    public static void cleanup(ServerPlayer p, UUID gameId) {
        Inventory inv = p.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isKit(inv.getItem(i), gameId)) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }

        if (isKit(p.containerMenu.getCarried(), gameId)) {
            p.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    private static void insertPreferMainHand(ServerPlayer p, ItemStack stack) {
        if (p.getMainHandItem().isEmpty()) {
            p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        } else {
            ItemStack held = p.getMainHandItem().copy();
            p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            if (!p.getInventory().add(held)) {
                p.drop(held, false);
            }
        }
    }

    private GameKit() {
    }

    public static void gloveToOffhand(ServerPlayer p) {
        if (p.getOffhandItem().isEmpty()) {
            Inventory inv = p.getInventory();

            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack st = (ItemStack)inv.items.get(i);
                if (st.getItem() instanceof GloveItem) {
                    p.setItemInHand(InteractionHand.OFF_HAND, st.copy());
                    inv.items.set(i, ItemStack.EMPTY);
                    return;
                }
            }
        }
    }
}
