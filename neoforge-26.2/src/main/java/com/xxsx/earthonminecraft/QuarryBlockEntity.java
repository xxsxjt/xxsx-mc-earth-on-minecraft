package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Quarry: the answer to "what if there is no lode here".
 *
 * <p>A bore head is only useful on a deposit. A quarry grinds out bulk rock instead: it reads the
 * block it is sitting on and yields that material's ordinary drops, so a quarry in a limestone bed
 * produces limestone and one on plain stone produces cobblestone. That gives every site a floor
 * value and makes bulk construction materials renewable without hand mining.</p>
 *
 * <p>It is deliberately slower and cheaper per unit than a bore head, because it is extracting
 * nothing rarer than the rock itself.</p>
 */
public class QuarryBlockEntity extends BlockEntity implements Container {
    public static final int OUTPUT_SLOTS = 9;
    public static final int TICKS_PER_UNIT = 40;
    public static final int ENERGY_PER_UNIT = 12;
    /** How far below the quarry it samples to decide what it is cutting. */
    public static final int SAMPLE_DEPTH = 3;

    private final NonNullList<ItemStack> items = NonNullList.withSize(OUTPUT_SLOTS, ItemStack.EMPTY);
    private int progress;
    private int outputCount;

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(EarthOnMinecraft.QUARRY_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, QuarryBlockEntity quarry) {
        boolean changed = false;

        if (!RealitySupportRules.isOperational(level, pos)) {
            quarry.finishTick(level, pos, state, false);
            return;
        }
        if (!quarry.canFitOutput()) {
            quarry.finishTick(level, pos, state, false);
            return;
        }
        if (!EnergyNetwork.consume(level, pos, ENERGY_PER_UNIT)) {
            quarry.progress = 0;
            quarry.finishTick(level, pos, state, changed);
            return;
        }

        quarry.progress++;
        changed = true;

        if (quarry.progress < TICKS_PER_UNIT) {
            quarry.finishTick(level, pos, state, changed);
            return;
        }

        quarry.progress = 0;
        if (quarry.cutOneUnit(level)) {
            changed = true;
        }
        quarry.finishTick(level, pos, state, changed);
    }

    /** Produces one unit of whatever the rock under the quarry yields. */
    private boolean cutOneUnit(Level level) {
        BlockState sampled = sampleRock(level);
        if (sampled == null || sampled.isAir()) {
            return false;
        }
        ItemStack yield = dropFor(sampled);
        if (yield.isEmpty()) {
            return false;
        }
        insert(yield);
        return true;
    }

    /** Looks a few blocks down for the rock body being quarried. */
    private BlockState sampleRock(Level level) {
        for (int depth = 1; depth <= SAMPLE_DEPTH; depth++) {
            BlockState state = level.getBlockState(worldPosition.below(depth));
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return state;
            }
        }
        return null;
    }

    /**
     * The bulk material a rock yields. Ores are deliberately excluded: a quarry must not become a
     * cheaper bore head, so anything with a deposit reserve returns plain stone instead.
     */
    private static ItemStack dropFor(BlockState state) {
        Block block = state.getBlock();
        if (DepositSpec.isLode(block) || block instanceof DepositBlock) {
            return new ItemStack(Items.COBBLESTONE);
        }
        ItemStack direct = new ItemStack(block.asItem());
        return direct.isEmpty() ? new ItemStack(Items.COBBLESTONE) : direct;
    }

    private boolean canFitOutput() {
        for (ItemStack stack : items) {
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void insert(ItemStack drop) {
        ItemStack remaining = drop.copy();
        for (int i = 0; i < items.size() && !remaining.isEmpty(); i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) {
                items.set(i, remaining);
                remaining = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(slot, remaining)) {
                int room = slot.getMaxStackSize() - slot.getCount();
                int moved = Math.min(room, remaining.getCount());
                if (moved > 0) {
                    slot.grow(moved);
                    remaining.shrink(moved);
                }
            }
        }
    }

    private void finishTick(Level level, BlockPos pos, BlockState state, boolean changed) {
        if (changed) {
            setChanged();
        }
        outputCount = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                outputCount += stack.getCount();
            }
        }
    }

    public int outputCount() {
        return outputCount;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("Progress", 0);
    }

    // ---- Container ----

    @Override
    public int getContainerSize() {
        return OUTPUT_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < items.size()) {
            items.set(slot, stack);
            setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }
}
