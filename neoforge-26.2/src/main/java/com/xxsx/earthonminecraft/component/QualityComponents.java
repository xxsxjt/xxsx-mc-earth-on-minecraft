package com.xxsx.earthonminecraft.component;

import com.xxsx.earthonminecraft.EarthOnMinecraft;
import com.xxsx.earthonminecraft.MaterialCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class QualityComponents {
    private static final Map<String, String> METAL_ALLOY_BY_ITEM = Map.ofEntries(
            Map.entry("sponge_iron", "sponge_iron"),
            Map.entry("wrought_iron", "wrought_iron"),
            Map.entry("iron_ingot", "iron"),
            Map.entry("iron", "iron"),
            Map.entry("steel_bloom", "steel"),
            Map.entry("steel", "steel"),
            Map.entry("high_carbon_steel", "high_carbon_steel"),
            Map.entry("pig_iron", "pig_iron"),
            Map.entry("cast_iron", "cast_iron"),
            Map.entry("stainless_steel_bloom", "stainless_steel"),
            Map.entry("stainless_steel", "stainless_steel"),
            Map.entry("bronze", "bronze"),
            Map.entry("brass", "brass"),
            Map.entry("cupronickel", "cupronickel"),
            Map.entry("monel", "monel"),
            Map.entry("solder", "solder"),
            Map.entry("aluminum_ingot", "aluminium"),
            Map.entry("aluminium", "aluminium"),
            Map.entry("aluminum_alloy_billet", "aluminium"),
            Map.entry("aluminium_alloy", "aluminium"),
            Map.entry("titanium_sponge", "titanium"),
            Map.entry("titanium", "titanium"),
            Map.entry("titanium_alloy", "titanium"),
            Map.entry("blister_copper", "copper"),
            Map.entry("anode_copper", "copper"),
            Map.entry("cathode_copper", "copper"),
            Map.entry("refined_copper", "copper"),
            Map.entry("copper_ingot", "copper"),
            Map.entry("gold_ingot", "gold")
    );
    private static final Set<String> TOOL_QUALITY_ITEMS = Set.of("geology_hammer");

    private QualityComponents() {
    }

    public static Item.Properties applyDefaults(String id, Item.Properties properties) {
        if (!TOOL_QUALITY_ITEMS.contains(id)) {
            properties = properties.component(EarthDataComponents.MATERIAL_BATCH.get(), MaterialBatch.defaultFor(id));
        }
        String alloyName = METAL_ALLOY_BY_ITEM.get(id);
        if (alloyName != null) {
            properties = properties.component(EarthDataComponents.METAL_BATCH.get(), defaultMetalBatchForItem(id));
        }
        if (TOOL_QUALITY_ITEMS.contains(id)) {
            properties = properties.component(EarthDataComponents.TOOL_QUALITY.get(), ToolQualityComponent.DEFAULT);
        }
        if ("optical_glass".equals(id)) {
            properties = properties.component(EarthDataComponents.OPTICAL_GLASS.get(), OpticalGlassComponent.DEFAULT);
        }
        return properties;
    }

    public static MetalBatch defaultMetalBatchForItem(String itemId) {
        String local = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        String alloyName = METAL_ALLOY_BY_ITEM.getOrDefault(local, local);
        MetalBatch.Alloy alloy = MetalBatch.Alloy.byName(alloyName);
        Optional<MaterialCatalog.MaterialRecord> record = MaterialCatalog.find(EarthOnMinecraft.id(local));
        if (record.isPresent() && "mass_fraction".equals(record.get().compositionBasis())
                && !record.get().constituents().isEmpty() && record.get().constituents().size() <= 8) {
            Map<String, Double> map = new LinkedHashMap<>();
            for (MaterialCatalog.Constituent c : record.get().constituents()) {
                if (c.fraction() >= 0.00001) {
                    map.put(c.species(), c.fraction());
                }
            }
            double workability = switch (local) {
                case "sponge_iron" -> 0.45;
                case "steel_bloom", "stainless_steel_bloom" -> 0.65;
                case "pig_iron", "cast_iron" -> 0.30;
                case "wrought_iron" -> 0.92;
                default -> 0.88;
            };
            return new MetalBatch(1, alloy, Map.copyOf(map), 0L, workability);
        }
        return MetalBatch.defaultFor(alloyName);
    }

    public static Optional<MaterialBatch> resolveMaterialBatch(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        MaterialBatch explicit = stack.get(EarthDataComponents.MATERIAL_BATCH.get());
        if (explicit != null) {
            return Optional.of(explicit);
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Optional<MaterialCatalog.MaterialRecord> record = MaterialCatalog.find(id);
        if (record.isPresent()) {
            return Optional.of(MaterialBatch.defaultFor(id.getPath()));
        }
        return Optional.empty();
    }

    public static Optional<MetalBatch> resolveMetalBatch(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        MetalBatch explicit = stack.get(EarthDataComponents.METAL_BATCH.get());
        if (explicit != null) {
            return Optional.of(explicit);
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (METAL_ALLOY_BY_ITEM.containsKey(id.getPath())) {
            return Optional.of(defaultMetalBatchForItem(id.getPath()));
        }
        return Optional.empty();
    }

    public static void appendTooltip(ItemStack stack, Consumer<Component> lines) {
        Boolean sterile = stack.get(EarthDataComponents.STERILE.get());
        if (Boolean.TRUE.equals(sterile)) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.sterile")
                    .withStyle(ChatFormatting.GREEN));
        }
        OpticalGlassComponent opticalGlass = stack.get(EarthDataComponents.OPTICAL_GLASS.get());
        if (opticalGlass != null) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.optical_glass",
                    String.format(Locale.ROOT, "%.2f", opticalGlass.refractiveIndex()),
                    opticalGlass.bubbleCount()).withStyle(ChatFormatting.AQUA));
            if (opticalGlass.hasHighBubbles()) {
                lines.accept(Component.translatable("tooltip.earth_on_minecraft.optical_glass.bubbles_high")
                        .withStyle(ChatFormatting.RED));
            }
        }
        FoodQuality food = stack.get(EarthDataComponents.FOOD_QUALITY.get());
        if (food != null) {
            lines.accept(gradeLine(food.grade()));
            if (!food.freshnessNote().isBlank()) {
                lines.accept(sourceLine(food.freshnessNote()));
            }
        }
        HerbComponent herb = stack.get(EarthDataComponents.HERB.get());
        if (herb != null) {
            lines.accept(gradeLine(herb.grade()));
            lines.accept(sourceLine(herb.species() + " / " + herb.prepared().getSerializedName()
                    + " / " + String.format(java.util.Locale.ROOT, "%.2f", herb.potency())));
        }
        PharmaceuticalComponent pharmaceutical = stack.get(EarthDataComponents.PHARMACEUTICAL.get());
        if (pharmaceutical != null) {
            lines.accept(gradeLine(pharmaceutical.grade()));
            lines.accept(sourceLine(pharmaceutical.drug() + " / " + pharmaceutical.form().getSerializedName()
                    + " " + pharmaceutical.doseMg() + " mg / " + String.format(java.util.Locale.ROOT, "%.2f", pharmaceutical.purity())));
        }
        GemComponent gem = stack.get(EarthDataComponents.GEM.get());
        if (gem != null) {
            lines.accept(gradeLine(gem.grade()));
            lines.accept(sourceLine(gem.species() + " / " + gem.cut().getSerializedName()
                    + " / " + String.format(java.util.Locale.ROOT, "%.2f", gem.carat()) + " ct"));
        }
        ToolQualityComponent tool = stack.get(EarthDataComponents.TOOL_QUALITY.get());
        if (tool != null) {
            lines.accept(gradeLine(tool.grade()));
            if (!tool.smith().isBlank()) {
                lines.accept(sourceLine(tool.smith()));
            }
        }
        MaterialBatch batch = stack.get(EarthDataComponents.MATERIAL_BATCH.get());
        if (batch == null && food == null && herb == null && pharmaceutical == null && gem == null && tool == null) {
            batch = resolveMaterialBatch(stack).orElse(null);
        }
        MetalBatch metal = stack.get(EarthDataComponents.METAL_BATCH.get());
        if (metal == null && tool == null) {
            metal = resolveMetalBatch(stack).orElse(null);
        }
        if (batch != null) {
            lines.accept(gradeLine(batch.grade()));
            if (com.xxsx.earthonminecraft.LoreIntegration.knowsClient(
                    com.xxsx.earthonminecraft.LoreIntegration.NODE_ANALYTICAL_CHEMISTRY)) {
                String tempNote = batch.temperatureK() > 300 ? " · " + batch.temperatureK() + " K" : "";
                lines.accept(Component.translatable("tooltip.earth_on_minecraft.assay.purity",
                        Component.literal(formatPercent(batch.purity()) + " wt%"),
                        Component.literal(batch.speciesId() + tempNote)).withStyle(ChatFormatting.AQUA));
            }
        }
        if (metal != null) {
            String classKey = "tooltip.earth_on_minecraft.assay.class." + metal.ironCarbonClass();
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.assay.metallurgy",
                    Component.translatable(classKey),
                    Component.literal(String.format(Locale.ROOT, "%.0f%%", metal.workability() * 100.0)),
                    Component.translatable(metal.maxGradeCap().translationKey())).withStyle(ChatFormatting.GRAY));
            if (!metal.composition().isEmpty() && com.xxsx.earthonminecraft.LoreIntegration.knowsClient(
                    com.xxsx.earthonminecraft.LoreIntegration.NODE_ANALYTICAL_CHEMISTRY)) {
                String assay = metal.composition().entrySet().stream()
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .map(entry -> entry.getKey() + " "
                                + formatPercent(entry.getValue()) + "%")
                        .collect(Collectors.joining(" · "));
                lines.accept(Component.translatable("tooltip.earth_on_minecraft.assay.composition",
                        Component.literal(assay)).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (com.xxsx.earthonminecraft.LoreIntegration.knowsClient(
                com.xxsx.earthonminecraft.LoreIntegration.NODE_ANALYTICAL_CHEMISTRY)) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            MaterialCatalog.find(id).ifPresent(record -> {
                if (!record.constituents().isEmpty()) {
                    String assay = record.constituents().stream()
                            .sorted((a, b) -> Double.compare(b.fraction(), a.fraction()))
                            .map(c -> c.species() + " " + formatPercent(c.fraction()) + "%")
                            .collect(Collectors.joining(" · "));
                    lines.accept(Component.translatable("tooltip.earth_on_minecraft.assay.composition",
                            Component.literal(assay)).withStyle(ChatFormatting.DARK_GRAY));
                }
            });
        }
    }

    public static String formatPercent(double fraction) {
        double pct = fraction * 100.0;
        if (pct >= 99.99) {
            return String.format(Locale.ROOT, "%.4f", pct);
        }
        if (pct >= 1.0) {
            return String.format(Locale.ROOT, "%.2f", pct);
        }
        if (pct >= 0.01) {
            return String.format(Locale.ROOT, "%.3f", pct);
        }
        return String.format(Locale.ROOT, "%.4f", pct);
    }

    public static void requireMatchingGrade(Grade declared, Grade derived) {
        if (declared != derived) {
            throw new IllegalArgumentException("invalid_grade");
        }
    }

    private static Component gradeLine(Grade grade) {
        return Component.translatable("tooltip.earth_on_minecraft.grade",
                        Component.translatable(grade.translationKey()),
                        Component.literal(Integer.toString(grade.tier())))
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component sourceLine(String source) {
        return Component.translatable("tooltip.earth_on_minecraft.grade_source", Component.literal(source))
                .withStyle(ChatFormatting.DARK_GRAY);
    }
}
