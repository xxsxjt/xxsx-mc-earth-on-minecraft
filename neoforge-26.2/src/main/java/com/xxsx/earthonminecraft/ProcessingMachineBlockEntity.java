package com.xxsx.earthonminecraft;

import com.xxsx.earthlore.api.GateResult;
import com.xxsx.earthlore.api.LoreMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ProcessingMachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT_START = 2;
    public static final int OUTPUT_SLOT_COUNT = 7;
    public static final int SLOT_SECONDARY_START = SLOT_OUTPUT_START + OUTPUT_SLOT_COUNT;
    public static final int SECONDARY_INPUT_SLOTS = 4;
    public static final int SLOT_REAGENT = SLOT_SECONDARY_START + SECONDARY_INPUT_SLOTS;
    public static final int SLOT_COUNT = SLOT_REAGENT + 1;
    public static final int DATA_REQUIRED_SECONDARY = 16;
    public static final int DATA_AVAILABLE_SECONDARY = 17;
    public static final int DATA_TANK_AMOUNT = 18;
    public static final int DATA_TANK_CAPACITY = 19;
    public static final int DATA_TANK_FLUID = 20;
    public static final int DATA_TEMPERATURE_K = 21;
    public static final int DATA_TARGET_TEMPERATURE_K = 22;
    public static final int DATA_MAX_TEMPERATURE_K = 23;
    public static final int DATA_ENERGY_STORED = 24;
    public static final int DATA_ENERGY_CAPACITY = 25;
    public static final int DATA_DRIVE_STATE = 26;
    public static final int DATA_MANUAL_TICKS = 27;
    public static final int DATA_FAULT_DX = 28;
    public static final int DATA_FAULT_DY = 29;
    public static final int DATA_FAULT_DZ = 30;
    public static final int DATA_FAULT_ROLE = 31;
    public static final int DATA_FAULT_META = 32;
    public static final int DATA_OUTPUT_TANK_AMOUNT = 33;
    public static final int DATA_OUTPUT_TANK_FLUID = 34;
    public static final int DATA_CATALYST_MISSING = 35;
    public static final int DATA_REQUIRED_PRESSURE_KPA = 36;
    public static final int DATA_COUNT = 37;
    public static final int AMBIENT_TEMP_K = 293;
    /** Fluid tank size in mB. One bucket is 1000 mB and a spec ingot cast is 144 mB. */
    public static final int TANK_CAPACITY = 8000;
    private static final int LAYOUT_VERSION = 2;
    private static final String HAS_FUEL_SLOT_KEY = "HasFuelSlotV1";
    private static final String LAYOUT_VERSION_KEY = "SlotLayoutVersion";
    private static final String TANK_AMOUNT_KEY = "TankAmount";
    private static final String TANK_FLUID_KEY = "TankFluid";
    private static final String OUTPUT_TANK_AMOUNT_KEY = "OutputTankAmount";
    private static final String OUTPUT_TANK_FLUID_KEY = "OutputTankFluid";
    private static final int[] EMPTY_SLOTS = new int[0];
    private static final int[] MATERIAL_INPUT_SLOTS = createMaterialInputSlots();
    private static final int[] INPUT_SLOTS = createInputSlots();
    private static final int[] OUTPUT_SLOTS = createOutputSlots();
    private static final int[] ALL_AUTOMATION_SLOTS = createAllAutomationSlots();
    private static final int[] AUTOMATION_SLOTS_WITHOUT_FUEL = createAutomationSlotsWithoutFuel();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> activeRecipe == null ? kind().processTicks() : activeRecipe.processTicks();
                case 2 -> redstoneMode.id;
                case 3 -> active ? 1 : 0;
                case 4 -> structureValid ? 1 : 0;
                case 5 -> burnTime;
                case 6 -> Math.max(1, burnTimeTotal);
                case 7 -> gridPowered ? 1 : 0;
                case 8, 9, 10, 11, 12, 13 -> sideModes[index - 8].id;
                case 14 -> selectedRouteIndex;
                case 15 -> currentRouteCount();
                case DATA_REQUIRED_SECONDARY -> activeRecipe == null ? 0 : requiredSecondaryCount(activeRecipe);
                case DATA_AVAILABLE_SECONDARY -> availableSecondaryCount();
                case DATA_TANK_AMOUNT -> tankAmount;
                case DATA_TANK_CAPACITY -> TANK_CAPACITY;
                case DATA_TANK_FLUID -> tank.fluid() == null ? -1 : BuiltInRegistries.FLUID.getId(tank.fluid());
                case DATA_TEMPERATURE_K -> temperatureK;
                case DATA_TARGET_TEMPERATURE_K -> targetTemperatureK();
                case DATA_MAX_TEMPERATURE_K -> currentHeatCeilingK();
                case DATA_ENERGY_STORED -> energyStored;
                case DATA_ENERGY_CAPACITY -> energyCapacity();
                case DATA_DRIVE_STATE -> driveState.id;
                case DATA_MANUAL_TICKS -> manualWorkTicks;
                case DATA_FAULT_DX -> faultDx;
                case DATA_FAULT_DY -> faultDy;
                case DATA_FAULT_DZ -> faultDz;
                case DATA_FAULT_ROLE -> faultRoleId;
                case DATA_FAULT_META -> packFaultMeta(faultWrongBlock, faultMatchedParts, faultTotalParts);
                case DATA_OUTPUT_TANK_AMOUNT -> outputTankAmount;
                case DATA_OUTPUT_TANK_FLUID -> outputTank.fluid() == null ? -1 : BuiltInRegistries.FLUID.getId(outputTank.fluid());
                case DATA_CATALYST_MISSING -> catalystMissing() ? 1 : 0;
                case DATA_REQUIRED_PRESSURE_KPA -> targetPressureKpa();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = Math.max(0, value);
                case 2 -> redstoneMode = RedstoneMode.byId(value);
                case 3 -> active = value != 0;
                case 5 -> burnTime = Math.max(0, value);
                case 6 -> burnTimeTotal = Math.max(0, value);
                case 7 -> gridPowered = value != 0;
                case 8, 9, 10, 11, 12, 13 -> sideModes[index - 8] = SideMode.byId(value);
                case 14 -> selectedRouteIndex = Math.max(0, value);
                case DATA_TEMPERATURE_K -> temperatureK = Math.max(AMBIENT_TEMP_K, value);
                case DATA_ENERGY_STORED -> energyStored = Math.max(0, value);
                case DATA_DRIVE_STATE -> driveState = DriveState.byId(value);
                case DATA_MANUAL_TICKS -> manualWorkTicks = Math.max(0, value);
                case DATA_FAULT_DX -> faultDx = value;
                case DATA_FAULT_DY -> faultDy = value;
                case DATA_FAULT_DZ -> faultDz = value;
                case DATA_FAULT_ROLE -> faultRoleId = Math.max(0, value);
                case DATA_FAULT_META -> {
                    faultWrongBlock = (value & 1) != 0;
                    faultMatchedParts = (value >> 1) & 0x3F;
                    faultTotalParts = (value >> 7) & 0x3F;
                }
                case DATA_OUTPUT_TANK_AMOUNT -> outputTankAmount = Math.max(0, value);
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
    private int progress;
    private int burnTime;
    private int burnTimeTotal;
    private RedstoneMode redstoneMode = RedstoneMode.ALWAYS;
    private boolean active;
    private boolean gridPowered;
    private boolean structureValid = true;
    private boolean assemblySynced;
    private boolean lastAssemblyComplete;
    private boolean panelStateSynced;
    private boolean lastPanelActive;
    private int structureCheckCooldown;
    private int selectedRouteIndex;
    private ItemStack lastRouteInput = ItemStack.EMPTY;
    private String lastRouteFluid = "";
    private ProcessingMachineBlock.Recipe activeRecipe;
    private final SideMode[] sideModes = createDefaultSideModes();
    private final MachineFluidTank tank = new MachineFluidTank(TANK_CAPACITY);
    private final MachineFluidTank outputTank = new MachineFluidTank(TANK_CAPACITY);
    private int tankAmount;
    /** Fluid id currently held in the input tank; empty when the tank is dry. */
    private String tankFluid = "";
    private int outputTankAmount;
    /** Fluid id currently held in the output tank; empty when the tank is dry. */
    private String outputTankFluid = "";
    private int temperatureK = AMBIENT_TEMP_K;
    private int fuelMaxTempK = AMBIENT_TEMP_K;
    private int energyStored;
    private int manualWorkTicks;
    private DriveState driveState = DriveState.IDLE;
    private int faultDx;
    private int faultDy;
    private int faultDz;
    private int faultRoleId;
    private boolean faultWrongBlock;
    private int faultMatchedParts;
    private int faultTotalParts;

    public ProcessingMachineBlockEntity(BlockPos pos, BlockState state) {
        super(EarthOnMinecraft.PROCESSING_MACHINE_BLOCK_ENTITY.get(), pos, state);
        // Pipes and buckets mutate the tanks directly, so mirror them into the synced slots here.
        tank.setListener(this::syncTankState);
        outputTank.setListener(this::syncTankState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProcessingMachineBlockEntity machine) {
        boolean changed = false;
        if (machine.active || machine.gridPowered) {
            changed = true;
        }
        machine.active = false;
        machine.gridPowered = false;

        if (!machine.assemblySynced || machine.structureCheckCooldown-- <= 0) {
            boolean completeStructure = RealitySupportRules.isOperational(level, pos)
                    && MachineMultiblock.isComplete(level, pos, machine.kind());
            if (!machine.assemblySynced || machine.lastAssemblyComplete != completeStructure) {
                MachineMultiblock.syncAssembly(level, pos, machine.kind(), completeStructure);
                machine.assemblySynced = true;
                machine.lastAssemblyComplete = completeStructure;
                machine.panelStateSynced = false;
            }
            MachineMultiblock.refreshProjection(level, pos, machine.kind());
            machine.syncStructureDiagnostics(level, pos, completeStructure);
            if (machine.structureValid != completeStructure) {
                if (!completeStructure) {
                    MachineFeedback.playFault(level, pos);
                }
                machine.structureValid = completeStructure;
                changed = true;
            }
            machine.structureCheckCooldown = 20;
        }
        if (!machine.structureValid) {
            machine.driveState = DriveState.IDLE;
            if (machine.coolStep()) {
                changed = true;
            }
            if (machine.progress != 0) {
                machine.progress = 0;
                changed = true;
            }
            machine.finishTick(level, pos, state, changed);
            return;
        }

        if (machine.rechargeElectricBuffer(level, pos)) {
            changed = true;
        }

        if (!machine.redstoneMode.allows(level.hasNeighborSignal(pos))) {
            machine.driveState = DriveState.IDLE;
            if (machine.tickIdleHeat()) {
                changed = true;
            }
            machine.finishTick(level, pos, state, changed);
            return;
        }

        if (machine.tryExportFluidToNeighbors(level, pos)) {
            changed = true;
        }

        ItemStack input = machine.items.get(SLOT_INPUT);
        List<ProcessingMachineBlock.Recipe> routes = ProcessingMachineBlock.matchingRecipes(machine.kind(), input);
        if (routes.isEmpty() && input.isEmpty() && !machine.tank.isEmpty()) {
            // Fluid-only routes have no primary item, so the tank contents select the route.
            routes = ProcessingMachineBlock.fluidOnlyRecipes(machine.kind(), machine.tank.fluidId());
        }
        machine.syncSelectedRoute(input, routes.size());
        Optional<ProcessingMachineBlock.Recipe> match = routes.isEmpty()
                ? Optional.empty()
                : Optional.of(routes.get(machine.selectedRouteIndex));
        if (match.isEmpty()) {
            machine.driveState = DriveState.IDLE;
            if (machine.tickIdleHeat()) {
                changed = true;
            }
            if (machine.progress != 0) {
                machine.progress = 0;
                changed = true;
            }
            machine.finishTick(level, pos, state, changed);
            return;
        }

        ProcessingMachineBlock.Recipe recipe = match.get();
        ItemStack reagent = machine.items.get(SLOT_REAGENT);
        boolean hasCokeCharge = !reagent.isEmpty() && reagent.getItem() == EarthOnMinecraft.COKE.get();
        boolean missingCatalyst = recipe.hasCatalyst() && !hasRequiredCatalyst(recipe, reagent);
        boolean missingPrimary = recipe.hasItemInput() && input.getCount() < Math.max(1, recipe.primaryCount());
        boolean missingSecondary = recipe.hasSecondaryInput() && !machine.hasSecondaryInputs(recipe);
        if (missingPrimary || missingSecondary || (recipe.requiresCoke() && !hasCokeCharge) || missingCatalyst) {
            if (machine.progress != 0 || machine.activeRecipe != recipe) {
                machine.progress = 0;
                changed = true;
            }
            machine.activeRecipe = recipe;
            machine.driveState = DriveState.IDLE;
            if (machine.tickIdleHeat()) {
                changed = true;
            }
            machine.finishTick(level, pos, state, changed);
            return;
        }
        if (!machine.canFitOutputs(recipe.outputStacks())) {
            machine.activeRecipe = recipe;
            machine.driveState = DriveState.IDLE;
            if (machine.tickIdleHeat()) {
                changed = true;
            }
            machine.finishTick(level, pos, state, changed);
            return;
        }
        if (!machine.hasFluidInputs(recipe)) {
            if (machine.progress != 0) {
                machine.progress = 0;
                changed = true;
            }
            machine.activeRecipe = recipe;
            machine.driveState = DriveState.IDLE;
            if (machine.tickIdleHeat()) {
                changed = true;
            }
            machine.finishTick(level, pos, state, changed);
            return;
        }
        if (!machine.canStoreFluidOutputs(recipe)) {
            machine.activeRecipe = recipe;
            machine.driveState = DriveState.IDLE;
            if (machine.tickIdleHeat()) {
                changed = true;
            }
            machine.finishTick(level, pos, state, changed);
            return;
        }
        GateResult loreGate = LoreIntegration.checkMachine(machine, state.getBlock(), recipe.outputStacks());
        if (loreGate.locked()) {
            LoreMachines.setFault(machine, "lore_locked", loreGate.missing());
            if (machine.progress != 0) {
                machine.progress = 0;
                changed = true;
            }
            machine.activeRecipe = recipe;
            machine.driveState = DriveState.IDLE;
            if (machine.tickIdleHeat()) {
                changed = true;
            }
            machine.finishTick(level, pos, state, changed);
            return;
        }
        LoreMachines.clearFault(machine);

        ProcessingMachineBlock.Kind kind = machine.kind();
        ProcessingMachineBlock.PowerMode powerMode = kind.powerMode();
        int energyCost = recipe.energyPerTickEffective();
        int requiredTempK = recipe.requiredTemperatureK();
        boolean supplied = false;
        switch (powerMode) {
            case PASSIVE -> {
                supplied = true;
                machine.driveState = DriveState.PASSIVE;
                if (machine.coolStep()) {
                    changed = true;
                }
            }
            case MANUAL -> {
                if (machine.coolStep()) {
                    changed = true;
                }
                if (machine.manualWorkTicks > 0) {
                    machine.manualWorkTicks--;
                    supplied = true;
                    machine.driveState = DriveState.MANUAL_ACTIVE;
                    changed = true;
                } else {
                    machine.driveState = DriveState.MANUAL_WAIT;
                }
            }
            case KINETIC -> {
                if (machine.coolStep()) {
                    changed = true;
                }
                if (machine.hasKineticDrive(level, pos)) {
                    supplied = true;
                    machine.driveState = DriveState.KINETIC_ACTIVE;
                } else if (machine.manualWorkTicks > 0) {
                    machine.manualWorkTicks--;
                    supplied = true;
                    machine.driveState = DriveState.MANUAL_ACTIVE;
                    changed = true;
                } else {
                    machine.driveState = DriveState.KINETIC_WAIT;
                }
            }
            case MANUAL_OR_GRID -> {
                if (machine.coolStep()) {
                    changed = true;
                }
                if (machine.consumeElectric(level, pos, Math.max(1, energyCost))) {
                    machine.gridPowered = true;
                    supplied = true;
                    machine.driveState = DriveState.GRID_ACTIVE;
                    changed = true;
                } else if (machine.manualWorkTicks > 0) {
                    machine.manualWorkTicks--;
                    supplied = true;
                    machine.driveState = DriveState.MANUAL_ACTIVE;
                    changed = true;
                } else {
                    machine.driveState = DriveState.MANUAL_WAIT;
                }
            }
            case MANUAL_OR_HEAT -> {
                if (machine.manualWorkTicks > 0) {
                    machine.manualWorkTicks--;
                    supplied = true;
                    machine.driveState = DriveState.MANUAL_ACTIVE;
                    machine.coolStep();
                    changed = true;
                } else {
                    if (machine.burnTime <= 0 && machine.tryConsumeFuel()) {
                        changed = true;
                    }
                    if (machine.burnTime > 0) {
                        machine.burnTime--;
                        int ceiling = Math.min(kind.maxTemperatureK(), Math.max(AMBIENT_TEMP_K, machine.fuelMaxTempK));
                        machine.heatStep(ceiling);
                        supplied = true;
                        machine.driveState = DriveState.THERMAL_ACTIVE;
                        changed = true;
                    } else {
                        if (machine.coolStep()) {
                            changed = true;
                        }
                        machine.driveState = DriveState.MANUAL_WAIT;
                    }
                }
            }
            case GRID_ONLY -> {
                if (machine.consumeElectric(level, pos, Math.max(1, energyCost))) {
                    machine.gridPowered = true;
                    supplied = true;
                    machine.driveState = DriveState.GRID_ACTIVE;
                    if (requiredTempK > AMBIENT_TEMP_K) {
                        machine.heatStep(Math.min(Math.max(kind.maxTemperatureK(), requiredTempK), requiredTempK));
                    } else {
                        machine.coolStep();
                    }
                    changed = true;
                } else {
                    if (machine.coolStep()) {
                        changed = true;
                    }
                    machine.driveState = DriveState.NO_POWER;
                }
            }
            case LOCAL_OR_GRID -> {
                if (machine.coolStep()) {
                    changed = true;
                }
                if (machine.consumeElectric(level, pos, Math.max(1, energyCost))) {
                    machine.gridPowered = true;
                    supplied = true;
                    machine.driveState = DriveState.GRID_ACTIVE;
                    changed = true;
                } else {
                    if (machine.burnTime <= 0 && machine.tryConsumeFuel()) {
                        changed = true;
                    }
                    if (machine.burnTime > 0) {
                        machine.burnTime--;
                        supplied = true;
                        machine.driveState = DriveState.LOCAL_FUEL_ACTIVE;
                        changed = true;
                    } else {
                        machine.driveState = DriveState.NO_POWER;
                    }
                }
            }
            case HEAT_OR_GRID, HEAT_ONLY -> {
                boolean heated = false;
                int ceilingK = AMBIENT_TEMP_K;
                if (powerMode.grid() && machine.consumeElectric(level, pos, Math.max(1, energyCost))) {
                    machine.gridPowered = true;
                    heated = true;
                    ceilingK = kind.maxTemperatureK();
                    changed = true;
                } else if (powerMode.allowsLocalFuel()) {
                    if (machine.burnTime <= 0 && machine.tryConsumeFuel()) {
                        changed = true;
                    }
                    if (machine.burnTime > 0) {
                        machine.burnTime--;
                        heated = true;
                        ceilingK = Math.min(kind.maxTemperatureK(), Math.max(AMBIENT_TEMP_K, machine.fuelMaxTempK));
                        changed = true;
                    }
                }
                if (heated) {
                    if (machine.heatStep(ceilingK)) {
                        changed = true;
                    }
                } else if (machine.coolStep()) {
                    changed = true;
                }
                if (machine.temperatureK >= requiredTempK && (heated || machine.temperatureK > AMBIENT_TEMP_K)) {
                    supplied = true;
                    machine.driveState = machine.gridPowered ? DriveState.GRID_ACTIVE : DriveState.THERMAL_ACTIVE;
                } else if (heated) {
                    supplied = false;
                    machine.driveState = ceilingK < requiredTempK ? DriveState.FUEL_TEMP_LOW : DriveState.HEATING;
                } else {
                    supplied = false;
                    machine.driveState = DriveState.NO_POWER;
                }
            }
        }
        if (!supplied) {
            if (machine.burnTime <= 0 && machine.burnTimeTotal != 0) {
                machine.burnTimeTotal = 0;
                changed = true;
            }
            machine.activeRecipe = recipe;
            machine.finishTick(level, pos, state, changed);
            return;
        }
        if (machine.burnTime <= 0 && machine.burnTimeTotal != 0) {
            machine.burnTimeTotal = 0;
            changed = true;
        }

        machine.active = true;
        machine.activeRecipe = recipe;
        machine.progress++;
        changed = true;

        if (machine.progress >= recipe.processTicks()) {
            ItemStack primarySnapshot = input.copy();
            List<ItemStack> secondarySnapshots = new java.util.ArrayList<>(SECONDARY_INPUT_SLOTS);
            for (int i = 0; i < SECONDARY_INPUT_SLOTS; i++) {
                secondarySnapshots.add(machine.items.get(SLOT_SECONDARY_START + i).copy());
            }
            ItemStack reagentSnapshot = reagent.copy();
            List<ItemStack> evaluatedOutputs = MetallurgyAssay.evaluateMachineOutputs(
                    kind, recipe, primarySnapshot, secondarySnapshots, reagentSnapshot, machine.temperatureK);
            List<ItemStack> cappedOutputs = new java.util.ArrayList<>(evaluatedOutputs.size());
            for (ItemStack rawOut : evaluatedOutputs) {
                ItemStack capped = LoreIntegration.capMachineOutput(machine, rawOut, state.getBlock());
                cappedOutputs.add(capped);
                LoreIntegration.awardMachineProduct(machine, capped);
            }
            evaluatedOutputs = cappedOutputs;
            if (recipe.requiresCoke()) {
                reagent.shrink(1);
                if (reagent.isEmpty()) {
                    machine.items.set(SLOT_REAGENT, ItemStack.EMPTY);
                }
            }
            machine.consumeSecondaryInputs(recipe);
            machine.consumeFluidInputs(recipe);
            if (!input.isEmpty()) {
                input.shrink(recipe.primaryCount());
            }
            if (input.isEmpty()) {
                machine.items.set(SLOT_INPUT, ItemStack.EMPTY);
            }
            machine.insertOutputs(evaluatedOutputs);
            machine.produceFluidOutputs(recipe);
            machine.progress = 0;
            MachineFeedback.playComplete(level, pos, kind);
        }

        machine.finishTick(level, pos, state, changed);
    }

    /**
     * Checks that every secondary input demanded by the route is stocked in the secondary slots.
     * A secondary entry may be satisfied by any slot; counts are matched across slots.
     */
    private boolean hasSecondaryInputs(ProcessingMachineBlock.Recipe recipe) {
        return countAvailable(recipe) >= requiredSecondaryCount(recipe);
    }

    /** True when the tank holds every fluid the route consumes. */
    private boolean hasFluidInputs(ProcessingMachineBlock.Recipe recipe) {
        if (recipe.fluidInputs().isEmpty()) {
            return true;
        }
        String requiredFluid = recipe.fluidInputs().getFirst().fluidId();
        int requiredAmount = 0;
        for (ProcessingMachineBlock.FluidInput fluid : recipe.fluidInputs()) {
            if (!requiredFluid.equals(fluid.fluidId())) {
                return false;
            }
            requiredAmount += fluid.amount();
        }
        return tank.holds(requiredFluid, requiredAmount);
    }

    private static boolean hasRequiredCatalyst(ProcessingMachineBlock.Recipe recipe, ItemStack reagent) {
        if (!recipe.hasCatalyst()) {
            return true;
        }
        return !reagent.isEmpty() && reagent.getItem() == recipe.catalyst().get().asItem();
    }

    public boolean catalystMissing() {
        ProcessingMachineBlock.Recipe recipe = activeRecipe != null ? activeRecipe : selectedRecipe();
        if (recipe == null || !recipe.hasCatalyst()) {
            return false;
        }
        return !hasRequiredCatalyst(recipe, items.get(SLOT_REAGENT));
    }

    public int targetPressureKpa() {
        ProcessingMachineBlock.Recipe recipe = activeRecipe != null ? activeRecipe : selectedRecipe();
        return recipe != null ? recipe.requiredPressureKpa() : 101;
    }

    /**
     * True when the output tank can accept the route's fluid outputs. A route may only add a fluid
     * that matches whatever is already stored in {@code outputTank}, so a full or mismatched tank
     * blocks the process instead of voiding the product.
     */
    private boolean canStoreFluidOutputs(ProcessingMachineBlock.Recipe recipe) {
        if (recipe.fluidOutputs().isEmpty()) {
            return true;
        }
        String stored = outputTank.fluidId();
        int amount = outputTank.amount();
        int room = outputTank.capacity() - amount;
        for (ProcessingMachineBlock.FluidOutput output : recipe.fluidOutputs()) {
            if (stored.isEmpty()) {
                stored = output.fluidId();
            } else if (!stored.equals(output.fluidId())) {
                return false;
            }
            if (output.amount() > room) {
                return false;
            }
            room -= output.amount();
        }
        return true;
    }

    private void consumeFluidInputs(ProcessingMachineBlock.Recipe recipe) {
        for (ProcessingMachineBlock.FluidInput fluid : recipe.fluidInputs()) {
            tank.drain(fluid.fluidId(), fluid.amount());
        }
        syncTankState();
    }

    private void produceFluidOutputs(ProcessingMachineBlock.Recipe recipe) {
        for (ProcessingMachineBlock.FluidOutput output : recipe.fluidOutputs()) {
            outputTank.fill(output.fluidId(), output.amount());
        }
        syncTankState();
    }

    private void syncTankState() {
        this.tankAmount = tank.amount();
        this.tankFluid = tank.fluidId();
        this.outputTankAmount = outputTank.amount();
        this.outputTankFluid = outputTank.fluidId();
        setChanged();
    }

    /**
     * Transfers produced fluid from this machine's output tank (or input tank when holding a
     * produced fluid) into an adjacent machine that consumes it, respecting side modes.
     */
    public boolean tryExportFluidToNeighbors(Level level, BlockPos pos) {
        MachineFluidTank sourceTank = null;
        ProcessingMachineBlock.Kind currentKind = kind();
        if (!outputTank.isEmpty()) {
            sourceTank = outputTank;
        } else if (!tank.isEmpty()) {
            String storedInput = tank.fluidId();
            if (!ProcessingMachineBlock.acceptsFluidInput(currentKind, storedInput)
                    || ProcessingMachineBlock.producesFluidOutput(currentKind, storedInput)) {
                sourceTank = tank;
            }
        }
        if (sourceTank == null || sourceTank.isEmpty()) {
            return false;
        }
        String storedFluid = sourceTank.fluidId();
        boolean movedAny = false;
        for (Direction direction : Direction.values()) {
            if (!sideMode(direction).allowsOutput()) {
                continue;
            }
            if (!(level.getBlockEntity(pos.relative(direction)) instanceof ProcessingMachineBlockEntity neighbor)) {
                continue;
            }
            if (!neighbor.sideMode(direction.getOpposite()).allowsInput()) {
                continue;
            }
            if (!ProcessingMachineBlock.acceptsFluidInput(neighbor.kind(), storedFluid)) {
                continue;
            }
            int room = neighbor.inputTank().capacity() - neighbor.inputTank().amount();
            if (room <= 0) {
                continue;
            }
            int amountToMove = Math.min(sourceTank.amount(), Math.min(576, room));
            int accepted = neighbor.inputTank().fill(storedFluid, amountToMove);
            if (accepted > 0) {
                sourceTank.drain(storedFluid, accepted);
                movedAny = true;
                if (sourceTank.isEmpty()) {
                    break;
                }
            }
        }
        return movedAny;
    }

    /**
     * Handles right-click fluid transfer with vanilla buckets, bottles, gas cells, or molten metal
     * ingots so both inputTank and outputTank are playable without external pipe mods. Drains
     * outputTank first (or inputTank on fallback) when holding an empty container, and fills
     * inputTank when holding a fluid carrier.
     */
    public boolean tryInteractWithFluidItem(net.minecraft.world.entity.player.Player player,
                                            net.minecraft.world.InteractionHand hand,
                                            ItemStack stack) {
        if (stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE)) {
            for (MachineFluidTank targetTank : List.of(outputTank, tank)) {
                if (targetTank.isEmpty()) {
                    continue;
                }
                ItemStack filled = itemForStoredFluid(targetTank.fluidId(), targetTank.amount(), stack.getItem());
                int drainCost = drainCostForStoredFluid(targetTank.fluidId(), stack.getItem());
                if (!filled.isEmpty() && drainCost > 0 && targetTank.amount() >= drainCost) {
                    if (!player.level().isClientSide()) {
                        targetTank.drain(targetTank.fluidId(), drainCost);
                        if (!player.getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                        if (stack.isEmpty()) {
                            player.setItemInHand(hand, filled);
                        } else if (!player.getInventory().add(filled)) {
                            player.drop(filled, false);
                        }
                    }
                    return true;
                }
            }
        }
        String fillFluid = fluidCarriedByItem(stack.getItem());
        int fillAmount = fluidAmountForItem(stack.getItem());
        if (fillFluid != null && fillAmount > 0 && ProcessingMachineBlock.acceptsFluidInput(kind(), fillFluid)) {
            if (tank.isEmpty() || (tank.fluidId().equals(fillFluid) && tank.amount() + fillAmount <= tank.capacity())) {
                if (!player.level().isClientSide()) {
                    tank.fill(fillFluid, fillAmount);
                    Item remainder = containerRemainderForItem(stack.getItem());
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                        if (remainder != null) {
                            ItemStack remStack = new ItemStack(remainder);
                            if (stack.isEmpty()) {
                                player.setItemInHand(hand, remStack);
                            } else if (!player.getInventory().add(remStack)) {
                                player.drop(remStack, false);
                            }
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String fluidCarriedByItem(Item item) {
        if (item == Items.WATER_BUCKET || item == EarthOnMinecraft.SOFTENED_WATER.get()) {
            return "earth_on_minecraft:steam";
        }
        if (item == EarthOnMinecraft.OXYGEN_GAS_CELL.get()) {
            return "earth_on_minecraft:oxygen";
        }
        if (item == EarthOnMinecraft.HYDROGEN_GAS_CELL.get()) {
            return "earth_on_minecraft:hydrogen";
        }
        if (item == EarthOnMinecraft.NITROGEN_GAS_CELL.get()) {
            return "earth_on_minecraft:nitrogen";
        }
        if (item == EarthOnMinecraft.CHLORINE_GAS_CELL.get()) {
            return "earth_on_minecraft:chlorine";
        }
        if (item == EarthOnMinecraft.CARBON_DIOXIDE_CELL.get()) {
            return "earth_on_minecraft:carbon_dioxide";
        }
        if (item == EarthOnMinecraft.CARBON_MONOXIDE_CELL.get()) {
            return "earth_on_minecraft:carbon_monoxide";
        }
        if (item == EarthOnMinecraft.SULFUR_DIOXIDE_CELL.get()) {
            return "earth_on_minecraft:sulfur_dioxide";
        }
        if (item == EarthOnMinecraft.URANIUM_HEXAFLUORIDE_CELL.get()) {
            return "earth_on_minecraft:uranium_hexafluoride";
        }
        if (item == EarthOnMinecraft.CRUDE_OIL_SAMPLE.get()) {
            return "earth_on_minecraft:crude_oil";
        }
        if (item == EarthOnMinecraft.TAILINGS_DUST.get()) {
            return "earth_on_minecraft:slurry";
        }
        return null;
    }

    private static int fluidAmountForItem(Item item) {
        if (item == Items.WATER_BUCKET) {
            return 1000;
        }
        if (item == EarthOnMinecraft.TAILINGS_DUST.get()) {
            return 288;
        }
        return 500;
    }

    @Nullable
    private static Item containerRemainderForItem(Item item) {
        return item == Items.WATER_BUCKET ? Items.BUCKET : null;
    }

    private static ItemStack itemForStoredFluid(String fluidId, int amount, Item container) {
        if (container == Items.BUCKET) {
            if (amount >= 144) {
                switch (fluidId) {
                    case "earth_on_minecraft:molten_iron" -> {
                        return new ItemStack(Items.IRON_INGOT);
                    }
                    case "earth_on_minecraft:molten_copper", "earth_on_minecraft:molten_blister_copper" -> {
                        return new ItemStack(Items.COPPER_INGOT);
                    }
                    default -> {
                    }
                }
            }
            if (amount >= 250 && "earth_on_minecraft:spent_electrolyte".equals(fluidId)) {
                return new ItemStack(EarthOnMinecraft.ELECTROLYTE.get());
            }
            if (amount >= 500 && "earth_on_minecraft:steam".equals(fluidId)) {
                return new ItemStack(EarthOnMinecraft.SOFTENED_WATER.get());
            }
        }
        if (container == Items.GLASS_BOTTLE) {
            if (amount >= 250 && "earth_on_minecraft:spent_electrolyte".equals(fluidId)) {
                return new ItemStack(EarthOnMinecraft.ELECTROLYTE.get());
            }
            if (amount >= 500) {
                return switch (fluidId) {
                    case "earth_on_minecraft:oxygen" -> new ItemStack(EarthOnMinecraft.OXYGEN_GAS_CELL.get());
                    case "earth_on_minecraft:hydrogen" -> new ItemStack(EarthOnMinecraft.HYDROGEN_GAS_CELL.get());
                    case "earth_on_minecraft:nitrogen" -> new ItemStack(EarthOnMinecraft.NITROGEN_GAS_CELL.get());
                    case "earth_on_minecraft:chlorine" -> new ItemStack(EarthOnMinecraft.CHLORINE_GAS_CELL.get());
                    case "earth_on_minecraft:carbon_dioxide" -> new ItemStack(EarthOnMinecraft.CARBON_DIOXIDE_CELL.get());
                    case "earth_on_minecraft:carbon_monoxide" -> new ItemStack(EarthOnMinecraft.CARBON_MONOXIDE_CELL.get());
                    case "earth_on_minecraft:sulfur_dioxide" -> new ItemStack(EarthOnMinecraft.SULFUR_DIOXIDE_CELL.get());
                    case "earth_on_minecraft:steam" -> new ItemStack(EarthOnMinecraft.SOFTENED_WATER.get());
                    default -> ItemStack.EMPTY;
                };
            }
        }
        return ItemStack.EMPTY;
    }

    private static int drainCostForStoredFluid(String fluidId, Item container) {
        if (container == Items.BUCKET) {
            return switch (fluidId) {
                case "earth_on_minecraft:molten_iron", "earth_on_minecraft:molten_copper",
                     "earth_on_minecraft:molten_blister_copper" -> 144;
                case "earth_on_minecraft:spent_electrolyte" -> 250;
                case "earth_on_minecraft:steam" -> 500;
                default -> 0;
            };
        }
        if (container == Items.GLASS_BOTTLE) {
            return switch (fluidId) {
                case "earth_on_minecraft:spent_electrolyte" -> 250;
                case "earth_on_minecraft:oxygen", "earth_on_minecraft:hydrogen",
                     "earth_on_minecraft:nitrogen", "earth_on_minecraft:chlorine",
                     "earth_on_minecraft:carbon_dioxide", "earth_on_minecraft:carbon_monoxide",
                     "earth_on_minecraft:sulfur_dioxide", "earth_on_minecraft:steam" -> 500;
                default -> 0;
            };
        }
        return 0;
    }

    public MachineFluidTank tank() {
        return tank;
    }

    public MachineFluidTank inputTank() {
        return tank;
    }

    public MachineFluidTank outputTank() {
        return outputTank;
    }

    public int tankAmount() {
        return tank.amount();
    }

    public String tankFluidId() {
        return tank.fluidId();
    }

    public int outputTankAmount() {
        return outputTank.amount();
    }

    public String outputTankFluidId() {
        return outputTank.fluidId();
    }

    private int requiredSecondaryCount(ProcessingMachineBlock.Recipe recipe) {
        int required = 0;
        for (ProcessingMachineBlock.Input input : recipe.secondaryInputs()) {
            required += Math.max(1, input.count());
        }
        return required;
    }

    private int availableSecondaryCount() {
        ProcessingMachineBlock.Recipe recipe = selectedRecipe();
        return recipe == null ? 0 : matchedSecondaryCount(recipe);
    }

    private int countAvailable(ProcessingMachineBlock.Recipe recipe) {
        int matched = matchedSecondaryCount(recipe);
        return matched < requiredSecondaryCount(recipe) ? -1 : matched;
    }

    private int matchedSecondaryCount(ProcessingMachineBlock.Recipe recipe) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(SECONDARY_INPUT_SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SECONDARY_INPUT_SLOTS; i++) {
            remaining.set(i, items.get(SLOT_SECONDARY_START + i).copy());
        }
        int matched = 0;
        for (ProcessingMachineBlock.Input input : recipe.secondaryInputs()) {
            int need = Math.max(1, input.count());
            for (int slot = 0; slot < remaining.size() && need > 0; slot++) {
                ItemStack stack = remaining.get(slot);
                if (!stack.isEmpty() && stack.getItem() == input.item().get().asItem()) {
                    int taken = Math.min(need, stack.getCount());
                    stack.shrink(taken);
                    need -= taken;
                }
            }
            matched += Math.max(1, input.count()) - need;
        }
        return matched;
    }

    private void consumeSecondaryInputs(ProcessingMachineBlock.Recipe recipe) {
        for (ProcessingMachineBlock.Input input : recipe.secondaryInputs()) {
            int remaining = Math.max(1, input.count());
            for (int slot = SLOT_SECONDARY_START; slot < SLOT_SECONDARY_START + SECONDARY_INPUT_SLOTS && remaining > 0; slot++) {
                ItemStack stack = items.get(slot);
                if (stack.isEmpty() || stack.getItem() != input.item().get().asItem()) {
                    continue;
                }
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
                if (stack.isEmpty()) {
                    items.set(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    public ProcessingMachineBlock.Kind kind() {
        if (getBlockState().getBlock() instanceof ProcessingMachineBlock block) {
            return block.kind();
        }
        return ProcessingMachineBlock.Kind.CRUSHER;
    }

    public ContainerData data() {
        return data;
    }

    public RedstoneMode redstoneMode() {
        return redstoneMode;
    }

    public boolean structureValid() {
        return structureValid;
    }

    public boolean gridPowered() {
        return gridPowered;
    }

    public int selectedRouteIndex() {
        return selectedRouteIndex;
    }

    public int currentRouteCount() {
        ItemStack input = items.get(SLOT_INPUT);
        return input.isEmpty()
                ? ProcessingMachineBlock.fluidOnlyRecipes(kind(), tank.fluidId()).size()
                : ProcessingMachineBlock.matchingRecipes(kind(), input).size();
    }

    public ProcessingMachineBlock.Recipe selectedRecipe() {
        List<ProcessingMachineBlock.Recipe> routes = currentRecipes();
        if (routes.isEmpty()) {
            return null;
        }
        return routes.get(Math.floorMod(selectedRouteIndex, routes.size()));
    }

    private List<ProcessingMachineBlock.Recipe> currentRecipes() {
        ItemStack input = items.get(SLOT_INPUT);
        return input.isEmpty()
                ? ProcessingMachineBlock.fluidOnlyRecipes(kind(), tank.fluidId())
                : ProcessingMachineBlock.matchingRecipes(kind(), input);
    }

    public int energyPerTick() {
        return activeRecipe != null ? activeRecipe.energyPerTickEffective() : kind().energyPerTick();
    }

    public int temperatureK() {
        return temperatureK;
    }

    public int targetTemperatureK() {
        ProcessingMachineBlock.Recipe r = activeRecipe != null ? activeRecipe : selectedRecipe();
        return r != null ? r.requiredTemperatureK() : kind().requiredTemperatureK();
    }

    public int currentHeatCeilingK() {
        if (gridPowered && kind().powerMode().grid()) {
            return Math.max(AMBIENT_TEMP_K, kind().maxTemperatureK());
        }
        if (burnTime > 0 && fuelMaxTempK > AMBIENT_TEMP_K) {
            return Math.min(kind().maxTemperatureK(), fuelMaxTempK);
        }
        ItemStack fuel = items.get(SLOT_FUEL);
        int fuelCap = getFuelMaxTemperatureK(fuel);
        if (fuelCap > 0) {
            return Math.min(kind().maxTemperatureK(), fuelCap);
        }
        return Math.max(AMBIENT_TEMP_K, kind().maxTemperatureK());
    }

    public int energyStored() {
        return energyStored;
    }

    public int energyCapacity() {
        return kind().energyBufferCapacity();
    }

    public DriveState driveState() {
        return driveState;
    }

    public int manualWorkTicks() {
        return manualWorkTicks;
    }

    public void triggerManualWork(int ticks) {
        if (!kind().powerMode().manualFallback()) {
            return;
        }
        this.manualWorkTicks = Math.min(160, Math.max(0, this.manualWorkTicks) + Math.max(1, ticks));
        setChanged();
    }

    public boolean hasKineticDrive(Level level, BlockPos pos) {
        if (isKineticDriveItem(items.get(SLOT_FUEL))) {
            return true;
        }
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (level.getFluidState(neighbor).is(net.minecraft.tags.FluidTags.WATER)) {
                return true;
            }
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.getBlock() == EarthOnMinecraft.CONVEYOR_BELT.get()) {
                return true;
            }
        }
        return pos.getY() >= 80 && level.canSeeSky(pos.above());
    }

    public static boolean isKineticDriveItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null || !EarthOnMinecraft.MODID.equals(key.getNamespace())) {
            return false;
        }
        return switch (key.getPath()) {
            case "waterwheel", "water_power", "wind", "wheel", "servo_motor", "conveyor_drive" -> true;
            default -> false;
        };
    }

    public void cycleRedstoneMode() {
        this.redstoneMode = this.redstoneMode.next();
        setChanged();
    }

    public void setRedstoneMode(RedstoneMode mode) {
        if (this.redstoneMode != mode) {
            this.redstoneMode = mode;
            setChanged();
        }
    }

    public void cycleSideMode(Direction side) {
        int index = side.ordinal();
        this.sideModes[index] = this.sideModes[index].next();
        setChanged();
    }

    public void cycleSelectedRoute() {
        int routeCount = currentRouteCount();
        if (routeCount <= 1) {
            return;
        }
        this.selectedRouteIndex = (this.selectedRouteIndex + 1) % routeCount;
        this.progress = 0;
        setChanged();
    }

    public SideMode sideMode(Direction side) {
        if (side == null) {
            return SideMode.BOTH;
        }
        return sideModes[side.ordinal()];
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_INPUT) {
            return ProcessingMachineBlock.acceptsInput(kind(), stack);
        }
        if (isSecondaryInputSlot(slot)) {
            return acceptsSecondaryInput(stack);
        }
        if (slot == SLOT_REAGENT) {
            return (kind() == ProcessingMachineBlock.Kind.REDUCTION_FURNACE
                    && stack.getItem() == EarthOnMinecraft.COKE.get())
                    || ProcessingMachineBlock.acceptsCatalyst(kind(), stack);
        }
        if (slot == SLOT_FUEL) {
            if (kind().acceptsLocalFuel() && getFuelTicks(stack) > 0) {
                return true;
            }
            return kind().powerMode() == ProcessingMachineBlock.PowerMode.KINETIC && isKineticDriveItem(stack);
        }
        return false;
    }

    /**
     * Secondary slots accept any item that some route of this machine consumes as a secondary
     * input. Coke stays reserved for the dedicated reagent slot so the two never compete.
     */
    public boolean acceptsSecondaryInput(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() == EarthOnMinecraft.COKE.get()) {
            return false;
        }
        for (ProcessingMachineBlock.Recipe recipe : ProcessingMachineBlock.recipesFor(kind())) {
            for (ProcessingMachineBlock.Input input : recipe.secondaryInputs()) {
                if (stack.getItem() == input.item().get().asItem()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isSecondaryInputSlot(int slot) {
        return slot >= SLOT_SECONDARY_START && slot < SLOT_SECONDARY_START + SECONDARY_INPUT_SLOTS;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (sideMode(side)) {
            case INPUT -> kind().acceptsLocalFuel() ? INPUT_SLOTS : MATERIAL_INPUT_SLOTS;
            case OUTPUT -> OUTPUT_SLOTS;
            case BOTH -> kind().acceptsLocalFuel() ? ALL_AUTOMATION_SLOTS : AUTOMATION_SLOTS_WITHOUT_FUEL;
            case OFF -> EMPTY_SLOTS;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return sideMode(direction).allowsInput() && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return sideMode(direction).allowsOutput() && isOutputSlot(slot);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(kind().displayNameKey());
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
        return new ProcessingMachineMenu(containerId, inventory, this, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        boolean hasFuelSlot = input.getBooleanOr(HAS_FUEL_SLOT_KEY, false);
        int layoutVersion = input.getIntOr(LAYOUT_VERSION_KEY, 1);
        this.items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        NonNullList<ItemStack> loaded = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, loaded);
        if (!hasFuelSlot) {
            migrateLegacyOutputSlots(loaded);
            layoutVersion = 1;
        }
        if (layoutVersion < LAYOUT_VERSION) {
            migrateReagentSlot(loaded);
        }
        this.items = loaded;
        this.progress = input.getIntOr("Progress", 0);
        this.burnTime = input.getIntOr("BurnTime", 0);
        this.burnTimeTotal = input.getIntOr("BurnTimeTotal", burnTime);
        this.temperatureK = Math.max(AMBIENT_TEMP_K, input.getIntOr("TemperatureK", AMBIENT_TEMP_K));
        this.fuelMaxTempK = Math.max(AMBIENT_TEMP_K, input.getIntOr("FuelMaxTempK", AMBIENT_TEMP_K));
        this.energyStored = Math.max(0, input.getIntOr("EnergyStored", 0));
        this.manualWorkTicks = Math.max(0, input.getIntOr("ManualWorkTicks", 0));
        this.redstoneMode = RedstoneMode.byId(input.getIntOr("RedstoneMode", 0));
        this.selectedRouteIndex = Math.max(0, input.getIntOr("SelectedRouteIndex", 0));
        for (int i = 0; i < sideModes.length; i++) {
            this.sideModes[i] = SideMode.byId(input.getIntOr("SideMode" + i, SideMode.BOTH.id));
        }
        tank.set(input.getStringOr(TANK_FLUID_KEY, ""), input.getIntOr(TANK_AMOUNT_KEY, 0));
        outputTank.set(input.getStringOr(OUTPUT_TANK_FLUID_KEY, ""), input.getIntOr(OUTPUT_TANK_AMOUNT_KEY, 0));
        this.tankAmount = tank.amount();
        this.tankFluid = tank.fluidId();
        this.outputTankAmount = outputTank.amount();
        this.outputTankFluid = outputTank.fluidId();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putBoolean(HAS_FUEL_SLOT_KEY, true);
        output.putInt(LAYOUT_VERSION_KEY, LAYOUT_VERSION);
        output.putInt("Progress", this.progress);
        output.putInt("BurnTime", this.burnTime);
        output.putInt("BurnTimeTotal", this.burnTimeTotal);
        output.putInt("TemperatureK", this.temperatureK);
        output.putInt("FuelMaxTempK", this.fuelMaxTempK);
        output.putInt("EnergyStored", this.energyStored);
        output.putInt("ManualWorkTicks", this.manualWorkTicks);
        output.putInt("RedstoneMode", this.redstoneMode.id);
        output.putInt("SelectedRouteIndex", this.selectedRouteIndex);
        for (int i = 0; i < sideModes.length; i++) {
            output.putInt("SideMode" + i, this.sideModes[i].id);
        }
        output.putString(TANK_FLUID_KEY, tank.fluidId());
        output.putInt(TANK_AMOUNT_KEY, tank.amount());
        output.putString(OUTPUT_TANK_FLUID_KEY, outputTank.fluidId());
        output.putInt(OUTPUT_TANK_AMOUNT_KEY, outputTank.amount());
    }

    private void migrateLegacyOutputSlots(NonNullList<ItemStack> loaded) {
        for (int i = SLOT_OUTPUT_START + OUTPUT_SLOT_COUNT - 1; i >= SLOT_OUTPUT_START; i--) {
            loaded.set(i, loaded.get(i - 1));
        }
        loaded.set(SLOT_FUEL, ItemStack.EMPTY);
    }

    /**
     * Pre-v2 saves stored the coke reagent directly after the output row. In v2 that same index
     * is the first secondary input slot, so shift it into the new reagent position.
     */
    private void migrateReagentSlot(NonNullList<ItemStack> loaded) {
        int legacyReagentSlot = SLOT_OUTPUT_START + OUTPUT_SLOT_COUNT;
        ItemStack legacy = loaded.get(legacyReagentSlot);
        if (!legacy.isEmpty()) {
            loaded.set(legacyReagentSlot, ItemStack.EMPTY);
            loaded.set(SLOT_REAGENT, legacy);
        }
    }

    public static int getFuelTicks(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        if (item == EarthOnMinecraft.COAL_DUST.get().asItem()) {
            return 1200;
        }
        if (item == EarthOnMinecraft.WOOD_CHIPS.get().asItem()) {
            return 300;
        }
        if (item == EarthOnMinecraft.COKE.get().asItem()) {
            return 2400;
        }
        if (item == EarthOnMinecraft.PETROLEUM_COKE.get().asItem()) {
            return 3200;
        }
        if (item == EarthOnMinecraft.COAL_GAS_CELL.get().asItem() || item == EarthOnMinecraft.NATURAL_GAS_CELL.get().asItem()) {
            return 1800;
        }
        if (item == Items.COAL || item == Items.CHARCOAL) {
            return 1600;
        }
        if (item == Items.COAL_BLOCK) {
            return 16000;
        }
        if (item == Items.DRIED_KELP_BLOCK) {
            return 4000;
        }
        if (item == Items.BLAZE_ROD) {
            return 2400;
        }
        if (item == Items.LAVA_BUCKET) {
            return 20000;
        }
        return 0;
    }

    public static int getFuelMaxTemperatureK(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        if (item == EarthOnMinecraft.WOOD_CHIPS.get().asItem() || item == Items.DRIED_KELP_BLOCK) {
            return 900;
        }
        if (item == EarthOnMinecraft.COAL_DUST.get().asItem()) {
            return 1400;
        }
        if (item == Items.COAL || item == Items.CHARCOAL || item == Items.COAL_BLOCK) {
            return 1550;
        }
        if (item == EarthOnMinecraft.COAL_GAS_CELL.get().asItem() || item == EarthOnMinecraft.NATURAL_GAS_CELL.get().asItem()) {
            return 1750;
        }
        if (item == EarthOnMinecraft.COKE.get().asItem()
                || item == EarthOnMinecraft.PETROLEUM_COKE.get().asItem()
                || item == Items.BLAZE_ROD
                || item == Items.LAVA_BUCKET) {
            return 2100;
        }
        return 0;
    }

    private boolean tryConsumeFuel() {
        if (!kind().acceptsLocalFuel()) {
            return false;
        }
        ItemStack fuel = this.items.get(SLOT_FUEL);
        int ticks = getFuelTicks(fuel);
        if (ticks <= 0) {
            return false;
        }
        int maxTemp = Math.max(AMBIENT_TEMP_K, getFuelMaxTemperatureK(fuel));
        Item item = fuel.getItem();
        fuel.shrink(1);
        if (fuel.isEmpty()) {
            this.items.set(SLOT_FUEL, item == Items.LAVA_BUCKET ? new ItemStack(Items.BUCKET) : ItemStack.EMPTY);
        }
        this.burnTime = ticks;
        this.burnTimeTotal = ticks;
        this.fuelMaxTempK = maxTemp;
        return true;
    }

    private boolean rechargeElectricBuffer(Level level, BlockPos pos) {
        int cap = energyCapacity();
        if (cap <= 0 || energyStored >= cap) {
            return false;
        }
        int pull = Math.min(cap - energyStored, Math.max(64, kind().energyPerTick() * 4));
        int received = EnergyNetwork.extractUpTo(level, pos, pull);
        if (received > 0) {
            energyStored += received;
            return true;
        }
        return false;
    }

    private boolean consumeElectric(Level level, BlockPos pos, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (energyStored < amount) {
            int pulled = EnergyNetwork.extractUpTo(level, pos, Math.max(amount - energyStored, Math.min(energyCapacity() - energyStored, amount * 2)));
            energyStored += pulled;
        }
        if (energyStored >= amount) {
            energyStored -= amount;
            return true;
        }
        return EnergyNetwork.consume(level, pos, amount);
    }

    private boolean heatStep(int ceilingK) {
        int target = Math.max(AMBIENT_TEMP_K, ceilingK);
        if (temperatureK < target) {
            temperatureK = Math.min(target, temperatureK + 20);
            return true;
        }
        if (temperatureK > target) {
            temperatureK = Math.max(target, temperatureK - 4);
            return true;
        }
        return false;
    }

    private boolean coolStep() {
        if (temperatureK > AMBIENT_TEMP_K) {
            temperatureK = Math.max(AMBIENT_TEMP_K, temperatureK - 4);
            return true;
        }
        return false;
    }

    private boolean tickIdleHeat() {
        if (burnTime > 0) {
            burnTime--;
            if (burnTime <= 0) {
                burnTimeTotal = 0;
            }
            heatStep(Math.min(kind().maxTemperatureK(), Math.max(AMBIENT_TEMP_K, fuelMaxTempK)));
            return true;
        }
        return coolStep();
    }

    private void syncStructureDiagnostics(Level level, BlockPos pos, boolean complete) {
        if (complete || MachineMultiblock.patternFor(kind()) == MachineMultiblock.Pattern.NONE) {
            this.faultDx = 0;
            this.faultDy = 0;
            this.faultDz = 0;
            this.faultRoleId = 0;
            this.faultWrongBlock = false;
            this.faultMatchedParts = 0;
            this.faultTotalParts = 0;
            return;
        }
        MachineMultiblock.diagnose(level, pos, kind()).ifPresentOrElse(diag -> {
            this.faultDx = diag.dx();
            this.faultDy = diag.dy();
            this.faultDz = diag.dz();
            this.faultRoleId = diag.expectedRole().id();
            this.faultWrongBlock = diag.wrongBlock();
            this.faultMatchedParts = Math.min(63, diag.matchedParts());
            this.faultTotalParts = Math.min(63, diag.totalParts());
        }, () -> {
            this.faultRoleId = 0;
            this.faultWrongBlock = false;
        });
    }

    private static int packFaultMeta(boolean wrongBlock, int matched, int total) {
        return (wrongBlock ? 1 : 0)
                | ((Math.min(63, Math.max(0, matched)) & 0x3F) << 1)
                | ((Math.min(63, Math.max(0, total)) & 0x3F) << 7);
    }

    private boolean canFitOutputs(List<ItemStack> outputs) {
        NonNullList<ItemStack> simulated = NonNullList.withSize(OUTPUT_SLOT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < OUTPUT_SLOT_COUNT; i++) {
            simulated.set(i, this.items.get(SLOT_OUTPUT_START + i).copy());
        }

        for (ItemStack output : outputs) {
            if (!fitInto(simulated, output.copy())) {
                return false;
            }
        }
        return true;
    }

    private void syncSelectedRoute(ItemStack input, int routeCount) {
        boolean inputChanged = !ItemStack.isSameItemSameComponents(input, lastRouteInput);
        boolean fluidChanged = !tank.fluidId().equals(lastRouteFluid);
        if (inputChanged || fluidChanged) {
            this.lastRouteInput = input.isEmpty() ? ItemStack.EMPTY : input.copyWithCount(1);
            this.lastRouteFluid = tank.fluidId();
            this.selectedRouteIndex = 0;
            this.progress = 0;
        }
        if (routeCount <= 0) {
            this.selectedRouteIndex = 0;
            return;
        }
        if (this.selectedRouteIndex >= routeCount) {
            this.selectedRouteIndex = routeCount - 1;
        }
    }

    private void insertOutputs(List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            fitInto(this.items, output.copy(), SLOT_OUTPUT_START, SLOT_COUNT);
        }
    }

    private static boolean fitInto(NonNullList<ItemStack> stacks, ItemStack output) {
        return fitInto(stacks, output, 0, stacks.size());
    }

    private static boolean fitInto(NonNullList<ItemStack> stacks, ItemStack output, int start, int end) {
        for (int i = start; i < end && !output.isEmpty(); i++) {
            ItemStack existing = stacks.get(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, output)) {
                int move = Math.min(output.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (move > 0) {
                    existing.grow(move);
                    output.shrink(move);
                }
            }
        }
        for (int i = start; i < end && !output.isEmpty(); i++) {
            if (stacks.get(i).isEmpty()) {
                int move = Math.min(output.getCount(), output.getMaxStackSize());
                stacks.set(i, output.copyWithCount(move));
                output.shrink(move);
            }
        }
        return output.isEmpty();
    }

    private void setChangedIfNeeded(boolean changed) {
        if (changed) {
            setChanged();
        }
    }

    private void finishTick(Level level, BlockPos pos, BlockState state, boolean changed) {
        if (syncVisualState(level, pos, state)) {
            changed = true;
        }
        boolean panelActive = structureValid && active;
        if (!panelStateSynced || lastPanelActive != panelActive) {
            MachineMultiblock.syncPanelActive(level, pos, kind(), panelActive);
            panelStateSynced = true;
            lastPanelActive = panelActive;
        }
        setChangedIfNeeded(changed);
    }

    private boolean syncVisualState(Level level, BlockPos pos, BlockState state) {
        BlockState updated = state;
        if (updated.hasProperty(ProcessingMachineBlock.ACTIVE)
                && updated.getValue(ProcessingMachineBlock.ACTIVE) != active) {
            updated = updated.setValue(ProcessingMachineBlock.ACTIVE, active);
        }
        boolean fault = !structureValid;
        if (updated.hasProperty(ProcessingMachineBlock.FAULT)
                && updated.getValue(ProcessingMachineBlock.FAULT) != fault) {
            updated = updated.setValue(ProcessingMachineBlock.FAULT, fault);
        }
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
            return true;
        }
        return false;
    }

    private static SideMode[] createDefaultSideModes() {
        SideMode[] modes = new SideMode[Direction.values().length];
        for (int i = 0; i < modes.length; i++) {
            modes[i] = SideMode.BOTH;
        }
        return modes;
    }

    private static int[] createOutputSlots() {
        int[] slots = new int[OUTPUT_SLOT_COUNT];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = SLOT_OUTPUT_START + i;
        }
        return slots;
    }

    private static int[] createMaterialInputSlots() {
        int[] slots = new int[1 + SECONDARY_INPUT_SLOTS + 1];
        int index = 0;
        slots[index++] = SLOT_INPUT;
        for (int i = 0; i < SECONDARY_INPUT_SLOTS; i++) {
            slots[index++] = SLOT_SECONDARY_START + i;
        }
        slots[index] = SLOT_REAGENT;
        return slots;
    }

    private static int[] createInputSlots() {
        int[] slots = new int[3 + SECONDARY_INPUT_SLOTS];
        int index = 0;
        slots[index++] = SLOT_INPUT;
        slots[index++] = SLOT_FUEL;
        for (int i = 0; i < SECONDARY_INPUT_SLOTS; i++) {
            slots[index++] = SLOT_SECONDARY_START + i;
        }
        slots[index] = SLOT_REAGENT;
        return slots;
    }

    private static int[] createAllAutomationSlots() {
        int[] slots = new int[SLOT_COUNT];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    private static int[] createAutomationSlotsWithoutFuel() {
        int[] slots = new int[SLOT_COUNT - 1];
        int index = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (slot != SLOT_FUEL) {
                slots[index++] = slot;
            }
        }
        return slots;
    }

    private static boolean isOutputSlot(int slot) {
        return slot >= SLOT_OUTPUT_START && slot < SLOT_OUTPUT_START + OUTPUT_SLOT_COUNT;
    }

    public enum SideMode {
        INPUT(0, "screen.earth_on_minecraft.side.input", "screen.earth_on_minecraft.side.input.tooltip"),
        OUTPUT(1, "screen.earth_on_minecraft.side.output", "screen.earth_on_minecraft.side.output.tooltip"),
        BOTH(2, "screen.earth_on_minecraft.side.both", "screen.earth_on_minecraft.side.both.tooltip"),
        OFF(3, "screen.earth_on_minecraft.side.off", "screen.earth_on_minecraft.side.off.tooltip");

        private final int id;
        private final String labelKey;
        private final String tooltipKey;

        SideMode(int id, String labelKey, String tooltipKey) {
            this.id = id;
            this.labelKey = labelKey;
            this.tooltipKey = tooltipKey;
        }

        public static SideMode byId(int id) {
            for (SideMode mode : values()) {
                if (mode.id == id) {
                    return mode;
                }
            }
            return BOTH;
        }

        public SideMode next() {
            return byId((id + 1) % values().length);
        }

        public boolean allowsInput() {
            return this == INPUT || this == BOTH;
        }

        public boolean allowsOutput() {
            return this == OUTPUT || this == BOTH;
        }

        public String labelKey() {
            return labelKey;
        }

        public String tooltipKey() {
            return tooltipKey;
        }
    }

    public enum RedstoneMode {
        ALWAYS(0, "screen.earth_on_minecraft.redstone.always", "screen.earth_on_minecraft.redstone.always.desc"),
        REQUIRE_SIGNAL(1, "screen.earth_on_minecraft.redstone.require_signal", "screen.earth_on_minecraft.redstone.require_signal.desc"),
        REQUIRE_NO_SIGNAL(2, "screen.earth_on_minecraft.redstone.require_no_signal", "screen.earth_on_minecraft.redstone.require_no_signal.desc");

        private final int id;
        private final String labelKey;
        private final String descriptionKey;

        RedstoneMode(int id, String labelKey, String descriptionKey) {
            this.id = id;
            this.labelKey = labelKey;
            this.descriptionKey = descriptionKey;
        }

        public static RedstoneMode byId(int id) {
            for (RedstoneMode mode : values()) {
                if (mode.id == id) {
                    return mode;
                }
            }
            return ALWAYS;
        }

        public RedstoneMode next() {
            return byId((id + 1) % values().length);
        }

        public boolean allows(boolean powered) {
            return switch (this) {
                case ALWAYS -> true;
                case REQUIRE_SIGNAL -> powered;
                case REQUIRE_NO_SIGNAL -> !powered;
            };
        }

        public String labelKey() {
            return labelKey;
        }

        public String descriptionKey() {
            return descriptionKey;
        }
    }

    public enum DriveState {
        IDLE(0, "screen.earth_on_minecraft.machine.drive.state.idle"),
        PASSIVE(1, "screen.earth_on_minecraft.machine.drive.state.passive"),
        MANUAL_WAIT(2, "screen.earth_on_minecraft.machine.drive.state.manual_wait"),
        MANUAL_ACTIVE(3, "screen.earth_on_minecraft.machine.drive.state.manual_active"),
        KINETIC_WAIT(4, "screen.earth_on_minecraft.machine.drive.state.kinetic_wait"),
        KINETIC_ACTIVE(5, "screen.earth_on_minecraft.machine.drive.state.kinetic_active"),
        HEATING(6, "screen.earth_on_minecraft.machine.drive.state.heating"),
        FUEL_TEMP_LOW(7, "screen.earth_on_minecraft.machine.drive.state.fuel_temp_low"),
        THERMAL_ACTIVE(8, "screen.earth_on_minecraft.machine.drive.state.thermal_active"),
        GRID_ACTIVE(9, "screen.earth_on_minecraft.machine.drive.state.grid_active"),
        LOCAL_FUEL_ACTIVE(10, "screen.earth_on_minecraft.machine.drive.state.local_fuel_active"),
        NO_POWER(11, "screen.earth_on_minecraft.machine.drive.state.no_power");

        private final int id;
        private final String labelKey;

        DriveState(int id, String labelKey) {
            this.id = id;
            this.labelKey = labelKey;
        }

        public int id() {
            return id;
        }

        public String labelKey() {
            return labelKey;
        }

        public static DriveState byId(int id) {
            for (DriveState state : values()) {
                if (state.id == id) {
                    return state;
                }
            }
            return IDLE;
        }
    }
}
