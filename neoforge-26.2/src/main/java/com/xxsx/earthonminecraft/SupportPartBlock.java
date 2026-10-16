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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class SupportPartBlock extends Block {
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    public SupportPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(ASSEMBLED, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return RealitySupportRules.canPlace(context.getLevel(), context.getClickedPos())
                ? defaultBlockState()
                : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ASSEMBLED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return openConnectedMachine(level, pos, player, true);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return openConnectedMachine(level, pos, player, false);
    }

    protected InteractionResult openConnectedMachine(Level level, BlockPos pos, Player player, boolean showMissingMessage) {
        if (level.isClientSide()) {
            BlockState state = level.getBlockState(pos);
            return state.hasProperty(ASSEMBLED) && state.getValue(ASSEMBLED)
                    ? InteractionResult.SUCCESS
                    : showMissingMessage ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return MachineMultiblock.findControllerForPart(level, pos)
                .map(controller -> ProcessingMachineBlock.openMachineAt(level, controller, player))
                .orElseGet(() -> {
                    if (showMissingMessage) {
                        player.sendOverlayMessage(Component.translatable("message.earth_on_minecraft.support_part.not_connected"));
                        return InteractionResult.SUCCESS_SERVER;
                    }
                    return InteractionResult.PASS;
                });
    }
}
