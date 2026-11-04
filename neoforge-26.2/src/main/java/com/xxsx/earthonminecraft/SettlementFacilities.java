package com.xxsx.earthonminecraft;

import com.xxsx.earthonminecraft.component.EarthDataComponents;
import com.xxsx.earthonminecraft.component.Grade;
import com.xxsx.earthonminecraft.component.MaterialBatch;
import com.xxsx.earthonminecraft.component.MetalBatch;
import com.xxsx.earthonminecraft.component.QualityComponents;
import com.xxsx.earthonminecraft.component.ToolQualityComponent;
import com.xxsx.earthonminecraft.living.SettlementSavedData;
import com.xxsx.earthonminecraft.living.SettlementDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SettlementFacilities {
    public static final String ADMINISTRATION = "settlement.earth_on_minecraft.facility.administration";
    public static final String MINING = "settlement.earth_on_minecraft.facility.mining";
    public static final String WORKSHOP = "settlement.earth_on_minecraft.facility.workshop";
    public static final String POWER = "settlement.earth_on_minecraft.facility.power";
    public static final String WATERWORKS = "settlement.earth_on_minecraft.facility.waterworks";
    public static final String WAREHOUSE = "settlement.earth_on_minecraft.facility.warehouse";
    public static final String ARCANA = "settlement.earth_on_minecraft.facility.arcana";
    public static final String CULTIVATION = "settlement.earth_on_minecraft.facility.cultivation";

    public static final Identifier LOOT_PRIMITIVE_HAMLET = EarthOnMinecraft.id("chests/settlement_primitive_hamlet");
    public static final Identifier LOOT_MINING_SETTLEMENT = EarthOnMinecraft.id("chests/settlement_mining_settlement");
    public static final Identifier LOOT_INDUSTRIAL_TOWN = EarthOnMinecraft.id("chests/settlement_industrial_town");
    public static final Identifier LOOT_MECHANIZED_CITY = EarthOnMinecraft.id("chests/settlement_mechanized_city");

    private SettlementFacilities() {
    }

    public static ResourceKey<LootTable> chestLootTableForTier(int tier) {
        int clamped = Math.max(1, Math.min(4, tier));
        Identifier id = switch (clamped) {
            case 1 -> LOOT_PRIMITIVE_HAMLET;
            case 2 -> LOOT_MINING_SETTLEMENT;
            case 3 -> LOOT_INDUSTRIAL_TOWN;
            default -> LOOT_MECHANIZED_CITY;
        };
        return ResourceKey.create(Registries.LOOT_TABLE, id);
    }

    public static List<ItemStack> deterministicAssayedSuppliesForTier(int tier, long seed) {
        int clamped = Math.max(1, Math.min(4, tier));
        List<ItemStack> stacks = new ArrayList<>();
        double jitter = ((SettlementDataManager.mix(seed) & 0xFFL) / 255.0) * 0.015;
        switch (clamped) {
            case 1 -> {
                stacks.add(new ItemStack(Items.CHARCOAL, 4));
                ItemStack clay = new ItemStack(EarthOnMinecraft.CLAY_DUST.get(), 4);
                clay.set(EarthDataComponents.MATERIAL_BATCH.get(),
                        MaterialBatch.defaultFor("clay_dust").withPurityAndGrade(0.86 + jitter, Grade.COMMON));
                stacks.add(clay);
                ItemStack hammer = new ItemStack(EarthOnMinecraft.GEOLOGY_HAMMER.get());
                hammer.set(EarthDataComponents.TOOL_QUALITY.get(),
                        new ToolQualityComponent(1, Grade.COMMON, "primitive_hamlet", 0.55));
                stacks.add(hammer);
            }
            case 2 -> {
                ItemStack mag = new ItemStack(EarthOnMinecraft.MAGNETITE_CHUNK.get(), 4);
                mag.set(EarthDataComponents.MATERIAL_BATCH.get(),
                        MaterialBatch.defaultFor("magnetite_chunk").withPurityAndGrade(0.84 + jitter, Grade.FINE));
                stacks.add(mag);
                ItemStack conc = new ItemStack(EarthOnMinecraft.IRON_CONCENTRATE.get(), 3);
                conc.set(EarthDataComponents.MATERIAL_BATCH.get(),
                        MaterialBatch.defaultFor("iron_concentrate").withPurityAndGrade(0.955 + jitter, Grade.FINE));
                stacks.add(conc);
                ItemStack hammer = new ItemStack(EarthOnMinecraft.GEOLOGY_HAMMER.get());
                hammer.set(EarthDataComponents.TOOL_QUALITY.get(),
                        new ToolQualityComponent(1, Grade.FINE, "mining_settlement", 0.72));
                stacks.add(hammer);
            }
            case 3 -> {
                ItemStack coke = new ItemStack(EarthOnMinecraft.COKE.get(), 6);
                coke.set(EarthDataComponents.MATERIAL_BATCH.get(),
                        MaterialBatch.defaultFor("coke").withPurityAndGrade(0.925 + jitter, Grade.FINE));
                stacks.add(coke);
                stacks.add(new ItemStack(EarthOnMinecraft.FIREBRICK.get(), 6));
                Item steelItem = SpecItems.BY_ID.get("steel").get();
                ItemStack steel = new ItemStack(steelItem, 3);
                MetalBatch steelMetal = QualityComponents.defaultMetalBatchForItem("steel");
                steel.set(EarthDataComponents.METAL_BATCH.get(), steelMetal);
                steel.set(EarthDataComponents.MATERIAL_BATCH.get(),
                        MaterialBatch.defaultFor("steel").withPurityAndGrade(0.986 + jitter, Grade.FINE));
                stacks.add(steel);
                Item pickaxeItem = SpecItems.BY_ID.get("steel_pickaxe").get();
                ItemStack pickaxe = new ItemStack(pickaxeItem);
                pickaxe.set(EarthDataComponents.TOOL_QUALITY.get(),
                        new ToolQualityComponent(1, Grade.FINE, "industrial_town", 0.78));
                stacks.add(pickaxe);
            }
            default -> {
                stacks.add(new ItemStack(EarthOnMinecraft.COPPER_WIRE.get(), 6));
                stacks.add(new ItemStack(EarthOnMinecraft.CERAMIC_INSULATOR.get(), 4));
                ItemStack feCat = new ItemStack(EarthOnMinecraft.IRON_CATALYST.get(), 1);
                feCat.set(EarthDataComponents.MATERIAL_BATCH.get(),
                        MaterialBatch.defaultFor("iron_catalyst").withPurityAndGrade(0.994 + jitter * 0.2, Grade.MASTERWORK));
                stacks.add(feCat);
                ItemStack vCat = new ItemStack(EarthOnMinecraft.VANADIUM_CATALYST.get(), 1);
                vCat.set(EarthDataComponents.MATERIAL_BATCH.get(),
                        MaterialBatch.defaultFor("vanadium_catalyst").withPurityAndGrade(0.995 + jitter * 0.2, Grade.MASTERWORK));
                stacks.add(vCat);
                Item scannerItem = SpecItems.BY_ID.get("ore_scanner").get();
                ItemStack scanner = new ItemStack(scannerItem);
                scanner.set(EarthDataComponents.TOOL_QUALITY.get(),
                        new ToolQualityComponent(1, Grade.MASTERWORK, "mechanized_city", 0.92));
                stacks.add(scanner);
            }
        }
        return List.copyOf(stacks);
    }

    /**
     * Physically builds or retrofits a multi-building settlement compound appropriate for the given tier (1..4),
     * placing tier-appropriate industrial stations, structural supports, and a supply chest linked to the tier's
     * custom loot table, then indexes all facilities into the settlement record.
     */
    public static SettlementSavedData.SettlementSnapshot buildOrRetrofitCompound(
            ServerLevel level, BlockPos boardPos, int tier, String settlementId) {
        int clampedTier = Math.max(1, Math.min(4, tier));
        SettlementSavedData savedData = SettlementSavedData.get(level);
        Identifier targetProfile = SettlementDataManager.canonicalProfileForTier(clampedTier);
        long seed = SettlementDataManager.mix(level.getSeed() ^ boardPos.asLong() ^ clampedTier);
        String nameKey = SettlementDataManager.chooseSettlementNameKey(targetProfile, seed);

        SettlementSavedData.SettlementSnapshot current = settlementId == null || settlementId.isBlank()
                ? savedData.getOrCreate(level, boardPos, targetProfile, nameKey)
                : savedData.byId(settlementId).orElseGet(() -> savedData.getOrCreate(level, boardPos, targetProfile, nameKey));
        String activeId = current.id();

        placeSupportedBlock(level, boardPos, EarthOnMinecraft.SETTLEMENT_BOARD.get().defaultBlockState(), Blocks.COBBLESTONE);
        registerBoard(level, boardPos, activeId);

        Block foundation = switch (clampedTier) {
            case 1 -> Blocks.COBBLESTONE;
            case 2 -> Blocks.STONE_BRICKS;
            default -> Blocks.SMOOTH_STONE;
        };

        switch (clampedTier) {
            case 1 -> {
                // Tier 1: primitive_hamlet — Campfire, Pit Kiln, Curing/Drying Rack, Composter, Farmland (NO electricity/modern machines)
                placeFacilityStation(level, boardPos.offset(2, 0, 0), EarthOnMinecraft.CAMPFIRE.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(-2, 0, 0), EarthOnMinecraft.PIT_KILN.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(0, 0, 2), EarthOnMinecraft.CURING_BARN.get(), foundation, activeId);
                placeSupportedBlock(level, boardPos.offset(2, 0, 2), Blocks.COMPOSTER.defaultBlockState(), Blocks.DIRT);
                placeSupportedBlock(level, boardPos.offset(-2, 0, 2), Blocks.FARMLAND.defaultBlockState(), Blocks.DIRT);
            }
            case 2 -> {
                // Tier 2: mining_settlement — Bloomery, Ironworking Anvil, Sieve, Jaw Crusher, Timber Mine Supports
                placeFacilityStation(level, boardPos.offset(2, 0, 0), EarthOnMinecraft.BLOOMERY.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(-2, 0, 0), EarthOnMinecraft.IRONWORKING_ANVIL.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(0, 0, 2), EarthOnMinecraft.SIEVE.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(2, 0, 2), EarthOnMinecraft.JAW_CRUSHER.get(), foundation, activeId);
                placeSupportedBlock(level, boardPos.offset(-2, 0, 2), Blocks.OAK_LOG.defaultBlockState(), foundation);
                placeSupportedBlock(level, boardPos.offset(-2, 1, 2), Blocks.OAK_FENCE.defaultBlockState(), Blocks.OAK_LOG);
            }
            case 3 -> {
                // Tier 3: industrial_town — Shaft Furnace, Coke Oven (Industrial Kiln), Ore Roaster, Ball Mill, Lime Kiln
                placeFacilityStation(level, boardPos.offset(2, 0, 0), EarthOnMinecraft.SHAFT_FURNACE.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(-2, 0, 0), EarthOnMinecraft.INDUSTRIAL_KILN.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(0, 0, 2), EarthOnMinecraft.ORE_ROASTER.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(2, 0, 2), EarthOnMinecraft.BALL_MILL.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(-2, 0, 2), EarthOnMinecraft.LIME_KILN.get(), foundation, activeId);
            }
            default -> {
                // Tier 4: mechanized_city — EOU Power Grid (Generator, Battery Box, Cable), Chemical Reactor, Distillation Column, Electrolytic Cell, Conveyor Belt
                placeFacilityStation(level, boardPos.offset(2, 0, 0), EarthOnMinecraft.COMBUSTION_GENERATOR.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(2, 0, 1), EarthOnMinecraft.COPPER_POWER_CABLE.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(2, 0, 2), EarthOnMinecraft.BATTERY_BOX.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(-2, 0, 0), EarthOnMinecraft.CHEMICAL_REACTOR.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(0, 0, 2), EarthOnMinecraft.DISTILLATION_COLUMN.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(-2, 0, 2), EarthOnMinecraft.ELECTROLYTIC_CELL.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(0, 0, -2), EarthOnMinecraft.CONVEYOR_BELT.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(-2, 0, -2), EarthOnMinecraft.BALL_MILL.get(), foundation, activeId);
                placeFacilityStation(level, boardPos.offset(2, 0, -2), EarthOnMinecraft.SHAFT_FURNACE.get(), foundation, activeId);
            }
        }

        // Place tier supply chest at offset (1, 0, -2) with custom chest loot table
        BlockPos chestPos = boardPos.offset(1, 0, -2);
        placeSupportedBlock(level, chestPos, Blocks.CHEST.defaultBlockState(), foundation);
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(chestLootTableForTier(clampedTier), seed);
            chest.setChanged();
        }
        savedData.registerFacility(level, chestPos, WAREHOUSE, activeId);
        savedData.upgradeToTier(level, activeId, clampedTier);
        return savedData.byId(activeId).orElseGet(() -> savedData.getOrCreate(level, boardPos));
    }

    private static void placeFacilityStation(ServerLevel level, BlockPos pos, Block block, Block foundation, String settlementId) {
        BlockState state = block.defaultBlockState();
        placeSupportedBlock(level, pos, state, foundation);
        registerFacility(level, pos, state, settlementId);
    }

    private static void placeSupportedBlock(ServerLevel level, BlockPos pos, BlockState state, Block foundation) {
        BlockPos below = pos.below();
        if (level.getBlockState(below).isAir()) {
            level.setBlock(below, foundation.defaultBlockState(), 3);
        }
        level.setBlock(pos, state, 3);
    }

    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        registerFacility(level, event.getPos(), event.getPlacedBlock());
    }

    public static void onBlockBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        facilityFor(event.getState()).ifPresent(facility ->
                SettlementSavedData.get(level).unregisterFacility(level, event.getPos(), facility));
    }

    public static void registerBoard(ServerLevel level, BlockPos pos, String settlementId) {
        SettlementSavedData.get(level).registerFacility(level, pos, ADMINISTRATION, settlementId);
    }

    public static void indexNearby(ServerLevel level, BlockPos center, int horizontalRadius, int verticalRadius,
                                   String settlementId) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        List<IndexedAddonAnchor> addonAnchors = new ArrayList<>();
        int minY = Math.max(level.getMinY(), center.getY() - verticalRadius);
        int maxY = Math.min(level.getMaxY() - 1, center.getY() + verticalRadius);
        for (int x = center.getX() - horizontalRadius; x <= center.getX() + horizontalRadius; x++) {
            for (int z = center.getZ() - horizontalRadius; z <= center.getZ() + horizontalRadius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    Optional<Identifier> profile = profileFor(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
                    if (profile.isPresent()) {
                        BlockPos anchorPos = cursor.immutable();
                        long seed = SettlementDataManager.mix(level.getSeed() ^ anchorPos.asLong()
                                ^ profile.get().hashCode());
                        String nameKey = SettlementDataManager.chooseSettlementNameKey(profile.get(), seed);
                        String anchorSettlementId = SettlementSavedData.get(level)
                                .getOrCreate(level, anchorPos, profile.get(), nameKey).id();
                        addonAnchors.add(new IndexedAddonAnchor(anchorPos, anchorSettlementId));
                    }
                }
            }
        }

        for (int x = center.getX() - horizontalRadius; x <= center.getX() + horizontalRadius; x++) {
            for (int z = center.getZ() - horizontalRadius; z <= center.getZ() + horizontalRadius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    BlockPos pos = cursor.immutable();
                    BlockState state = level.getBlockState(cursor);
                    Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    String targetSettlementId = settlementId;
                    if (blockId.getNamespace().equals("fantasy_earth")
                            || blockId.getNamespace().equals("xuanhuan_earth")) {
                        double nearestDistance = 64.0D;
                        for (IndexedAddonAnchor anchor : addonAnchors) {
                            double distance = pos.distSqr(anchor.pos());
                            if (distance <= nearestDistance) {
                                targetSettlementId = anchor.settlementId();
                                nearestDistance = distance;
                            }
                        }
                    }
                    registerFacility(level, pos, state, targetSettlementId);
                }
            }
        }
    }

    public static Optional<String> facilityFor(BlockState state) {
        Block block = state.getBlock();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (blockId.getNamespace().equals("fantasy_earth")) {
            return switch (blockId.getPath()) {
                case "settlement_anchor", "alchemy_table", "rune_carving_table", "ritual_pedestal",
                     "arcane_focus_mat" -> Optional.of(ARCANA);
                default -> Optional.empty();
            };
        }
        if (blockId.getNamespace().equals("xuanhuan_earth")) {
            return switch (blockId.getPath()) {
                case "settlement_anchor", "alchemy_furnace", "talisman_table", "spirit_array_core",
                     "spirit_vein_node", "spirit_spring_stone" -> Optional.of(CULTIVATION);
                default -> Optional.empty();
            };
        }
        if (block == EarthOnMinecraft.SETTLEMENT_BOARD.get()) {
            return Optional.of(ADMINISTRATION);
        }
        if (Set.of(
                EarthOnMinecraft.JAW_CRUSHER.get(), EarthOnMinecraft.BALL_MILL.get(), EarthOnMinecraft.SIEVE.get(),
                EarthOnMinecraft.MAGNETIC_SEPARATOR.get(), EarthOnMinecraft.FLOTATION_CELL.get()).contains(block)) {
            return Optional.of(MINING);
        }
        if (Set.of(
                EarthOnMinecraft.ORE_ROASTER.get(), EarthOnMinecraft.REDUCTION_FURNACE.get(),
                EarthOnMinecraft.INDUSTRIAL_KILN.get(), EarthOnMinecraft.POWDER_PRESS.get()).contains(block)) {
            return Optional.of(WORKSHOP);
        }
        if (Set.of(
                EarthOnMinecraft.COMBUSTION_GENERATOR.get(), EarthOnMinecraft.STEAM_TURBINE_GENERATOR.get(),
                EarthOnMinecraft.BATTERY_BOX.get(), EarthOnMinecraft.THIN_COPPER_POWER_CABLE.get(),
                EarthOnMinecraft.COPPER_POWER_CABLE.get(), EarthOnMinecraft.HEAVY_COPPER_POWER_CABLE.get()).contains(block)) {
            return Optional.of(POWER);
        }
        if (Set.of(
                EarthOnMinecraft.LEACHING_TANK.get(), EarthOnMinecraft.ELECTROLYTIC_CELL.get(),
                EarthOnMinecraft.CHEMICAL_REACTOR.get(), EarthOnMinecraft.DISTILLATION_COLUMN.get(),
                EarthOnMinecraft.GAS_SEPARATOR.get(),
                EarthOnMinecraft.ABSORPTION_TOWER.get(), EarthOnMinecraft.CONDENSER.get(),
                EarthOnMinecraft.FILTER_PRESS.get(), EarthOnMinecraft.THICKENER.get(),
                EarthOnMinecraft.WATER_TREATMENT_UNIT.get(), EarthOnMinecraft.TANNERY.get()).contains(block)) {
            return Optional.of(WATERWORKS);
        }
        if (Set.of(
                EarthOnMinecraft.ANVIL.get(), EarthOnMinecraft.IRONWORKING_ANVIL.get(),
                EarthOnMinecraft.BLOOMERY.get(), EarthOnMinecraft.SHAFT_FURNACE.get(),
                EarthOnMinecraft.PIT_KILN.get(), EarthOnMinecraft.LIME_KILN.get(),
                EarthOnMinecraft.GLASS_KILN.get(), EarthOnMinecraft.BREAD_OVEN.get(),
                EarthOnMinecraft.CASTING_TABLE.get(),
                EarthOnMinecraft.CONVERTER.get(), EarthOnMinecraft.SMELTER.get(),
                EarthOnMinecraft.GEM_CUTTER.get(), EarthOnMinecraft.TABLET_PRESS.get()).contains(block)) {
            return Optional.of(WORKSHOP);
        }
        if (Set.of(
                EarthOnMinecraft.CARPENTRY_BENCH.get(), EarthOnMinecraft.LOOM.get(),
                EarthOnMinecraft.MILL.get(), EarthOnMinecraft.STONE_MILL.get(),
                EarthOnMinecraft.RICE_HULLER.get(), EarthOnMinecraft.OIL_PRESS.get(),
                EarthOnMinecraft.NOODLE_PRESS.get()).contains(block)) {
            return Optional.of(WORKSHOP);
        }
        if (Set.of(
                EarthOnMinecraft.CAMPFIRE.get(), EarthOnMinecraft.STOVE.get(),
                EarthOnMinecraft.STEAMER.get(), EarthOnMinecraft.STEAMER_BASKET.get(),
                EarthOnMinecraft.SMOKEHOUSE.get(), EarthOnMinecraft.CURING_BARN.get(),
                EarthOnMinecraft.COFFEE_ROASTER.get(), EarthOnMinecraft.TEA_KILN.get(),
                EarthOnMinecraft.HERB_PROCESSING_TABLE.get(), EarthOnMinecraft.STILL.get(),
                EarthOnMinecraft.FERMENTING_CROCK.get(), EarthOnMinecraft.BAR_COUNTER.get()).contains(block)) {
            return Optional.of(WORKSHOP);
        }
        if (Set.of(
                EarthOnMinecraft.CONVEYOR_BELT.get(), EarthOnMinecraft.MACHINE_INPUT_INTERFACE.get(),
                EarthOnMinecraft.MACHINE_OUTPUT_INTERFACE.get()).contains(block)) {
            return Optional.of(WAREHOUSE);
        }
        return Optional.empty();
    }

    public static Optional<AddonSettlementAnchor> nearbyAddonSettlement(ServerLevel level, BlockPos center, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos nearestPos = null;
        Identifier nearestProfile = null;
        double nearestDistance = Double.MAX_VALUE;
        int minY = Math.max(level.getMinY(), center.getY() - 8);
        int maxY = Math.min(level.getMaxY() - 1, center.getY() + 8);
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    Identifier blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(cursor).getBlock());
                    Optional<Identifier> profile = profileFor(blockId);
                    double distance = cursor.distSqr(center);
                    if (profile.isPresent() && distance < nearestDistance) {
                        nearestPos = cursor.immutable();
                        nearestProfile = profile.get();
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearestProfile == null ? Optional.empty() : Optional.of(new AddonSettlementAnchor(nearestPos, nearestProfile));
    }

    public record AddonSettlementAnchor(BlockPos pos, Identifier profileId) {
    }

    private static void registerFacility(ServerLevel level, BlockPos pos, BlockState state) {
        registerFacility(level, pos, state, "");
    }

    private static void registerFacility(ServerLevel level, BlockPos pos, BlockState state, String settlementId) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Optional<Identifier> profileId = profileFor(blockId);
        String facilitySettlementId = settlementId;
        if (profileId.isPresent()) {
            SettlementSavedData savedData = SettlementSavedData.get(level);
            long seed = SettlementDataManager.mix(level.getSeed() ^ pos.asLong() ^ blockId.hashCode());
            String nameKey = SettlementDataManager.chooseSettlementNameKey(profileId.get(), seed);
            facilitySettlementId = savedData.getOrCreate(level, pos, profileId.get(), nameKey).id();
        } else if (facilitySettlementId.isBlank() && facilityFor(state).isPresent()) {
            AddonSettlementAnchor anchor = nearbyAddonSettlement(level, pos, 8).orElse(null);
            if (anchor != null) {
                long seed = SettlementDataManager.mix(level.getSeed() ^ anchor.pos().asLong()
                        ^ anchor.profileId().hashCode());
                String nameKey = SettlementDataManager.chooseSettlementNameKey(anchor.profileId(), seed);
                facilitySettlementId = SettlementSavedData.get(level)
                        .getOrCreate(level, anchor.pos(), anchor.profileId(), nameKey).id();
            }
        }
        String targetSettlementId = facilitySettlementId;
        facilityFor(state).ifPresent(facility ->
                SettlementSavedData.get(level).registerFacility(level, pos, facility, targetSettlementId));
    }

    private static Optional<Identifier> profileFor(Identifier blockId) {
        if (!blockId.getPath().equals("settlement_anchor")) {
            return Optional.empty();
        }
        return switch (blockId.getNamespace()) {
            case "fantasy_earth" -> Optional.of(EarthOnMinecraft.id("arcane_settlement"));
            case "xuanhuan_earth" -> Optional.of(EarthOnMinecraft.id("cultivation_settlement"));
            default -> Optional.empty();
        };
    }

    private record IndexedAddonAnchor(BlockPos pos, String settlementId) {
    }
}
