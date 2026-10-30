package com.xxsx.earthonminecraft;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class GeologyHammerItem extends Item {
    public GeologyHammerItem(Properties properties) {
        super(properties.durability(96));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.geology_hammer")
                .withStyle(ChatFormatting.GRAY));
    }
}
