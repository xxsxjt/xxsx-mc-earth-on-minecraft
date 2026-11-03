package com.xxsx.earthonminecraft;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tracked geological host-rock blocks used by worldgen features that otherwise replace vanilla rock. */
public final class SpecLodeBlocks {
    public record Entry(String id, String hostRock, MapColor mapColor) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("andesite_volcanic_belt", "andesite", MapColor.STONE),
            new Entry("anthracite_deepslate_halo", "deepslate", MapColor.DEEPSLATE),
            new Entry("auriferous_quartz_calcite_halo", "calcite", MapColor.QUARTZ),
            new Entry("basalt_mafic_dike", "basalt", MapColor.COLOR_BLACK),
            new Entry("bituminous_sandstone_halo", "sandstone", MapColor.SAND),
            new Entry("chalcopyrite_granite_halo", "granite", MapColor.DIRT),
            new Entry("diorite_intrusion", "diorite", MapColor.QUARTZ),
            new Entry("granitic_intrusion", "granite", MapColor.DIRT),
            new Entry("kimberlite_tuff_halo", "tuff", MapColor.TERRACOTTA_GREEN),
            new Entry("limestone_calcite_bed", "calcite", MapColor.QUARTZ),
            new Entry("magnetite_tuff_halo", "tuff", MapColor.TERRACOTTA_GREEN),
            new Entry("sedimentary_sandstone_bed", "sandstone", MapColor.SAND),
            new Entry("tuff_volcanic_belt", "tuff", MapColor.TERRACOTTA_GREEN)
    );

    public static final Map<String, DeferredBlock<Block>> BY_ID = new LinkedHashMap<>();
    private static boolean registered;

    private SpecLodeBlocks() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        for (Entry entry : ENTRIES) {
            DeferredBlock<Block> block = EarthOnMinecraft.BLOCKS.registerBlock(entry.id(), DepositBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(entry.mapColor())
                            .strength(3.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.STONE));
            var item = EarthOnMinecraft.ITEMS.registerItem(entry.id(),
                    props -> new GuidedBlockItem(block.get(), props,
                            "tooltip.earth_on_minecraft.natural_source"),
                    props -> props);
            BY_ID.put(entry.id(), block);
            EarthOnMinecraft.addTabItem(item);
        }
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }
}
