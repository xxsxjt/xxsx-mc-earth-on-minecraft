package com.xxsx.earthonminecraft;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public class MachineBlockItem extends BlockItem {
    private final ProcessingMachineBlock.Kind kind;

    public MachineBlockItem(Block block, Properties properties, ProcessingMachineBlock.Kind kind) {
        super(block, properties);
        this.kind = kind;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getBlock().getDescriptionId());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        RouteGuide.addBeginnerShortcutTips(stack.getItem(), lines);
        lines.accept(Component.translatable(kind.descriptionKey()).withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.machine.use").withStyle(ChatFormatting.AQUA));
        lines.accept(Component.translatable(kind.powerMode().labelKey()).withStyle(ChatFormatting.YELLOW));
        if (kind.powerMode().manualFallback()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.machine.crank_hint").withStyle(ChatFormatting.GOLD));
        }
        lines.accept(Component.translatable("screen.earth_on_minecraft.machine.spec",
                Math.max(1, (kind.processTicks() + 19) / 20), kind.energyPerTick()).withStyle(ChatFormatting.AQUA));
        lines.accept(Component.translatable(MachineMultiblock.patternFor(kind).descriptionKey()).withStyle(ChatFormatting.GREEN));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.multiblock.legend").withStyle(ChatFormatting.DARK_GREEN));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.machine.redstone").withStyle(ChatFormatting.GREEN));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.machine.routes", ProcessingMachineBlock.recipesFor(kind).size())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
