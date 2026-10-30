package com.xxsx.earthonminecraft;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-lode ore capacity, generated from {@code spec/eom/worldgen.jsonl}.
 *
 * <p>A lode block represents one ore body. Its capacity is the region reserve
 * ({@code reserve_chunks}) divided across the region's lodes ({@code lodes_per_region}),
 * so a rich body outlasts a lean one by the ratio the spec already states. Do not edit by
 * hand: regenerate with {@code tools/generate_lode_reserves.py}.</p>
 */
public final class SpecLodeReserves {
    private static final Map<String, Integer> CAPACITY = new LinkedHashMap<>();

    static {
        CAPACITY.put("andesite_volcanic_belt", 16);  // lodes/region=10, reserve_chunks=9
        CAPACITY.put("anthracite_coal_seam", 144);  // lodes/region=6, reserve_chunks=72
        CAPACITY.put("anthracite_deepslate_halo", 16);  // lodes/region=7, reserve_chunks=8
        CAPACITY.put("auriferous_quartz_calcite_halo", 16);  // lodes/region=20, reserve_chunks=8
        CAPACITY.put("auriferous_quartz_vein", 16);  // lodes/region=16, reserve_chunks=8
        CAPACITY.put("basalt_mafic_dike", 16);  // lodes/region=14, reserve_chunks=8
        CAPACITY.put("bauxite_laterite_deposit", 16);  // lodes/region=12, reserve_chunks=9
        CAPACITY.put("bituminous_coal_seam", 504);  // lodes/region=2, reserve_chunks=84
        CAPACITY.put("bituminous_sandstone_halo", 32);  // lodes/region=3, reserve_chunks=8
        CAPACITY.put("borate_deposit", 16);  // lodes/region=12, reserve_chunks=4.0328
        CAPACITY.put("cassiterite_placer_deposit", 16);  // lodes/region=20, reserve_chunks=8
        CAPACITY.put("chalcopyrite_granite_halo", 16);  // lodes/region=8, reserve_chunks=8
        CAPACITY.put("chalcopyrite_vein", 16);  // lodes/region=8, reserve_chunks=9
        CAPACITY.put("chromite_lens", 16);  // lodes/region=12, reserve_chunks=4.0328
        CAPACITY.put("cinnabar_vein", 16);  // lodes/region=18, reserve_chunks=8
        CAPACITY.put("diamondiferous_kimberlite_pipe", 16);  // lodes/region=96, reserve_chunks=8
        CAPACITY.put("diorite_intrusion", 16);  // lodes/region=14, reserve_chunks=8
        CAPACITY.put("emerald_beryl_vein", 16);  // lodes/region=10, reserve_chunks=8
        CAPACITY.put("evaporite_salt_bed", 16);  // lodes/region=18, reserve_chunks=9
        CAPACITY.put("galena_lens", 16);  // lodes/region=6, reserve_chunks=4.8672
        CAPACITY.put("granitic_intrusion", 16);  // lodes/region=10, reserve_chunks=10
        CAPACITY.put("kimberlite_pipe", 16);  // lodes/region=36, reserve_chunks=10
        CAPACITY.put("kimberlite_tuff_halo", 16);  // lodes/region=40, reserve_chunks=8
        CAPACITY.put("lapis_lazuli_body", 16);  // lodes/region=14, reserve_chunks=8
        CAPACITY.put("limestone_calcite_bed", 100);  // lodes/region=6, reserve_chunks=50
        CAPACITY.put("magnetite_lens", 26);  // lodes/region=5, reserve_chunks=11
        CAPACITY.put("magnetite_tuff_halo", 32);  // lodes/region=3, reserve_chunks=8
        CAPACITY.put("manganite_lens", 16);  // lodes/region=12, reserve_chunks=4.0328
        CAPACITY.put("phosphorite_bed", 16);  // lodes/region=14, reserve_chunks=10
        CAPACITY.put("poor_magnetite_lens", 48);  // lodes/region=3, reserve_chunks=12
        CAPACITY.put("rare_earth_carbonatite", 16);  // lodes/region=36, reserve_chunks=8
        CAPACITY.put("redstone_mineral_body", 16);  // lodes/region=10, reserve_chunks=8
        CAPACITY.put("rich_magnetite_lens", 16);  // lodes/region=16, reserve_chunks=8
        CAPACITY.put("sedimentary_sandstone_bed", 216);  // lodes/region=4, reserve_chunks=72
        CAPACITY.put("sphalerite_lens", 16);  // lodes/region=6, reserve_chunks=4.8672
        CAPACITY.put("tuff_volcanic_belt", 16);  // lodes/region=9, reserve_chunks=9
        CAPACITY.put("uraninite_lens", 16);  // lodes/region=24, reserve_chunks=2.5538
    }

    private SpecLodeReserves() {
    }

    /** Ore units one lode block holds, or {@code null} when the lode is unknown. */
    public static Integer capacity(String lodeId) {
        return CAPACITY.get(lodeId);
    }

    public static Map<String, Integer> all() {
        return Map.copyOf(CAPACITY);
    }
}
