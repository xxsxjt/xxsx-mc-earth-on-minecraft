package com.xxsx.earthonminecraft.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

import java.util.List;

public record GemComponent(int schemaVersion, String species, String variety, Grade grade, Clarity clarity,
                           ColorGrade colorGrade, double carat, Treatment treatment, Cut cut, String matrix,
                           int pattern) {
    public static final GemComponent DEFAULT = new GemComponent(1, "-", "", Grade.COMMON, Clarity.TRANSLUCENT,
            ColorGrade.MEDIUM, 1.0, Treatment.NONE, Cut.ROUGH, "", 0);

    public static final Codec<GemComponent> CODEC = RecordCodecBuilder.<GemComponent>create(instance -> instance.group(
            ExtraCodecs.intRange(1, 1).optionalFieldOf("schema_version", 1).forGetter(GemComponent::schemaVersion),
            Codec.string(1, 64).optionalFieldOf("species", "-").forGetter(GemComponent::species),
            Codec.string(0, 32).optionalFieldOf("variety", "").forGetter(GemComponent::variety),
            Grade.CODEC.optionalFieldOf("grade", Grade.COMMON).forGetter(GemComponent::grade),
            Clarity.CODEC.optionalFieldOf("clarity", Clarity.TRANSLUCENT).forGetter(GemComponent::clarity),
            ColorGrade.CODEC.optionalFieldOf("color_grade", ColorGrade.MEDIUM).forGetter(GemComponent::colorGrade),
            Codec.doubleRange(0.1, 50.0).optionalFieldOf("carat", 1.0).forGetter(GemComponent::carat),
            Treatment.CODEC.optionalFieldOf("treatment", Treatment.NONE).forGetter(GemComponent::treatment),
            Cut.CODEC.optionalFieldOf("cut", Cut.ROUGH).forGetter(GemComponent::cut),
            Codec.string(0, 32).optionalFieldOf("matrix", "").forGetter(GemComponent::matrix),
            ExtraCodecs.intRange(0, 65535).optionalFieldOf("pattern", 0).forGetter(GemComponent::pattern)
    ).apply(instance, GemComponent::new)).validate(GemComponent::validate);

    private static DataResult<GemComponent> validate(GemComponent value) {
        if (value.schemaVersion != 1) {
            return DataResult.error(() -> "unsupported gem schema_version " + value.schemaVersion);
        }
        if (value.species.isBlank()) {
            return DataResult.error(() -> "gem species must not be blank");
        }
        return DataResult.success(value);
    }

    public enum Clarity implements StringRepresentable {
        OPAQUE, TRANSLUCENT, TRANSPARENT, FLAWLESS;

        public static final Codec<Clarity> CODEC = StringRepresentable.fromEnum(Clarity::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum ColorGrade implements StringRepresentable {
        PALE, MEDIUM, VIVID, TOP;

        public static final Codec<ColorGrade> CODEC = StringRepresentable.fromEnum(ColorGrade::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Treatment implements StringRepresentable {
        NONE, HEAT, OIL, DYE, RESIN, IRRADIATION;

        public static final Codec<Treatment> CODEC = StringRepresentable.fromEnum(Treatment::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Cut implements StringRepresentable {
        ROUGH, CABOCHON, BEAD, CARVED, ROUND, OVAL, EMERALD, STEP;

        public static final Codec<Cut> CODEC = StringRepresentable.fromEnum(Cut::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
