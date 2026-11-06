package com.xxsx.earthonminecraft;

import com.xxsx.earthlore.api.GateResult;
import com.xxsx.earthlore.api.LoreMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a {@link MinerBlock}: finds lodes, drains their {@link DepositReserve}, and emits loot.
 *
 * <p>Search is a bounded cube around the head, re-scanned every {@value #RESCAN_TICKS} ticks so a
 * moving operation does not scan the world every tick. Each unit costs {@link #ENERGY_PER_UNIT}
 * EOU and obeys the same reality-support rule as every other machine, so a bore head out in the
 * open still needs infrastructure behind it.</p>
 *
 * <p>The output side is a plain {@link Container}, so conveyor belts and hoppers can pull from it
 * without any extra plumbing.</p>
 */
public class MinerBlockEntity extends BlockEntity implements Container {
    public static final int OUTPUT_SLOTS = 9;

    /** How far from the head a lode can be worked. */
    public static final int SCAN_RADIUS = 24;
    /** Ticks between lode re-scans. */
    public static final int RESCAN_TICKS = 40;
    /** Ticks one unit of ore takes. */
    public static final int TICKS_PER_UNIT = 20;
    /** EOU drawn per unit of ore produced. */
    public static final int ENERGY_PER_UNIT = 24;
    /** Hard cap on how many lodes one head tracks, to bound the scan cost. */
    public static final int MAX_TRACKED_LODES = 64;

    private final NonNullList<ItemStack> items = NonNullList.withSize(OUTPUT_SLOTS, ItemStack.EMPTY);
    private final List<BlockPos> tracked = new ArrayList<>();

    private int progress;
    private int scannedCount;
    private int reserveTotal;
    private int outputCount;
    private int cooldown;

    public MinerBlockEntity(BlockPos pos, BlockState state) {
        super(EarthOnMinecraft.MINER_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MinerBlockEntity miner) {
        boolean changed = false;

        if (miner.cooldown-- <= 0) {
            miner.cooldown = RESCAN_TICKS;
            miner.rescan(level);
            changed = true;
        }

        if (!RealitySupportRules.isOperational(level, pos)) {
            miner.finishTick(level, pos, state, false);
            return;
        }
        GateResult loreGate = LoreIntegration.checkMachine(miner, state.getBlock());
        if (loreGate.locked()) {
            LoreMachines.setFault(miner, "lore_locked", loreGate.missing());
            if (miner.progress != 0) {
                miner.progress = 0;
                changed = true;
            }
            miner.finishTick(level, pos, state, changed);
            return;
        }
        LoreMachines.clearFault(miner);

        // Drop lodes that ran dry so the head moves on.
        miner.tracked.removeIf(lode ->
                !(level.getBlockEntity(lode) instanceof DepositBlockEntity deposit)
                        || deposit.reserve().isEmpty());

        if (miner.tracked.isEmpty() || !miner.canFitOutput()) {
            miner.finishTick(level, pos, state, changed);
            return;
        }

        miner.progress++;
        changed = true;

        if (miner.progress < TICKS_PER_UNIT) {
            miner.finishTick(level, pos, state, changed);
            return;
        }

        miner.progress = 0;
        if (miner.mineOneUnit(level)) {
            changed = true;
        }
        miner.finishTick(level, pos, state, changed);
    }

    public static boolean fitInto(NonNullList<ItemStack> stacks, ItemStack output) {
        for (ItemStack existing : stacks) {
            if (!output.isEmpty() && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, output)) {
                int moved = Math.min(output.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(moved);
                output.shrink(moved);
            }
        }
        for (int i = 0; i < stacks.size() && !output.isEmpty(); i++) {
            if (stacks.get(i).isEmpty()) {
                int moved = Math.min(output.getCount(), output.getMaxStackSize());
                stacks.set(i, output.copyWithCount(moved));
                output.shrink(moved);
            }
        }
        return output.isEmpty();
    }

    /** Works one unit out of the nearest live lode. Returns true when something was produced. */
    private boolean mineOneUnit(Level level) {
        if (tracked.isEmpty() || !(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return false;
        }
        BlockPos lode = tracked.get(0);
        if (!(level.getBlockEntity(lode) instanceof DepositBlockEntity deposit)) {
            tracked.remove(0);
            return false;
        }
        BlockState lodeState = level.getBlockState(lode);
        List<ItemStack> drops = DepositReserve.rollLoot(server, lode, lodeState);
        double yieldFactor = com.xxsx.earthlore.knowledge.KnowledgeService.running()
                ? LoreMachines.yieldFactor(this, lodeState.getBlock()) : 1.0;
        List<ItemStack> adjustedDrops = new ArrayList<>(drops.size());
        for (ItemStack rawDrop : drops) {
            if (rawDrop.isEmpty()) {
                continue;
            }
            int scaledCount = yieldFactor < 1.0
                    ? Math.max(1, (int) Math.floor(rawDrop.getCount() * yieldFactor))
                    : rawDrop.getCount();
            ItemStack adjusted = LoreIntegration.capMachineOutput(this, rawDrop.copyWithCount(scaledCount), getBlockState().getBlock());
            adjustedDrops.add(adjusted);
        }
        drops = adjustedDrops;
        NonNullList<ItemStack> simulated = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            simulated.set(i, items.get(i).copy());
        }
        for (ItemStack drop : drops) {
            if (!fitInto(simulated, drop.copy())) {
                return false;
            }
        }
        if (!EnergyNetwork.consume(level, worldPosition, ENERGY_PER_UNIT)) {
            progress = TICKS_PER_UNIT - 1;
            return false;
        }
        DepositReserve reserve = deposit.reserve();
        if (reserve.extract(1) <= 0) {
            tracked.remove(0);
            return false;
        }
        for (ItemStack drop : drops) {
            insert(drop);
            LoreIntegration.awardMachineProduct(this, drop);
        }
        deposit.syncReserve();

        if (reserve.isEmpty()) {
            // A worked-out lode becomes its depleted form so the world reflects the extraction.
            BlockState depleted = SpecDepletedBlocks.byLode(reserve.lodeId());
            if (depleted != null) {
                level.setBlock(lode, depleted, Block.UPDATE_ALL);
            }
            tracked.remove(0);
        }
        return true;
    }

    private void rescan(Level level) {
        tracked.clear();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        List<BlockPos> found = new ArrayList<>();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS && found.size() < MAX_TRACKED_LODES; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS && found.size() < MAX_TRACKED_LODES; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS && found.size() < MAX_TRACKED_LODES; dz++) {
                    cursor.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    if (level.getBlockEntity(cursor) instanceof DepositBlockEntity deposit
                            && !deposit.reserve().isEmpty()) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        // Nearest first so the head works the closest body before walking the scan radius.
        found.sort((a, b) -> Double.compare(a.distSqr(worldPosition), b.distSqr(worldPosition)));
        tracked.addAll(found);

        scannedCount = found.size();
        reserveTotal = 0;
        for (BlockPos lode : found) {
            if (level.getBlockEntity(lode) instanceof DepositBlockEntity deposit) {
                reserveTotal += deposit.reserve().remaining();
            }
        }
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
        boolean active = !tracked.isEmpty() && progress > 0;
        if (state.hasProperty(MinerBlock.ACTIVE) && state.getValue(MinerBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(MinerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
        if (changed) {
            setChanged();
        }
        syncData();
    }

    private void syncData() {
        outputCount = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                outputCount += stack.getCount();
            }
        }
    }

    public int reserveCount() {
        return reserveTotal;
    }

    public int outputCount() {
        return outputCount;
    }

    public int scannedCount() {
        return scannedCount;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Progress", progress);
        output.putInt("Cooldown", cooldown);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("Progress", 0);
        cooldown = input.getIntOr("Cooldown", 0);
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
