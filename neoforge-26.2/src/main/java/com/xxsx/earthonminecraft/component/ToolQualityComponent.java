package com.xxsx.earthonminecraft.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

public record ToolQualityComponent(int schemaVersion, Grade grade, String smith, double rolls) {
    public static final ToolQualityComponent DEFAULT = new ToolQualityComponent(1, Grade.COMMON, "", 0.5);

    public static final Codec<ToolQualityComponent> CODEC = RecordCodecBuilder.<ToolQualityComponent>create(instance -> instance.group(
            ExtraCodecs.intRange(1, 1).optionalFieldOf("schema_version", 1).forGetter(ToolQualityComponent::schemaVersion),
            Grade.CODEC.optionalFieldOf("grade", Grade.COMMON).forGetter(ToolQualityComponent::grade),
            Codec.string(0, 32).optionalFieldOf("smith", "").forGetter(ToolQualityComponent::smith),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("rolls", 0.5).forGetter(ToolQualityComponent::rolls)
    ).apply(instance, ToolQualityComponent::new)).validate(ToolQualityComponent::validate);

    private static DataResult<ToolQualityComponent> validate(ToolQualityComponent value) {
        if (value.schemaVersion != 1) {
            return DataResult.error(() -> "unsupported tool_quality schema_version " + value.schemaVersion);
        }
        return DataResult.success(value);
    }

    public double durabilityMultiplier() {
        return switch (grade) {
            case CRUDE -> 0.60;
            case COMMON -> 1.00;
            case FINE -> 1.30;
            case MASTERWORK -> 1.60;
            case HEIRLOOM -> 2.00;
        };
    }

    public double damageMultiplier() {
        return switch (grade) {
            case CRUDE -> 0.80;
            case COMMON -> 1.00;
            case FINE -> 1.10;
            case MASTERWORK -> 1.20;
            case HEIRLOOM -> 1.35;
        };
    }

    public double miningSpeedMultiplier() {
        return switch (grade) {
            case CRUDE -> 0.90;
            case COMMON -> 1.00;
            case FINE -> 1.10;
            case MASTERWORK -> 1.20;
            case HEIRLOOM -> 1.30;
        };
    }
}
