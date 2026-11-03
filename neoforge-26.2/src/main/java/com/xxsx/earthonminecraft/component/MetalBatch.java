package com.xxsx.earthonminecraft.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

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
            if (entry.getValue() < 0.001) {
                return DataResult.error(() -> "metal_batch composition entries must be at least 0.001");
            }
            sum += entry.getValue();
        }
        if (!value.composition.isEmpty() && Math.abs(sum - 1.0) > 0.001) {
            return DataResult.error(() -> "metal_batch composition must sum to 1.0 +/- 0.001");
        }
        return DataResult.success(value);
    }

    public static MetalBatch defaultFor(String alloyName) {
        Alloy alloy = Alloy.byName(alloyName);
        return new MetalBatch(1, alloy, defaultComposition(alloy), 0L, 1.0);
    }

    public static Map<String, Double> defaultComposition(Alloy alloy) {
        return switch (alloy) {
            case IRON -> Map.of("Fe", 0.985, "C", 0.008, "Mn", 0.005, "Si", 0.002);
            case STEEL -> Map.of("Fe", 0.982, "C", 0.010, "Mn", 0.006, "Si", 0.002);
            case BRONZE -> Map.of("Cu", 0.880, "Sn", 0.100, "Zn", 0.015, "Pb", 0.005);
            case BRASS -> Map.of("Cu", 0.670, "Zn", 0.320, "Pb", 0.008, "Fe", 0.002);
            case CUPRONICKEL -> Map.of("Cu", 0.700, "Ni", 0.290, "Fe", 0.005, "Mn", 0.005);
            case STAINLESS_STEEL -> Map.of("Fe", 0.710, "Cr", 0.180, "Ni", 0.080, "Mn", 0.020, "Si", 0.008, "C", 0.002);
            case ALUMINIUM -> Map.of("Al", 0.990, "Si", 0.006, "Fe", 0.004);
            case TITANIUM -> Map.of("Ti", 0.992, "Fe", 0.005, "O", 0.003);
        };
    }

    public enum Alloy implements StringRepresentable {
        IRON("iron"),
        STEEL("steel"),
        BRONZE("bronze"),
        BRASS("brass"),
        CUPRONICKEL("cupronickel"),
        STAINLESS_STEEL("stainless_steel"),
        ALUMINIUM("aluminium"),
        TITANIUM("titanium");

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
