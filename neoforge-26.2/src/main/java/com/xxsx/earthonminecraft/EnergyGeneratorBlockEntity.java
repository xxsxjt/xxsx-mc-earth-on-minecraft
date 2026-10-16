package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class EnergyGeneratorBlockEntity extends BaseContainerBlockEntity implements ElectricEnergyStorage {
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_COUNT = 1;
    public static final int DATA_COUNT = 5;
    public static final int CAPACITY = 64_000;
    public static final int TURBINE_CAPACITY = 192_000;
    public static final int GENERATION_PER_TICK = 80;
    public static final int TURBINE_GENERATION_PER_TICK = 320;
    public static final int TRANSFER_PER_TICK = 320;
    public static final int TURBINE_TRANSFER_PER_TICK = 1_024;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy;
                case 1 -> generatorCapacity();
                case 2 -> burnTime;
                case 3 -> Math.max(1, burnTimeTotal);
                case 4 -> active ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energy = Math.max(0, Math.min(generatorCapacity(), value));
                case 2 -> burnTime = Math.max(0, value);
                case 3 -> burnTimeTotal = Math.max(0, value);
                case 4 -> active = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int energy;
    private int burnTime;
    private int burnTimeTotal;
    private boolean active;

    public EnergyGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(EarthOnMinecraft.ENERGY_GENERATOR_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnergyGeneratorBlockEntity generator) {
        boolean changed = false;
        if (generator.active) {
            changed = true;
        }
        generator.active = false;

        if (!RealitySupportRules.isOperational(level, pos)) {
            if (generator.burnTime != 0) {
                generator.burnTime = 0;
                generator.burnTimeTotal = 0;
                changed = true;
            }
            if (generator.syncActiveState(level, pos, state)) {
                changed = true;
            }
            if (changed) {
                generator.setChanged();
            }
            return;
        }

        int capacity = generator.generatorCapacity();
        if (generator.energy > capacity) {
            generator.energy = capacity;
            changed = true;
        }

        if (generator.energy < capacity && generator.burnTime <= 0 && generator.tryConsumeFuel()) {
            changed = true;
        }

        if (generator.energy < capacity && generator.burnTime > 0) {
            generator.burnTime--;
            generator.energy = Math.min(capacity, generator.energy + generator.generationPerTick());
            generator.active = true;
            changed = true;
        }

        if (generator.energy > 0) {
            int moved = EnergyNetwork.distributeToBuffers(level, pos, Math.min(generator.transferPerTick(), generator.energy));
            if (moved > 0) {
                generator.energy -= moved;
                changed = true;
            }
        }

        if (generator.syncActiveState(level, pos, state)) {
            changed = true;
        }

        if (changed) {
            generator.setChanged();
        }
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
        return generatorCapacity();
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        return 0;
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        int extracted = Math.min(Math.max(0, amount), energy);
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
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_FUEL && getGeneratorFuelTicks(stack, isSteamTurbineGenerator()) > 0;
    }

    @Override
    protected Component getDefaultName() {
        if (getBlockState().getBlock() == EarthOnMinecraft.STEAM_TURBINE_GENERATOR.get()) {
            return Component.translatable("block.earth_on_minecraft.steam_turbine_generator");
        }
        return Component.translatable("block.earth_on_minecraft.combustion_generator");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new EnergyGeneratorMenu(containerId, inventory, this, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.energy = input.getIntOr("Energy", 0);
        this.burnTime = input.getIntOr("BurnTime", 0);
        this.burnTimeTotal = input.getIntOr("BurnTimeTotal", burnTime);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("Energy", this.energy);
        output.putInt("BurnTime", this.burnTime);
        output.putInt("BurnTimeTotal", this.burnTimeTotal);
    }

    private boolean tryConsumeFuel() {
        ItemStack fuel = this.items.get(SLOT_FUEL);
        int ticks = getGeneratorFuelTicks(fuel, isSteamTurbineGenerator());
        if (ticks <= 0) {
            return false;
        }
        Item item = fuel.getItem();
        fuel.shrink(1);
        if (fuel.isEmpty()) {
            this.items.set(SLOT_FUEL, item == Items.LAVA_BUCKET ? new ItemStack(Items.BUCKET) : ItemStack.EMPTY);
        }
        this.burnTime = ticks;
        this.burnTimeTotal = ticks;
        return true;
    }

    public static int getGeneratorFuelTicks(ItemStack stack, boolean steamTurbineGenerator) {
        if (stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        if (steamTurbineGenerator) {
            if (item == EarthOnMinecraft.NUCLEAR_HEAT_MODULE.get().asItem()) {
                return 120_000;
            }
            if (item == EarthOnMinecraft.STEAM_TURBINE_ASSEMBLY.get().asItem()) {
                return 8_000;
            }
            return 0;
        }
        return ProcessingMachineBlockEntity.getFuelTicks(stack);
    }

    private int generatorCapacity() {
        return isSteamTurbineGenerator() ? TURBINE_CAPACITY : CAPACITY;
    }

    private int generationPerTick() {
        return isSteamTurbineGenerator() ? TURBINE_GENERATION_PER_TICK : GENERATION_PER_TICK;
    }

    private int transferPerTick() {
        return isSteamTurbineGenerator() ? TURBINE_TRANSFER_PER_TICK : TRANSFER_PER_TICK;
    }

    private boolean isSteamTurbineGenerator() {
        return getBlockState().getBlock() == EarthOnMinecraft.STEAM_TURBINE_GENERATOR.get();
    }

    private boolean syncActiveState(Level level, BlockPos pos, BlockState state) {
        if (state.hasProperty(EnergyGeneratorBlock.ACTIVE) && state.getValue(EnergyGeneratorBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(EnergyGeneratorBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
            return true;
        }
        return false;
    }
}
