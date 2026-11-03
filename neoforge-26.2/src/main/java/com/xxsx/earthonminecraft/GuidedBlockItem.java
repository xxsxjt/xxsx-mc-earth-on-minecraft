package com.xxsx.earthonminecraft;

import com.xxsx.earthonminecraft.component.QualityComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public class GuidedBlockItem extends BlockItem {
    private final String hintKey;

    public GuidedBlockItem(Block block, Properties properties, String hintKey) {
        super(block, properties);
        this.hintKey = hintKey;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getBlock().getDescriptionId());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable(hintKey).withStyle(ChatFormatting.GOLD));
        QualityComponents.appendTooltip(stack, lines);
        EarthOnMinecraftTooltips.addMaterialDetails(stack, lines, flag);
        EarthOnMinecraftTooltips.addRouteTips(stack, lines);
    }
}
