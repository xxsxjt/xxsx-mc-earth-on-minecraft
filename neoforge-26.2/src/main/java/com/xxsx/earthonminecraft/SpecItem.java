package com.xxsx.earthonminecraft;

import com.xxsx.earthonminecraft.component.QualityComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class SpecItem extends Item {
    public SpecItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionResult medResult = EarthMedicalAndWeaponSupport.tryUseMedicine(level, player, hand, stack);
        if (medResult != InteractionResult.PASS) {
            return medResult;
        }
        return super.use(level, player, hand);
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        EarthMedicalAndWeaponSupport.onWeaponHurtEnemy(stack, target, attacker);
        super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        EarthMedicalAndWeaponSupport.appendHoverText(this, lines);
        QualityComponents.appendTooltip(stack, lines);
        EarthOnMinecraftTooltips.addMaterialDetails(stack, lines, flag);
        EarthOnMinecraftTooltips.addRouteTips(stack, lines);
    }
}
