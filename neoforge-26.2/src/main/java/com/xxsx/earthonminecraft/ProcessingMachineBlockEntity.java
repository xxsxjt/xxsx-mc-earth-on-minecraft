package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
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
    public static final int SLOT_COUNT = SLOT_OUTPUT_START + OUTPUT_SLOT_COUNT;
    public static final int DATA_COUNT = 16;
    private static final String HAS_FUEL_SLOT_KEY = "HasFuelSlotV1";
    private static final int[] EMPTY_SLOTS = new int[0];
    private static final int[] MATERIAL_INPUT_SLOTS = {SLOT_INPUT};
    private static final int[] INPUT_SLOTS = {SLOT_INPUT, SLOT_FUEL};
    private static final int[] OUTPUT_SLOTS = createOutputSlots();
    private static final int[] ALL_AUTOMATION_SLOTS = createAllAutomationSlots();
    private static final int[] AUTOMATION_SLOTS_WITHOUT_FUEL = createAutomationSlotsWithoutFuel();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> kind().processTicks();
                case 2 -> redstoneMode.id;
                case 3 -> active ? 1 : 0;
                case 4 -> structureValid ? 1 : 0;
                case 5 -> burnTime;
                case 6 -> Math.max(1, burnTimeTotal);
                case 7 -> gridPowered ? 1 : 0;
                case 8, 9, 10, 11, 12, 13 -> sideModes[index - 8].id;
                case 14 -> selectedRouteIndex;
                case 15 -> currentRouteCount();
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
    private final SideMode[] sideModes = createDefaultSideModes();

    public ProcessingMachineBlockEntity(BlockPos pos, BlockState state) {
        super(EarthOnMinecraft.PROCESSING_MACHINE_BLOCK_ENTITY.get(), pos, state);
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

        ItemStack input = machine.items.get(SLOT_INPUT);
        List<ProcessingMachineBlock.Recipe> routes = ProcessingMachineBlock.matchingRecipes(machine.kind(), input);
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
        if (!machine.canFitOutputs(recipe.outputStacks())) {
            machine.finishTick(level, pos, state, changed);
            return;
        }

        ProcessingMachineBlock.Kind kind = machine.kind();
        if (EnergyNetwork.consume(level, pos, kind.energyPerTick())) {
            machine.gridPowered = true;
        } else if (kind.acceptsLocalFuel()) {
            if (machine.burnTime <= 0 && machine.tryConsumeFuel()) {
                changed = true;
            }
            if (machine.burnTime <= 0) {
                machine.burnTimeTotal = 0;
                machine.finishTick(level, pos, state, changed);
                return;
            }
            machine.burnTime--;
        } else {
            if (machine.burnTime != 0 || machine.burnTimeTotal != 0) {
                machine.burnTime = 0;
                machine.burnTimeTotal = 0;
                changed = true;
            }
            machine.finishTick(level, pos, state, changed);
            return;
        }

        machine.active = true;
        machine.progress++;
        changed = true;

        if (machine.progress >= kind.processTicks()) {
            input.shrink(1);
            if (input.isEmpty()) {
                machine.items.set(SLOT_INPUT, ItemStack.EMPTY);
            }
            machine.insertOutputs(recipe.outputStacks());
            machine.progress = 0;
            MachineFeedback.playComplete(level, pos, kind);
        }

        machine.finishTick(level, pos, state, changed);
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
        return ProcessingMachineBlock.matchingRecipes(kind(), items.get(SLOT_INPUT)).size();
    }

    public int energyPerTick() {
        return kind().energyPerTick();
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
            return ProcessingMachineBlock.findRecipe(kind(), stack).isPresent();
        }
        return slot == SLOT_FUEL && kind().acceptsLocalFuel() && getFuelTicks(stack) > 0;
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
        this.items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        if (!hasFuelSlot) {
            migrateLegacyOutputSlots();
        }
        this.progress = input.getIntOr("Progress", 0);
        this.burnTime = input.getIntOr("BurnTime", 0);
        this.burnTimeTotal = input.getIntOr("BurnTimeTotal", burnTime);
        this.redstoneMode = RedstoneMode.byId(input.getIntOr("RedstoneMode", 0));
        this.selectedRouteIndex = Math.max(0, input.getIntOr("SelectedRouteIndex", 0));
        for (int i = 0; i < sideModes.length; i++) {
            this.sideModes[i] = SideMode.byId(input.getIntOr("SideMode" + i, SideMode.BOTH.id));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putBoolean(HAS_FUEL_SLOT_KEY, true);
        output.putInt("Progress", this.progress);
        output.putInt("BurnTime", this.burnTime);
        output.putInt("BurnTimeTotal", this.burnTimeTotal);
        output.putInt("RedstoneMode", this.redstoneMode.id);
        output.putInt("SelectedRouteIndex", this.selectedRouteIndex);
        for (int i = 0; i < sideModes.length; i++) {
            output.putInt("SideMode" + i, this.sideModes[i].id);
        }
    }

    private void migrateLegacyOutputSlots() {
        for (int i = SLOT_COUNT - 1; i >= SLOT_OUTPUT_START; i--) {
            this.items.set(i, this.items.get(i - 1));
        }
        this.items.set(SLOT_FUEL, ItemStack.EMPTY);
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
        if (!ItemStack.isSameItemSameComponents(input, lastRouteInput)) {
            this.lastRouteInput = input.isEmpty() ? ItemStack.EMPTY : input.copyWithCount(1);
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

    private static int[] createAllAutomationSlots() {
        int[] slots = new int[SLOT_COUNT];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    private static int[] createAutomationSlotsWithoutFuel() {
        int[] slots = new int[SLOT_COUNT - 1];
        slots[0] = SLOT_INPUT;
        for (int i = 1; i < slots.length; i++) {
            slots[i] = SLOT_OUTPUT_START + i - 1;
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
