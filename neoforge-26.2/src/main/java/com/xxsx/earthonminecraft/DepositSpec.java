package com.xxsx.earthonminecraft;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps lode blocks to their spec identity and starting reserve size.
 *
 * <p>The numbers come from {@code spec/eom/worldgen.jsonl}: every lode declares
 * {@code lodes_per_region} and {@code reserve_chunks}. A single placed block represents one lode,
 * so its capacity is the per-lode share of the region's reserve, floored at a sane minimum so
 * small deposits are still worth building a machine for.</p>
 */
public final class DepositSpec {
    /** Minimum units any lode holds, so a small body is not pointless to work. */
    private static final int MIN_CAPACITY = 16;
    /**
     * Gameplay multiplier applied on top of the spec reserve. The spec numbers keep the geological
     * proportions between lodes; this knob sets how much a single block is worth in play.
     */
    public static final int RESERVE_MULTIPLIER = 2;

    public record Lode(String id, String blockId, int capacity, String depletedBlockId) {
    }

    private static final Map<Block, Lode> BY_BLOCK = new LinkedHashMap<>();
    private static final Map<String, Lode> BY_ID = new LinkedHashMap<>();
    private static final Map<String, Block> BLOCK_BY_LODE = new LinkedHashMap<>();
    private static boolean bound;

    private DepositSpec() {
    }

    /**
     * Binds the lode table. Called after blocks are registered, because it needs the block
     * objects to key the lookup.
     */
    public static void bind() {
        if (bound) {
            return;
        }
        for (SpecDepletedBlocks.Entry entry : SpecDepletedBlocks.entries()) {
            String lodeId = entry.lode();
            Block block = blockForLode(lodeId);
            if (block == null) {
                continue;
            }
            var depleted = SpecDepletedBlocks.BY_ID.get(entry.id());
            Lode lode = new Lode(
                    lodeId,
                    lodeId,
                    capacityFor(lodeId),
                    depleted == null ? "" : entry.id());
            BY_BLOCK.put(block, lode);
            BY_ID.put(lodeId, lode);
            BLOCK_BY_LODE.put(lodeId, block);
        }
        bound = true;
    }

    private static Block blockForLode(String lodeId) {
        String blockId = switch (lodeId) {
            case "magnetite_lens" -> "magnetite_ore";
            case "poor_magnetite_lens" -> "poor_magnetite_ore";
            case "rich_magnetite_lens" -> "rich_magnetite_ore";
            case "andesite_volcanic_belt", "anthracite_deepslate_halo", "auriferous_quartz_calcite_halo",
                    "basalt_mafic_dike", "bituminous_sandstone_halo", "chalcopyrite_granite_halo",
                    "diorite_intrusion", "granitic_intrusion", "kimberlite_tuff_halo",
                    "limestone_calcite_bed", "magnetite_tuff_halo", "sedimentary_sandstone_bed",
                    "tuff_volcanic_belt" -> lodeId;
            case "kimberlite_pipe" -> "kimberlite";
            case "chalcopyrite_vein" -> "chalcopyrite_ore";
            case "chromite_lens" -> "chromite_ore";
            case "diamondiferous_kimberlite_pipe" -> "diamondiferous_kimberlite";
            case "lapis_lazuli_body" -> "lapis_lazuli_ore";
            case "manganite_lens" -> "manganite_ore";
            case "sphalerite_lens" -> "sphalerite_ore";
            case "galena_lens" -> "galena_ore";
            case "uraninite_lens" -> "uraninite_ore";
            case "redstone_mineral_body" -> "redstone_mineral_ore";
            case "anthracite_coal_seam", "auriferous_quartz_vein", "bauxite_laterite_deposit",
                    "bituminous_coal_seam", "borate_deposit", "cassiterite_placer_deposit",
                    "cinnabar_vein", "emerald_beryl_vein", "evaporite_salt_bed",
                    "phosphorite_bed", "rare_earth_carbonatite" -> lodeId;
            default -> null;
        };
        if (blockId == null) {
            return null;
        }
        var specBlock = SpecBlocks.BY_ID.get(blockId);
        if (specBlock != null) {
            return specBlock.get();
        }
        var lodeBlock = SpecLodeBlocks.BY_ID.get(blockId);
        if (lodeBlock != null) {
            return lodeBlock.get();
        }
        return switch (blockId) {
            case "poor_magnetite_ore" -> EarthOnMinecraft.POOR_MAGNETITE_ORE.get();
            case "magnetite_ore" -> EarthOnMinecraft.MAGNETITE_ORE.get();
            case "rich_magnetite_ore" -> EarthOnMinecraft.RICH_MAGNETITE_ORE.get();
            case "chalcopyrite_ore" -> EarthOnMinecraft.CHALCOPYRITE_ORE.get();
            case "auriferous_quartz_vein" -> EarthOnMinecraft.AURIFEROUS_QUARTZ_VEIN.get();
            case "bituminous_coal_seam" -> EarthOnMinecraft.BITUMINOUS_COAL_SEAM.get();
            case "anthracite_coal_seam" -> EarthOnMinecraft.ANTHRACITE_COAL_SEAM.get();
            case "kimberlite" -> EarthOnMinecraft.KIMBERLITE.get();
            case "diamondiferous_kimberlite" -> EarthOnMinecraft.DIAMONDIFEROUS_KIMBERLITE.get();
            case "lapis_lazuli_ore" -> EarthOnMinecraft.LAPIS_LAZULI_ORE.get();
            case "emerald_beryl_vein" -> EarthOnMinecraft.EMERALD_BERYL_VEIN.get();
            case "redstone_mineral_ore" -> EarthOnMinecraft.REDSTONE_MINERAL_ORE.get();
            case "cinnabar_vein" -> EarthOnMinecraft.CINNABAR_VEIN.get();
            case "bauxite_laterite_deposit" -> EarthOnMinecraft.BAUXITE_LATERITE_DEPOSIT.get();
            case "phosphorite_bed" -> EarthOnMinecraft.PHOSPHORITE_BED.get();
            case "evaporite_salt_bed" -> EarthOnMinecraft.EVAPORITE_SALT_BED.get();
            case "cassiterite_placer_deposit" -> EarthOnMinecraft.CASSITERITE_PLACER_DEPOSIT.get();
            case "rare_earth_carbonatite" -> EarthOnMinecraft.RARE_EARTH_CARBONATITE.get();
            default -> null;
        };
    }

    /**
     * Capacity in ore units for one lode block: the spec share floored at {@link #MIN_CAPACITY},
     * times {@link #RESERVE_MULTIPLIER}. Blocks whose reserve was already initialised keep their
     * saved capacity; only newly read blocks use this value.
     */
    public static int capacityFor(String lodeId) {
        Integer spec = SpecLodeReserves.capacity(lodeId);
        int base = spec == null ? DepositReserve.DEFAULT_CAPACITY : Math.max(MIN_CAPACITY, spec);
        return base * RESERVE_MULTIPLIER;
    }

    public static int capacityFor(Identifier lodeId) {
        return capacityFor(lodeId.getPath());
    }

    /** The lode a block belongs to, or a synthetic one so the reserve still works. */
    public static Identifier lodeFor(Block block) {
        bind();
        Lode lode = BY_BLOCK.get(block);
        if (lode != null) {
            return Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, lode.id());
        }
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, "unknown_lode") : id;
    }

    /** The depleted block for a lode id, or {@code null} when the spec names none. */
    public static net.minecraft.world.level.block.state.BlockState byLode(Identifier lodeId) {
        bind();
        Lode lode = BY_ID.get(lodeId.getPath());
        if (lode == null || lode.depletedBlockId().isEmpty()) {
            return null;
        }
        var holder = SpecDepletedBlocks.BY_ID.get(lode.depletedBlockId());
        return holder == null ? null : holder.get().defaultBlockState();
    }

    public static boolean isLode(Block block) {
        bind();
        return BY_BLOCK.containsKey(block);
    }

    /** The live block that represents a lode, or {@code null} for an unknown lode id. */
    public static Block blockOf(String lodeId) {
        bind();
        return BLOCK_BY_LODE.get(lodeId);
    }

    /** Lode id path of a live lode block, or {@code null} when the block is not a live lode. */
    public static String lodeIdOf(Block block) {
        bind();
        Lode lode = BY_BLOCK.get(block);
        return lode == null ? null : lode.id();
    }

    public static boolean isDepleted(Block block) {
        return SpecDepletedBlocks.BY_ID.values().stream().anyMatch(holder -> holder.get() == block);
    }

    public static java.util.Collection<Lode> lodes() {
        bind();
        return BY_ID.values();
    }
}
