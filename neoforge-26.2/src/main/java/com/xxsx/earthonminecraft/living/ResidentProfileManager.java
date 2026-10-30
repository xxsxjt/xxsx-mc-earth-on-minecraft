package com.xxsx.earthonminecraft.living;

import com.xxsx.earthonminecraft.EarthOnMinecraft;
import com.xxsx.earthonminecraft.SettlementFacilities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ResidentProfileManager {
    private static final int PROFILE_VERSION = 1;
    private static final String PREFIX = "EarthOnMinecraftResident";
    private static final String VERSION_KEY = PREFIX + "Version";
    private static final String NAME_KEY = PREFIX + "NameKey";
    private static final String ROLE_ID_KEY = PREFIX + "RoleId";
    private static final String ROLE_TITLE_KEY = PREFIX + "RoleTitleKey";
    private static final String IDENTITY_KEY = PREFIX + "IdentityKey";
    private static final String SKILL_KEY = PREFIX + "Skill";
    private static final String PROFESSION_KEY = PREFIX + "Profession";
    private static final String SETTLEMENT_ID_KEY = PREFIX + "SettlementId";
    private static final String SETTLEMENT_PROFILE_KEY = PREFIX + "SettlementProfile";
    private static final String SETTLEMENT_NAME_KEY = PREFIX + "SettlementNameKey";
    private static final String OWNS_CUSTOM_NAME_KEY = PREFIX + "OwnsCustomName";
    private static final String EXTERNAL_PROFILE_KEY = "EarthOnMinecraftSettlementProfile";
    private static final String EXTERNAL_ANCHOR_X_KEY = "EarthOnMinecraftSettlementAnchorX";
    private static final String EXTERNAL_ANCHOR_Y_KEY = "EarthOnMinecraftSettlementAnchorY";
    private static final String EXTERNAL_ANCHOR_Z_KEY = "EarthOnMinecraftSettlementAnchorZ";
    private static final String EXTERNAL_ROLE_KEY = "EarthOnMinecraftResidentRole";
    private static final Map<String, Identifier> ADDON_ROLE_IDS = Map.of(
            "fantasy_earth:hedge_witch", EarthOnMinecraft.id("fantasy_hedge_witch"),
            "fantasy_earth:goblin_appraiser", EarthOnMinecraft.id("fantasy_goblin_appraiser"),
            "fantasy_earth:academy_researcher", EarthOnMinecraft.id("fantasy_academy_researcher"),
            "xuanhuan_earth:wandering_merchant", EarthOnMinecraft.id("xuanhuan_wandering_merchant"),
            "xuanhuan_earth:spirit_spring_keeper", EarthOnMinecraft.id("xuanhuan_spirit_spring_keeper"),
            "xuanhuan_earth:sect_steward", EarthOnMinecraft.id("xuanhuan_sect_steward"));

    private ResidentProfileManager() {
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity() instanceof Villager villager) {
            ensure(villager);
        } else {
            ensureAddonResident(level, event.getEntity());
        }
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide()
                && villager.tickCount % 200 == 0) {
            ResidentSnapshot profile = ensure(villager);
            if (villager.level() instanceof ServerLevel level) {
                ResidentWorkBehavior.tick(level, villager, profile);
            }
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        ResidentSnapshot profile = event.getTarget() instanceof Villager villager
                ? ensure(villager) : read(event.getTarget()).orElse(null);
        if (profile != null && event.getEntity().isShiftKeyDown()) {
            event.getEntity().sendOverlayMessage(profile.summary());
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        read(event.getEntity()).ifPresent(profile -> SettlementSavedData.get(level)
                .unregisterResident(profile.settlementId(), event.getEntity().getUUID()));
    }

    public static ResidentSnapshot ensure(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return read(villager).orElseGet(() -> transientProfile(villager));
        }

        CompoundTag data = villager.getPersistentData();
        UUID uuid = villager.getUUID();
        long residentSeed = SettlementDataManager.mix(uuid.getMostSignificantBits()
                ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 29));

        SettlementSavedData savedData = SettlementSavedData.get(level);
        String storedSettlementId = data.getStringOr(SETTLEMENT_ID_KEY, "");
        SettlementSavedData.SettlementSnapshot settlement = savedData.byId(storedSettlementId)
                .orElseGet(() -> savedData.getOrCreate(level, villager.blockPosition()));

        String nameKey = data.getStringOr(NAME_KEY, "");
        if (nameKey.isBlank()) {
            nameKey = SettlementDataManager.chooseNameKey(residentSeed);
        }

        String professionId = professionId(villager);
        SettlementDataManager.RoleSelection roleSelection;
        if (villager.isBaby()) {
            roleSelection = new SettlementDataManager.RoleSelection(EarthOnMinecraft.id("apprentice"),
                    new SettlementDataManager.ResidentRole("resident.earth_on_minecraft.role.apprentice",
                            "resident.earth_on_minecraft.identity.child", java.util.List.of(), java.util.List.of(), 1,
                            java.util.List.of()));
        } else {
            roleSelection = SettlementDataManager.chooseRole(professionId, settlement.profileId(),
                    residentSeed ^ settlement.profileId().hashCode());
        }
        String roleId = roleSelection.id().toString();
        String roleTitleKey = roleSelection.role().titleKey();
        String identityKey = roleSelection.role().identityKey();
        int skill = 1 + Math.floorMod((int) SettlementDataManager.mix(residentSeed ^ 0x6A09E667F3BCC909L), 5);

        boolean ownsCustomName = data.getBooleanOr(OWNS_CUSTOM_NAME_KEY, false);
        if (ownsCustomName && villager.getCustomName() != null) {
            String oldNameKey = data.getStringOr(NAME_KEY, "");
            String oldRoleTitleKey = data.getStringOr(ROLE_TITLE_KEY, "");
            if (!oldNameKey.isBlank() && !oldRoleTitleKey.isBlank()
                    && !villager.getCustomName().equals(displayName(oldNameKey, oldRoleTitleKey))) {
                ownsCustomName = false;
            }
        }

        ResidentSnapshot profile = new ResidentSnapshot(nameKey, roleId, roleTitleKey, identityKey, skill,
                professionId, settlement.id(), settlement.profileId(), settlement.nameKey());

        if (!villager.hasCustomName() || ownsCustomName) {
            villager.setCustomName(profile.displayName());
            villager.setCustomNameVisible(false);
            ownsCustomName = true;
        }

        data.putInt(VERSION_KEY, PROFILE_VERSION);
        data.putString(NAME_KEY, nameKey);
        data.putString(ROLE_ID_KEY, roleId);
        data.putString(ROLE_TITLE_KEY, roleTitleKey);
        data.putString(IDENTITY_KEY, identityKey);
        data.putInt(SKILL_KEY, skill);
        data.putString(PROFESSION_KEY, professionId);
        data.putString(SETTLEMENT_ID_KEY, settlement.id());
        data.putString(SETTLEMENT_PROFILE_KEY, settlement.profileId());
        data.putString(SETTLEMENT_NAME_KEY, settlement.nameKey());
        data.putBoolean(OWNS_CUSTOM_NAME_KEY, ownsCustomName);

        savedData.registerResident(level, villager.blockPosition(), uuid, roleId, nameKey, skill);
        addRoleTrades(villager, roleSelection, settlement);
        return profile;
    }

    private static void ensureAddonResident(ServerLevel level, Entity entity) {
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Identifier profileId = switch (typeId.getNamespace() + ":" + typeId.getPath()) {
            case "fantasy_earth:arcane_settler" -> EarthOnMinecraft.id("arcane_settlement");
            case "xuanhuan_earth:cultivation_settler" -> EarthOnMinecraft.id("cultivation_settlement");
            default -> null;
        };
        if (profileId == null) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        long seed = SettlementDataManager.mix(entity.getUUID().getMostSignificantBits()
                ^ entity.getUUID().getLeastSignificantBits());
        String storedProfileId = data.getStringOr(EXTERNAL_PROFILE_KEY, "");
        int anchorX = data.getIntOr(EXTERNAL_ANCHOR_X_KEY, Integer.MIN_VALUE);
        int anchorY = data.getIntOr(EXTERNAL_ANCHOR_Y_KEY, Integer.MIN_VALUE);
        int anchorZ = data.getIntOr(EXTERNAL_ANCHOR_Z_KEY, Integer.MIN_VALUE);
        BlockPos anchorPos = anchorX == Integer.MIN_VALUE || anchorY == Integer.MIN_VALUE || anchorZ == Integer.MIN_VALUE
                ? null : new BlockPos(anchorX, anchorY, anchorZ);
        if (storedProfileId.equals(EarthOnMinecraft.id("arcane_settlement").toString())
                || storedProfileId.equals(EarthOnMinecraft.id("cultivation_settlement").toString())) {
            profileId = Identifier.parse(storedProfileId);
        }
        Identifier desiredProfileId = profileId;
        if (anchorPos == null) {
            var anchor = SettlementFacilities.nearbyAddonSettlement(level, entity.blockPosition(), 8)
                    .filter(candidate -> desiredProfileId.equals(candidate.profileId())).orElse(null);
            anchorPos = anchor == null ? entity.blockPosition() : anchor.pos();
        }
        String settlementNameKey = SettlementDataManager.chooseSettlementNameKey(desiredProfileId,
                SettlementDataManager.mix(level.getSeed() ^ anchorPos.asLong() ^ seed));
        var settlement = SettlementSavedData.get(level).getOrCreate(level, anchorPos, desiredProfileId, settlementNameKey);
        String nameKey = SettlementDataManager.chooseNameKey(SettlementDataManager.mix(entity.getUUID().getMostSignificantBits()
                ^ entity.getUUID().getLeastSignificantBits()));
        Identifier fixedRoleId = ADDON_ROLE_IDS.get(data.getStringOr(EXTERNAL_ROLE_KEY, ""));
        SettlementDataManager.RoleSelection roleSelection = fixedRoleId == null
                ? SettlementDataManager.chooseRole(typeId.toString(), desiredProfileId.toString(), seed)
                : new SettlementDataManager.RoleSelection(fixedRoleId, SettlementDataManager.role(fixedRoleId));
        String roleId = roleSelection.id().toString();
        String titleKey = roleSelection.role().titleKey();
        String identityKey = roleSelection.role().identityKey();
        int skill = 1 + Math.floorMod(entity.getUUID().hashCode(), 5);
        data.putInt(VERSION_KEY, PROFILE_VERSION);
        data.putString(NAME_KEY, nameKey);
        data.putString(ROLE_ID_KEY, roleId);
        data.putString(ROLE_TITLE_KEY, titleKey);
        data.putString(IDENTITY_KEY, identityKey);
        data.putInt(SKILL_KEY, skill);
        data.putString(PROFESSION_KEY, typeId.toString());
        data.putString(SETTLEMENT_ID_KEY, settlement.id());
        data.putString(SETTLEMENT_PROFILE_KEY, settlement.profileId());
        data.putString(SETTLEMENT_NAME_KEY, settlement.nameKey());
        data.putString(EXTERNAL_PROFILE_KEY, desiredProfileId.toString());
        data.putInt(EXTERNAL_ANCHOR_X_KEY, anchorPos.getX());
        data.putInt(EXTERNAL_ANCHOR_Y_KEY, anchorPos.getY());
        data.putInt(EXTERNAL_ANCHOR_Z_KEY, anchorPos.getZ());
        if (!entity.hasCustomName()) {
            entity.setCustomName(displayName(nameKey, titleKey));
        }
        SettlementSavedData.get(level).registerResident(level, anchorPos, entity.getUUID(), roleId, nameKey, skill,
                settlement.id());
    }

    private static void addRoleTrades(Villager villager, SettlementDataManager.RoleSelection roleSelection,
                                      SettlementSavedData.SettlementSnapshot settlement) {
        if (villager.isBaby()) {
            return;
        }
        var tradeRole = roleSelection.role();
        if (!tradeRole.settlementProfiles().isEmpty()
                && !tradeRole.settlementProfiles().contains(settlement.profileId())) {
            return;
        }
        var offers = villager.getOffers();
        int villagerLevel = villager.getVillagerData().level();
        for (SettlementDataManager.TradeDefinition trade : tradeRole.trades()) {
            if (trade.level() > villagerLevel) {
                continue;
            }
            var cost = BuiltInRegistries.ITEM.getValue(Identifier.parse(trade.inputId()));
            var result = BuiltInRegistries.ITEM.getValue(Identifier.parse(trade.outputId()));
            if (cost == Items.AIR || result == Items.AIR) {
                EarthOnMinecraft.LOGGER.warn("Skipping resident trade with unknown item: {} -> {}",
                        trade.inputId(), trade.outputId());
                continue;
            }
            boolean alreadyPresent = offers.stream().anyMatch(offer ->
                    offer.getBaseCostA().is(cost) && offer.getBaseCostA().getCount() == trade.inputCount()
                            && offer.getResult().is(result) && offer.getResult().getCount() == trade.outputCount());
            if (alreadyPresent) {
                continue;
            }
            offers.add(new MerchantOffer(new ItemCost(cost, trade.inputCount()),
                    new ItemStack(result, trade.outputCount()), trade.maxUses(), trade.xp(), trade.priceMultiplier()));
        }
    }

    public static Optional<ResidentSnapshot> read(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.getIntOr(VERSION_KEY, 0) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ResidentSnapshot(
                data.getStringOr(NAME_KEY, "resident.earth_on_minecraft.name.unknown"),
                data.getStringOr(ROLE_ID_KEY, EarthOnMinecraft.id("resident").toString()),
                data.getStringOr(ROLE_TITLE_KEY, "resident.earth_on_minecraft.role.resident"),
                data.getStringOr(IDENTITY_KEY, "resident.earth_on_minecraft.identity.resident"),
                Math.max(1, data.getIntOr(SKILL_KEY, 1)),
                data.getStringOr(PROFESSION_KEY, "minecraft:none"),
                data.getStringOr(SETTLEMENT_ID_KEY, ""),
                data.getStringOr(SETTLEMENT_PROFILE_KEY, EarthOnMinecraft.id("agricultural_village").toString()),
                data.getStringOr(SETTLEMENT_NAME_KEY, "settlement.earth_on_minecraft.name.unknown")
        ));
    }

    private static ResidentSnapshot transientProfile(Villager villager) {
        long seed = SettlementDataManager.mix(villager.getUUID().getLeastSignificantBits());
        String nameKey = SettlementDataManager.chooseNameKey(seed);
        SettlementDataManager.RoleSelection role = SettlementDataManager.chooseRole(professionId(villager), "", seed);
        return new ResidentSnapshot(nameKey, role.id().toString(), role.role().titleKey(), role.role().identityKey(),
                1, professionId(villager), "", "", "settlement.earth_on_minecraft.name.unknown");
    }

    private static String professionId(Villager villager) {
        return villager.getVillagerData().profession().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("minecraft:none");
    }

    private static Component displayName(String nameKey, String roleTitleKey) {
        return Component.translatable("resident.earth_on_minecraft.display",
                Component.translatable(nameKey), Component.translatable(roleTitleKey));
    }

    public record ResidentSnapshot(String nameKey, String roleId, String roleTitleKey, String identityKey,
                                   int skill, String professionId, String settlementId,
                                   String settlementProfileId, String settlementNameKey) {
        public Component displayName() {
            return ResidentProfileManager.displayName(nameKey, roleTitleKey);
        }

        public Component summary() {
            return Component.translatable("message.earth_on_minecraft.resident.summary",
                    Component.translatable(nameKey), Component.translatable(roleTitleKey),
                    Component.translatable(settlementNameKey), skill);
        }

        public Identifier roleIdentifier() {
            return Identifier.parse(roleId);
        }
    }
}
