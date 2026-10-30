package com.xxsx.earthonminecraft.api.v1;

import java.util.Optional;
import net.minecraft.resources.Identifier;

public interface MaterialPropertyApi {
    Optional<MaterialProperties> find(Identifier id);

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
}
