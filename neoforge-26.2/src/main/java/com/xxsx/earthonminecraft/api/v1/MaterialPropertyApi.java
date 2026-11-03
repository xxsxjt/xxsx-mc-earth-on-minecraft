package com.xxsx.earthonminecraft.api.v1;

import com.xxsx.earthonminecraft.component.Grade;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public interface MaterialPropertyApi {
    Optional<MaterialProperties> find(Identifier id);

    default Optional<AssayReport> inspectMaterial(Identifier id) {
        return Optional.empty();
    }

    default AssayReport inspectStack(ItemStack stack) {
        return AssayReport.EMPTY;
    }

    record MaterialProperties(Identifier id, String formula, String categoryKey, String formKey,
                              String sourceKey, String processKey, String useKey, String simplificationKey) {
        public boolean isMixture() {
            return simplificationKey.endsWith(".mixture") || formula.contains("+") || formula.contains("/");
        }

        public String compositionBasis() {
            if (isMixture()) {
                return "mixture";
            }
            return "ideal_formula";
        }

        public String energyPolicy() {
            return "chemical";
        }
    }

    /**
     * Cross-addon assay & purity profile for Xuanhuan (flying-sword/artifact embryos),
     * Magic (arcane conductors) and Sci-Fi (high-purity semiconductors & aerospace alloys).
     */
    record AssayReport(
            Identifier id,
            Grade grade,
            double purity,
            Map<String, Double> composition,
            String ironCarbonClass,
            double workability,
            int temperatureK,
            double impurityPenalty,
            double xuanhuanArtifactEmbryoFactor,
            double magicArcaneConductivityFactor,
            double scifiHighPurityFactor
    ) {
        public static final AssayReport EMPTY = new AssayReport(
                Identifier.withDefaultNamespace("air"), Grade.CRUDE, 0.0, Map.of(),
                "non_ferrous", 0.0, 293, 1.0, 0.0, 0.0, 0.0);

        public double fractionOf(String species) {
            return composition.getOrDefault(species, 0.0);
        }

        public double purityCoefficient() {
            return Math.max(0.0, purity * (1.0 - impurityPenalty));
        }
    }
}
