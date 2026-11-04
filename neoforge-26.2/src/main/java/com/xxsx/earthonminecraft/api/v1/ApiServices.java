package com.xxsx.earthonminecraft.api.v1;

import com.xxsx.earthonminecraft.EarthOnMinecraft;
import com.xxsx.earthonminecraft.MachineMultiblock;
import com.xxsx.earthonminecraft.MaterialChemistry;
import com.xxsx.earthonminecraft.MaterialCatalog;
import com.xxsx.earthonminecraft.ProcessingMachineBlock;
import com.xxsx.earthonminecraft.component.EarthDataComponents;
import com.xxsx.earthonminecraft.component.Grade;
import com.xxsx.earthonminecraft.component.MaterialBatch;
import com.xxsx.earthonminecraft.component.MetalBatch;
import com.xxsx.earthonminecraft.component.QualityComponents;
import com.xxsx.earthonminecraft.component.ToolQualityComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    static final MaterialPropertyApi MATERIALS = new MaterialService();
    static final GeologyQueryApi GEOLOGY = new GeologyService();
    static final MachineProcessingApi PROCESSING = new ProcessingService();
    static final EnergyApi ENERGY = ApiServices::energyPort;
    static final LogisticsApi LOGISTICS = ApiServices::itemPort;
    static final SettlementQueryApi SETTLEMENTS = new SettlementService();

    private ApiServices() {
    }

    private static final class MaterialService implements MaterialPropertyApi {
        @Override
        public Optional<MaterialProperties> find(Identifier id) {
            Optional<MaterialProperties> known = MaterialChemistry.describe(id)
                    .map(info -> new MaterialProperties(
                            id, info.formula(), info.categoryKey(), info.formKey(), info.sourceKey(),
                            info.processKey(), info.useKey(), info.simplificationKey()));
            if (known.isPresent()) {
                return known;
            }
            return MaterialCatalog.find(id)
                    .map(record -> new MaterialProperties(
                            id, record.formula(), "tooltip.earth_on_minecraft.chem.detail.mineral",
                            "tooltip.earth_on_minecraft.chem.form.natural_block",
                            "tooltip.earth_on_minecraft.chem.source.mineral",
                            "tooltip.earth_on_minecraft.chem.process.mineral",
                            "tooltip.earth_on_minecraft.chem.use.general",
                            record.isMixture()
                                    ? "tooltip.earth_on_minecraft.chem.simplify.mixture"
                                    : "tooltip.earth_on_minecraft.chem.simplify.stackable"));
        }

        @Override
        public Optional<AssayReport> inspectMaterial(Identifier id) {
            Optional<MaterialCatalog.MaterialRecord> recordOpt = MaterialCatalog.find(id);
            if (recordOpt.isEmpty()) {
                return Optional.empty();
            }
            MaterialCatalog.MaterialRecord record = recordOpt.get();
            MaterialBatch batch = MaterialBatch.defaultFor(id.getPath());
            Map<String, Double> comp = new LinkedHashMap<>();
            for (MaterialCatalog.Constituent c : record.constituents()) {
                comp.put(c.species(), c.fraction());
            }
            MetalBatch metal = QualityComponents.defaultMetalBatchForItem(id.getPath());
            boolean isAlloy = "alloy".equals(record.kind());
            return Optional.of(buildReport(
                    id, batch.grade(), batch.purity(), Map.copyOf(comp),
                    isAlloy ? metal.ironCarbonClass() : "non_ferrous",
                    isAlloy ? metal.workability() : 1.0,
                    batch.temperatureK(), batch.moisture()));
        }

        @Override
        public AssayReport inspectStack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return AssayReport.EMPTY;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            MaterialBatch batch = QualityComponents.resolveMaterialBatch(stack)
                    .orElseGet(() -> MaterialBatch.defaultFor(id.getPath()));
            MetalBatch metal = QualityComponents.resolveMetalBatch(stack).orElse(null);
            ToolQualityComponent tool = stack.get(EarthDataComponents.TOOL_QUALITY.get());

            Grade grade = tool != null ? tool.grade() : batch.grade();
            Map<String, Double> comp = new LinkedHashMap<>();
            if (metal != null && !metal.composition().isEmpty()) {
                comp.putAll(metal.composition());
            } else {
                MaterialCatalog.find(id).ifPresent(record -> {
                    for (MaterialCatalog.Constituent c : record.constituents()) {
                        comp.put(c.species(), c.fraction());
                    }
                });
            }
            String icClass = metal != null ? metal.ironCarbonClass() : "non_ferrous";
            double workability = metal != null ? metal.workability() : 1.0;
            return buildReport(id, grade, batch.purity(), Map.copyOf(comp), icClass,
                    workability, batch.temperatureK(), batch.moisture());
        }

        private static AssayReport buildReport(
                Identifier id, Grade grade, double purity, Map<String, Double> comp,
                String icClass, double workability, int tempK, double moisture) {
            double s = comp.getOrDefault("S", 0.0) + comp.getOrDefault("FeS2", 0.0) * 0.53;
            double p = comp.getOrDefault("P", 0.0);
            double slag = comp.getOrDefault("Slag", 0.0);
            boolean silicateIsPrimary = comp.getOrDefault("SiO2", 0.0) >= 0.50 || id.getPath().contains("quartz") || id.getPath().contains("spirit_stone");
            double gangueSi = silicateIsPrimary ? 0.0 : comp.getOrDefault("SiO2", 0.0) * 0.5;

            double penalty = Math.min(0.95, s * 35.0 + p * 30.0 + slag * 3.2 + gangueSi + moisture * 1.5);
            double gradeBonus = 1.0 + grade.tier() * 0.15;

            // 1. Xuanhuan flying-sword / artifact embryo factor
            double xuanhuanBase;
            if ("steel".equals(icClass)) {
                xuanhuanBase = 1.15;
            } else if ("wrought_iron".equals(icClass)) {
                xuanhuanBase = 0.95;
            } else if ("pig_iron".equals(icClass) || "sponge_iron".equals(icClass)) {
                xuanhuanBase = 0.45;
            } else if (comp.containsKey("Cu") || comp.containsKey("Au") || comp.containsKey("Ti") || id.getPath().contains("spirit_stone")) {
                xuanhuanBase = 1.05;
            } else {
                xuanhuanBase = 0.75;
            }
            double xuanhuanFactor = round4(Math.max(0.05, purity * xuanhuanBase * gradeBonus * (1.0 - penalty)));

            // 2. Magic arcane conductor factor (rewards Cu, Ag, Au, rare earths & high purity; penalizes Fe/Slag/S scattering)
            double cuAuAg = comp.getOrDefault("Cu", 0.0) + comp.getOrDefault("Au", 0.0) * 1.25
                    + comp.getOrDefault("Ag", 0.0) * 1.35 + comp.getOrDefault("CeCO3F", 0.0) * 0.9;
            double feScatter = comp.getOrDefault("Fe", 0.0) > 0.5 ? 0.55 : 1.0 - comp.getOrDefault("Fe", 0.0) * 0.8;
            double magicBase = cuAuAg > 0.2 ? (0.85 + cuAuAg * 0.45) : (0.65 * feScatter);
            double magicFactor = round4(Math.max(0.05, purity * magicBase * (1.0 + grade.tier() * 0.12) * (1.0 - penalty)));

            // 3. Sci-Fi semiconductor & special alloy factor (rewards 6N/9N Si, Ti, Al, Invar, Stainless & ultra-low impurities)
            double siTi = comp.getOrDefault("Si", 0.0) + comp.getOrDefault("Ti", 0.0)
                    + comp.getOrDefault("Al", 0.0) * 0.9 + comp.getOrDefault("Cr", 0.0) * 0.8 + comp.getOrDefault("Ni", 0.0) * 0.8;
            double nineNBonus = purity >= 0.99999 ? 1.45 : (purity >= 0.995 ? 1.20 : 0.90);
            double scifiBase = siTi > 0.15 ? 1.10 : 0.85;
            double scifiFactor = round4(Math.max(0.05, purity * scifiBase * nineNBonus * (1.0 + grade.tier() * 0.12) * (1.0 - penalty)));

            return new AssayReport(
                    id, grade, round4(purity), comp, icClass, round4(workability),
                    tempK, round4(penalty), xuanhuanFactor, magicFactor, scifiFactor);
        }

        private static double round4(double v) {
            return Math.round(v * 10_000.0) / 10_000.0;
        }
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
            // Fluid-only routes have no item input at all, so `input()` is null. Reporting the
            // first item input as the route key would throw; use null and let the caller fall back
            // to the fluid list.
            Identifier input = recipe.hasItemInput()
                    ? BuiltInRegistries.ITEM.getKey(recipe.input().get().asItem())
                    : null;
            List<RouteInput> inputs = recipe.inputs().stream()
                    .map(entry -> new RouteInput(BuiltInRegistries.ITEM.getKey(entry.item().get().asItem()), entry.count()))
                    .toList();
            List<RouteOutput> outputs = recipe.outputs().stream()
                    .map(output -> new RouteOutput(BuiltInRegistries.ITEM.getKey(output.item().get().asItem()), output.count()))
                    .toList();
            List<RouteFluid> fluidInputs = recipe.fluidInputs().stream()
                    .map(entry -> new RouteFluid(fluidId(entry.fluid()), entry.amount()))
                    .toList();
            List<RouteFluid> fluidOutputs = recipe.fluidOutputs().stream()
                    .map(entry -> new RouteFluid(fluidId(entry.fluid()), entry.amount()))
                    .toList();
            String note = recipe.requiresCoke() ? recipe.note() + " | coke charge required" : recipe.note();
            return new ProcessingRoute(id(recipe.kind()), input, inputs, outputs, fluidInputs,
                    fluidOutputs, recipe.processTicks(), recipe.energyPerTickEffective(), note,
                    recipe.sourceId());
        }

        private static Identifier fluidId(net.minecraft.world.level.material.Fluid fluid) {
            return BuiltInRegistries.FLUID.getKey(fluid);
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
                    info.tier(), info.tierProgressKey(), info.profileNameKey(), info.scaleKey(), info.technologyKey(), info.industryKeys(),
                    info.demandKeys(), info.supplyKeys(), info.roleCounts(), info.facilityCounts(), info.security(),
                    info.reputation(), info.createdAt(), info.updatedAt());
        }
    }
}
