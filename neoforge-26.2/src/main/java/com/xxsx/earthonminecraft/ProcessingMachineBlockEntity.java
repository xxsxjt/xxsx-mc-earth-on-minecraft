package com.xxsx.earthonminecraft;

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
    public static final int DATA_COUNT = 21;
    public static final int DATA_REQUIRED_SECONDARY = 16;
    public static final int DATA_AVAILABLE_SECONDARY = 17;
    public static final int DATA_TANK_AMOUNT = 18;
    public static final int DATA_TANK_CAPACITY = 19;
    public static final int DATA_TANK_FLUID = 20;
    /** Fluid tank size in mB. One bucket is 1000 mB and a spec ingot cast is 144 mB. */
    public static final int TANK_CAPACITY = 8000;
    private static final int LAYOUT_VERSION = 2;
    private static final String HAS_FUEL_SLOT_KEY = "HasFuelSlotV1";
    private static final String LAYOUT_VERSION_KEY = "SlotLayoutVersion";
    private static final String TANK_AMOUNT_KEY = "TankAmount";
    private static final String TANK_FLUID_KEY = "TankFluid";
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
    private int tankAmount;
    /** Fluid id currently held in the tank; empty when the tank is dry. */
    private String tankFluid = "";

    public ProcessingMachineBlockEntity(BlockPos pos, BlockState state) {
        super(EarthOnMinecraft.PROCESSING_MACHINE_BLOCK_ENTITY.get(), pos, state);
        // Pipes and buckets mutate the tank directly, so mirror it into the synced slots here.
        tank.setListener(this::syncTankState);
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
            if (machine.progress != 0) {
                machine.progress = 0;
                changed = true;
            }
            machine.finishTick(level, pos, state, changed);
            return;
        }

        if (!machine.redstoneMode.allows(level.hasNeighborSignal(pos))) {
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
        boolean missingPrimary = recipe.hasItemInput() && input.getCount() < Math.max(1, recipe.primaryCount());
        boolean missingSecondary = recipe.hasSecondaryInput() && !machine.hasSecondaryInputs(recipe);
        if (missingPrimary || missingSecondary || recipe.requiresCoke() && !hasCokeCharge) {
            if (machine.progress != 0 || machine.activeRecipe != recipe) {
                machine.progress = 0;
                changed = true;
            }
            machine.activeRecipe = recipe;
            machine.finishTick(level, pos, state, changed);
            return;
        }
        if (!machine.canFitOutputs(recipe.outputStacks())) {
            machine.activeRecipe = recipe;
            machine.finishTick(level, pos, state, changed);
            return;
        }
        if (!machine.hasFluidInputs(recipe)) {
            if (machine.progress != 0) {
                machine.progress = 0;
                changed = true;
            }
            machine.activeRecipe = recipe;
            machine.finishTick(level, pos, state, changed);
            return;
        }
        if (!machine.canStoreFluidOutputs(recipe)) {
            machine.activeRecipe = recipe;
            machine.finishTick(level, pos, state, changed);
            return;
        }

        ProcessingMachineBlock.Kind kind = machine.kind();
        ProcessingMachineBlock.PowerMode powerMode = kind.powerMode();
        int energyCost = recipe.energyPerTickEffective();
        boolean supplied = false;
        if (powerMode.passive()) {
            // Ambient process: fermentation and curing run without fuel or grid.
            supplied = true;
        } else if (energyCost > 0 && powerMode.grid() && EnergyNetwork.consume(level, pos, energyCost)) {
            machine.gridPowered = true;
            supplied = true;
        }
        if (!supplied && powerMode.manualFallback()) {
            // Hand, crank, water wheel or animal drive. Checked before the fuel fallback so a manual
            // station never burns fuel it does not need.
            supplied = true;
        }
        if (!supplied && energyCost > 0 && powerMode.allowsLocalFuel()) {
            // Local fuel is the fallback for a grid-capable machine that has no grid connection.
            if (machine.burnTime <= 0 && machine.tryConsumeFuel()) {
                changed = true;
            }
            if (machine.burnTime > 0) {
                machine.burnTime--;
                supplied = true;
            }
        }
        if (!supplied && powerMode.requiresFuel()) {
            // Heat process: keep the fire going even when the route draws no electricity.
            if (machine.burnTime <= 0 && machine.tryConsumeFuel()) {
                changed = true;
            }
            if (machine.burnTime > 0) {
                machine.burnTime--;
                supplied = true;
            }
        }
        if (!supplied && energyCost <= 0 && !powerMode.requiresFuel()) {
            // A route that declares no electric draw and needs no fire runs without a supply.
            supplied = true;
        }
        if (!supplied) {
            if (machine.burnTime != 0 || machine.burnTimeTotal != 0) {
                machine.burnTime = 0;
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
            machine.insertOutputs(recipe.outputStacks());
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

    /**
     * True when the tank can accept the route's fluid outputs. A route may only add a fluid that
     * matches whatever is already stored, so a full or mismatched tank blocks the process instead
     * of voiding the product.
     */
    private boolean canStoreFluidOutputs(ProcessingMachineBlock.Recipe recipe) {
        if (recipe.fluidOutputs().isEmpty()) {
            return true;
        }
        // A single tank can only hold one fluid, so every output must agree with the stored fluid
        // (or with the first output if the tank is empty) and must fit in the remaining room.
        String stored = tank.fluidId();
        int amount = tank.amount();
        String consumedFluid = "";
        int consumedAmount = 0;
        for (ProcessingMachineBlock.FluidInput input : recipe.fluidInputs()) {
            if (!consumedFluid.isEmpty() && !consumedFluid.equals(input.fluidId())) {
                return false;
            }
            consumedFluid = input.fluidId();
            consumedAmount += input.amount();
        }
        if (consumedAmount > 0) {
            if (!stored.equals(consumedFluid) || amount < consumedAmount) {
                return false;
            }
            amount -= consumedAmount;
        }
        if (amount == 0) {
            stored = "";
        }
        int room = tank.capacity() - amount;
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
            tank.fill(output.fluidId(), output.amount());
        }
        syncTankState();
    }

    private void syncTankState() {
        this.tankAmount = tank.amount();
        this.tankFluid = tank.fluidId();
        setChanged();
    }

    /**
     * Transfers produced fluid from this machine's tank into an adjacent machine that consumes it,
     * respecting both machines' side configuration modes.
     */
    public boolean tryExportFluidToNeighbors(Level level, BlockPos pos) {
        if (tank.isEmpty()) {
            return false;
        }
        String storedFluid = tank.fluidId();
        ProcessingMachineBlock.Kind currentKind = kind();
        if (ProcessingMachineBlock.acceptsFluidInput(currentKind, storedFluid)
                && !ProcessingMachineBlock.producesFluidOutput(currentKind, storedFluid)) {
            return false;
        }
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
            int room = neighbor.tank().capacity() - neighbor.tank().amount();
            if (room <= 0) {
                continue;
            }
            int amountToMove = Math.min(tank.amount(), Math.min(576, room));
            int accepted = neighbor.tank().fill(storedFluid, amountToMove);
            if (accepted > 0) {
                tank.drain(storedFluid, accepted);
                movedAny = true;
                if (tank.isEmpty()) {
                    break;
                }
            }
        }
        return movedAny;
    }

    /**
     * Handles right-click fluid transfer with vanilla buckets, gas cells, or molten metal ingots
     * so every fluid route is playable without external pipe mods.
     */
    public boolean tryInteractWithFluidItem(net.minecraft.world.entity.player.Player player,
                                            net.minecraft.world.InteractionHand hand,
                                            ItemStack stack) {
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
        if ((stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE)) && !tank.isEmpty()) {
            ItemStack filled = itemForStoredFluid(tank.fluidId(), tank.amount(), stack.getItem());
            int drainCost = drainCostForStoredFluid(tank.fluidId(), stack.getItem());
            if (!filled.isEmpty() && drainCost > 0 && tank.amount() >= drainCost) {
                if (!player.level().isClientSide()) {
                    tank.drain(tank.fluidId(), drainCost);
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
        if (container == Items.BUCKET && amount >= 144) {
            return switch (fluidId) {
                case "earth_on_minecraft:molten_iron" -> new ItemStack(Items.IRON_INGOT);
                case "earth_on_minecraft:molten_copper" -> new ItemStack(Items.COPPER_INGOT);
                default -> ItemStack.EMPTY;
            };
        }
        return ItemStack.EMPTY;
    }

    private static int drainCostForStoredFluid(String fluidId, Item container) {
        if (container == Items.BUCKET) {
            return switch (fluidId) {
                case "earth_on_minecraft:molten_iron", "earth_on_minecraft:molten_copper" -> 144;
                default -> 0;
            };
        }
        return 0;
    }
    public MachineFluidTank tank() {
        return tank;
    }

    public int tankAmount() {
        return tank.amount();
    }

    public String tankFluidId() {
        return tank.fluidId();
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
            return kind() == ProcessingMachineBlock.Kind.REDUCTION_FURNACE
                    && stack.getItem() == EarthOnMinecraft.COKE.get();
        }
        return slot == SLOT_FUEL && kind().acceptsLocalFuel() && getFuelTicks(stack) > 0;
    }

    /**
     * Secondary slots accept any item that some route of this machine consumes as a secondary
     * input. Coke stays reserved for the dedicated reagent slot so the two never compete.
     */
    public boolean acceptsSecondaryInput(ItemStack stack) {
        if (stack.isEmpty()) {
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
        this.redstoneMode = RedstoneMode.byId(input.getIntOr("RedstoneMode", 0));
        this.selectedRouteIndex = Math.max(0, input.getIntOr("SelectedRouteIndex", 0));
        for (int i = 0; i < sideModes.length; i++) {
            this.sideModes[i] = SideMode.byId(input.getIntOr("SideMode" + i, SideMode.BOTH.id));
        }
        tank.set(input.getStringOr(TANK_FLUID_KEY, ""), input.getIntOr(TANK_AMOUNT_KEY, 0));
        this.tankAmount = tank.amount();
        this.tankFluid = tank.fluidId();
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
        output.putInt("RedstoneMode", this.redstoneMode.id);
        output.putInt("SelectedRouteIndex", this.selectedRouteIndex);
        for (int i = 0; i < sideModes.length; i++) {
            output.putInt("SideMode" + i, this.sideModes[i].id);
        }
        output.putString(TANK_FLUID_KEY, tank.fluidId());
        output.putInt(TANK_AMOUNT_KEY, tank.amount());
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

    private boolean tryConsumeFuel() {
        if (!kind().acceptsLocalFuel()) {
            return false;
        }
        ItemStack fuel = this.items.get(SLOT_FUEL);
        int ticks = getFuelTicks(fuel);
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
}
