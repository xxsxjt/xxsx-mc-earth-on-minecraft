package com.xxsx.earthonminecraft.living;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xxsx.earthonminecraft.EarthOnMinecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SettlementSavedData extends SavedData {
    private static final int DATA_VERSION = 2;
    private static final int DEFAULT_SETTLEMENT_RADIUS = 96;

    private static final Codec<SettlementSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("version", DATA_VERSION).forGetter(data -> DATA_VERSION),
            SettlementRecord.CODEC.listOf().optionalFieldOf("settlements", List.of()).forGetter(SettlementSavedData::records)
    ).apply(instance, (version, records) -> new SettlementSavedData(records)));

    public static final SavedDataType<SettlementSavedData> TYPE = new SavedDataType<SettlementSavedData>(
            EarthOnMinecraft.id("settlements"), () -> new SettlementSavedData(), CODEC);

    private final Map<String, SettlementRecord> settlements = new LinkedHashMap<>();

    public SettlementSavedData() {
    }

    private SettlementSavedData(List<SettlementRecord> records) {
        for (SettlementRecord record : records) {
            settlements.put(record.id, record);
        }
    }

    public static SettlementSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public SettlementSnapshot getOrCreate(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().identifier().toString();
        Optional<SettlementRecord> nearby = nearestRecord(dimension, pos, DEFAULT_SETTLEMENT_RADIUS);
        SettlementRecord record = nearby.orElseGet(() -> create(level, pos, dimension));
        return snapshot(record);
    }

    public SettlementSnapshot getOrCreate(ServerLevel level, BlockPos pos, Identifier profileId, String nameKey) {
        String dimension = level.dimension().identifier().toString();
        String requestedProfileId = profileId.toString();
        Optional<SettlementRecord> matching = settlements.values().stream()
                .filter(record -> record.dimension.equals(dimension))
                .filter(record -> record.profileId.equals(requestedProfileId))
                .filter(record -> horizontalDistanceSq(record.center(), pos)
                        <= (long) DEFAULT_SETTLEMENT_RADIUS * DEFAULT_SETTLEMENT_RADIUS)
                .min(Comparator.comparingLong(record -> horizontalDistanceSq(record.center(), pos)));
        SettlementRecord record = matching.orElseGet(() -> nearestRecord(dimension, pos, DEFAULT_SETTLEMENT_RADIUS)
                .filter(candidate -> isAutoProfile(candidate.profileId))
                .orElseGet(() -> create(level, pos, dimension, profileId, nameKey)));
        if (!record.profileId.equals(requestedProfileId)) {
            record.profileId = profileId.toString();
            record.nameKey = nameKey;
            record.updatedAt = level.getGameTime();
            setDirty();
        }
        return snapshot(record);
    }

    public Optional<SettlementSnapshot> nearest(ServerLevel level, BlockPos pos, int radius) {
        return nearestRecord(level.dimension().identifier().toString(), pos, radius).map(this::snapshot);
    }

    public Optional<SettlementSnapshot> byId(String id) {
        return Optional.ofNullable(settlements.get(id)).map(this::snapshot);
    }

    public Optional<BlockPos> nearestFacility(ServerLevel level, BlockPos pos, String facilityId, int radius) {
        long radiusSq = (long) radius * radius;
        return nearestRecord(level.dimension().identifier().toString(), pos, DEFAULT_SETTLEMENT_RADIUS)
                .stream()
                .flatMap(record -> record.facilityPositions.getOrDefault(facilityId, new LinkedHashSet<>()).stream())
                .map(BlockPos::of)
                .filter(candidate -> candidate.distSqr(pos) <= radiusSq)
                .min(Comparator.comparingDouble(candidate -> candidate.distSqr(pos)));
    }

    public SettlementSnapshot registerResident(ServerLevel level, BlockPos pos, UUID residentId, String roleId,
                                               String nameKey, int skill) {
        return registerResident(level, pos, residentId, roleId, nameKey, skill, "");
    }

    public SettlementSnapshot registerResident(ServerLevel level, BlockPos pos, UUID residentId, String roleId,
                                                String nameKey, int skill, String settlementId) {
        String dimension = level.dimension().identifier().toString();
        SettlementRecord record = settlements.get(settlementId);
        if (record == null || !record.dimension.equals(dimension)) {
            record = nearestRecord(dimension, pos, DEFAULT_SETTLEMENT_RADIUS)
                    .orElseGet(() -> create(level, pos, dimension));
        }
        String residentKey = residentId.toString();
        boolean changed = !roleId.equals(record.residentRoles.put(residentKey, roleId));
        changed |= !nameKey.equals(record.residentNames.put(residentKey, nameKey));
        changed |= !Integer.valueOf(skill).equals(record.residentSkills.put(residentKey, Math.max(1, skill)));
        if (changed) {
            record.updatedAt = level.getGameTime();
            setDirty();
        }
        return snapshot(record);
    }

    public void unregisterResident(String settlementId, UUID residentId) {
        SettlementRecord record = settlements.get(settlementId);
        if (record == null) {
            return;
        }
        String residentKey = residentId.toString();
        boolean changed = record.residentRoles.remove(residentKey) != null;
        changed |= record.residentNames.remove(residentKey) != null;
        changed |= record.residentSkills.remove(residentKey) != null;
        if (changed) {
            setDirty();
        }
    }

    public SettlementSnapshot registerFacility(ServerLevel level, BlockPos pos, String facilityId) {
        return registerFacility(level, pos, facilityId, "");
    }

    public SettlementSnapshot registerFacility(ServerLevel level, BlockPos pos, String facilityId, String settlementId) {
        String dimension = level.dimension().identifier().toString();
        SettlementRecord record = settlements.get(settlementId);
        if (record == null || !record.dimension.equals(dimension)) {
            record = nearestRecord(dimension, pos, DEFAULT_SETTLEMENT_RADIUS)
                    .orElseGet(() -> create(level, pos, dimension));
        }
        if (record.facilityPositions.computeIfAbsent(facilityId, ignored -> new LinkedHashSet<>()).add(pos.asLong())) {
            record.updatedAt = level.getGameTime();
            setDirty();
        }
        return snapshot(record);
    }

    public void unregisterFacility(ServerLevel level, BlockPos pos, String facilityId) {
        String dimension = level.dimension().identifier().toString();
        long position = pos.asLong();
        settlements.values().stream()
                .filter(record -> record.dimension.equals(dimension))
                .filter(record -> record.facilityPositions.getOrDefault(facilityId, new LinkedHashSet<>())
                        .contains(position))
                .findFirst()
                .ifPresent(record -> {
                    LinkedHashSet<Long> positions = record.facilityPositions.get(facilityId);
                    if (positions.remove(position)) {
                        if (positions.isEmpty()) {
                            record.facilityPositions.remove(facilityId);
                        }
                        record.updatedAt = level.getGameTime();
                        setDirty();
                    }
                });
    }

    private SettlementRecord create(ServerLevel level, BlockPos pos, String dimension) {
        long seed = SettlementDataManager.mix(level.getSeed() ^ pos.asLong());
        SettlementDataManager.ProfileSelection selection = SettlementDataManager.chooseProfile(seed);
        SettlementDataManager.SettlementProfile profile = selection.profile();
        String nameKey = profile.nameKeys().get(Math.floorMod((int) SettlementDataManager.mix(seed ^ 0x73A9B41DL), profile.nameKeys().size()));
        return create(level, pos, dimension, selection.id(), nameKey);
    }

    private SettlementRecord create(ServerLevel level, BlockPos pos, String dimension, Identifier profileId,
                                    String nameKey) {
        String baseId = dimension + "@" + Math.floorDiv(pos.getX(), 32) + "," + Math.floorDiv(pos.getZ(), 32);
        String id = baseId;
        int suffix = 2;
        while (settlements.containsKey(id)) {
            id = baseId + "-" + suffix++;
        }
        SettlementRecord record = new SettlementRecord(id, dimension, pos.asLong(), profileId.toString(), nameKey,
                Map.of(), Map.of(), Map.of(), Map.of(), level.getGameTime(), level.getGameTime(), 50, 0);
        settlements.put(id, record);
        setDirty();
        return record;
    }

    private Optional<SettlementRecord> nearestRecord(String dimension, BlockPos pos, int radius) {
        long radiusSq = (long) radius * radius;
        return settlements.values().stream()
                .filter(record -> record.dimension.equals(dimension))
                .filter(record -> horizontalDistanceSq(record.center(), pos) <= radiusSq)
                .min(Comparator.comparingLong(record -> horizontalDistanceSq(record.center(), pos)));
    }

    private static boolean isAutoProfile(String profileId) {
        return profileId.equals(EarthOnMinecraft.id("agricultural_village").toString())
                || profileId.equals(EarthOnMinecraft.id("resource_village").toString())
                || profileId.equals(EarthOnMinecraft.id("mechanized_town").toString());
    }

    private SettlementSnapshot snapshot(SettlementRecord record) {
        Identifier profileId = Identifier.parse(record.profileId);
        SettlementDataManager.SettlementProfile profile = SettlementDataManager.profile(profileId);
        Map<String, Integer> roleCounts = new LinkedHashMap<>();
        record.residentRoles.values().stream().sorted().forEach(role -> roleCounts.merge(role, 1, Integer::sum));
        List<ResidentSummary> residents = record.residentRoles.entrySet().stream()
                .map(entry -> new ResidentSummary(
                        record.residentNames.getOrDefault(entry.getKey(), "resident.earth_on_minecraft.name.unknown"),
                        entry.getValue(), Math.max(1, record.residentSkills.getOrDefault(entry.getKey(), 1))))
                .sorted(Comparator.comparing(ResidentSummary::roleId).thenComparing(ResidentSummary::nameKey))
                .toList();
        Map<String, Integer> facilityCounts = new LinkedHashMap<>();
        record.facilityPositions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> facilityCounts.put(entry.getKey(), entry.getValue().size()));
        return new SettlementSnapshot(record.id, record.dimension, record.center(), record.profileId, record.nameKey,
                profile.displayNameKey(), profile.scaleKey(), profile.technologyKey(), profile.industryKeys(),
                profile.demandKeys(), profile.supplyKeys(), residents, Map.copyOf(roleCounts), Map.copyOf(facilityCounts),
                record.security, record.reputation, record.createdAt, record.updatedAt);
    }

    private List<SettlementRecord> records() {
        return new ArrayList<>(settlements.values());
    }

    private static long horizontalDistanceSq(BlockPos first, BlockPos second) {
        long dx = first.getX() - second.getX();
        long dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    public record SettlementSnapshot(String id, String dimension, BlockPos center, String profileId, String nameKey,
                                     String profileNameKey, String scaleKey, String technologyKey,
                                     List<String> industryKeys, List<String> demandKeys, List<String> supplyKeys,
                                     List<ResidentSummary> residents, Map<String, Integer> roleCounts,
                                     Map<String, Integer> facilityCounts,
                                     int security, int reputation, long createdAt, long updatedAt) {
        public int residentCount() {
            return residents.isEmpty()
                    ? roleCounts.values().stream().mapToInt(Integer::intValue).sum()
                    : residents.size();
        }
    }

    public record ResidentSummary(String nameKey, String roleId, int skill) {
    }

    private static final class SettlementRecord {
        private static final Codec<SettlementRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(record -> record.id),
                Codec.STRING.fieldOf("dimension").forGetter(record -> record.dimension),
                Codec.LONG.fieldOf("center").forGetter(record -> record.center),
                Codec.STRING.fieldOf("profile").forGetter(record -> record.profileId),
                Codec.STRING.fieldOf("name_key").forGetter(record -> record.nameKey),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("resident_roles", Map.of())
                        .forGetter(record -> Map.copyOf(record.residentRoles)),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("resident_names", Map.of())
                        .forGetter(record -> Map.copyOf(record.residentNames)),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("resident_skills", Map.of())
                        .forGetter(record -> Map.copyOf(record.residentSkills)),
                Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf()).optionalFieldOf("facility_positions", Map.of())
                        .forGetter(SettlementRecord::facilityLists),
                Codec.LONG.optionalFieldOf("created_at", 0L).forGetter(record -> record.createdAt),
                Codec.LONG.optionalFieldOf("updated_at", 0L).forGetter(record -> record.updatedAt),
                Codec.INT.optionalFieldOf("security", 50).forGetter(record -> record.security),
                Codec.INT.optionalFieldOf("reputation", 0).forGetter(record -> record.reputation)
        ).apply(instance, SettlementRecord::new));

        private final String id;
        private final String dimension;
        private final long center;
        private String profileId;
        private String nameKey;
        private final Map<String, String> residentRoles;
        private final Map<String, String> residentNames;
        private final Map<String, Integer> residentSkills;
        private final Map<String, LinkedHashSet<Long>> facilityPositions;
        private final long createdAt;
        private long updatedAt;
        private int security;
        private int reputation;

        private SettlementRecord(String id, String dimension, long center, String profileId, String nameKey,
                                 Map<String, String> residentRoles, Map<String, String> residentNames,
                                 Map<String, Integer> residentSkills, Map<String, List<Long>> facilityPositions,
                                 long createdAt, long updatedAt, int security, int reputation) {
            this.id = id;
            this.dimension = dimension;
            this.center = center;
            this.profileId = profileId;
            this.nameKey = nameKey;
            this.residentRoles = new LinkedHashMap<>(residentRoles);
            this.residentNames = new LinkedHashMap<>(residentNames);
            this.residentSkills = new LinkedHashMap<>(residentSkills);
            this.facilityPositions = new LinkedHashMap<>();
            facilityPositions.forEach((key, positions) -> this.facilityPositions.put(key, new LinkedHashSet<>(positions)));
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.security = Math.max(0, Math.min(100, security));
            this.reputation = reputation;
        }

        private BlockPos center() {
            return BlockPos.of(center);
        }

        private Map<String, List<Long>> facilityLists() {
            Map<String, List<Long>> result = new LinkedHashMap<>();
            facilityPositions.forEach((key, positions) -> result.put(key, List.copyOf(positions)));
            return result;
        }
    }
}
