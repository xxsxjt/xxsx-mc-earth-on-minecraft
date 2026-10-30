package com.xxsx.earthonminecraft.api.v1;

import com.xxsx.earthonminecraft.EarthOnMinecraft;
import com.xxsx.earthonminecraft.MachineMultiblock;
import com.xxsx.earthonminecraft.MaterialChemistry;
import com.xxsx.earthonminecraft.MaterialCatalog;
import com.xxsx.earthonminecraft.ProcessingMachineBlock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;

final class ApiServices {
    static final MaterialPropertyApi MATERIALS = ApiServices::material;
    static final GeologyQueryApi GEOLOGY = new GeologyService();
    static final MachineProcessingApi PROCESSING = new ProcessingService();
    static final EnergyApi ENERGY = ApiServices::energyPort;
    static final LogisticsApi LOGISTICS = ApiServices::itemPort;
    static final SettlementQueryApi SETTLEMENTS = new SettlementService();

    private ApiServices() {
    }

    private static Optional<MaterialPropertyApi.MaterialProperties> material(Identifier id) {
        Optional<MaterialPropertyApi.MaterialProperties> known = MaterialChemistry.describe(id)
                .map(info -> new MaterialPropertyApi.MaterialProperties(
                        id, info.formula(), info.categoryKey(), info.formKey(), info.sourceKey(),
                        info.processKey(), info.useKey(), info.simplificationKey()));
        if (known.isPresent()) {
            return known;
        }
        return MaterialCatalog.find(id)
                .map(record -> new MaterialPropertyApi.MaterialProperties(
                        id, record.formula(), "tooltip.earth_on_minecraft.chem.detail.mineral",
                        "tooltip.earth_on_minecraft.chem.form.natural_block",
                        "tooltip.earth_on_minecraft.chem.source.mineral",
                        "tooltip.earth_on_minecraft.chem.process.mineral",
                        "tooltip.earth_on_minecraft.chem.use.general",
                        record.isMixture()
                                ? "tooltip.earth_on_minecraft.chem.simplify.mixture"
                                : "tooltip.earth_on_minecraft.chem.simplify.stackable"));
    }

    private static Optional<EnergyApi.EnergyPort> energyPort(Level level, BlockPos pos, Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof EnergyApi.EnergyPort port ? Optional.of(port) : Optional.empty();
    }

    private static Optional<LogisticsApi.ItemPort> itemPort(Level level, BlockPos pos, Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container
                ? Optional.of(new ContainerItemPort(container, side))
                : Optional.empty();
    }

    private static final class GeologyService implements GeologyQueryApi {
        @Override
        public Optional<GeologySample> inspect(LevelReader level, BlockPos pos) {
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            return describe(blockId);
        }

        @Override
        public Optional<GeologySample> describe(Identifier blockId) {
            return MATERIALS.find(blockId).map(material -> new GeologySample(
                    blockId, role(blockId), material.formula(), material.categoryKey(),
                    material.sourceKey(), material.processKey(),
                    EarthOnMinecraft.MODID.equals(blockId.getNamespace())));
        }

        private static DepositRole role(Identifier id) {
            String path = id.getPath();
            if (path.contains("laterite")) {
                return DepositRole.LATERITE;
            }
            if (path.contains("placer")) {
                return DepositRole.PLACER;
            }
            if (path.contains("carbonatite")) {
                return DepositRole.CARBONATITE;
            }
            if (path.endsWith("_ore")) {
                return DepositRole.ORE;
            }
            if (path.endsWith("_vein")) {
                return DepositRole.VEIN;
            }
            if (path.endsWith("_seam")) {
                return DepositRole.SEAM;
            }
            if (path.endsWith("_bed")) {
                return DepositRole.BED;
            }
            if (path.contains("dirt") || path.contains("sand") || path.contains("gravel") || path.equals("mud")) {
                return DepositRole.SOIL;
            }
            if (MATERIALS.find(id).isPresent()) {
                return DepositRole.ROCK;
            }
            return DepositRole.OTHER;
        }
    }

    private static final class ProcessingService implements MachineProcessingApi {
        @Override
        public List<MachineSpec> machines() {
            return Arrays.stream(ProcessingMachineBlock.Kind.values()).map(this::spec).toList();
        }

        @Override
        public Optional<MachineSpec> machine(Identifier machineId) {
            return Arrays.stream(ProcessingMachineBlock.Kind.values())
                    .filter(kind -> id(kind).equals(machineId))
                    .findFirst()
                    .map(this::spec);
        }

        @Override
        public List<ProcessingRoute> routes() {
            return ProcessingMachineBlock.recipes().stream().map(this::route).toList();
        }

        @Override
        public List<ProcessingRoute> routesFor(Identifier machineId) {
            return ProcessingMachineBlock.recipes().stream()
                    .filter(recipe -> id(recipe.kind()).equals(machineId))
                    .map(this::route)
                    .toList();
        }

        private MachineSpec spec(ProcessingMachineBlock.Kind kind) {
            return new MachineSpec(id(kind), kind.displayNameKey(), kind.processFamily().name().toLowerCase(),
                    kind.processTicks(), kind.energyPerTick(), kind.powerMode().name().toLowerCase(),
                    MachineMultiblock.patternFor(kind).name().toLowerCase(),
                    ProcessingMachineBlock.recipesFor(kind).size());
        }

        private ProcessingRoute route(ProcessingMachineBlock.Recipe recipe) {
            Identifier input = BuiltInRegistries.ITEM.getKey(recipe.input().get().asItem());
            List<RouteOutput> outputs = recipe.outputs().stream()
                    .map(output -> new RouteOutput(BuiltInRegistries.ITEM.getKey(output.item().get().asItem()), output.count()))
                    .toList();
            return new ProcessingRoute(id(recipe.kind()), input, outputs, recipe.kind().processTicks(),
                    recipe.kind().energyPerTick(), recipe.note());
        }

        private static Identifier id(ProcessingMachineBlock.Kind kind) {
            return EarthOnMinecraft.id(kind.blockId());
        }
    }

    private static final class ContainerItemPort implements LogisticsApi.ItemPort {
        private final Container container;
        private final Direction side;

        private ContainerItemPort(Container container, Direction side) {
            this.container = container;
            this.side = side;
        }

        @Override
        public ItemStack insert(ItemStack stack, boolean simulate) {
            ItemStack remaining = stack.copy();
            boolean changed = false;
            for (int slot : slots()) {
                if (remaining.isEmpty() || !canInsert(slot, remaining)) {
                    continue;
                }
                ItemStack existing = container.getItem(slot);
                int max = container.getMaxStackSize(remaining);
                if (existing.isEmpty()) {
                    int moved = Math.min(max, remaining.getCount());
                    if (!simulate) {
                        ItemStack inserted = remaining.copy();
                        inserted.setCount(moved);
                        container.setItem(slot, inserted);
                        changed = true;
                    }
                    remaining.shrink(moved);
                } else if (ItemStack.isSameItemSameComponents(existing, remaining)) {
                    int moved = Math.min(max - existing.getCount(), remaining.getCount());
                    if (moved > 0) {
                        if (!simulate) {
                            ItemStack merged = existing.copy();
                            merged.grow(moved);
                            container.setItem(slot, merged);
                            changed = true;
                        }
                        remaining.shrink(moved);
                    }
                }
            }
            if (changed) {
                container.setChanged();
            }
            return remaining;
        }

        @Override
        public ItemStack extract(int maxAmount, boolean simulate) {
            int requested = Math.max(0, maxAmount);
            for (int slot : slots()) {
                ItemStack existing = container.getItem(slot);
                if (existing.isEmpty() || !canExtract(slot, existing)) {
                    continue;
                }
                int moved = Math.min(requested, existing.getCount());
                ItemStack result = existing.copy();
                result.setCount(moved);
                if (!simulate) {
                    container.removeItem(slot, moved);
                    container.setChanged();
                }
                return result;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int slotCount() {
            return slots().length;
        }

        private int[] slots() {
            if (container instanceof WorldlyContainer worldly && side != null) {
                return worldly.getSlotsForFace(side);
            }
            int[] slots = new int[container.getContainerSize()];
            for (int slot = 0; slot < slots.length; slot++) {
                slots[slot] = slot;
            }
            return slots;
        }

        private boolean canInsert(int slot, ItemStack stack) {
            if (!container.canPlaceItem(slot, stack)) {
                return false;
            }
            return !(container instanceof WorldlyContainer worldly) || side == null
                    || worldly.canPlaceItemThroughFace(slot, stack, side);
        }

        private boolean canExtract(int slot, ItemStack stack) {
            return !(container instanceof WorldlyContainer worldly) || side == null
                    || worldly.canTakeItemThroughFace(slot, stack, side);
        }
    }

    private static final class SettlementService implements SettlementQueryApi {
        @Override
        public Optional<ResidentInfo> resident(net.minecraft.world.entity.Entity entity) {
            return com.xxsx.earthonminecraft.api.SettlementQueryApi.resident(entity).map(info -> new ResidentInfo(
                    info.uuid(), info.nameKey(), info.roleId(), info.roleTitleKey(), info.identityKey(), info.skill(),
                    info.vanillaProfessionId(), info.settlementId(), info.settlementProfileId(), info.settlementNameKey()));
        }

        @Override
        public Optional<SettlementInfo> settlement(net.minecraft.server.level.ServerLevel level, String settlementId) {
            return com.xxsx.earthonminecraft.api.SettlementQueryApi.settlement(level, settlementId).map(this::map);
        }

        @Override
        public Optional<SettlementInfo> nearestSettlement(net.minecraft.server.level.ServerLevel level,
                                                          BlockPos pos, int radius) {
            return com.xxsx.earthonminecraft.api.SettlementQueryApi.nearestSettlement(level, pos, radius).map(this::map);
        }

        private SettlementInfo map(com.xxsx.earthonminecraft.api.SettlementQueryApi.SettlementInfo info) {
            return new SettlementInfo(info.id(), info.dimension(), info.center(), info.profileId(), info.nameKey(),
                    info.profileNameKey(), info.scaleKey(), info.technologyKey(), info.industryKeys(),
                    info.demandKeys(), info.supplyKeys(), info.roleCounts(), info.facilityCounts(), info.security(),
                    info.reputation(), info.createdAt(), info.updatedAt());
        }
    }
}
