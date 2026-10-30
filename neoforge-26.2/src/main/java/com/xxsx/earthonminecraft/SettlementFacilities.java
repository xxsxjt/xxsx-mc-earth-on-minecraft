package com.xxsx.earthonminecraft;

import com.xxsx.earthonminecraft.living.SettlementSavedData;
import com.xxsx.earthonminecraft.living.SettlementDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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

    private SettlementFacilities() {
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
                EarthOnMinecraft.CHEMICAL_REACTOR.get(), EarthOnMinecraft.GAS_SEPARATOR.get(),
                EarthOnMinecraft.ABSORPTION_TOWER.get(), EarthOnMinecraft.CONDENSER.get(),
                EarthOnMinecraft.FILTER_PRESS.get(), EarthOnMinecraft.THICKENER.get(),
                EarthOnMinecraft.WATER_TREATMENT_UNIT.get(), EarthOnMinecraft.TANNERY.get()).contains(block)) {
            return Optional.of(WATERWORKS);
        }
        if (Set.of(
                EarthOnMinecraft.ANVIL.get(), EarthOnMinecraft.CASTING_TABLE.get(),
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
