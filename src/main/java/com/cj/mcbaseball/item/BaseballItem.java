package com.cj.mcbaseball.item;

import com.cj.mcbaseball.anim.ThrowKind;
import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.entity.BaseballEntity;
import com.cj.mcbaseball.fielding.FieldingSystem;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.GameKit;
import com.cj.mcbaseball.game.GameManager;
import com.cj.mcbaseball.game.GamePhase;
import com.cj.mcbaseball.game.LineupSlot;
import com.cj.mcbaseball.network.ThrowAnimPacket;
import com.cj.mcbaseball.physics.BallPhysics;
import com.cj.mcbaseball.physics.BaseballUnits;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

public class BaseballItem extends Item {
    private static final int MAX_USE = 72000;

    public BaseballItem(Properties props) {
        super(props);
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    public static float chargeFraction(int ticksHeld) {
        return Mth.clamp((float)ticksHeld / (float)((Integer)BaseballConfig.THROW_FULL_CHARGE_TICKS.get()).intValue(), 0.0F, 1.0F);
    }

    public static double throwSpeed(float charge) {
        double min = (Double)BaseballConfig.THROW_MIN_SPEED.get();
        double max = (Double)BaseballConfig.THROW_MAX_SPEED.get();
        return Mth.lerp(1.0 - Math.pow(1.0 - (double)charge, 2.0), min, max);
    }

    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!level.isClientSide && user instanceof ServerPlayer sp) {
            int held = this.getUseDuration(stack) - remaining;
            if (held % 2 == 0) {
                if (GameKit.gameBallId(stack) != null) {
                    BaseballGame g = GameManager.forPlayer(sp.getUUID());
                    if (g != null && g.phase == GamePhase.PITCHING && g.pitch == null && g.slotOf(sp) == g.pitcherSlot()) {
                        return;
                    }
                }

                float charge = chargeFraction(held);
                int mph = (int)Math.round(BaseballUnits.displayMph(throwSpeed(charge)));
                sp.displayClientMessage(chargeBar(charge).append(Component.literal("  " + mph + " MPH").withStyle(ChatFormatting.WHITE)), true);
            }
        }
    }

    private static MutableComponent chargeBar(float charge) {
        int filled = Math.round(charge * 12.0F);
        ChatFormatting color = charge >= 1.0F ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        return Component.literal("THROW ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal("|".repeat(filled)).withStyle(color))
            .append(Component.literal("|".repeat(12 - filled)).withStyle(ChatFormatting.DARK_GRAY));
    }

    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (user instanceof Player player) {
            int held = this.getUseDuration(stack) - remaining;
            if (held >= 2) {
                if (!level.isClientSide && player instanceof ServerPlayer sp) {
                    FieldingSystem.Release r = FieldingSystem.humanRelease(sp, stack, held);
                    if (r == FieldingSystem.Release.KEEP) {
                        return;
                    }

                    if (r == FieldingSystem.Release.THROWN) {
                        BaseballGame game = GameManager.forPlayer(sp.getUUID());
                        LineupSlot slot = game == null ? null : game.slotOf(sp);
                        String abbr = slot == null ? "" : slot.position.abbr;
                        boolean of = abbr.equals("LF") || abbr.equals("CF") || abbr.equals("RF");
                        ThrowKind kind = held < 6
                            ? ThrowKind.FLIP
                            : (
                                of && held >= 18
                                    ? ThrowKind.CROWHOP
                                    : (!of && !abbr.equals("P") && !abbr.equals("C") && held < 14 ? ThrowKind.SIDEARM : ThrowKind.OVERHAND)
                            );
                        ThrowAnimPacket.broadcast(sp, kind);
                    }

                    if (r != FieldingSystem.Release.NOT_GAME) {
                        stack.shrink(1);
                        return;
                    }
                }

                if (GameKit.gameBallId(stack) == null || !level.isClientSide) {
                    if (!level.isClientSide) {
                        float charge = chargeFraction(held);
                        double speed = throwSpeed(charge);
                        Vec3 look = player.getLookAngle();
                        Vec3 eye = player.getEyePosition();
                        Vec3 spawn = eye.add(look.scale(0.35));
                        if (level.clip(new ClipContext(eye, spawn, Block.COLLIDER, Fluid.NONE, player)).getType() != Type.MISS) {
                            spawn = eye;
                        }

                        BaseballEntity ball = BaseballEntity.create(level, spawn);
                        ball.launch(player, look.scale(speed));
                        ball.setSpin(BallPhysics.backspin(look, 7.0));
                        level.addFreshEntity(ball);
                        level.playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            SoundEvents.SNOWBALL_THROW,
                            SoundSource.PLAYERS,
                            0.6F,
                            0.7F + charge * 0.3F
                        );
                        if (player instanceof ServerPlayer sp) {
                            sp.displayClientMessage(Component.literal(Math.round(BaseballUnits.displayMph(speed)) + " MPH").withStyle(ChatFormatting.WHITE), true);
                        }
                    }

                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                }
            }
        }
    }

    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mcbaseball.baseball.tip").withStyle(ChatFormatting.GRAY));
    }
}
