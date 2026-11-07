package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
        if (!EarthMedicalAndWeaponSupport.isAnvilHammer(player.getItemInHand(hammerHand))) {
            hammerHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        }
        ItemStack activeHammer = player.getItemInHand(hammerHand);
        if (!EarthMedicalAndWeaponSupport.isAnvilHammer(activeHammer)) {
            return InteractionResult.PASS;
        }
        InteractionHand workpieceHand = hammerHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack workpiece = player.getItemInHand(workpieceHand);
        int hearthTempK = sampleNearbyTemperatureK(level, pos);
        Optional<ItemStack> result = forgeResult(workpiece, activeHammer, hearthTempK);
        if (result.isEmpty()) {
            if (!level.isClientSide()) {
                player.swing(hammerHand, true);
                player.sendSystemMessage(Component.translatable("message.earth_on_minecraft.anvil.invalid_workpiece"));
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack output = result.get();
        if ((workpiece.is(EarthOnMinecraft.SPONGE_IRON.get()) || workpiece.is(EarthOnMinecraft.WROUGHT_IRON.get()))
                && (hearthTempK >= 950 || activeHammer.getItem() != EarthOnMinecraft.GEOLOGY_HAMMER.get())) {
            output.grow(1);
        }
        workpiece.shrink(1);
        if (workpiece.isEmpty()) {
            player.setItemInHand(workpieceHand, output);
        } else if (!player.addItem(output)) {
            popResource(level, pos, output);
        }
        activeHammer.hurtAndBreak(1, player, hammerHand == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND
                : EquipmentSlot.OFFHAND);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS,
                0.85F, 0.95F + level.getRandom().nextFloat() * 0.10F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    pos.getX() + 0.5D, pos.getY() + 1.02D, pos.getZ() + 0.5D,
                    10, 0.18D, 0.08D, 0.18D, 0.12D);
            serverLevel.sendParticles(ParticleTypes.LAVA,
                    pos.getX() + 0.5D, pos.getY() + 1.02D, pos.getZ() + 0.5D,
                    4, 0.12D, 0.05D, 0.12D, 0.02D);
        }
        player.swing(hammerHand, true);
        player.sendSystemMessage(Component.translatable("message.earth_on_minecraft.anvil.forged",
                output.getHoverName()));
        return InteractionResult.SUCCESS;
    }

    public static Optional<ItemStack> forgeResult(ItemStack workpiece) {
        return forgeResult(workpiece, ItemStack.EMPTY, 293);
    }

    public static Optional<ItemStack> forgeResult(ItemStack workpiece, ItemStack hammer, int temperatureK) {
        return MetallurgyAssay.forgeAnvilWorkpiece(workpiece, hammer, temperatureK);
    }

    private static int sampleNearbyTemperatureK(Level level, BlockPos pos) {
        int bestK = 293;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (level.getBlockEntity(neighbor) instanceof ProcessingMachineBlockEntity machine) {
                bestK = Math.max(bestK, machine.temperatureK());
            } else {
                BlockState state = level.getBlockState(neighbor);
                if (state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)) {
                    bestK = Math.max(bestK, 1300);
                } else if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CAMPFIRE)) {
                    bestK = Math.max(bestK, 1050);
                }
            }
        }
        return bestK;
    }
}
