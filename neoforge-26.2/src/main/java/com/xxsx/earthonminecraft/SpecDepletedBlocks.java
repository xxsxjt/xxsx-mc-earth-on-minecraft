package com.xxsx.earthonminecraft;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The 37 depleted lode blocks named by {@code spec/eom/worldgen.jsonl}.
 *
 * <p>A lode that a miner has worked dry is replaced by its depleted form. These blocks look
 * like spent host rock and remain in the world after the reserve reaches zero. A bounded quarry can
 * later remove them as ordinary spent-rock blocks, so a worked-out region remains readable and
 * excavatable instead of silently refilling.</p>
 */
public final class SpecDepletedBlocks {
    public record Entry(String id, String lode) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("depleted_andesite_volcanic_belt", "andesite_volcanic_belt"),
            new Entry("depleted_anthracite_coal_seam", "anthracite_coal_seam"),
            new Entry("depleted_anthracite_deepslate_halo", "anthracite_deepslate_halo"),
            new Entry("depleted_auriferous_quartz_calcite_halo", "auriferous_quartz_calcite_halo"),
            new Entry("depleted_auriferous_quartz_vein", "auriferous_quartz_vein"),
            new Entry("depleted_basalt_mafic_dike", "basalt_mafic_dike"),
            new Entry("depleted_bauxite_laterite_deposit", "bauxite_laterite_deposit"),
            new Entry("depleted_bituminous_coal_seam", "bituminous_coal_seam"),
            new Entry("depleted_bituminous_sandstone_halo", "bituminous_sandstone_halo"),
            new Entry("depleted_borate_deposit", "borate_deposit"),
            new Entry("depleted_cassiterite_placer_deposit", "cassiterite_placer_deposit"),
            new Entry("depleted_chalcopyrite_granite_halo", "chalcopyrite_granite_halo"),
            new Entry("depleted_chalcopyrite_vein", "chalcopyrite_vein"),
            new Entry("depleted_chromite_lens", "chromite_lens"),
            new Entry("depleted_cinnabar_vein", "cinnabar_vein"),
            new Entry("depleted_diamondiferous_kimberlite_pipe", "diamondiferous_kimberlite_pipe"),
            new Entry("depleted_diorite_intrusion", "diorite_intrusion"),
            new Entry("depleted_emerald_beryl_vein", "emerald_beryl_vein"),
            new Entry("depleted_evaporite_salt_bed", "evaporite_salt_bed"),
            new Entry("depleted_galena_lens", "galena_lens"),
            new Entry("depleted_granitic_intrusion", "granitic_intrusion"),
            new Entry("depleted_kimberlite_pipe", "kimberlite_pipe"),
            new Entry("depleted_kimberlite_tuff_halo", "kimberlite_tuff_halo"),
            new Entry("depleted_lapis_lazuli_body", "lapis_lazuli_body"),
            new Entry("depleted_limestone_calcite_bed", "limestone_calcite_bed"),
            new Entry("depleted_magnetite_lens", "magnetite_lens"),
            new Entry("depleted_magnetite_tuff_halo", "magnetite_tuff_halo"),
            new Entry("depleted_manganite_lens", "manganite_lens"),
            new Entry("depleted_phosphorite_bed", "phosphorite_bed"),
            new Entry("depleted_poor_magnetite_lens", "poor_magnetite_lens"),
            new Entry("depleted_rare_earth_carbonatite", "rare_earth_carbonatite"),
            new Entry("depleted_redstone_mineral_body", "redstone_mineral_body"),
            new Entry("depleted_rich_magnetite_lens", "rich_magnetite_lens"),
            new Entry("depleted_sedimentary_sandstone_bed", "sedimentary_sandstone_bed"),
            new Entry("depleted_sphalerite_lens", "sphalerite_lens"),
            new Entry("depleted_tuff_volcanic_belt", "tuff_volcanic_belt"),
            new Entry("depleted_uraninite_lens", "uraninite_lens")
    );

    public static final Map<String, DeferredBlock<Block>> BY_ID = new LinkedHashMap<>();
    private static boolean registered;

    private SpecDepletedBlocks() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        for (Entry entry : ENTRIES) {
            DeferredBlock<Block> block = EarthOnMinecraft.BLOCKS.registerBlock(entry.id(), DepositBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(2.0F, 4.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.STONE));
            var item = EarthOnMinecraft.ITEMS.registerItem(entry.id(),
                    props -> new GuidedBlockItem(block.get(), props,
                            "tooltip.earth_on_minecraft.depleted_lode"),
                    props -> props);
            BY_ID.put(entry.id(), block);
            EarthOnMinecraft.addTabItem(item);
        }
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static boolean isDepleted(Block block) {
        return BY_ID.values().stream().anyMatch(holder -> holder.get() == block);
    }

    public static String lodeFor(Block block) {
        return ENTRIES.stream()
                .filter(entry -> BY_ID.containsKey(entry.id()) && BY_ID.get(entry.id()).get() == block)
                .map(Entry::lode)
                .findFirst()
                .orElse("");
    }

    /**
     * The depleted block state for a lode, or {@code null} when the spec names none. This is what
     * a bore head swaps in once it has drained a lode.
     */
    public static net.minecraft.world.level.block.state.BlockState byLode(net.minecraft.resources.Identifier lodeId) {
        if (lodeId == null) {
            return null;
        }
        String name = lodeId.getPath();
        for (Entry entry : ENTRIES) {
            if (entry.lode().equals(name)) {
                DeferredBlock<Block> holder = BY_ID.get(entry.id());
                return holder == null ? null : holder.get().defaultBlockState();
            }
        }
        return null;
    }
}
