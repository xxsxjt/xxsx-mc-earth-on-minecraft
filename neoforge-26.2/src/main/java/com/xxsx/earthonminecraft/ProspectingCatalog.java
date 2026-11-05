package com.xxsx.earthonminecraft;

import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Shared lode classification for prospecting: the geology hammer, the ore scanner and the
 * worldgen surface signal all read the same tables here.
 *
 * <p>Every table is keyed by lode id ({@link DepositSpec#lodeFor}), so a lode keeps one
 * classification no matter which block form it was placed as.</p>
 */
public final class ProspectingCatalog {
    /** Host-rock strata laid down everywhere; they hold reserves but are not ore targets. */
    public static final Set<String> BACKGROUND_STRATA = Set.of(
            "sedimentary_sandstone_bed", "limestone_calcite_bed", "granitic_intrusion",
            "diorite_intrusion", "andesite_volcanic_belt", "tuff_volcanic_belt", "basalt_mafic_dike");

    /** The one background layer players do look for: limestone feeds lime, cement and flux. */
    public static final String LIMESTONE = "limestone_calcite_bed";

    /** Main lode -> the halo rock that forms its surface signal. Lodes not listed show themselves. */
    private static final Map<String, String> HALO_FOR_LODE = Map.of(
            "poor_magnetite_lens", "magnetite_tuff_halo",
            "magnetite_lens", "magnetite_tuff_halo",
            "rich_magnetite_lens", "magnetite_tuff_halo",
            "chalcopyrite_vein", "chalcopyrite_granite_halo",
            "auriferous_quartz_vein", "auriferous_quartz_calcite_halo",
            "bituminous_coal_seam", "bituminous_sandstone_halo",
            "anthracite_coal_seam", "anthracite_deepslate_halo",
            "kimberlite_pipe", "kimberlite_tuff_halo",
            "diamondiferous_kimberlite_pipe", "kimberlite_tuff_halo");

    /** Ore scanner targets, in the order sneak-use cycles through them. */
    public enum Filter {
        ALL("all", Set.of()),
        IRON("iron", Set.of("poor_magnetite_lens", "magnetite_lens", "rich_magnetite_lens",
                "magnetite_tuff_halo")),
        COPPER("copper", Set.of("chalcopyrite_vein", "chalcopyrite_granite_halo")),
        GOLD("gold", Set.of("auriferous_quartz_vein", "auriferous_quartz_calcite_halo")),
        COAL("coal", Set.of("bituminous_coal_seam", "bituminous_sandstone_halo",
                "anthracite_coal_seam", "anthracite_deepslate_halo")),
        TIN("tin", Set.of("cassiterite_placer_deposit")),
        LEAD_ZINC("lead_zinc", Set.of("galena_lens", "sphalerite_lens")),
        GEMS("gems", Set.of("kimberlite_pipe", "kimberlite_tuff_halo", "diamondiferous_kimberlite_pipe",
                "emerald_beryl_vein", "lapis_lazuli_body")),
        RARE("rare", Set.of("rare_earth_carbonatite", "uraninite_lens", "cinnabar_vein",
                "redstone_mineral_body")),
        INDUSTRIAL("industrial", Set.of("bauxite_laterite_deposit", "phosphorite_bed", "evaporite_salt_bed",
                "borate_deposit", "chromite_lens", "manganite_lens", LIMESTONE));

        private static final List<Filter> ORDER = List.of(values());

        private final String key;
        private final Set<String> lodes;

        Filter(String key, Set<String> lodes) {
            this.key = key;
            this.lodes = lodes;
        }

        /** Stable id stored on the scanner stack. */
        public String key() {
            return key;
        }

        public String translationKey() {
            return "message.earth_on_minecraft.ore_scanner.filter." + key;
        }

        public boolean accepts(String lodeId) {
            return this == ALL ? isProspectable(lodeId) : lodes.contains(lodeId);
        }

        /** Lode ids of a named family; empty for {@code ALL}, which is defined by exclusion. */
        public Set<String> lodes() {
            return lodes;
        }

        public Filter next() {
            return ORDER.get((ordinal() + 1) % ORDER.size());
        }

        public static Filter byKey(String key) {
            for (Filter filter : ORDER) {
                if (filter.key.equals(key)) {
                    return filter;
                }
            }
            return ALL;
        }
    }

    private ProspectingCatalog() {
    }

    public static boolean isHalo(String lodeId) {
        return lodeId.endsWith("_halo");
    }

    public static boolean isBackground(String lodeId) {
        return BACKGROUND_STRATA.contains(lodeId);
    }

    /**
     * Lodes the hammer and the scanner's "all" target report: ore bodies and their halos, plus
     * limestone; host-rock strata are left out so they do not drown the answer.
     */
    public static boolean isProspectable(String lodeId) {
        return !isBackground(lodeId) || LIMESTONE.equals(lodeId);
    }

    /** Main ore bodies, the only lodes that leave a surface signal. */
    public static boolean isMainLode(String lodeId) {
        return !isHalo(lodeId) && !isBackground(lodeId);
    }

    /**
     * The block placed at the surface above a main lode: its halo rock when it has one, the lode's
     * own block as an outcrop otherwise. Empty for halos, host strata and unknown ids.
     */
    public static Optional<Block> surfaceSignalFor(String lodeId) {
        if (!isMainLode(lodeId)) {
            return Optional.empty();
        }
        String signalLode = HALO_FOR_LODE.getOrDefault(lodeId, lodeId);
        return Optional.ofNullable(DepositSpec.blockOf(signalLode));
    }

    /** Whether the surface signal for this lode is a halo patch rather than an outcrop. */
    public static boolean signalsWithHalo(String lodeId) {
        return HALO_FOR_LODE.containsKey(lodeId);
    }
}
