package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

public class IronworkingAnvilBlock extends Block {
    public IronworkingAnvilBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack hammer, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        InteractionHand hammerHand = hand;
        if (player.getItemInHand(hammerHand).getItem() != EarthOnMinecraft.GEOLOGY_HAMMER.get()) {
            hammerHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        }
        ItemStack activeHammer = player.getItemInHand(hammerHand);
        if (activeHammer.getItem() != EarthOnMinecraft.GEOLOGY_HAMMER.get()) {
            return InteractionResult.PASS;
        }
        InteractionHand workpieceHand = hammerHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack workpiece = player.getItemInHand(workpieceHand);
        Optional<ItemStack> result = forgeResult(workpiece);
        if (result.isEmpty()) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.translatable("message.earth_on_minecraft.anvil.invalid_workpiece"));
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack output = result.get();
        workpiece.shrink(1);
        if (workpiece.isEmpty()) {
            player.setItemInHand(workpieceHand, output);
        } else if (!player.addItem(output)) {
            popResource(level, pos, output);
        }
        activeHammer.hurtAndBreak(1, player, hammerHand == InteractionHand.MAIN_HAND
                ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        player.sendSystemMessage(Component.translatable("message.earth_on_minecraft.anvil.forged",
                output.getHoverName()));
        return InteractionResult.SUCCESS_SERVER;
    }

    public static Optional<ItemStack> forgeResult(ItemStack workpiece) {
        if (workpiece.isEmpty()) {
            return Optional.empty();
        }
        if (workpiece.getItem() == EarthOnMinecraft.SPONGE_IRON.get()) {
            return Optional.of(new ItemStack(EarthOnMinecraft.WROUGHT_IRON.get()));
        }
        if (workpiece.getItem() == EarthOnMinecraft.WROUGHT_IRON.get()) {
            return Optional.of(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        }
        return Optional.empty();
    }
}
