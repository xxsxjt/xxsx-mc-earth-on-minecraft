package com.xxsx.earthonminecraft.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

public record FoodQuality(int schemaVersion, Grade grade, String freshnessNote) {
    public static final FoodQuality DEFAULT = new FoodQuality(1, Grade.COMMON, "");

    public static final Codec<FoodQuality> CODEC = RecordCodecBuilder.<FoodQuality>create(instance -> instance.group(
            ExtraCodecs.intRange(1, 1).optionalFieldOf("schema_version", 1).forGetter(FoodQuality::schemaVersion),
            Grade.CODEC.optionalFieldOf("grade", Grade.COMMON).forGetter(FoodQuality::grade),
            Codec.string(0, 32).optionalFieldOf("freshness_note", "").forGetter(FoodQuality::freshnessNote)
    ).apply(instance, FoodQuality::new)).validate(FoodQuality::validate);

    private static DataResult<FoodQuality> validate(FoodQuality value) {
        if (value.schemaVersion != 1) {
            return DataResult.error(() -> "unsupported food_quality schema_version " + value.schemaVersion);
        }
        return DataResult.success(value);
    }

    public FoodQuality withGrade(Grade newGrade) {
        return new FoodQuality(schemaVersion, newGrade, freshnessNote);
    }
}
