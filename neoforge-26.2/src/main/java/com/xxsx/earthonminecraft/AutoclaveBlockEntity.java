package com.xxsx.earthonminecraft;

import com.xxsx.earthlore.api.GateResult;
import com.xxsx.earthlore.api.LoreMachines;
import com.xxsx.earthonminecraft.component.EarthDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
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

/**
 * Block entity for M-103 灭菌锅 ({@code autoclave}).
 */
public class AutoclaveBlockEntity extends BlockEntity implements Container, ElectricEnergyStorage {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT = 2;
    public static final int CYCLE_TICKS = 120;
    public static final int EOU_PER_TICK = 8;
    public static final int ENERGY_CAPACITY = 3200;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int progress;
    private int energyStored;
    private String lastFault = "";

    public AutoclaveBlockEntity(BlockPos pos, BlockState state) {
        super(EarthOnMinecraft.AUTOCLAVE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AutoclaveBlockEntity autoclave) {
        boolean changed = false;
        if (autoclave.energyStored < ENERGY_CAPACITY) {
            int pulled = EnergyNetwork.extractUpTo(level, pos, Math.min(64, ENERGY_CAPACITY - autoclave.energyStored));
            if (pulled > 0) {
                autoclave.energyStored += pulled;
                changed = true;
            }
        }

        ItemStack input = autoclave.items.get(SLOT_INPUT);
        if (input.isEmpty() || !input.is(AutoclaveBlock.STERILIZABLE) || !autoclave.canFitOutput(input)) {
            if (autoclave.progress > 0) {
                autoclave.progress = 0;
                autoclave.lastFault = "cycle_incomplete";
                changed = true;
            }
            autoclave.updateActiveState(level, pos, state, false);
            if (changed) {
                autoclave.setChanged();
            }
            return;
        }

        GateResult gate = LoreIntegration.checkMachine(autoclave, state.getBlock(), input);
        if (gate.locked()) {
            LoreMachines.setFault(autoclave, "lore_locked", gate.missing());
            autoclave.lastFault = "lore_locked";
            if (autoclave.progress > 0) {
                autoclave.progress = 0;
                changed = true;
            }
            autoclave.updateActiveState(level, pos, state, false);
            if (changed) {
                autoclave.setChanged();
            }
            return;
        }
        LoreMachines.clearFault(autoclave);

        if (!autoclave.consumePower(level, pos, EOU_PER_TICK)) {
            if (autoclave.progress > 0) {
                autoclave.progress = 0;
                autoclave.lastFault = "pressure_low";
                changed = true;
            } else if (autoclave.lastFault.isEmpty()) {
                autoclave.lastFault = "pressure_low";
            }
            autoclave.updateActiveState(level, pos, state, false);
            if (changed) {
                autoclave.setChanged();
            }
            return;
        }

        autoclave.lastFault = "";
        autoclave.progress++;
        changed = true;
        autoclave.updateActiveState(level, pos, state, true);

        if (autoclave.progress >= CYCLE_TICKS) {
            autoclave.progress = 0;
            ItemStack out = input.copyWithCount(1);
            out.set(EarthDataComponents.STERILE.get(), true);
            out = LoreIntegration.capMachineOutput(autoclave, out, state.getBlock());
            input.shrink(1);
            if (input.isEmpty()) {
                autoclave.items.set(SLOT_INPUT, ItemStack.EMPTY);
            }
            ItemStack existingOut = autoclave.items.get(SLOT_OUTPUT);
            if (existingOut.isEmpty()) {
                autoclave.items.set(SLOT_OUTPUT, out);
            } else if (ItemStack.isSameItemSameComponents(existingOut, out)) {
                existingOut.grow(1);
            }
            LoreIntegration.awardMachineProduct(autoclave, out);
        }

        if (changed) {
            autoclave.setChanged();
        }
    }

    private boolean consumePower(Level level, BlockPos pos, int amount) {
        if (energyStored >= amount) {
            energyStored -= amount;
            return true;
        }
        return EnergyNetwork.consume(level, pos, amount);
    }

    private boolean canFitOutput(ItemStack input) {
        ItemStack probe = input.copyWithCount(1);
        probe.set(EarthDataComponents.STERILE.get(), true);
        probe = LoreIntegration.capMachineOutput(this, probe, getBlockState().getBlock());
        ItemStack existing = items.get(SLOT_OUTPUT);
        if (existing.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(existing, probe) && existing.getCount() < existing.getMaxStackSize();
    }

    private void updateActiveState(Level level, BlockPos pos, BlockState state, boolean active) {
        if (state.hasProperty(AutoclaveBlock.ACTIVE) && state.getValue(AutoclaveBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(AutoclaveBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    public ItemStack tryInsert(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(AutoclaveBlock.STERILIZABLE)) {
            return stack;
        }
        ItemStack current = items.get(SLOT_INPUT);
        if (current.isEmpty()) {
            items.set(SLOT_INPUT, stack.copy());
            setChanged();
            return ItemStack.EMPTY;
        }
        if (ItemStack.isSameItemSameComponents(current, stack) && current.getCount() < current.getMaxStackSize()) {
            int moved = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
            current.grow(moved);
            ItemStack rem = stack.copy();
            rem.shrink(moved);
            setChanged();
            return rem;
        }
        return stack;
    }

    public ItemStack extractOutput() {
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        items.set(SLOT_OUTPUT, ItemStack.EMPTY);
        setChanged();
        return out;
    }

    public Component statusMessage() {
        if (!lastFault.isEmpty()) {
            return Component.translatable("message.earth_on_minecraft.autoclave.fault." + lastFault)
                    .withStyle(ChatFormatting.RED);
        }
        return Component.translatable("message.earth_on_minecraft.autoclave.status",
                progress, CYCLE_TICKS, energyStored).withStyle(ChatFormatting.AQUA);
    }

    public String lastFault() {
        return lastFault;
    }

    public int progress() {
        return progress;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Progress", progress);
        output.putInt("Energy", energyStored);
        output.putString("LastFault", lastFault);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("Progress", 0);
        energyStored = input.getIntOr("Energy", 0);
        lastFault = input.getStringOr("LastFault", "");
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
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
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (slot == SLOT_INPUT && !removed.isEmpty() && progress > 0) {
            progress = 0;
            lastFault = "cycle_incomplete";
        }
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < items.size()) {
            if (slot == SLOT_INPUT && progress > 0 && (stack.isEmpty() || !ItemStack.isSameItemSameComponents(items.get(SLOT_INPUT), stack))) {
                progress = 0;
                lastFault = "cycle_incomplete";
            }
            items.set(slot, stack);
            setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT && stack.is(AutoclaveBlock.STERILIZABLE);
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public int getEnergyStored() {
        return energyStored;
    }

    @Override
    public int getEnergyCapacity() {
        return ENERGY_CAPACITY;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = Math.max(0, Math.min(ENERGY_CAPACITY - energyStored, maxReceive));
        if (!simulate && received > 0) {
            energyStored += received;
            setChanged();
        }
        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int extracted = Math.max(0, Math.min(energyStored, maxExtract));
        if (!simulate && extracted > 0) {
            energyStored -= extracted;
            setChanged();
        }
        return extracted;
    }
}
