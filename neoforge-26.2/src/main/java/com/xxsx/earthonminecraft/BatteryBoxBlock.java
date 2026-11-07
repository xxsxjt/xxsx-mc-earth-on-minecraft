package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class BatteryBoxBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public BatteryBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!RealitySupportRules.canPlace(context.getLevel(), context.getClickedPos())) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return open(level, pos, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if ((stack.getItem() instanceof HandCrankItem || Ae2CrankCompat.isAe2CrankItem(stack))
                && level.getBlockEntity(pos) instanceof BatteryBoxBlockEntity battery
                && battery.getEnergyStored() < battery.getEnergyCapacity()) {
            if (!level.isClientSide()) {
                int accepted = battery.receiveEnergy(HandCrankItem.CRANK_ENERGY_EOU, false);
                MachineFeedback.emitBatteryCrank(level, pos);
                if (accepted > 0) {
                    if (stack.isDamageableItem()) {
                        stack.hurtAndBreak(1, player, hand);
                    }
                    player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                            "message.earth_on_minecraft.hand_crank.battery",
                            accepted,
                            battery.getEnergyStored(),
                            battery.getEnergyCapacity()).withStyle(net.minecraft.ChatFormatting.AQUA));
                }
                player.swing(hand, true);
            }
            return InteractionResult.SUCCESS;
        }
        return open(level, pos, player);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryBoxBlockEntity(pos, state);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    private InteractionResult open(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof BatteryBoxBlockEntity battery) {
            serverPlayer.openMenu(battery, buf -> buf.writeBlockPos(pos));
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
