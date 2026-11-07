package com.xxsx.earthonminecraft;

import com.xxsx.earthonminecraft.component.EarthDataComponents;
import com.xxsx.earthonminecraft.component.Grade;
import com.xxsx.earthonminecraft.component.ToolQualityComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides real combat attributes & quality-scaled hit effects for forged Earth weapons
 * and real physiological/injury treatment for core Earth medicines (linked with {@code earth_human}).
 */
public final class EarthMedicalAndWeaponSupport {
    private static final Identifier BASE_ATTACK_DAMAGE_ID = Identifier.withDefaultNamespace("base_attack_damage");
    private static final Identifier BASE_ATTACK_SPEED_ID = Identifier.withDefaultNamespace("base_attack_speed");
    private static final Identifier WEAPON_REACH_ID = EarthOnMinecraft.id("weapon_reach_bonus");

    public record WeaponSpec(double attackDamageBonus, double attackSpeedDelta, double reachBonus, String roleKey) {
    }

    public record MedicineSpec(
            double partHeal,
            double fractureRelief,
            double lungAirRestore,
            double hydrationDelta,
            double fatigueDelta,
            boolean normalizeTemperature,
            boolean clearToxins,
            int absorptionAmplifier,
            int regenSeconds,
            String tooltipKey
    ) {
    }

    private static final Map<String, WeaponSpec> WEAPONS = Map.ofEntries(
            Map.entry("wooden_sword", new WeaponSpec(3.0D, -2.4D, 0.0D, "tooltip.earth_on_minecraft.weapon.sword")),
            Map.entry("stone_sword", new WeaponSpec(4.0D, -2.4D, 0.0D, "tooltip.earth_on_minecraft.weapon.sword")),
            Map.entry("copper_sword", new WeaponSpec(4.5D, -2.3D, 0.0D, "tooltip.earth_on_minecraft.weapon.sword")),
            Map.entry("iron_sword", new WeaponSpec(5.5D, -2.4D, 0.0D, "tooltip.earth_on_minecraft.weapon.iron_blank")),
            Map.entry("steel_sword", new WeaponSpec(7.0D, -2.2D, 0.0D, "tooltip.earth_on_minecraft.weapon.steel_blank")),
            Map.entry("diamond_sword", new WeaponSpec(8.0D, -2.2D, 0.0D, "tooltip.earth_on_minecraft.weapon.sword")),
            Map.entry("dagger", new WeaponSpec(4.2D, -1.5D, 0.0D, "tooltip.earth_on_minecraft.weapon.dagger")),
            Map.entry("spear", new WeaponSpec(5.8D, -2.6D, 1.25D, "tooltip.earth_on_minecraft.weapon.spear")),
            Map.entry("battle_axe", new WeaponSpec(8.0D, -3.0D, 0.0D, "tooltip.earth_on_minecraft.weapon.battle_axe")),
            Map.entry("war_hammer", new WeaponSpec(8.5D, -3.1D, 0.0D, "tooltip.earth_on_minecraft.weapon.war_hammer")),
            Map.entry("iron_axe", new WeaponSpec(7.0D, -3.0D, 0.0D, "tooltip.earth_on_minecraft.weapon.battle_axe")),
            Map.entry("steel_axe", new WeaponSpec(8.5D, -2.9D, 0.0D, "tooltip.earth_on_minecraft.weapon.battle_axe")),
            Map.entry("copper_pickaxe", new WeaponSpec(2.5D, -2.8D, 0.0D, "tooltip.earth_on_minecraft.weapon.tool")),
            Map.entry("iron_pickaxe", new WeaponSpec(3.5D, -2.8D, 0.0D, "tooltip.earth_on_minecraft.weapon.tool")),
            Map.entry("steel_pickaxe", new WeaponSpec(4.5D, -2.7D, 0.0D, "tooltip.earth_on_minecraft.weapon.tool"))
    );

    private static final Map<String, MedicineSpec> MEDICINES = Map.ofEntries(
            Map.entry("splint", new MedicineSpec(
                    3.0D, 0.72D, 0.0D, 0.0D, 0.0D, false, false, -1, 4,
                    "tooltip.earth_on_minecraft.medicine.splint")),
            Map.entry("plaster", new MedicineSpec(
                    4.0D, 0.85D, 0.0D, 0.0D, -5.0D, false, false, -1, 6,
                    "tooltip.earth_on_minecraft.medicine.plaster")),
            Map.entry("bandage", new MedicineSpec(
                    3.5D, 0.35D, 0.0D, 0.0D, 0.0D, false, false, -1, 5,
                    "tooltip.earth_on_minecraft.medicine.bandage")),
            Map.entry("antibiotic_ointment", new MedicineSpec(
                    5.0D, 0.30D, 0.0D, 0.0D, -8.0D, true, true, -1, 8,
                    "tooltip.earth_on_minecraft.medicine.antibiotic_ointment")),
            Map.entry("aspirin", new MedicineSpec(
                    2.0D, 0.15D, 0.0D, 0.0D, -18.0D, true, false, 0, 4,
                    "tooltip.earth_on_minecraft.medicine.aspirin")),
            Map.entry("burn_ointment", new MedicineSpec(
                    4.0D, 0.20D, 15.0D, 0.0D, -10.0D, true, false, 0, 6,
                    "tooltip.earth_on_minecraft.medicine.burn_ointment")),
            Map.entry("oral_rehydration_salts", new MedicineSpec(
                    2.0D, 0.0D, 0.0D, 45.0D, -25.0D, true, false, -1, 4,
                    "tooltip.earth_on_minecraft.medicine.oral_rehydration_salts")),
            Map.entry("saline_solution", new MedicineSpec(
                    3.5D, 0.15D, 20.0D, 38.0D, -20.0D, true, false, -1, 5,
                    "tooltip.earth_on_minecraft.medicine.saline_solution")),
            Map.entry("activated_charcoal_tablet", new MedicineSpec(
                    2.5D, 0.0D, 55.0D, 0.0D, -8.0D, false, true, -1, 3,
                    "tooltip.earth_on_minecraft.medicine.activated_charcoal_tablet")),
            Map.entry("vitamin_tablet", new MedicineSpec(
                    3.0D, 0.15D, 25.0D, 10.0D, -22.0D, false, false, 0, 6,
                    "tooltip.earth_on_minecraft.medicine.vitamin_tablet"))
    );

    private EarthMedicalAndWeaponSupport() {
    }

    public static Item.Properties applyWeaponAttributes(String id, Item.Properties props) {
        WeaponSpec spec = WEAPONS.get(id);
        if (spec == null) {
            return props;
        }
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, spec.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, spec.attackSpeedDelta(), AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
        if (spec.reachBonus() > 0.0D) {
            builder.add(Attributes.ENTITY_INTERACTION_RANGE,
                    new AttributeModifier(WEAPON_REACH_ID, spec.reachBonus(), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        return props.stacksTo(1).attributes(builder.build());
    }

    public static boolean isAnvilHammer(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() == EarthOnMinecraft.GEOLOGY_HAMMER.get()) {
            return true;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && EarthOnMinecraft.MODID.equals(id.getNamespace())
                && ("war_hammer".equals(id.getPath()) || "wrench".equals(id.getPath()));
    }

    public static void onWeaponHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !WEAPONS.containsKey(id.getPath())) {
            return;
        }
        ToolQualityComponent quality = stack.getOrDefault(EarthDataComponents.TOOL_QUALITY.get(), ToolQualityComponent.DEFAULT);
        int tier = quality.grade().tier();
        float bonusDamage = tier > Grade.COMMON.tier() ? (tier - Grade.COMMON.tier()) * 1.5F : 0.0F;
        String path = id.getPath();
        if ("war_hammer".equals(path)) {
            target.knockback(0.65D + 0.12D * tier,
                    attacker.getX() - target.getX(),
                    attacker.getZ() - target.getZ());
            bonusDamage += 1.5F;
        } else if ("dagger".equals(path)) {
            bonusDamage += 1.0F;
        }
        if (bonusDamage > 0.05F && !target.level().isClientSide()) {
            target.invulnerableTime = 0;
            target.hurt(attacker.damageSources().generic(), bonusDamage);
        }
    }

    public static InteractionResult tryUseMedicine(Level level, Player player, InteractionHand hand, ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return InteractionResult.PASS;
        }
        MedicineSpec spec = MEDICINES.get(id.getPath());
        if (spec == null) {
            return InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (!needsMedicine(player, spec)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.earth_on_minecraft.medicine.not_needed")
                        .withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            applyMedicineEffects(serverPlayer, spec);
            level.playSound(null, player.blockPosition(), SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.7F, 1.1F);
            player.getCooldowns().addCooldown(stack, 20);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.sendOverlayMessage(Component.translatable("message.earth_on_minecraft.medicine.applied",
                    stack.getHoverName()).withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    public static boolean needsMedicine(Player player, MedicineSpec spec) {
        if (player.getHealth() < player.getMaxHealth() - 0.05F && spec.partHeal() > 0.0D) {
            return true;
        }
        CompoundTag data = player.getPersistentData();
        double legInj = data.getDoubleOr("earth_human.body.injury.leg", 0.0D);
        double handInj = data.getDoubleOr("earth_human.body.injury.right_hand", 0.0D);
        double headHp = data.getDoubleOr("earth_human.body.head.health", 4.0D);
        double torsoHp = data.getDoubleOr("earth_human.body.torso.health", 8.0D);
        double limbsHp = data.getDoubleOr("earth_human.body.limbs.health", 8.0D);
        double scale = Math.max(1.0D, player.getMaxHealth() / 20.0D);
        boolean anyPartDamaged = headHp < 4.0D * scale - 0.05D
                || torsoHp < 8.0D * scale - 0.05D
                || limbsHp < 8.0D * scale - 0.05D;
        if (spec.fractureRelief() >= 0.60D) {
            return legInj > 0.01D || handInj > 0.01D || limbsHp < 8.0D * scale - 0.05D;
        }
        if (spec.partHeal() > 0.0D && (anyPartDamaged || legInj > 0.01D || handInj > 0.01D)) {
            return true;
        }
        double air = data.getDoubleOr("earth_human.air_quality", 100.0D);
        if (spec.lungAirRestore() > 0.0D && air < 99.0D) {
            return true;
        }
        double hydration = data.getDoubleOr("earth_human.hydration", 100.0D);
        if (spec.hydrationDelta() > 0.0D && hydration < 99.0D) {
            return true;
        }
        double fatigue = data.getDoubleOr("earth_human.fatigue", 0.0D);
        if (spec.fatigueDelta() < 0.0D && fatigue > 1.0D) {
            return true;
        }
        double temp = data.getDoubleOr("earth_human.body_temperature", 37.0D);
        if (spec.normalizeTemperature() && Math.abs(temp - 37.0D) > 0.35D) {
            return true;
        }
        if (spec.clearToxins() && (player.hasEffect(MobEffects.POISON)
                || player.hasEffect(MobEffects.WITHER)
                || player.hasEffect(MobEffects.NAUSEA)
                || player.hasEffect(MobEffects.MINING_FATIGUE))) {
            return true;
        }
        return spec.absorptionAmplifier() >= 0 && player.getAbsorptionAmount() < 2.0F;
    }

    public static void applyMedicineEffects(ServerPlayer player, MedicineSpec spec) {
        if (spec.partHeal() > 0.0D) {
            player.heal((float) (spec.partHeal() * 0.75D));
        }
        if (spec.regenSeconds() > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, spec.regenSeconds() * 20, 0, false, false, true));
        }
        if (spec.absorptionAmplifier() >= 0) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 90, spec.absorptionAmplifier(), false, false, true));
        }
        if (spec.clearToxins()) {
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.WITHER);
            player.removeEffect(MobEffects.NAUSEA);
            player.removeEffect(MobEffects.MINING_FATIGUE);
        }

        // Direct bridge to earth_human if present, with NBT fallback
        boolean bridged = false;
        try {
            Class<?> bodyClass = Class.forName("com.xxsx.earthhuman.HumanBodyStatus");
            Class<?> statusClass = Class.forName("com.xxsx.earthhuman.HumanStatus");
            Class<?> netClass = Class.forName("com.xxsx.earthhuman.HumanNetwork");

            Method getBody = bodyClass.getMethod("get", Player.class);
            Object body = getBody.invoke(null, player);
            if (spec.fractureRelief() > 0.0D) {
                Method treatFracture = bodyClass.getMethod("treatFracture", ServerPlayer.class, double.class, double.class);
                treatFracture.invoke(body, player, spec.fractureRelief(), spec.partHeal());
            } else if (spec.partHeal() > 0.0D) {
                Method healWorst = bodyClass.getMethod("healWorst", ServerPlayer.class, double.class, boolean.class);
                healWorst.invoke(body, player, spec.partHeal(), true);
            }
            Method applyFunc = bodyClass.getMethod("applyFunctionalEffects", ServerPlayer.class);
            applyFunc.invoke(body, player);
            Method saveBody = bodyClass.getMethod("save", Player.class);
            saveBody.invoke(body, player);
            Method syncBody = netClass.getMethod("syncBody", ServerPlayer.class, bodyClass);
            syncBody.invoke(null, player, body);

            Method getStatus = statusClass.getMethod("get", Player.class);
            Object status = getStatus.invoke(null, player);
            if (spec.hydrationDelta() != 0.0D) {
                double h = statusClass.getField("hydration").getDouble(status);
                statusClass.getField("hydration").setDouble(status, Math.max(0.0D, Math.min(100.0D, h + spec.hydrationDelta())));
            }
            if (spec.fatigueDelta() != 0.0D) {
                double f = statusClass.getField("fatigue").getDouble(status);
                statusClass.getField("fatigue").setDouble(status, Math.max(0.0D, Math.min(100.0D, f + spec.fatigueDelta())));
            }
            if (spec.lungAirRestore() > 0.0D) {
                double a = statusClass.getField("airQuality").getDouble(status);
                statusClass.getField("airQuality").setDouble(status, Math.max(0.0D, Math.min(100.0D, a + spec.lungAirRestore())));
            }
            if (spec.normalizeTemperature()) {
                double t = statusClass.getField("bodyTemperature").getDouble(status);
                double nextT = t + (37.0D - t) * 0.65D;
                statusClass.getField("bodyTemperature").setDouble(status, nextT);
            }
            Method saveStatus = statusClass.getMethod("save", Player.class);
            saveStatus.invoke(status, player);
            Method syncStatus = netClass.getMethod("sync", ServerPlayer.class, statusClass);
            syncStatus.invoke(null, player, status);
            bridged = true;
        } catch (Throwable ignored) {
        }

        if (!bridged) {
            CompoundTag data = player.getPersistentData();
            if (spec.fractureRelief() > 0.0D) {
                double leg = data.getDoubleOr("earth_human.body.injury.leg", 0.0D);
                double hand = data.getDoubleOr("earth_human.body.injury.right_hand", 0.0D);
                data.putDouble("earth_human.body.injury.leg", Math.max(0.0D, leg - spec.fractureRelief()));
                data.putDouble("earth_human.body.injury.right_hand", Math.max(0.0D, hand - spec.fractureRelief()));
            }
            if (spec.lungAirRestore() > 0.0D) {
                double air = data.getDoubleOr("earth_human.air_quality", 100.0D);
                data.putDouble("earth_human.air_quality", Math.min(100.0D, air + spec.lungAirRestore()));
            }
            if (spec.hydrationDelta() != 0.0D) {
                double h = data.getDoubleOr("earth_human.hydration", 100.0D);
                data.putDouble("earth_human.hydration", Math.max(0.0D, Math.min(100.0D, h + spec.hydrationDelta())));
            }
            if (spec.fatigueDelta() != 0.0D) {
                double f = data.getDoubleOr("earth_human.fatigue", 0.0D);
                data.putDouble("earth_human.fatigue", Math.max(0.0D, Math.min(100.0D, f + spec.fatigueDelta())));
            }
        }
    }

    public static void appendHoverText(Item item, Consumer<Component> lines) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) {
            return;
        }
        WeaponSpec weapon = WEAPONS.get(id.getPath());
        if (weapon != null) {
            lines.accept(Component.translatable(weapon.roleKey()).withStyle(ChatFormatting.GOLD));
        }
        MedicineSpec med = MEDICINES.get(id.getPath());
        if (med != null) {
            lines.accept(Component.translatable(med.tooltipKey()).withStyle(ChatFormatting.GREEN));
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.medicine.use_hint").withStyle(ChatFormatting.AQUA));
        }
    }
}
