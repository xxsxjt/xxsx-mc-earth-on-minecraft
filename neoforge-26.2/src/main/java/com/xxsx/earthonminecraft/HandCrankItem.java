package com.xxsx.earthonminecraft;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

/**
 * Early-game mechanical crank for driving comminution, classification and manual processing stations
 * (Jaw Crusher, Ball Mill, Sieve, Magnetic Separator, Mill, Stone Mill, Filter Press, Tablet Press, Anvil)
 * or hand-charging a Battery Box before full grid automation is built.
 */
public class HandCrankItem extends Item {
    public static final int DURABILITY = 256;
    public static final int CRANK_WORK_TICKS = 80;
    public static final int CRANK_ENERGY_EOU = 240;

    public HandCrankItem(Properties properties) {
        super(properties.durability(DURABILITY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ProcessingMachineBlockEntity machine && machine.canCrankTurn()) {
            if (!level.isClientSide()) {
                machine.applyCrankTurn(CRANK_WORK_TICKS);
                MachineFeedback.emitManualCrank(level, pos, level.getBlockState(pos), machine.kind());
                if (player != null) {
                    context.getItemInHand().hurtAndBreak(1, player, context.getHand());
                    player.swing(context.getHand(), true);
                    player.sendOverlayMessage(Component.translatable(
                            "message.earth_on_minecraft.hand_crank.turned",
                            machine.getDisplayName(),
                            machine.manualWorkTicks()).withStyle(ChatFormatting.GREEN));
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (be instanceof BatteryBoxBlockEntity battery && battery.getEnergyStored() < battery.getEnergyCapacity()) {
            if (!level.isClientSide()) {
                int accepted = battery.receiveEnergy(CRANK_ENERGY_EOU, false);
                MachineFeedback.emitBatteryCrank(level, pos);
                if (player != null && accepted > 0) {
                    context.getItemInHand().hurtAndBreak(1, player, context.getHand());
                    player.swing(context.getHand(), true);
                    player.sendOverlayMessage(Component.translatable(
                            "message.earth_on_minecraft.hand_crank.battery",
                            accepted,
                            battery.getEnergyStored(),
                            battery.getEnergyCapacity()).withStyle(ChatFormatting.AQUA));
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.hand_crank.0")
                .withStyle(ChatFormatting.GOLD));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.hand_crank.1")
                .withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.hand_crank.2")
                .withStyle(ChatFormatting.AQUA));
    }
}
