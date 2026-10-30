package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class MachineInterfaceBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int EXPORT_INTERVAL_TICKS = 10;
    private static final int[] INPUT_SLOTS = createInputSlots();
    private static final int[] OUTPUT_SLOTS = createOutputSlots();

    private final MachineInterfaceBlock.InterfaceType interfaceType;
    private int exportCooldown;

    public MachineInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(EarthOnMinecraft.MACHINE_INTERFACE_BLOCK_ENTITY.get(), pos, state);
        this.interfaceType = state.getBlock() instanceof MachineInterfaceBlock block
                ? block.interfaceType()
                : MachineInterfaceBlock.InterfaceType.INPUT;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MachineInterfaceBlockEntity interfaceBlock) {
        if (interfaceBlock.interfaceType != MachineInterfaceBlock.InterfaceType.OUTPUT) {
            return;
        }
        interfaceBlock.exportCooldown++;
        if (interfaceBlock.exportCooldown < EXPORT_INTERVAL_TICKS) {
            return;
        }
        interfaceBlock.exportCooldown = 0;
        interfaceBlock.exportOneItem();
    }

    @Override
    public int getContainerSize() {
        return target().map(ProcessingMachineBlockEntity::getContainerSize).orElse(0);
    }

    @Override
    public boolean isEmpty() {
        return target().map(ProcessingMachineBlockEntity::isEmpty).orElse(true);
    }

    @Override
    public ItemStack getItem(int slot) {
        return target().map(machine -> machine.getItem(slot)).orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (interfaceType != MachineInterfaceBlock.InterfaceType.OUTPUT || !isOutputSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return target().map(machine -> machine.removeItem(slot, amount)).orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (interfaceType != MachineInterfaceBlock.InterfaceType.OUTPUT || !isOutputSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return target().map(machine -> machine.removeItemNoUpdate(slot)).orElse(ItemStack.EMPTY);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        target().ifPresent(machine -> {
            if (interfaceType == MachineInterfaceBlock.InterfaceType.INPUT && isInputSlot(slot) && machine.canPlaceItem(slot, stack)) {
                machine.setItem(slot, stack);
            }
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return target().map(machine -> machine.stillValid(player)).orElse(false);
    }

    @Override
    public void clearContent() {
        // Interface blocks do not own inventory; clearing them must not wipe the controller.
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return interfaceType == MachineInterfaceBlock.InterfaceType.INPUT ? INPUT_SLOTS : OUTPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return interfaceType == MachineInterfaceBlock.InterfaceType.INPUT
                && isInputSlot(slot)
                && target().map(machine -> machine.canPlaceItem(slot, stack)).orElse(false);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return interfaceType == MachineInterfaceBlock.InterfaceType.OUTPUT && isOutputSlot(slot);
    }

    private Optional<ProcessingMachineBlockEntity> target() {
        if (level == null) {
            return Optional.empty();
        }
        return MachineMultiblock.findControllerForPart(level, worldPosition)
                .map(level::getBlockEntity)
                .filter(ProcessingMachineBlockEntity.class::isInstance)
                .map(ProcessingMachineBlockEntity.class::cast);
    }

    private void exportOneItem() {
        if (level == null) {
            return;
        }
        Optional<ProcessingMachineBlockEntity> machine = target();
        if (machine.isEmpty()) {
            return;
        }
        ProcessingMachineBlockEntity targetMachine = machine.get();
        for (int slot : OUTPUT_SLOTS) {
            ItemStack stack = targetMachine.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (trySendToAdjacentTransport(targetMachine.getBlockPos(), stack)) {
                targetMachine.removeItem(slot, 1);
                targetMachine.setChanged();
                return;
            }
        }
    }

    private boolean trySendToAdjacentTransport(BlockPos controller, ItemStack stack) {
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = worldPosition.relative(direction);
            if (targetPos.equals(controller)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(targetPos);
            if (blockEntity instanceof ConveyorBeltBlockEntity conveyor && conveyor.receiveCargo(stack)) {
                return true;
            }
        }
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = worldPosition.relative(direction);
            if (targetPos.equals(controller)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(targetPos);
            if (blockEntity instanceof WorldlyContainer worldly
                    && !(blockEntity instanceof ProcessingMachineBlockEntity)
                    && insertIntoWorldly(worldly, direction.getOpposite(), stack)) {
                return true;
            }
            if (blockEntity instanceof Container container
                    && !(blockEntity instanceof ConveyorBeltBlockEntity)
                    && !(blockEntity instanceof ProcessingMachineBlockEntity)
                    && insertIntoContainer(container, stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean insertIntoWorldly(WorldlyContainer container, Direction side, ItemStack stack) {
        for (int slot : container.getSlotsForFace(side)) {
            if (container.canPlaceItemThroughFace(slot, stack, side) && insertIntoSlot(container, slot, stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean insertIntoContainer(Container container, ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.canPlaceItem(slot, stack) && insertIntoSlot(container, slot, stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean insertIntoSlot(Container container, int slot, ItemStack stack) {
        ItemStack existing = container.getItem(slot);
        if (existing.isEmpty()) {
            container.setItem(slot, stack.copyWithCount(1));
            container.setChanged();
            return true;
        }
        if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
            ItemStack merged = existing.copy();
            merged.grow(1);
            container.setItem(slot, merged);
            container.setChanged();
            return true;
        }
        return false;
    }

    private static int[] createInputSlots() {
        int[] slots = new int[3 + ProcessingMachineBlockEntity.SECONDARY_INPUT_SLOTS];
        int index = 0;
        slots[index++] = ProcessingMachineBlockEntity.SLOT_INPUT;
        slots[index++] = ProcessingMachineBlockEntity.SLOT_FUEL;
        for (int i = 0; i < ProcessingMachineBlockEntity.SECONDARY_INPUT_SLOTS; i++) {
            slots[index++] = ProcessingMachineBlockEntity.SLOT_SECONDARY_START + i;
        }
        slots[index] = ProcessingMachineBlockEntity.SLOT_REAGENT;
        return slots;
    }

    private static int[] createOutputSlots() {
        int[] slots = new int[ProcessingMachineBlockEntity.OUTPUT_SLOT_COUNT];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = ProcessingMachineBlockEntity.SLOT_OUTPUT_START + i;
        }
        return slots;
    }

    private static boolean isInputSlot(int slot) {
        return slot == ProcessingMachineBlockEntity.SLOT_INPUT
                || slot == ProcessingMachineBlockEntity.SLOT_FUEL
                || slot == ProcessingMachineBlockEntity.SLOT_REAGENT
                || ProcessingMachineBlockEntity.isSecondaryInputSlot(slot);
    }

    private static boolean isOutputSlot(int slot) {
        return slot >= ProcessingMachineBlockEntity.SLOT_OUTPUT_START
                && slot < ProcessingMachineBlockEntity.SLOT_OUTPUT_START + ProcessingMachineBlockEntity.OUTPUT_SLOT_COUNT;
    }
}
