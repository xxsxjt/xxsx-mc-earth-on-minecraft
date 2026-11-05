package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A lode block that carries a finite {@link DepositReserve}.
 *
 * <p>Right-clicking reports how much ore is left, which is the only way to plan a bore head
 * placement without guessing. The reserve is created on first access, so lodes generated before
 * this feature existed still work.</p>
 */
public class DepositBlock extends Block implements EntityBlock {
    public DepositBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DepositBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DepositBlockEntity deposit) {
            DepositReserve reserve = deposit.reserve();
            player.sendSystemMessage(Component.translatable(
                    "screen.earth_on_minecraft.deposit.status",
                    reserve.remaining(), reserve.capacity()));
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, net.minecraft.world.InteractionHand hand,
                                          BlockHitResult hitResult) {
        // Prospecting tools run their own logic (the hammer assays this block itself).
        if (stack.getItem() instanceof GeologyHammerItem || stack.getItem() instanceof OreScannerItem) {
            return InteractionResult.PASS;
        }
        return useWithoutItem(state, level, pos, player, hitResult);
    }
}
