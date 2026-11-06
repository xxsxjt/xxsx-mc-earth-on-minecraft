package com.xxsx.earthonminecraft;

import com.xxsx.earthonminecraft.component.EarthDataComponents;
import com.xxsx.earthonminecraft.component.Grade;
import com.xxsx.earthonminecraft.component.MaterialBatch;
import com.xxsx.earthonminecraft.component.MetalBatch;
import com.xxsx.earthonminecraft.component.OpticalGlassComponent;
import com.xxsx.earthonminecraft.component.QualityComponents;
import com.xxsx.earthonminecraft.component.ToolQualityComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Metallurgical composition, impurity window, temperature response and forging upgrade rules.
 */
public final class MetallurgyAssay {
    private MetallurgyAssay() {
    }

    /**
     * Forges a workpiece on the Ironworking Anvil with the given hammer and workpiece/hearth temperature.
     */
    public static Optional<ItemStack> forgeAnvilWorkpiece(ItemStack workpiece, ItemStack hammer, int envTempK) {
        if (workpiece.isEmpty()) {
            return Optional.empty();
        }
        MaterialBatch inputBatch = QualityComponents.resolveMaterialBatch(workpiece)
                .orElseGet(() -> MaterialBatch.defaultFor(itemPath(workpiece)));
        MetalBatch inputMetal = QualityComponents.resolveMetalBatch(workpiece).orElse(null);
        int effectiveTempK = Math.max(envTempK, inputBatch.temperatureK());
        Grade hammerGrade = Optional.ofNullable(hammer.get(EarthDataComponents.TOOL_QUALITY.get()))
                .map(ToolQualityComponent::grade)
                .orElse(Grade.COMMON);
        double tempBonus = forgingTemperatureFactor(effectiveTempK);
        double hammerBonus = hammerGrade.tier() * 0.08;

        Item item = workpiece.getItem();
        if (item == EarthOnMinecraft.SPONGE_IRON.get()) {
            // Sponge iron forging: expel slag and decarburize excess carbon into the wrought-iron window (C < 0.02%).
            MetalBatch source = inputMetal != null ? inputMetal : MetalBatch.defaultFor("sponge_iron");
            double oldSlag = Math.max(0.003, source.slagContent());
            double newSlag = round5(Math.max(0.0010, oldSlag * (0.035 - 0.005 * Math.min(2.0, tempBonus + hammerBonus))));
            double newC = 0.00015; // 0.015 wt% C (< 0.02 wt% wrought iron window)
            double spRetention = Math.max(0.08, 0.22 - 0.06 * tempBonus - 0.04 * hammerBonus);
            double newS = round5(Math.max(0.00008, source.sulfurContent() * spRetention));
            double newP = round5(Math.max(0.00008, source.phosphorusContent() * spRetention));
            double newMn = 0.0010;
            double newFe = round5(1.0 - (newSlag + newC + newS + newP + newMn));
            Map<String, Double> comp = MetalBatch.ordered(
                    "Fe", newFe, "Slag", newSlag, "Mn", newMn, "C", newC, "P", newP, "S", newS);
            double workability = Math.min(0.98, source.workability() + 0.42 + 0.05 * tempBonus);
            MetalBatch forgedMetal = new MetalBatch(1, MetalBatch.Alloy.WROUGHT_IRON, comp, source.heatId() + 1, workability);

            Grade baseGrade = inputBatch.grade().stepUp();
            if (effectiveTempK >= 1150 && effectiveTempK <= 1450) {
                baseGrade = baseGrade.stepUp();
            }
            if (hammerGrade.tier() >= Grade.FINE.tier()) {
                baseGrade = baseGrade.stepUp();
            }
            Grade finalGrade = baseGrade.min(forgedMetal.maxGradeCap());

            ItemStack out = new ItemStack(EarthOnMinecraft.WROUGHT_IRON.get());
            out.set(EarthDataComponents.METAL_BATCH.get(), forgedMetal);
            out.set(EarthDataComponents.MATERIAL_BATCH.get(), new MaterialBatch(
                    1, "earth_on_minecraft:wrought_iron", MaterialBatch.Phase.SOLID,
                    finalGrade, newFe, 0.0, 1000, Math.max(293, effectiveTempK - 80)));
            return Optional.of(out);
        }

        if (item == EarthOnMinecraft.WROUGHT_IRON.get()) {
            MetalBatch source = inputMetal != null ? inputMetal : MetalBatch.defaultFor("wrought_iron");
            double newSlag = round5(Math.max(0.0004, source.slagContent() * 0.40));
            double newC = round5(Math.min(0.00018, Math.max(0.00008, source.carbonContent())));
            double newS = round5(Math.max(0.00008, source.sulfurContent() * 0.70));
            double newP = round5(Math.max(0.00008, source.phosphorusContent() * 0.75));
            double newMn = 0.0010;
            double newFe = round5(1.0 - (newSlag + newC + newS + newP + newMn));
            Map<String, Double> comp = MetalBatch.ordered(
                    "Fe", newFe, "Slag", newSlag, "Mn", newMn, "C", newC, "P", newP, "S", newS);
            double workability = Math.min(0.99, source.workability() + 0.04 + 0.03 * tempBonus);
            MetalBatch forgedMetal = new MetalBatch(1, MetalBatch.Alloy.WROUGHT_IRON, comp, source.heatId() + 1, workability);

            Grade nextGrade = inputBatch.grade();
            if (effectiveTempK >= 1150 && effectiveTempK <= 1450) {
                nextGrade = nextGrade.stepUp();
            }
            Grade finalGrade = nextGrade.min(forgedMetal.maxGradeCap());
            ItemStack out = new ItemStack(Items.IRON_INGOT);
            // Keep vanilla default iron_ingot clean unless input had explicit non-default components or high heat/hammer
            boolean customized = workpiece.has(EarthDataComponents.METAL_BATCH.get())
                    || effectiveTempK > 350
                    || hammerGrade != Grade.COMMON
                    || inputBatch.grade() != Grade.FINE;
            if (customized) {
                out.set(EarthDataComponents.METAL_BATCH.get(), forgedMetal);
                out.set(EarthDataComponents.MATERIAL_BATCH.get(), new MaterialBatch(
                        1, "earth_on_minecraft:iron_ingot", MaterialBatch.Phase.SOLID,
                        finalGrade, newFe, 0.0, 1000, Math.max(293, effectiveTempK - 80)));
            }
            return Optional.of(out);
        }

        if (item == EarthOnMinecraft.STEEL_BLOOM.get()) {
            MetalBatch source = inputMetal != null ? inputMetal : QualityComponents.defaultMetalBatchForItem("steel_bloom");
            double c = source.carbonContent();
            // Normalize carbon into the steel window (0.2% - 2.1%) if close, or preserve pig-iron excess if severely overcarburized
            double newC = c > 0.021 ? round5(c * 0.92) : round5(Math.max(0.0045, Math.min(0.0160, c > 0 ? c : 0.0100)));
            double newSlag = round5(Math.max(0.0005, source.slagContent() * 0.20));
            double newS = round5(Math.max(0.00010, source.sulfurContent() * 0.65));
            double newP = round5(Math.max(0.00010, source.phosphorusContent() * 0.70));
            double newMn = round5(Math.max(0.0040, source.fractionOf("Mn")));
            double newSi = 0.0015;
            double newFe = round5(1.0 - (newC + newSlag + newS + newP + newMn + newSi));
            MetalBatch.Alloy alloy = newC >= 0.012 ? MetalBatch.Alloy.HIGH_CARBON_STEEL : MetalBatch.Alloy.STEEL;
            Map<String, Double> comp = MetalBatch.ordered(
                    "Fe", newFe, "C", newC, "Mn", newMn, "Si", newSi, "Slag", newSlag, "S", newS, "P", newP);
            double workability = Math.min(0.96, source.workability() + 0.22 + 0.05 * tempBonus);
            MetalBatch forgedMetal = new MetalBatch(1, alloy, comp, source.heatId() + 1, workability);

            Grade baseGrade = inputBatch.grade().stepUp();
            if (effectiveTempK >= 1150 && effectiveTempK <= 1450) {
                baseGrade = baseGrade.stepUp();
            }
            if (hammerGrade.tier() >= Grade.FINE.tier()) {
                baseGrade = baseGrade.stepUp();
            }
            if ("pig_iron".equals(forgedMetal.ironCarbonClass())) {
                baseGrade = Grade.CRUDE;
            }
            Grade finalGrade = baseGrade.min(forgedMetal.maxGradeCap());

            ItemStack out = new ItemStack(SpecItems.BY_ID.get("steel").get());
            out.set(EarthDataComponents.METAL_BATCH.get(), forgedMetal);
            out.set(EarthDataComponents.MATERIAL_BATCH.get(), new MaterialBatch(
                    1, "earth_on_minecraft:steel", MaterialBatch.Phase.SOLID,
                    finalGrade, newFe + newC + newMn, 0.0, 1000, Math.max(293, effectiveTempK - 80)));
            return Optional.of(out);
        }

        Item steelItem = SpecItems.BY_ID.containsKey("steel") ? SpecItems.BY_ID.get("steel").get() : null;
        if (steelItem != null && item == steelItem && workpiece.has(EarthDataComponents.METAL_BATCH.get())) {
            // Folding & refining steel on the anvil (百炼折叠锻打)
            MetalBatch source = workpiece.get(EarthDataComponents.METAL_BATCH.get());
            double newSlag = round5(Math.max(0.0002, source.slagContent() * 0.45));
            double newS = round5(Math.max(0.00005, source.sulfurContent() * 0.65));
            double newP = round5(Math.max(0.00005, source.phosphorusContent() * 0.70));
            double newC = round5(Math.max(0.0065, Math.min(0.0125, source.carbonContent())));
            double newMn = round5(Math.max(0.0040, source.fractionOf("Mn")));
            double newSi = 0.0015;
            double newFe = round5(1.0 - (newC + newSlag + newS + newP + newMn + newSi));
            Map<String, Double> comp = MetalBatch.ordered(
                    "Fe", newFe, "C", newC, "Mn", newMn, "Si", newSi, "Slag", newSlag, "S", newS, "P", newP);
            double workability = Math.min(0.99, source.workability() + 0.05);
            MetalBatch foldedMetal = new MetalBatch(1, source.alloy(), comp, source.heatId() + 1, workability);
            Grade upgraded = inputBatch.grade();
            if (effectiveTempK >= 1100 || hammerGrade.tier() >= Grade.FINE.tier() || source.slagContent() > 0.0005) {
                upgraded = upgraded.stepUp();
            }
            Grade finalGrade = upgraded.min(foldedMetal.maxGradeCap());
            ItemStack out = new ItemStack(steelItem);
            out.set(EarthDataComponents.METAL_BATCH.get(), foldedMetal);
            out.set(EarthDataComponents.MATERIAL_BATCH.get(), new MaterialBatch(
                    1, "earth_on_minecraft:steel", MaterialBatch.Phase.SOLID,
                    finalGrade, Math.min(0.9995, newFe + newC + newMn), 0.0, 1000, Math.max(293, effectiveTempK - 60)));
            return Optional.of(out);
        }

        return Optional.empty();
    }

    /**
     * Applies dynamic metallurgical/chemical assay calculation when a processing machine finishes a route,
     * only attaching non-default components when inputs carry custom components or when forged tools are produced.
     */
    public static List<ItemStack> evaluateMachineOutputs(
            ProcessingMachineBlock.Kind kind,
            ProcessingMachineBlock.Recipe recipe,
            ItemStack primaryInput,
            List<ItemStack> secondarySlots,
            ItemStack reagentSlot,
            int machineTempK) {
        List<ItemStack> rawOutputs = recipe.outputStacks();
        boolean isOpticalGlass = kind == ProcessingMachineBlock.Kind.GLASS_KILN
                && rawOutputs.stream().anyMatch(s -> s.getItem() == EarthOnMinecraft.OPTICAL_GLASS.get());
        boolean hasCustomPrimary = !primaryInput.isEmpty() && hasNonDefaultAssay(primaryInput);
        boolean hasCustomSecondary = secondarySlots.stream().anyMatch(s -> !s.isEmpty() && hasNonDefaultAssay(s));
        boolean isToolForging = kind == ProcessingMachineBlock.Kind.ANVIL
                && rawOutputs.stream().anyMatch(s -> s.has(EarthDataComponents.TOOL_QUALITY.get()));

        if (!hasCustomPrimary && !hasCustomSecondary && !isToolForging && !isOpticalGlass) {
            return rawOutputs;
        }

        List<ItemStack> evaluated = new ArrayList<>(rawOutputs.size());
        for (ItemStack baseOut : rawOutputs) {
            ItemStack out = baseOut.copy();
            applyMachineAssayToOutput(kind, recipe, primaryInput, secondarySlots, reagentSlot, machineTempK, out);
            evaluated.add(out);
        }
        return evaluated;
    }

    private static void applyMachineAssayToOutput(
            ProcessingMachineBlock.Kind kind,
            ProcessingMachineBlock.Recipe recipe,
            ItemStack primaryInput,
            List<ItemStack> secondarySlots,
            ItemStack reagentSlot,
            int machineTempK,
            ItemStack out) {
        MaterialBatch inBatch = QualityComponents.resolveMaterialBatch(primaryInput)
                .orElse(MaterialBatch.DEFAULT);
        MetalBatch inMetal = QualityComponents.resolveMetalBatch(primaryInput).orElse(null);
        int effectiveTempK = Math.max(machineTempK, inBatch.temperatureK());

        // 1. Anvil tool forging: propagate metal batch quality, carbon window & forging temperature into TOOL_QUALITY
        if (out.getItem() == EarthOnMinecraft.OPTICAL_GLASS.get()) {
            int bubbles = effectiveTempK >= 1650 ? 1 : (effectiveTempK >= 1560 ? 3 : 8);
            double n = effectiveTempK >= 1600 ? 1.54 : 1.52;
            OpticalGlassComponent glass = new OpticalGlassComponent(1, n, bubbles);
            out.set(EarthDataComponents.OPTICAL_GLASS.get(), glass);
            Grade baseGrade = effectiveTempK >= 1650 ? Grade.MASTERWORK : (effectiveTempK >= 1560 ? Grade.FINE : Grade.COMMON);
            Grade cappedGrade = glass.capGrade(baseGrade);
            MaterialBatch glassBatch = MaterialBatch.defaultFor("optical_glass")
                    .withPurityAndGrade(bubbles == 0 ? 0.999 : (bubbles <= 5 ? 0.995 : 0.960), cappedGrade)
                    .withTemperatureK(Math.max(293, effectiveTempK - 200));
            out.set(EarthDataComponents.MATERIAL_BATCH.get(), glassBatch);
            return;
        }
        if (out.has(EarthDataComponents.TOOL_QUALITY.get())) {
            Grade metalGrade = inBatch.grade();
            double roll = 0.55 + 0.08 * metalGrade.tier();
            if (inMetal != null) {
                metalGrade = metalGrade.min(inMetal.maxGradeCap());
                String icClass = inMetal.ironCarbonClass();
                if ("pig_iron".equals(icClass) || "sponge_iron".equals(icClass)) {
                    metalGrade = Grade.CRUDE;
                    roll = 0.20;
                } else if ("steel".equals(icClass)) {
                    roll += 0.12;
                }
            }
            if (effectiveTempK >= 1150 && effectiveTempK <= 1450) {
                metalGrade = metalGrade.stepUp();
                roll += 0.10;
            }
            if (inMetal != null) {
                metalGrade = metalGrade.min(inMetal.maxGradeCap());
            }
            double clampedRoll = Math.max(0.05, Math.min(1.0, roll));
            String smith = effectiveTempK >= 1150 ? "hot_forged_" + effectiveTempK + "K" : "cold_forged";
            out.set(EarthDataComponents.TOOL_QUALITY.get(),
                    new ToolQualityComponent(1, metalGrade, smith, clampedRoll));
            return;
        }

        // 2. Reduction / Bloomery / Shaft Furnace carburizing & smelting
        String outId = itemPath(out);
        if (out.getItem() == EarthOnMinecraft.SPONGE_IRON.get()) {
            double feedPurity = inBatch.purity();
            double slag = round5(Math.max(0.045, Math.min(0.180, 0.115 - (feedPurity - 0.90) * 0.40)));
            double s = round5(Math.max(0.0010, 0.0040 - (feedPurity - 0.90) * 0.015));
            double p = round5(Math.max(0.0010, 0.0030 - (feedPurity - 0.90) * 0.010));
            double c = 0.0180;
            double fe = round5(1.0 - (slag + c + s + p));
            MetalBatch spongeMetal = new MetalBatch(
                    1, MetalBatch.Alloy.SPONGE_IRON,
                    MetalBatch.ordered("Fe", fe, "Slag", slag, "C", c, "S", s, "P", p),
                    0L, 0.45);
            Grade grade = inBatch.grade().min(spongeMetal.maxGradeCap());
            out.set(EarthDataComponents.METAL_BATCH.get(), spongeMetal);
            out.set(EarthDataComponents.MATERIAL_BATCH.get(), new MaterialBatch(
                    1, "earth_on_minecraft:sponge_iron", MaterialBatch.Phase.SOLID,
                    grade, fe, 0.0, 1000, Math.max(293, effectiveTempK)));
            return;
        }

        if (out.getItem() == EarthOnMinecraft.STEEL_BLOOM.get()) {
            boolean usesCoalDust = secondarySlots.stream().anyMatch(s -> s.getItem() == EarthOnMinecraft.COAL_DUST.get());
            double baseS = inMetal != null ? inMetal.sulfurContent() : 0.0005;
            double baseP = inMetal != null ? inMetal.phosphorusContent() : 0.0005;
            double s = round5(usesCoalDust ? Math.max(0.0038, baseS + 0.0035) : Math.max(0.0004, baseS * 0.85));
            double p = round5(usesCoalDust ? Math.max(0.0022, baseP + 0.0018) : Math.max(0.0004, baseP * 0.85));
            double slag = round5(usesCoalDust ? 0.0280 : 0.0120);
            double c = round5(effectiveTempK >= 1500 ? 0.0120 : 0.0085);
            double mn = 0.0030;
            double fe = round5(1.0 - (slag + c + mn + s + p));
            MetalBatch bloomMetal = new MetalBatch(
                    1, MetalBatch.Alloy.STEEL,
                    MetalBatch.ordered("Fe", fe, "Slag", slag, "C", c, "Mn", mn, "S", s, "P", p),
                    0L, usesCoalDust ? 0.55 : 0.72);
            Grade grade = (usesCoalDust ? Grade.CRUDE : inBatch.grade().stepUp()).min(bloomMetal.maxGradeCap());
            out.set(EarthDataComponents.METAL_BATCH.get(), bloomMetal);
            out.set(EarthDataComponents.MATERIAL_BATCH.get(), new MaterialBatch(
                    1, "earth_on_minecraft:steel_bloom", MaterialBatch.Phase.SOLID,
                    grade, fe + c + mn, 0.0, 1000, Math.max(293, effectiveTempK)));
            return;
        }

        // 3. General purity propagation for upgraded material batches
        if (out.has(EarthDataComponents.MATERIAL_BATCH.get())) {
            MaterialBatch defaultOut = MaterialBatch.defaultFor(outId);
            double delta = (inBatch.purity() - MaterialBatch.defaultFor(itemPath(primaryInput)).purity()) * 0.65;
            double newPurity = Math.max(0.10, Math.min(0.9999999, defaultOut.purity() + delta));
            Grade newGrade = defaultOut.grade().max(inBatch.grade());
            out.set(EarthDataComponents.MATERIAL_BATCH.get(),
                    defaultOut.withPurityAndGrade(newPurity, newGrade).withTemperatureK(Math.max(293, effectiveTempK)));
        }
    }

    /**
     * Synthesizes an iron-carbon alloy batch from explicit mass fractions (Fe/C/S/P/Slag/Mn/Si)
     * and classifies its metallurgical window and impurity cap.
     */
    public static MetalBatch createIronCarbonBatch(double carbonFraction, double sulfurFraction,
                                                   double phosphorusFraction, double slagFraction) {
        double c = round5(Math.max(0.00005, Math.min(0.0600, carbonFraction)));
        double s = round5(Math.max(0.00005, Math.min(0.0200, sulfurFraction)));
        double p = round5(Math.max(0.00005, Math.min(0.0200, phosphorusFraction)));
        double slag = round5(Math.max(0.00010, Math.min(0.2500, slagFraction)));
        double mn = 0.0030;
        double fe = round5(1.0 - (c + s + p + slag + mn));
        MetalBatch.Alloy alloy;
        double workability;
        if (slag >= 0.030) {
            alloy = MetalBatch.Alloy.SPONGE_IRON;
            workability = 0.45;
        } else if (c < 0.0002) {
            alloy = MetalBatch.Alloy.WROUGHT_IRON;
            workability = 0.94;
        } else if (c <= 0.021) {
            alloy = c >= 0.012 ? MetalBatch.Alloy.HIGH_CARBON_STEEL : MetalBatch.Alloy.STEEL;
            workability = 0.86;
        } else {
            alloy = MetalBatch.Alloy.PIG_IRON;
            workability = 0.28;
        }
        workability = Math.max(0.10, workability - (s + p) * 18.0 - slag * 2.0);
        Map<String, Double> comp = MetalBatch.ordered(
                "Fe", fe, "C", c, "Mn", mn, "Slag", slag, "S", s, "P", p);
        return new MetalBatch(1, alloy, comp, 1L, round5(workability));
    }

    public static double forgingTemperatureFactor(int tempK) {
        if (tempK >= 1150 && tempK <= 1450) {
            return 1.0;
        }
        if (tempK >= 950 && tempK < 1150) {
            return 0.6;
        }
        if (tempK > 1450 && tempK <= 1650) {
            return 0.5;
        }
        return 0.0;
    }

    private static boolean hasNonDefaultAssay(ItemStack stack) {
        String id = itemPath(stack);
        MaterialBatch batch = stack.get(EarthDataComponents.MATERIAL_BATCH.get());
        if (batch != null) {
            MaterialBatch def = MaterialBatch.defaultFor(id);
            if (batch.grade() != def.grade()
                    || Math.abs(batch.purity() - def.purity()) > 1e-4
                    || batch.temperatureK() > 350) {
                return true;
            }
        }
        MetalBatch metal = stack.get(EarthDataComponents.METAL_BATCH.get());
        if (metal != null) {
            MetalBatch def = QualityComponents.defaultMetalBatchForItem(id);
            if (metal.alloy() != def.alloy()
                    || metal.heatId() != def.heatId()
                    || !metal.composition().equals(def.composition())) {
                return true;
            }
        }
        return false;
    }

    private static String itemPath(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "stone" : id.getPath();
    }

    private static double round5(double val) {
        return Math.round(val * 100_000.0) / 100_000.0;
    }
}
