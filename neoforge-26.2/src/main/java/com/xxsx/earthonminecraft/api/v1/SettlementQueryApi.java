package com.xxsx.earthonminecraft.api.v1;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public interface SettlementQueryApi {
    Optional<ResidentInfo> resident(Entity entity);

    Optional<SettlementInfo> settlement(ServerLevel level, String settlementId);

    Optional<SettlementInfo> nearestSettlement(ServerLevel level, BlockPos pos, int radius);

    record ResidentInfo(String uuid, String nameKey, String roleId, String roleTitleKey,
                        String identityKey, int skill, String vanillaProfessionId,
                        String settlementId, String settlementProfileId, String settlementNameKey) {
    }

    record SettlementInfo(String id, String dimension, BlockPos center, String profileId,
                          String nameKey, int tier, String tierProgressKey,
                          String profileNameKey, String scaleKey, String technologyKey,
                          List<String> industryKeys, List<String> demandKeys, List<String> supplyKeys,
                          Map<String, Integer> roleCounts, Map<String, Integer> facilityCounts,
                          int security, int reputation, long createdAt, long updatedAt) {
        public SettlementInfo {
            tier = Math.max(1, Math.min(4, tier));
            industryKeys = List.copyOf(industryKeys);
            demandKeys = List.copyOf(demandKeys);
            supplyKeys = List.copyOf(supplyKeys);
            roleCounts = Map.copyOf(roleCounts);
            facilityCounts = Map.copyOf(facilityCounts);
        }

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
