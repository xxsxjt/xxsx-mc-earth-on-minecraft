package com.xxsx.earthonminecraft.living;

import com.xxsx.earthonminecraft.SettlementFacilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.List;
import java.util.Optional;

public final class ResidentWorkBehavior {
    private static final int WORK_RADIUS = 24;

    private ResidentWorkBehavior() {
    }

    public static void tick(ServerLevel level, Villager villager,
                            ResidentProfileManager.ResidentSnapshot profile) {
        if (villager.isBaby() || villager.isSleeping()) {
            return;
        }
        String role = profile.roleIdentifier().getPath();
        String facility = facilityForRole(role);
        if (facility == null) {
            return;
        }
        Optional<BlockPos> target = SettlementSavedData.get(level)
                .nearestFacility(level, villager.blockPosition(), facility, WORK_RADIUS);
        if (target.isEmpty()) {
            return;
        }

        BlockPos workPos = target.get();
        villager.getLookControl().setLookAt(
                workPos.getX() + 0.5D, workPos.getY() + 0.7D, workPos.getZ() + 0.5D);
        emitRoleEffect(level, villager, role);
        if (role.equals("warehouse_manager")) {
            restockSettlementChestIfEmpty(level, workPos, profile);
        }
    }

    private static void restockSettlementChestIfEmpty(
            ServerLevel level, BlockPos pos, ResidentProfileManager.ResidentSnapshot profile) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity chest)) {
            return;
        }
        if (chest.getLootTable() != null) {
            return;
        }
        boolean anyItem = false;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (!chest.getItem(i).isEmpty()) {
                anyItem = true;
                break;
            }
        }
        if (anyItem) {
            return;
        }
        int tier = SettlementSavedData.get(level).byId(profile.settlementId())
                .map(SettlementSavedData.SettlementSnapshot::tier)
                .orElse(1);
        List<ItemStack> supplies = SettlementFacilities.deterministicAssayedSuppliesForTier(
                tier, level.getGameTime() ^ pos.asLong());
        for (int i = 0; i < Math.min(supplies.size(), chest.getContainerSize()); i++) {
            chest.setItem(i, supplies.get(i));
        }
        chest.setChanged();
    }

    public static String facilityForRole(String role) {
        return switch (role) {
            case "miner", "geologist" -> SettlementFacilities.MINING;
            case "mechanic" -> SettlementFacilities.WORKSHOP;
            case "electrician" -> SettlementFacilities.POWER;
            case "water_operator" -> SettlementFacilities.WATERWORKS;
            case "warehouse_manager" -> SettlementFacilities.WAREHOUSE;
            default -> null;
        };
    }

    private static void emitRoleEffect(ServerLevel level, Villager villager, String role) {
        double x = villager.getX();
        double y = villager.getY() + 1.05D;
        double z = villager.getZ();
        SoundEvent sound;
        switch (role) {
            case "miner" -> {
                level.sendParticles(ParticleTypes.POOF, x, y, z, 2, 0.18D, 0.10D, 0.18D, 0.01D);
                sound = SoundEvents.STONE_HIT;
            }
            case "geologist" -> {
                level.sendParticles(ParticleTypes.CRIT, x, y + 0.18D, z, 1, 0.12D, 0.08D, 0.12D, 0.01D);
                sound = SoundEvents.BOOK_PAGE_TURN;
            }
            case "mechanic" -> {
                level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.12D, 0.06D, 0.12D, 0.005D);
                sound = SoundEvents.ANVIL_USE;
            }
            case "electrician" -> {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.12D, z, 2,
                        0.16D, 0.10D, 0.16D, 0.015D);
                sound = SoundEvents.COPPER_BULB_TURN_ON;
            }
            case "water_operator" -> {
                level.sendParticles(ParticleTypes.SPLASH, x, y, z, 2, 0.16D, 0.08D, 0.16D, 0.01D);
                sound = SoundEvents.BUCKET_FILL;
            }
            case "warehouse_manager" -> {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.15D, z, 1,
                        0.14D, 0.08D, 0.14D, 0.0D);
                sound = SoundEvents.BARREL_OPEN;
            }
            default -> {
                return;
            }
        }
        if (level.getRandom().nextInt(3) == 0) {
            level.playSound(null, villager.blockPosition(), sound, SoundSource.NEUTRAL,
                    0.18F, 0.92F + level.getRandom().nextFloat() * 0.16F);
        }
    }
}
