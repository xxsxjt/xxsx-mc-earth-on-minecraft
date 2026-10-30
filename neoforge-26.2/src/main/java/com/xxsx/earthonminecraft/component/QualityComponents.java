package com.xxsx.earthonminecraft.component;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.function.Consumer;

public final class QualityComponents {
    private static final Set<String> MATERIAL_BATCH_ITEMS = Set.of(
            "aluminosilicate_dust", "calcite_dust", "chalcopyrite_dust", "cinnabar_dust", "clay_dust",
            "copper_concentrate", "feldspar_dust", "gold_concentrate", "gold_dust", "gypsum_dust",
            "iron_concentrate", "lime_dust", "mafic_silicate_dust", "magnetite_chunk", "magnetite_dust",
            "mica_dust", "quartz_dust", "roasted_copper_concentrate", "silica_dust", "sponge_iron",
            "sulfur_dust"
    );
    private static final Set<String> METAL_BATCH_ITEMS = Set.of("steel_bloom");
    private static final Set<String> TOOL_QUALITY_ITEMS = Set.of("geology_hammer");

    private QualityComponents() {
    }

    public static Item.Properties applyDefaults(String id, Item.Properties properties) {
        if (MATERIAL_BATCH_ITEMS.contains(id)) {
            properties = properties.component(EarthDataComponents.MATERIAL_BATCH.get(), MaterialBatch.defaultFor(id));
        }
        if (METAL_BATCH_ITEMS.contains(id)) {
            properties = properties.component(EarthDataComponents.METAL_BATCH.get(), MetalBatch.defaultFor("steel"));
        }
        if (TOOL_QUALITY_ITEMS.contains(id)) {
            properties = properties.component(EarthDataComponents.TOOL_QUALITY.get(), ToolQualityComponent.DEFAULT);
        }
        return properties;
    }

    public static void appendTooltip(ItemStack stack, Consumer<Component> lines) {
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
        if (batch != null) {
            lines.accept(gradeLine(batch.grade()));
            lines.accept(sourceLine(batch.speciesId() + " / " + String.format(java.util.Locale.ROOT, "%.2f", batch.purity())));
        }
        MetalBatch metal = stack.get(EarthDataComponents.METAL_BATCH.get());
        if (metal != null) {
            lines.accept(sourceLine(metal.alloy().getSerializedName() + " / "
                    + String.format(java.util.Locale.ROOT, "%.2f", metal.workability())));
        }
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
