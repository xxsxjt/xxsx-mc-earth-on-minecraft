package com.xxsx.earthonminecraft.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public record MaterialBatch(int schemaVersion, String speciesId, Phase phase, Grade grade, double purity,
                            double moisture, int particleSizeUm, int temperatureK) {
    public static final MaterialBatch DEFAULT = new MaterialBatch(1, "earth_on_minecraft:stone", Phase.SOLID,
            Grade.COMMON, 1.0, 0.0, 1000, 293);

    public static final Codec<MaterialBatch> CODEC = RecordCodecBuilder.<MaterialBatch>create(instance -> instance.group(
            ExtraCodecs.intRange(1, 1).optionalFieldOf("schema_version", 1).forGetter(MaterialBatch::schemaVersion),
            Codec.string(1, 128).optionalFieldOf("species_id", "earth_on_minecraft:stone").forGetter(MaterialBatch::speciesId),
            Phase.CODEC.optionalFieldOf("phase", Phase.SOLID).forGetter(MaterialBatch::phase),
            Grade.CODEC.optionalFieldOf("grade", Grade.COMMON).forGetter(MaterialBatch::grade),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("purity", 1.0).forGetter(MaterialBatch::purity),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("moisture", 0.0).forGetter(MaterialBatch::moisture),
            ExtraCodecs.intRange(1, 1_000_000).optionalFieldOf("particle_size_um", 1000).forGetter(MaterialBatch::particleSizeUm),
            ExtraCodecs.intRange(1, 5000).optionalFieldOf("temperature_k", 293).forGetter(MaterialBatch::temperatureK)
    ).apply(instance, MaterialBatch::new)).validate(MaterialBatch::validate);

    private static DataResult<MaterialBatch> validate(MaterialBatch value) {
        if (value.schemaVersion != 1) {
            return DataResult.error(() -> "unsupported material_batch schema_version " + value.schemaVersion);
        }
        if (value.speciesId.isBlank()) {
            return DataResult.error(() -> "material_batch species_id must not be blank");
        }
        return DataResult.success(value);
    }

    public static MaterialBatch defaultFor(String itemId) {
        return new MaterialBatch(1, "earth_on_minecraft:" + itemId, Phase.SOLID, Grade.COMMON,
                1.0, 0.0, 1000, 293);
    }

    public enum Phase implements StringRepresentable {
        SOLID, LIQUID, GAS;

        public static final Codec<Phase> CODEC = StringRepresentable.fromEnum(Phase::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
