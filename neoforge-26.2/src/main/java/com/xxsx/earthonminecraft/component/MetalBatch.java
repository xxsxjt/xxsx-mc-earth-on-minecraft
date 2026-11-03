package com.xxsx.earthonminecraft.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record MetalBatch(int schemaVersion, Alloy alloy, Map<String, Double> composition, long heatId,
                         double workability) {
    public static final MetalBatch DEFAULT = new MetalBatch(1, Alloy.IRON, Map.of(), 0L, 1.0);

    public static final Codec<MetalBatch> CODEC = RecordCodecBuilder.<MetalBatch>create(instance -> instance.group(
            ExtraCodecs.intRange(1, 1).optionalFieldOf("schema_version", 1).forGetter(MetalBatch::schemaVersion),
            Alloy.CODEC.optionalFieldOf("alloy", Alloy.IRON).forGetter(MetalBatch::alloy),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("composition", Map.of()).forGetter(MetalBatch::composition),
            ExtraCodecs.NON_NEGATIVE_LONG.optionalFieldOf("heat_id", 0L).forGetter(MetalBatch::heatId),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("workability", 1.0).forGetter(MetalBatch::workability)
    ).apply(instance, MetalBatch::new)).validate(MetalBatch::validate);

    private static DataResult<MetalBatch> validate(MetalBatch value) {
        if (value.schemaVersion != 1) {
            return DataResult.error(() -> "unsupported metal_batch schema_version " + value.schemaVersion);
        }
        if (value.composition.size() > 8) {
            return DataResult.error(() -> "metal_batch composition must contain at most 8 entries");
        }
        double sum = 0.0;
        for (Map.Entry<String, Double> entry : value.composition.entrySet()) {
            if (entry.getKey().isBlank()) {
                return DataResult.error(() -> "metal_batch composition keys must not be blank");
            }
            if (entry.getValue() < 0.00001) {
                return DataResult.error(() -> "metal_batch composition entries must be at least 0.00001");
            }
            sum += entry.getValue();
        }
        if (!value.composition.isEmpty() && Math.abs(sum - 1.0) > 0.002) {
            return DataResult.error(() -> "metal_batch composition must sum to 1.0 +/- 0.002");
        }
        return DataResult.success(value);
    }

    public static MetalBatch defaultFor(String alloyName) {
        Alloy alloy = Alloy.byName(alloyName);
        double workability = switch (alloy) {
            case SPONGE_IRON -> 0.45;
            case PIG_IRON, CAST_IRON -> 0.30;
            case WROUGHT_IRON -> 0.92;
            case HIGH_CARBON_STEEL -> 0.80;
            default -> 0.88;
        };
        return new MetalBatch(1, alloy, defaultComposition(alloy), 0L, workability);
    }

    public static Map<String, Double> defaultComposition(Alloy alloy) {
        return switch (alloy) {
            case SPONGE_IRON -> ordered("Fe", 0.860, "Slag", 0.115, "C", 0.018, "S", 0.004, "P", 0.003);
            case WROUGHT_IRON -> ordered("Fe", 0.9955, "Slag", 0.0030, "Mn", 0.0010, "C", 0.00015, "P", 0.00020, "S", 0.00015);
            case IRON -> Map.of("Fe", 0.985, "C", 0.008, "Mn", 0.005, "Si", 0.002);
            case STEEL -> Map.of("Fe", 0.982, "C", 0.010, "Mn", 0.006, "Si", 0.002);
            case HIGH_CARBON_STEEL -> ordered("Fe", 0.978, "C", 0.014, "Mn", 0.005, "Si", 0.002, "S", 0.0005, "P", 0.0005);
            case PIG_IRON -> ordered("Fe", 0.940, "C", 0.042, "Si", 0.012, "Mn", 0.004, "P", 0.002);
            case CAST_IRON -> ordered("Fe", 0.938, "C", 0.036, "Si", 0.018, "Mn", 0.005, "P", 0.002, "S", 0.001);
            case BRONZE -> Map.of("Cu", 0.880, "Sn", 0.100, "Zn", 0.015, "Pb", 0.005);
            case BRASS -> Map.of("Cu", 0.670, "Zn", 0.320, "Pb", 0.008, "Fe", 0.002);
            case CUPRONICKEL -> Map.of("Cu", 0.700, "Ni", 0.290, "Fe", 0.005, "Mn", 0.005);
            case MONEL -> ordered("Ni", 0.660, "Cu", 0.315, "Fe", 0.015, "Mn", 0.010);
            case SOLDER -> ordered("Sn", 0.600, "Pb", 0.380, "Sb", 0.015, "Cu", 0.005);
            case STAINLESS_STEEL -> Map.of("Fe", 0.710, "Cr", 0.180, "Ni", 0.080, "Mn", 0.020, "Si", 0.008, "C", 0.002);
            case ALUMINIUM -> Map.of("Al", 0.990, "Si", 0.006, "Fe", 0.004);
            case TITANIUM -> Map.of("Ti", 0.992, "Fe", 0.005, "O", 0.003);
            case COPPER -> ordered("Cu", 0.996, "O", 0.002, "Ag", 0.001, "Fe", 0.001);
            case GOLD -> ordered("Au", 0.995, "Ag", 0.004, "Cu", 0.001);
        };
    }

    public static Map<String, Double> ordered(Object... kvs) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvs.length; i += 2) {
            String key = (String) kvs[i];
            double val = ((Number) kvs[i + 1]).doubleValue();
            if (val >= 0.00001) {
                map.put(key, val);
            }
        }
        return Map.copyOf(map);
    }

    public double fractionOf(String species) {
        return composition.getOrDefault(species, 0.0);
    }

    public double carbonContent() {
        return fractionOf("C");
    }

    public double sulfurContent() {
        return fractionOf("S");
    }

    public double phosphorusContent() {
        return fractionOf("P");
    }

    public double slagContent() {
        return fractionOf("Slag") + fractionOf("SiO2");
    }

    public boolean isFerrous() {
        return fractionOf("Fe") >= 0.50;
    }

    /**
     * Classifies ferrous batches by the iron-carbon metallurgical window:
     * <ul>
     *   <li>{@code sponge_iron}: Slag &gt;= 3.0% (unconsolidated bloom)</li>
     *   <li>{@code wrought_iron}: C &lt; 0.02% (0.0002 mass fraction)</li>
     *   <li>{@code steel}: 0.2% &lt;= C &lt;= 2.1% (0.002..0.021 mass fraction)</li>
     *   <li>{@code transition_iron}: 0.02% &lt;= C &lt; 0.2% (low-carbon mild iron)</li>
     *   <li>{@code pig_iron}: C &gt; 2.1% (&gt; 0.021 mass fraction)</li>
     * </ul>
     */
    public String ironCarbonClass() {
        if (!isFerrous()) {
            return "non_ferrous";
        }
        if (slagContent() >= 0.030) {
            return "sponge_iron";
        }
        double c = carbonContent();
        if (c < 0.0002) {
            return "wrought_iron";
        }
        if (c >= 0.002 && c <= 0.021) {
            return "steel";
        }
        if (c > 0.021) {
            return "pig_iron";
        }
        return "low_carbon_iron";
    }

    /**
     * Maximum achievable quality grade capped by sulfur, phosphorus and slag inclusions.
     */
    public Grade maxGradeCap() {
        double sp = sulfurContent() + phosphorusContent();
        double slag = slagContent();
        if (sp > 0.005 || slag > 0.050) {
            return Grade.CRUDE;
        }
        if (sp > 0.0025 || slag > 0.015) {
            return Grade.COMMON;
        }
        if (sp > 0.0010 || slag > 0.005) {
            return Grade.FINE;
        }
        if (sp > 0.0004 || slag > 0.0015) {
            return Grade.MASTERWORK;
        }
        return Grade.HEIRLOOM;
    }

    public enum Alloy implements StringRepresentable {
        SPONGE_IRON("sponge_iron"),
        WROUGHT_IRON("wrought_iron"),
        IRON("iron"),
        STEEL("steel"),
        HIGH_CARBON_STEEL("high_carbon_steel"),
        PIG_IRON("pig_iron"),
        CAST_IRON("cast_iron"),
        BRONZE("bronze"),
        BRASS("brass"),
        CUPRONICKEL("cupronickel"),
        MONEL("monel"),
        SOLDER("solder"),
        STAINLESS_STEEL("stainless_steel"),
        ALUMINIUM("aluminium"),
        TITANIUM("titanium"),
        COPPER("copper"),
        GOLD("gold");

        public static final Codec<Alloy> CODEC = StringRepresentable.fromEnum(Alloy::values);

        private final String name;

        Alloy(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public static Alloy byName(String name) {
            String normalized = name.toLowerCase(Locale.ROOT);
            for (Alloy alloy : values()) {
                if (alloy.name.equals(normalized)) {
                    return alloy;
                }
            }
            return IRON;
        }
    }
}
