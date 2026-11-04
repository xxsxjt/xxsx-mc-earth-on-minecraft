package com.xxsx.earthonminecraft.api;

import com.xxsx.earthonminecraft.living.ResidentProfileManager;
import com.xxsx.earthonminecraft.living.SettlementSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SettlementQueryApi {
    private SettlementQueryApi() {
    }

    public static Optional<ResidentInfo> resident(Entity entity) {
        return ResidentProfileManager.read(entity).map(profile -> new ResidentInfo(
                entity.getUUID().toString(), profile.nameKey(), profile.roleId(), profile.roleTitleKey(),
                profile.identityKey(), profile.skill(), profile.professionId(), profile.settlementId(),
                profile.settlementProfileId(), profile.settlementNameKey()));
    }

    public static Optional<SettlementInfo> settlement(ServerLevel level, String settlementId) {
        return SettlementSavedData.get(level).byId(settlementId).map(SettlementQueryApi::toInfo);
    }

    public static Optional<SettlementInfo> nearestSettlement(ServerLevel level, BlockPos pos, int radius) {
        return SettlementSavedData.get(level).nearest(level, pos, Math.max(1, radius)).map(SettlementQueryApi::toInfo);
    }

    private static SettlementInfo toInfo(SettlementSavedData.SettlementSnapshot snapshot) {
        return new SettlementInfo(snapshot.id(), snapshot.dimension(), snapshot.center(), snapshot.profileId(),
                snapshot.nameKey(), snapshot.tier(), snapshot.tierProgressKey(),
                snapshot.profileNameKey(), snapshot.scaleKey(), snapshot.technologyKey(),
                snapshot.industryKeys(), snapshot.demandKeys(), snapshot.supplyKeys(), snapshot.roleCounts(),
                snapshot.facilityCounts(), snapshot.security(), snapshot.reputation(), snapshot.createdAt(),
                snapshot.updatedAt());
    }

    public record ResidentInfo(String uuid, String nameKey, String roleId, String roleTitleKey,
                               String identityKey, int skill, String vanillaProfessionId,
                               String settlementId, String settlementProfileId, String settlementNameKey) {
    }

    public record SettlementInfo(String id, String dimension, BlockPos center, String profileId,
                                 String nameKey, int tier, String tierProgressKey,
                                 String profileNameKey, String scaleKey, String technologyKey,
                                 List<String> industryKeys, List<String> demandKeys, List<String> supplyKeys,
                                 Map<String, Integer> roleCounts, Map<String, Integer> facilityCounts,
                                 int security, int reputation, long createdAt, long updatedAt) {
        public SettlementInfo(String id, String dimension, BlockPos center, String profileId,
                              String nameKey, String profileNameKey, String scaleKey, String technologyKey,
                              List<String> industryKeys, List<String> demandKeys, List<String> supplyKeys,
                              Map<String, Integer> roleCounts, Map<String, Integer> facilityCounts,
                              int security, int reputation, long createdAt, long updatedAt) {
            this(id, dimension, center, profileId, nameKey, 1,
                    "settlement.earth_on_minecraft.tier.status.tier_1_need_mining",
                    profileNameKey, scaleKey, technologyKey, industryKeys, demandKeys, supplyKeys,
                    roleCounts, facilityCounts, security, reputation, createdAt, updatedAt);
        }

        public int residentCount() {
            return roleCounts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
