package com.xxsx.earthonminecraft;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SpecBlocks {
    public record BlockEntry(String id, String family, float hardness) {
    }

    private static final List<BlockEntry> ENTRIES = List.of(
            new BlockEntry("air_inlet", "multiblock", 3.0F),
            new BlockEntry("borate_deposit", "ore_block", 3.5F),
            new BlockEntry("carbonator", "device", 3.0F),
            new BlockEntry("chimney", "multiblock", 3.0F),
            new BlockEntry("chromite_ore", "ore_block", 3.5F),
            new BlockEntry("clay_brick_block", "multiblock", 3.0F),
            new BlockEntry("coffee_grinder", "device", 3.0F),
            new BlockEntry("firebrick_block", "multiblock", 3.0F),
            new BlockEntry("forge", "device", 3.0F),
            new BlockEntry("galena_ore", "ore_block", 3.5F),
            new BlockEntry("grindstone", "device", 3.0F),
            new BlockEntry("hammer", "device", 3.0F),
            new BlockEntry("juice_press", "device", 3.0F),
            new BlockEntry("manganite_ore", "ore_block", 3.5F),
            new BlockEntry("mold", "device", 3.0F),
            new BlockEntry("oven", "device", 3.0F),
            new BlockEntry("oven_mouth", "multiblock", 3.0F),
            new BlockEntry("packed_earth", "building", 1.5F),
            new BlockEntry("pasteurizer", "device", 3.0F),
            new BlockEntry("quench_tank", "device", 3.0F),
            new BlockEntry("rolling_mill", "device", 3.0F),
            new BlockEntry("sphalerite_ore", "ore_block", 3.5F),
            new BlockEntry("survey_stake", "building", 2.5F),
            new BlockEntry("tongs", "device", 3.0F),
            new BlockEntry("tuyere", "multiblock", 3.0F),
            new BlockEntry("uraninite_ore", "ore_block", 3.5F),
            new BlockEntry("wire_die", "device", 3.0F)
    );

    public static final Map<String, DeferredBlock<Block>> BY_ID = new LinkedHashMap<>();
    private static final java.util.List<DeferredBlock<Block>> ORE_BLOCKS = new java.util.ArrayList<>();
    private static boolean registered;

    private SpecBlocks() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        for (BlockEntry entry : ENTRIES) {
            boolean ore = entry.family().equals("ore_block");
            DeferredBlock<Block> block = ore
                    ? EarthOnMinecraft.BLOCKS.registerBlock(entry.id(), DepositBlock::new,
                            () -> BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.STONE)
                                    .strength(entry.hardness(), entry.hardness() * 2.0F)
                                    .requiresCorrectToolForDrops()
                                    .sound(SoundType.STONE))
                    : EarthOnMinecraft.BLOCKS.registerSimpleBlock(entry.id(),
                            () -> BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.STONE)
                                    .strength(entry.hardness(), entry.hardness() * 2.0F)
                                    .requiresCorrectToolForDrops()
                                    .sound(entry.family().equals("device") ? SoundType.METAL : SoundType.STONE));
            var item = EarthOnMinecraft.ITEMS.registerItem(entry.id(),
                    props -> new GuidedBlockItem(block.get(), props, ore
                            ? "tooltip.earth_on_minecraft.natural_source"
                            : "tooltip.earth_on_minecraft.block.header"),
                    props -> props);
            BY_ID.put(entry.id(), block);
            if (ore) {
                ORE_BLOCKS.add(block);
            }
            EarthOnMinecraft.addTabItem(item);
        }
    }

    /** Spec ore blocks that carry a finite deposit reserve. */
    public static java.util.List<DeferredBlock<Block>> oreBlocks() {
        return java.util.List.copyOf(ORE_BLOCKS);
    }
}
