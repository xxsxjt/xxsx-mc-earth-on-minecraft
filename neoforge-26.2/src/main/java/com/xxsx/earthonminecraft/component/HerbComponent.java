package com.xxsx.earthonminecraft.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Locale;

public record HerbComponent(int schemaVersion, String species, Part part, Nature nature, List<Flavor> flavor,
                            int toxicity, Prepared prepared, double potency, Grade grade, String batchId) {
    public static final HerbComponent DEFAULT = new HerbComponent(1, "-", Part.ROOT, Nature.NEUTRAL, List.of(),
            0, Prepared.RAW, 1.0, Grade.COMMON, "");

    public static final Codec<HerbComponent> CODEC = RecordCodecBuilder.<HerbComponent>create(instance -> instance.group(
            ExtraCodecs.intRange(1, 1).optionalFieldOf("schema_version", 1).forGetter(HerbComponent::schemaVersion),
            Codec.string(1, 64).optionalFieldOf("species", "-").forGetter(HerbComponent::species),
            Part.CODEC.optionalFieldOf("part", Part.ROOT).forGetter(HerbComponent::part),
            Nature.CODEC.optionalFieldOf("nature", Nature.NEUTRAL).forGetter(HerbComponent::nature),
            Flavor.CODEC.listOf().optionalFieldOf("flavor", List.of()).forGetter(HerbComponent::flavor),
            ExtraCodecs.intRange(0, 3).optionalFieldOf("toxicity", 0).forGetter(HerbComponent::toxicity),
            Prepared.CODEC.optionalFieldOf("prepared", Prepared.RAW).forGetter(HerbComponent::prepared),
            Codec.doubleRange(0.5, 1.5).optionalFieldOf("potency", 1.0).forGetter(HerbComponent::potency),
            Grade.CODEC.optionalFieldOf("grade", Grade.COMMON).forGetter(HerbComponent::grade),
            Codec.string(0, 32).optionalFieldOf("batch_id", "").forGetter(HerbComponent::batchId)
    ).apply(instance, HerbComponent::new)).validate(HerbComponent::validate);

    private static DataResult<HerbComponent> validate(HerbComponent value) {
        if (value.schemaVersion != 1) {
            return DataResult.error(() -> "unsupported herb schema_version " + value.schemaVersion);
        }
        if (value.species.isBlank()) {
            return DataResult.error(() -> "herb species must not be blank");
        }
        if (value.flavor.size() > 3) {
            return DataResult.error(() -> "herb flavor must contain at most 3 entries");
        }
        return DataResult.success(value);
    }

    public static Grade gradeFor(double potency, Prepared prepared) {
        double effective = potency + (prepared != Prepared.RAW ? 0.1 : 0.0);
        if (effective < 0.70) {
            return Grade.CRUDE;
        }
        if (effective < 0.90) {
            return Grade.COMMON;
        }
        if (effective < 1.10) {
            return Grade.FINE;
        }
        if (effective < 1.30) {
            return Grade.MASTERWORK;
        }
        return Grade.HEIRLOOM;
    }

    public enum Part implements StringRepresentable {
        ROOT, RHIZOME, STEM, LEAF, FLOWER, FRUIT, SEED, BARK, RESIN, MINERAL, ANIMAL;

        public static final Codec<Part> CODEC = StringRepresentable.fromEnum(Part::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Nature implements StringRepresentable {
        COLD, COOL, NEUTRAL, WARM, HOT;

        public static final Codec<Nature> CODEC = StringRepresentable.fromEnum(Nature::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Flavor implements StringRepresentable {
        SOUR, BITTER, SWEET, PUNGENT, SALTY, BLAND, ASTRINGENT;

        public static final Codec<Flavor> CODEC = StringRepresentable.fromEnum(Flavor::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Prepared implements StringRepresentable {
        RAW, WINE, VINEGAR, HONEY, GINGER, CALCINED, STEAMED;

        public static final Codec<Prepared> CODEC = StringRepresentable.fromEnum(Prepared::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
