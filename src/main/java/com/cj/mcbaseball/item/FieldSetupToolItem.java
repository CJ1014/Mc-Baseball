package com.cj.mcbaseball.item;

import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.field.FieldMarker;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class FieldSetupToolItem extends Item {
    private static final String TAG_POS = "Controller";
    private static final String TAG_DIM = "Dimension";
    private static final String TAG_MARKER = "Marker";

    public FieldSetupToolItem(Properties props) {
        super(props);
    }

    public static void link(ItemStack stack, Level level, BlockPos controller, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.put("Controller", NbtUtils.writeBlockPos(controller));
        tag.putString("Dimension", level.dimension().location().toString());
        player.displayClientMessage(
            Component.translatable("mcbaseball.tool.linked", new Object[]{controller.getX(), controller.getY(), controller.getZ()})
                .withStyle(ChatFormatting.GREEN),
            true
        );
        level.playSound(null, controller, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    public static FieldMarker selected(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return FieldMarker.byId(tag == null ? 0 : tag.getInt("Marker"));
    }

    @Nullable
    public static BlockPos linkedController(ItemStack stack) {
        return linkedPos(stack);
    }

    @Nullable
    private static BlockPos linkedPos(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("Controller") ? NbtUtils.readBlockPos(tag.getCompound("Controller")) : null;
    }

    @Nullable
    private static ResourceKey<Level> linkedDim(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Dimension")) {
            ResourceLocation rl = ResourceLocation.tryParse(tag.getString("Dimension"));
            return rl == null ? null : ResourceKey.create(Registries.DIMENSION, rl);
        } else {
            return null;
        }
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        } else {
            if (!level.isClientSide) {
                FieldMarker next = FieldMarker.byId((selected(stack).ordinal() + 1) % FieldMarker.values().length);
                stack.getOrCreateTag().putInt("Marker", next.ordinal());
                player.displayClientMessage(
                    Component.translatable("mcbaseball.tool.selected", new Object[]{next.displayName().copy().withStyle(ChatFormatting.YELLOW)}), true
                );
                level.playSound(null, player.blockPosition(), (SoundEvent)SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.3F, 1.6F);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }

    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        } else if (player.isShiftKeyDown()) {
            this.use(level, player, ctx.getHand());
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            BlockPos ctrl = linkedPos(stack);
            ResourceKey<Level> dim = linkedDim(stack);
            if (ctrl != null && dim != null) {
                if (level.dimension().equals(dim) && level.isLoaded(ctrl) && level.getBlockEntity(ctrl) instanceof FieldControllerBlockEntity be) {
                    FieldMarker var16 = selected(stack);
                    BlockPos target = ctx.getClickedPos();
                    ServerPlayer sp = (ServerPlayer)player;
                    switch (be.applyMarker(var16, target, sp)) {
                        case OK:
                            ((ServerLevel)level).playSound(null, target, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.4F);
                            Component msg = var16.multiPoint()
                                ? Component.translatable("mcbaseball.marking.wall_added", new Object[]{be.layout().outfieldWall().size()})
                                : Component.translatable(
                                    "mcbaseball.marking.set", new Object[]{var16.displayName(), target.getX(), target.getY(), target.getZ()}
                                );
                            player.displayClientMessage(msg.copy().withStyle(ChatFormatting.GREEN), true);
                            if (!var16.multiPoint()) {
                                for (FieldMarker m : FieldMarker.values()) {
                                    if (m.required() && !be.layout().has(m)) {
                                        stack.getOrCreateTag().putInt("Marker", m.ordinal());
                                        break;
                                    }
                                }
                            }
                            break;
                        case TOO_FAR:
                            player.displayClientMessage(
                                Component.translatable("mcbaseball.marking.too_far", new Object[]{BaseballConfig.FIELD_MAX_RADIUS.get()})
                                    .withStyle(ChatFormatting.RED),
                                true
                            );
                            break;
                        case WALL_FULL:
                            player.displayClientMessage(Component.translatable("mcbaseball.marking.wall_full").withStyle(ChatFormatting.YELLOW), true);
                            break;
                        case NO_PERMISSION:
                            player.displayClientMessage(
                                Component.translatable("mcbaseball.field.no_permission", new Object[]{be.ownerName()}).withStyle(ChatFormatting.RED), true
                            );
                    }

                    return InteractionResult.CONSUME;
                } else {
                    player.displayClientMessage(Component.translatable("mcbaseball.marking.controller_gone").withStyle(ChatFormatting.RED), true);
                    return InteractionResult.CONSUME;
                }
            } else {
                player.displayClientMessage(Component.translatable("mcbaseball.tool.not_linked").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
        }
    }

    public boolean isFoil(ItemStack stack) {
        return linkedPos(stack) != null;
    }

    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BlockPos p = linkedPos(stack);
        tooltip.add(
            p == null
                ? Component.translatable("mcbaseball.tool.tip.unlinked").withStyle(ChatFormatting.RED)
                : Component.translatable("mcbaseball.tool.tip.linked", new Object[]{p.getX(), p.getY(), p.getZ()}).withStyle(ChatFormatting.GREEN)
        );
        tooltip.add(
            Component.translatable("mcbaseball.tool.tip.marker", new Object[]{selected(stack).displayName().copy().withStyle(ChatFormatting.YELLOW)})
                .withStyle(ChatFormatting.GRAY)
        );
        tooltip.add(Component.translatable("mcbaseball.tool.tip.controls").withStyle(ChatFormatting.DARK_GRAY));
    }
}
