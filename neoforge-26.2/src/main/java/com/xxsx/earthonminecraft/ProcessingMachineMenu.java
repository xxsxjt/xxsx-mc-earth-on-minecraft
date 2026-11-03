package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ProcessingMachineMenu extends AbstractContainerMenu {
    public static final int BUTTON_REDSTONE_ALWAYS = 0;
    public static final int BUTTON_REDSTONE_REQUIRE_SIGNAL = 1;
    public static final int BUTTON_REDSTONE_REQUIRE_NO_SIGNAL = 2;
    public static final int BUTTON_ROUTE_NEXT = 10;
    public static final int BUTTON_SIDE_BASE = 20;
    private static final int PLAYER_INV_START = ProcessingMachineBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INV_END = PLAYER_INV_START + 27;
    private static final int HOTBAR_END = PLAYER_INV_END + 9;
    private static final int MENU_SECONDARY_START = 2;
    private static final int MENU_SECONDARY_END = 6;
    private static final int MENU_OUTPUT_START = 6;
    private static final int MENU_REAGENT = 13;
    private static final int MACHINE_MENU_END = 14;

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;
    private final ProcessingMachineBlock.Kind kind;

    public ProcessingMachineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    private ProcessingMachineMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, new SimpleContainer(ProcessingMachineBlockEntity.SLOT_COUNT),
                new SimpleContainerData(ProcessingMachineBlockEntity.DATA_COUNT), pos,
                kindFromClientLevel(inventory, pos));
    }

    public ProcessingMachineMenu(int containerId, Inventory inventory, ProcessingMachineBlockEntity machine, ContainerData data) {
        this(containerId, inventory, machine, data, machine.getBlockPos(), machine.kind());
    }

    private ProcessingMachineMenu(int containerId, Inventory inventory, Container container, ContainerData data,
                                  BlockPos pos, ProcessingMachineBlock.Kind kind) {
        super(EarthOnMinecraft.PROCESSING_MACHINE_MENU.get(), containerId);
        checkContainerSize(container, ProcessingMachineBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, ProcessingMachineBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.pos = pos;
        this.kind = kind;

        this.container.startOpen(inventory.player);
        addSlot(new Slot(container, ProcessingMachineBlockEntity.SLOT_INPUT, 38, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ProcessingMachineBlock.acceptsInput(ProcessingMachineMenu.this.kind, stack);
            }
        });
        addSlot(new Slot(container, ProcessingMachineBlockEntity.SLOT_FUEL, 38, 57) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ProcessingMachineMenu.this.kind.acceptsLocalFuel()
                        && ProcessingMachineBlockEntity.getFuelTicks(stack) > 0;
            }
        });
        int[][] secondarySlots = {{8, 76}, {26, 76}, {44, 76}, {62, 76}};
        for (int i = 0; i < secondarySlots.length; i++) {
            int slot = ProcessingMachineBlockEntity.SLOT_SECONDARY_START + i;
            addSlot(new Slot(container, slot, secondarySlots[i][0], secondarySlots[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return ProcessingMachineMenu.this.acceptsSecondaryInput(stack);
                }
            });
        }
        int[][] outputSlots = {
                {102, 20}, {120, 20}, {138, 20}, {156, 20},
                {111, 42}, {129, 42}, {147, 42}
        };
        for (int i = 0; i < outputSlots.length; i++) {
            int slot = ProcessingMachineBlockEntity.SLOT_OUTPUT_START + i;
            addSlot(new OutputSlot(container, slot, outputSlots[i][0], outputSlots[i][1]));
        }
        addSlot(new Slot(container, ProcessingMachineBlockEntity.SLOT_REAGENT, 80, 76) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ProcessingMachineMenu.this.kind == ProcessingMachineBlock.Kind.REDUCTION_FURNACE
                        && stack.getItem() == EarthOnMinecraft.COKE.get();
            }
        });

        addStandardInventorySlots(inventory, 8, 104);
        addDataSlots(data);
    }

    private boolean acceptsSecondaryInput(ItemStack stack) {
        if (container instanceof ProcessingMachineBlockEntity machine) {
            return machine.acceptsSecondaryInput(stack);
        }
        for (ProcessingMachineBlock.Recipe recipe : ProcessingMachineBlock.recipesFor(this.kind)) {
            for (ProcessingMachineBlock.Input input : recipe.secondaryInputs()) {
                if (!stack.isEmpty() && stack.getItem() == input.item().get().asItem()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (container instanceof ProcessingMachineBlockEntity machine && id >= BUTTON_REDSTONE_ALWAYS && id <= BUTTON_REDSTONE_REQUIRE_NO_SIGNAL) {
            machine.setRedstoneMode(ProcessingMachineBlockEntity.RedstoneMode.byId(id));
            return true;
        }
        if (container instanceof ProcessingMachineBlockEntity machine && id == BUTTON_ROUTE_NEXT) {
            machine.cycleSelectedRoute();
            return true;
        }
        if (container instanceof ProcessingMachineBlockEntity machine
                && id >= BUTTON_SIDE_BASE
                && id < BUTTON_SIDE_BASE + Direction.values().length) {
            machine.cycleSideMode(Direction.values()[id - BUTTON_SIDE_BASE]);
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        moved = stack.copy();

        if (index < MACHINE_MENU_END) {
            if (!moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean movedToMachine = false;
            boolean cokeReagentRoute = this.kind == ProcessingMachineBlock.Kind.REDUCTION_FURNACE
                    && stack.getItem() == EarthOnMinecraft.COKE.get()
                    && selectedRecipe().map(ProcessingMachineBlock.Recipe::requiresCoke).orElse(false);
            if (cokeReagentRoute) {
                movedToMachine = moveItemStackTo(stack, MENU_REAGENT, MENU_REAGENT + 1, false);
            } else if (ProcessingMachineBlock.acceptsInput(this.kind, stack)) {
                movedToMachine = moveItemStackTo(stack, 0, 1, false);
            } else if (acceptsSecondaryInput(stack)) {
                movedToMachine = moveItemStackTo(stack, MENU_SECONDARY_START, MENU_SECONDARY_END, false);
            } else if (this.kind.acceptsLocalFuel() && ProcessingMachineBlockEntity.getFuelTicks(stack) > 0) {
                movedToMachine = moveItemStackTo(stack, 1, 2, false);
            }
            if (movedToMachine) {
                // The required coke route uses the dedicated reagent slot; other coke recipes
                // are accepted as ordinary secondary inputs by the branch above.
            } else if (index < PLAYER_INV_START + 27) {
                if (!moveItemStackTo(stack, PLAYER_INV_START + 27, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_START + 27, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    public ProcessingMachineBlock.Kind kind() {
        return kind;
    }

    public BlockPos pos() {
        return pos;
    }

    public int progress() {
        return data.get(0);
    }

    public int maxProgress() {
        return Math.max(1, data.get(1));
    }

    public ProcessingMachineBlockEntity.RedstoneMode redstoneMode() {
        return ProcessingMachineBlockEntity.RedstoneMode.byId(data.get(2));
    }

    public boolean active() {
        return data.get(3) != 0;
    }

    public boolean structureValid() {
        return data.get(4) != 0;
    }

    public int burnTime() {
        return data.get(5);
    }

    public int burnTimeTotal() {
        return Math.max(1, data.get(6));
    }

    public boolean hasBurningFuel() {
        return burnTime() > 0;
    }

    public boolean gridPowered() {
        return data.get(7) != 0;
    }

    public int selectedRouteIndex() {
        return Math.max(0, data.get(14));
    }

    public int routeCount() {
        return Math.max(0, data.get(15));
    }

    public java.util.Optional<ProcessingMachineBlock.Recipe> selectedRecipe() {
        ItemStack input = getSlot(0).getItem();
        if (!input.isEmpty()) {
            return ProcessingMachineBlock.findRecipe(kind, input, selectedRouteIndex());
        }
        int fluidId = data.get(ProcessingMachineBlockEntity.DATA_TANK_FLUID);
        var fluid = fluidId < 0 ? null : BuiltInRegistries.FLUID.byId(fluidId);
        if (fluid == null) {
            return java.util.Optional.empty();
        }
        var id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) {
            return java.util.Optional.empty();
        }
        List<ProcessingMachineBlock.Recipe> routes = ProcessingMachineBlock.fluidOnlyRecipes(kind, id.toString());
        return routes.isEmpty() ? java.util.Optional.empty()
                : java.util.Optional.of(routes.get(Math.floorMod(selectedRouteIndex(), routes.size())));
    }

    public boolean outputsBlocked() {
        return selectedRecipe().map(recipe -> !canFitOutputs(recipe.outputStacks())).orElse(false);
    }

    /** Fluid required by the selected route, in mB. */
    public int requiredFluidAmount() {
        return selectedRecipe()
                .map(recipe -> recipe.fluidInputs().stream()
                        .mapToInt(ProcessingMachineBlock.FluidInput::amount).sum())
                .orElse(0);
    }

    public int tankAmount() {
        return data.get(ProcessingMachineBlockEntity.DATA_TANK_AMOUNT);
    }

    public int tankCapacity() {
        return Math.max(1, data.get(ProcessingMachineBlockEntity.DATA_TANK_CAPACITY));
    }

    /** True when the selected route needs more fluid than the tank currently holds. */
    public boolean fluidInputsMissing() {
        return selectedRecipe().map(recipe -> {
            int available = tankAmount();
            for (ProcessingMachineBlock.FluidInput fluid : recipe.fluidInputs()) {
                if (!tankHolds(fluid)) {
                    return true;
                }
                available -= fluid.amount();
            }
            return available < 0;
        }).orElse(false);
    }

    /**
     * The menu does not know the tank's fluid id, so it compares required amounts only; the
     * block entity performs the exact id check before starting a route.
     */
    private boolean tankHolds(ProcessingMachineBlock.FluidInput fluid) {
        int fluidId = data.get(ProcessingMachineBlockEntity.DATA_TANK_FLUID);
        var actual = fluidId < 0 ? null : BuiltInRegistries.FLUID.byId(fluidId);
        var actualId = actual == null ? null : BuiltInRegistries.FLUID.getKey(actual);
        return actualId != null && actualId.toString().equals(fluid.fluidId())
                && tankAmount() >= fluid.amount();
    }

    public boolean usesFluid() {
        return selectedRecipe().map(ProcessingMachineBlock.Recipe::usesFluid).orElse(false);
    }

    public int energyPerTick() {
        return selectedRecipe()
                .map(ProcessingMachineBlock.Recipe::energyPerTickEffective)
                .orElse(kind.energyPerTick());
    }

    public boolean acceptsLocalFuel() {
        return kind.acceptsLocalFuel();
    }

    /**
     * True when the selected route cannot run without grid power or a burning fire. Passive and
     * manual/kinetic routes report false so the screen does not warn about missing power.
     */
    public boolean needsExternalPower() {
        if (kind.powerMode().passive()) {
            return false;
        }
        if (energyPerTick() > 0) {
            return true;
        }
        return kind.powerMode().requiresFuel();
    }

    public ProcessingMachineBlock.PowerMode powerMode() {
        return kind.powerMode();
    }

    public ProcessingMachineBlock.ProcessFamily processFamily() {
        return kind.processFamily();
    }

    public ProcessingMachineBlockEntity.SideMode sideMode(Direction side) {
        return ProcessingMachineBlockEntity.SideMode.byId(data.get(8 + side.ordinal()));
    }

    public int requiredSecondaryCount() {
        return data.get(ProcessingMachineBlockEntity.DATA_REQUIRED_SECONDARY);
    }

    public int availableSecondaryCount() {
        return data.get(ProcessingMachineBlockEntity.DATA_AVAILABLE_SECONDARY);
    }

    public boolean secondaryInputsMissing() {
        int required = requiredSecondaryCount();
        return required > 0 && availableSecondaryCount() < required;
    }

    public boolean hasSecondaryInputs() {
        return requiredSecondaryCount() > 0;
    }

    private boolean canFitOutputs(List<ItemStack> outputs) {
        List<ItemStack> simulated = new ArrayList<>(ProcessingMachineBlockEntity.OUTPUT_SLOT_COUNT);
        for (int i = 0; i < ProcessingMachineBlockEntity.OUTPUT_SLOT_COUNT; i++) {
            simulated.add(getSlot(MENU_OUTPUT_START + i).getItem().copy());
        }
        for (ItemStack output : outputs) {
            if (!fitInto(simulated, output.copy())) {
                return false;
            }
        }
        return true;
    }

    private static boolean fitInto(List<ItemStack> stacks, ItemStack output) {
        for (ItemStack existing : stacks) {
            if (!output.isEmpty() && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, output)) {
                int move = Math.min(output.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(move);
                output.shrink(move);
            }
        }
        for (int i = 0; i < stacks.size() && !output.isEmpty(); i++) {
            if (stacks.get(i).isEmpty()) {
                int move = Math.min(output.getCount(), output.getMaxStackSize());
                stacks.set(i, output.copyWithCount(move));
                output.shrink(move);
            }
        }
        return output.isEmpty();
    }

    private static ProcessingMachineBlock.Kind kindFromClientLevel(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockState(pos).getBlock() instanceof ProcessingMachineBlock block) {
            return block.kind();
        }
        return ProcessingMachineBlock.Kind.CRUSHER;
    }

    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
