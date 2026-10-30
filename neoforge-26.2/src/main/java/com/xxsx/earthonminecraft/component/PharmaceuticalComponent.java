package com.xxsx.earthonminecraft.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public record PharmaceuticalComponent(int schemaVersion, String drug, Grade grade, double purity, int doseMg,
                                      Form form, String batchId, String shelfNote) {
    public static final PharmaceuticalComponent DEFAULT = new PharmaceuticalComponent(1, "-", Grade.COMMON, 0.95,
            100, Form.TABLET, "", "");

    public static final Codec<PharmaceuticalComponent> CODEC = RecordCodecBuilder.<PharmaceuticalComponent>create(instance -> instance.group(
            ExtraCodecs.intRange(1, 1).optionalFieldOf("schema_version", 1).forGetter(PharmaceuticalComponent::schemaVersion),
            Codec.string(1, 64).optionalFieldOf("drug", "-").forGetter(PharmaceuticalComponent::drug),
            Grade.CODEC.optionalFieldOf("grade", Grade.COMMON).forGetter(PharmaceuticalComponent::grade),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("purity", 0.95).forGetter(PharmaceuticalComponent::purity),
            ExtraCodecs.intRange(1, 2000).optionalFieldOf("dose_mg", 100).forGetter(PharmaceuticalComponent::doseMg),
            Form.CODEC.optionalFieldOf("form", Form.TABLET).forGetter(PharmaceuticalComponent::form),
            Codec.string(0, 32).optionalFieldOf("batch_id", "").forGetter(PharmaceuticalComponent::batchId),
            Codec.string(0, 32).optionalFieldOf("shelf_note", "").forGetter(PharmaceuticalComponent::shelfNote)
    ).apply(instance, PharmaceuticalComponent::new)).validate(PharmaceuticalComponent::validate);

    private static DataResult<PharmaceuticalComponent> validate(PharmaceuticalComponent value) {
        if (value.schemaVersion != 1) {
            return DataResult.error(() -> "unsupported pharmaceutical schema_version " + value.schemaVersion);
        }
        if (value.drug.isBlank()) {
            return DataResult.error(() -> "pharmaceutical drug must not be blank");
        }
        return DataResult.success(value);
    }

    public static Grade gradeForPurity(double purity) {
        if (purity < 0.70) {
            return Grade.CRUDE;
        }
        if (purity < 0.85) {
            return Grade.COMMON;
        }
        if (purity < 0.95) {
            return Grade.FINE;
        }
        if (purity < 0.99) {
            return Grade.MASTERWORK;
        }
        return Grade.HEIRLOOM;
    }

    public enum Form implements StringRepresentable {
        TABLET, CAPSULE, SYRUP, INJECTION, OINTMENT, POWDER;

        public static final Codec<Form> CODEC = StringRepresentable.fromEnum(Form::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
