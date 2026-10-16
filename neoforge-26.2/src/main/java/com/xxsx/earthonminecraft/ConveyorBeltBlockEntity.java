package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ConveyorBeltBlockEntity extends BlockEntity implements Container {
    private static final int TRANSFER_TICKS = 20;
    private static final int SLOT_CARGO = 0;
    private final NonNullList<ItemStack> cargo = NonNullList.withSize(1, ItemStack.EMPTY);
    private int progress;

    public ConveyorBeltBlockEntity(BlockPos pos, BlockState state) {
        super(EarthOnMinecraft.CONVEYOR_BELT_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConveyorBeltBlockEntity conveyor) {
        if (!RealitySupportRules.isOperational(level, pos)) {
            conveyor.progress = 0;
            return;
        }
        if (conveyor.cargo().isEmpty()) {
            conveyor.progress = 0;
            return;
        }
        conveyor.progress++;
        if (conveyor.progress >= TRANSFER_TICKS && conveyor.tryDeliver(level, pos, state.getValue(ConveyorBeltBlock.FACING))) {
            return;
        }
        conveyor.setChanged();
    }

    public static void clientTick(ConveyorBeltBlockEntity conveyor) {
        if (conveyor.cargo().isEmpty()) {
            conveyor.progress = 0;
        } else if (conveyor.progress < TRANSFER_TICKS) {
            conveyor.progress++;
        }
    }

    public boolean canReceive(ItemStack stack) {
        return !stack.isEmpty() && cargo().isEmpty();
    }

    public boolean receiveCargo(ItemStack stack) {
        if (!canReceive(stack)) {
            return false;
        }
        this.cargo.set(SLOT_CARGO, stack.copyWithCount(1));
        this.progress = 0;
        sync();
        return true;
    }

    public ItemStack cargo() {
        return this.cargo.get(SLOT_CARGO);
    }

    public float transportProgress(float partialTicks) {
        if (cargo().isEmpty()) {
            return 0.0F;
        }
        return Math.min(1.0F, (this.progress + partialTicks) / (float) TRANSFER_TICKS);
    }

    private boolean tryDeliver(Level level, BlockPos pos, Direction facing) {
        BlockPos targetPos = pos.relative(facing);
        BlockEntity blockEntity = level.getBlockEntity(targetPos);
        ItemStack stack = cargo();
        if (stack.isEmpty()) {
            return true;
        }
        if (blockEntity instanceof ConveyorBeltBlockEntity conveyor && conveyor.receiveCargo(stack)) {
            clearCargo();
            return true;
        }
        Direction targetFace = facing.getOpposite();
        if (blockEntity instanceof WorldlyContainer worldly && insertIntoWorldly(worldly, targetFace, stack)) {
            clearCargo();
            return true;
        }
        if (!(blockEntity instanceof ConveyorBeltBlockEntity)
                && blockEntity instanceof Container container
                && insertIntoContainer(container, stack)) {
            clearCargo();
            return true;
        }
        this.progress = TRANSFER_TICKS;
        return false;
    }

    private void clearCargo() {
        this.cargo.set(SLOT_CARGO, ItemStack.EMPTY);
        this.progress = 0;
        sync();
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

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return cargo().isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == SLOT_CARGO ? cargo() : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != SLOT_CARGO) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.removeItem(this.cargo, slot, amount);
        if (!removed.isEmpty()) {
            sync();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != SLOT_CARGO) {
            return ItemStack.EMPTY;
        }
        return ContainerHelper.takeItem(this.cargo, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == SLOT_CARGO) {
            this.cargo.set(SLOT_CARGO, stack.copyWithCount(Math.min(1, stack.getCount())));
            this.progress = 0;
            sync();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        clearCargo();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.cargo.set(SLOT_CARGO, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.cargo);
        this.progress = input.getIntOr("Progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.cargo);
        output.putInt("Progress", this.progress);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
