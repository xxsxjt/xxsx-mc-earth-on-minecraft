package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BatteryBoxBlockEntity extends BaseContainerBlockEntity implements ElectricEnergyStorage {
    public static final int SLOT_COUNT = 0;
    public static final int DATA_COUNT = 2;
    public static final int CAPACITY = 128_000;
    public static final int TRANSFER_LIMIT = 512;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy;
                case 1 -> CAPACITY;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                energy = Math.max(0, Math.min(CAPACITY, value));
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int energy;

    public BatteryBoxBlockEntity(BlockPos pos, BlockState state) {
        super(EarthOnMinecraft.BATTERY_BOX_BLOCK_ENTITY.get(), pos, state);
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getEnergyCapacity() {
        return CAPACITY;
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        if (level != null && !RealitySupportRules.isOperational(level, worldPosition)) {
            return 0;
        }
        int accepted = Math.min(Math.min(Math.max(0, amount), TRANSFER_LIMIT), CAPACITY - energy);
        if (!simulate && accepted > 0) {
            energy += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        if (level != null && !RealitySupportRules.isOperational(level, worldPosition)) {
            return 0;
        }
        int extracted = Math.min(Math.min(Math.max(0, amount), TRANSFER_LIMIT), energy);
        if (!simulate && extracted > 0) {
            energy -= extracted;
            setChanged();
        }
        return extracted;
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.earth_on_minecraft.battery_box");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new BatteryBoxMenu(containerId, inventory, this, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.energy = input.getIntOr("Energy", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Energy", this.energy);
    }
}
