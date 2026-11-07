package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
}
