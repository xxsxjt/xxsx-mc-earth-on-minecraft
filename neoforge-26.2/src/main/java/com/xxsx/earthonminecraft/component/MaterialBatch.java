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
        String local = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        Phase phase = defaultPhase(local);
        double purity = defaultPurity(local);
        Grade grade = defaultGrade(local, purity);
        double moisture = defaultMoisture(local);
        int particleUm = defaultParticleSizeUm(local);
        return new MaterialBatch(1, "earth_on_minecraft:" + local, phase, grade,
                purity, moisture, particleUm, 293);
    }

    public MaterialBatch withPurityAndGrade(double newPurity, Grade newGrade) {
        double clamped = Math.max(0.0, Math.min(1.0, newPurity));
        return new MaterialBatch(schemaVersion, speciesId, phase, newGrade, clamped, moisture, particleSizeUm, temperatureK);
    }

    public MaterialBatch withTemperatureK(int newTempK) {
        int clamped = Math.max(1, Math.min(5000, newTempK));
        return new MaterialBatch(schemaVersion, speciesId, phase, grade, purity, moisture, particleSizeUm, clamped);
    }

    private static Phase defaultPhase(String id) {
        if (id.endsWith("_gas_cell") || id.endsWith("_cell") && !id.equals("simple_battery_cell") && !id.equals("steam_cell")
                || id.equals("ethylene") || id.equals("propylene") || id.equals("butadiene")
                || id.equals("vinyl_chloride") || id.equals("ammonia") || id.equals("uranium_hexafluoride")) {
            return Phase.GAS;
        }
        if (id.endsWith("_acid") || id.equals("methanol") || id.equals("ethanol") || id.equals("acetone")
                || id.equals("benzene") || id.equals("toluene") || id.equals("styrene")
                || id.equals("ethylene_glycol") || id.equals("glycerol") || id.equals("chlorosilane")
                || id.equals("titanium_tetrachloride") || id.equals("hydrogen_peroxide")
                || id.equals("mercury_droplet") || id.equals("coal_tar") || id.equals("crude_oil_sample")
                || id.equals("naphtha") || id.equals("kerosene_fraction") || id.equals("diesel_fraction")
                || id.equals("lubricating_oil") || id.equals("industrial_solvent")
                || id.equals("hard_water_sample") || id.equals("softened_water") || id.equals("distilled_water")) {
            return Phase.LIQUID;
        }
        return Phase.SOLID;
    }

    private static double defaultPurity(String id) {
        return switch (id) {
            case "poor_magnetite_ore" -> 0.54;
            case "magnetite_ore" -> 0.73;
            case "rich_magnetite_ore" -> 0.94;
            case "chalcopyrite_ore" -> 0.55;
            case "auriferous_quartz_vein" -> 0.65;
            case "bituminous_coal_seam" -> 0.68;
            case "anthracite_coal_seam" -> 0.90;
            case "diamondiferous_kimberlite" -> 0.78;
            case "cinnabar_vein" -> 0.68;
            case "bauxite_laterite_deposit", "bauxite_laterite" -> 0.75;
            case "phosphorite_bed", "phosphorite" -> 0.72;
            case "evaporite_salt_bed", "evaporite_salt" -> 0.78;
            case "cassiterite_placer_deposit" -> 0.64;
            case "rare_earth_carbonatite" -> 0.63;
            case "galena_ore" -> 0.62;
            case "sphalerite_ore" -> 0.64;
            case "chromite_ore" -> 0.68;
            case "manganite_ore" -> 0.80;
            case "uraninite_ore" -> 0.45;
            case "borate_deposit" -> 0.80;
            case "magnetite_chunk", "coal_dust" -> 0.72;
            case "magnetite_dust" -> 0.76;
            case "iron_concentrate" -> 0.94;
            case "copper_concentrate" -> 0.82;
            case "roasted_copper_concentrate", "roasted_copper" -> 0.86;
            case "gold_concentrate" -> 0.77;
            case "lapis_concentrate" -> 0.86;
            case "beryl_concentrate" -> 0.88;
            case "redstone_concentrate" -> 0.96;
            case "sponge_iron" -> 0.860;
            case "wrought_iron" -> 0.9955;
            case "iron_ingot" -> 0.985;
            case "pig_iron" -> 0.940;
            case "cast_iron" -> 0.938;
            case "steel_bloom" -> 0.962;
            case "steel" -> 0.982;
            case "high_carbon_steel" -> 0.978;
            case "stainless_steel_bloom" -> 0.965;
            case "stainless_steel" -> 0.990;
            case "blister_copper" -> 0.982;
            case "anode_copper" -> 0.992;
            case "refined_copper" -> 0.998;
            case "cathode_copper" -> 0.9995;
            case "copper_ingot" -> 0.996;
            case "gold_ingot" -> 0.995;
            case "aluminum_ingot", "aluminium" -> 0.992;
            case "titanium_sponge" -> 0.990;
            case "titanium" -> 0.995;
            case "titanium_alloy" -> 0.998;
            case "metallurgical_silicon" -> 0.985;
            case "chlorosilane", "titanium_tetrachloride" -> 0.999;
            case "high_purity_silicon" -> 0.999999;
            case "polysilicon", "silicon_wafer" -> 0.9999999;
            case "coke" -> 0.890;
            case "petroleum_coke" -> 0.965;
            case "charcoal" -> 0.920;
            case "slag", "tailings_dust", "coarse_tailings", "pyrite_tailings", "silicate_tailings", "rare_earth_tailings" -> 0.40;
            case "yellowcake" -> 0.820;
            case "uranium_dioxide_powder", "uranium_tetrafluoride", "uranium_hexafluoride" -> 0.996;
            default -> {
                if (id.endsWith("_ore") || id.endsWith("_vein") || id.endsWith("_seam") || id.endsWith("_bed") || id.endsWith("_deposit")) {
                    yield 0.65;
                }
                if (id.endsWith("_chunk")) {
                    yield 0.72;
                }
                if (id.endsWith("_dust")) {
                    yield 0.85;
                }
                if (id.endsWith("_concentrate")) {
                    yield 0.88;
                }
                if (id.endsWith("_acid") || id.endsWith("_oxide") || id.endsWith("_chloride")
                        || id.endsWith("_sulfate") || id.endsWith("_nitrate") || id.endsWith("_carbonate")) {
                    yield 0.985;
                }
                yield 0.960;
            }
        };
    }

    private static Grade defaultGrade(String id, double purity) {
        return switch (id) {
            case "poor_magnetite_ore", "slag", "tailings_dust", "coarse_tailings", "pyrite_tailings",
                 "silicate_tailings", "rare_earth_tailings", "sponge_iron" -> Grade.CRUDE;
            case "rich_magnetite_ore", "iron_concentrate", "wrought_iron", "steel", "high_carbon_steel",
                 "stainless_steel", "refined_copper", "titanium", "titanium_alloy", "petroleum_coke",
                 "chlorosilane", "titanium_tetrachloride", "uranium_dioxide_powder", "uranium_hexafluoride" -> Grade.FINE;
            case "cathode_copper", "high_purity_silicon" -> Grade.MASTERWORK;
            case "polysilicon", "silicon_wafer" -> Grade.HEIRLOOM;
            default -> {
                if (purity >= 0.9995) {
                    yield Grade.HEIRLOOM;
                }
                if (purity >= 0.992) {
                    yield Grade.MASTERWORK;
                }
                if (purity >= 0.940) {
                    yield Grade.FINE;
                }
                if (purity < 0.600) {
                    yield Grade.CRUDE;
                }
                yield Grade.COMMON;
            }
        };
    }

    private static double defaultMoisture(String id) {
        if (id.endsWith("_concentrate") || id.equals("sludge_cake") || id.equals("filter_cake") || id.equals("clay_dust")) {
            return 0.04;
        }
        if (id.equals("bituminous_coal_seam") || id.equals("coal_dust") || id.equals("wood_chips")) {
            return 0.06;
        }
        return 0.0;
    }

    private static int defaultParticleSizeUm(String id) {
        if (id.endsWith("_dust") || id.endsWith("_powder") || id.equals("cement_raw_meal")) {
            return 75;
        }
        if (id.endsWith("_concentrate") || id.equals("yellowcake")) {
            return 120;
        }
        if (id.endsWith("_chunk") || id.endsWith("_grit") || id.equals("monazite_sand")) {
            return 5000;
        }
        return 1000;
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
