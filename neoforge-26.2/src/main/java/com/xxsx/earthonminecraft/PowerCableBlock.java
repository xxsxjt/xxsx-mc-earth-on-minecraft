package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PowerCableBlock extends Block {
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private final VoxelShape coreShape;
    private final VoxelShape downShape;
    private final VoxelShape upShape;
    private final VoxelShape northShape;
    private final VoxelShape eastShape;
    private final VoxelShape southShape;
    private final VoxelShape westShape;

    public PowerCableBlock(Properties properties, int radius) {
        super(properties);
        int min = 8 - radius;
        int max = 8 + radius;
        this.coreShape = Block.box(min, min, min, max, max, max);
        this.downShape = Block.box(min, 0, min, max, 8, max);
        this.upShape = Block.box(min, 8, min, max, 16, max);
        this.northShape = Block.box(min, min, 0, max, max, 8);
        this.eastShape = Block.box(8, min, min, 16, max, max);
        this.southShape = Block.box(min, min, 8, max, max, 16);
        this.westShape = Block.box(0, min, min, 8, max, max);
        registerDefaultState(this.stateDefinition.any()
                .setValue(DOWN, false)
                .setValue(UP, false)
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!RealitySupportRules.canPlace(context.getLevel(), context.getClickedPos())) {
            return null;
        }
        BlockState state = defaultBlockState();
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (Direction direction : Direction.values()) {
            state = state.setValue(propertyFor(direction), canConnectTo(level.getBlockState(pos.relative(direction))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return state.setValue(propertyFor(direction), canConnectTo(neighborState));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = coreShape;
        if (state.getValue(DOWN)) {
            shape = Shapes.or(shape, downShape);
        }
        if (state.getValue(UP)) {
            shape = Shapes.or(shape, upShape);
        }
        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, northShape);
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, eastShape);
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, southShape);
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, westShape);
        }
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DOWN, UP, NORTH, EAST, SOUTH, WEST);
    }

    public static boolean isCable(BlockState state) {
        return state.getBlock() instanceof PowerCableBlock;
    }

    private static boolean canConnectTo(BlockState state) {
        Block block = state.getBlock();
        return block instanceof PowerCableBlock
                || block instanceof EnergyGeneratorBlock
                || block instanceof BatteryBoxBlock
                || block instanceof ProcessingMachineBlock
                || block instanceof MinerBlock
                || block instanceof QuarryBlock;
    }

    private static BooleanProperty propertyFor(Direction direction) {
        return switch (direction) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
        };
    }
}
