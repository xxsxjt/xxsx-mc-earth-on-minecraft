package com.xxsx.earthonminecraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Single-slot fluid tank for a processing machine, expressed in millibuckets.
 *
 * <p>The tank holds at most one fluid at a time. It is a {@link ResourceHandler} so pipes and
 * buckets can fill and drain it through {@code Capabilities.Fluid.BLOCK}.</p>
 */
public class MachineFluidTank implements ResourceHandler<FluidResource> {
    private final int capacity;
    private Fluid fluid;
    private int amount;
    private Runnable listener = () -> {
    };

    public MachineFluidTank(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Registers a callback fired whenever the stored fluid or amount changes. The block entity
     * uses it to mirror the tank into its synced data slots when pipes fill or drain it.
     */
    public void setListener(Runnable listener) {
        this.listener = listener == null ? () -> {
        } : listener;
    }

    private void changed() {
        listener.run();
    }

    public int capacity() {
        return capacity;
    }

    public int amount() {
        return amount;
    }

    public Fluid fluid() {
        return fluid;
    }

    public boolean isEmpty() {
        return fluid == null || amount <= 0;
    }

    public String fluidId() {
        if (isEmpty()) {
            return "";
        }
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        return id == null ? "" : id.toString();
    }

    /** True when the tank holds at least {@code required} mB of the named fluid. */
    public boolean holds(String fluidId, int required) {
        if (isEmpty() || required <= 0) {
            return required <= 0;
        }
        Identifier expected = Identifier.tryParse(fluidId);
        if (expected == null) {
            return false;
        }
        Identifier actual = BuiltInRegistries.FLUID.getKey(fluid);
        return expected.equals(actual) && amount >= required;
    }

    /** Removes up to {@code requested} mB, returning how much was actually removed. */
    public int drain(String fluidId, int requested) {
        if (requested <= 0 || !holds(fluidId, 1)) {
            return 0;
        }
        int removed = Math.min(requested, amount);
        amount -= removed;
        if (amount <= 0) {
            amount = 0;
            fluid = null;
        }
        changed();
        return removed;
    }

    /** Adds up to {@code requested} mB of the given fluid, returning how much was accepted. */
    public int fill(String fluidId, int requested) {
        if (requested <= 0) {
            return 0;
        }
        Identifier id = Identifier.tryParse(fluidId);
        if (id == null) {
            return 0;
        }
        Fluid candidate = BuiltInRegistries.FLUID.getValue(id);
        if (candidate == null) {
            return 0;
        }
        if (!isEmpty() && fluid != candidate) {
            return 0;
        }
        int accepted = Math.min(requested, capacity - amount);
        if (accepted <= 0) {
            return 0;
        }
        fluid = candidate;
        amount += accepted;
        changed();
        return accepted;
    }

    public void set(String fluidId, int newAmount) {
        if (newAmount <= 0 || fluidId == null || fluidId.isEmpty()) {
            fluid = null;
            amount = 0;
            return;
        }
        Identifier id = Identifier.tryParse(fluidId);
        Fluid candidate = id == null ? null : BuiltInRegistries.FLUID.getValue(id);
        if (candidate == null) {
            fluid = null;
            amount = 0;
            return;
        }
        fluid = candidate;
        amount = Math.min(newAmount, capacity);
        changed();
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        return isEmpty() ? FluidResource.EMPTY : FluidResource.of(new FluidStack(fluid, amount));
    }

    @Override
    public long getAmountAsLong(int index) {
        return amount;
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return capacity;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource == null || resource.isEmpty()) {
            return false;
        }
        return isEmpty() || resource.getFluid() == fluid;
    }

    @Override
    public int insert(int index, FluidResource resource, int inserted, TransactionContext transaction) {
        if (resource == null || resource.isEmpty() || inserted <= 0) {
            return 0;
        }
        if (!isEmpty() && resource.getFluid() != fluid) {
            return 0;
        }
        int accepted = Math.min(inserted, capacity - amount);
        if (accepted <= 0) {
            return 0;
        }
        fluid = resource.getFluid();
        amount += accepted;
        changed();
        return accepted;
    }

    @Override
    public int extract(int index, FluidResource resource, int extracted, TransactionContext transaction) {
        if (resource == null || resource.isEmpty() || extracted <= 0 || isEmpty()) {
            return 0;
        }
        if (resource.getFluid() != fluid) {
            return 0;
        }
        int removed = Math.min(extracted, amount);
        amount -= removed;
        if (amount <= 0) {
            amount = 0;
            fluid = null;
        }
        changed();
        return removed;
    }
}
