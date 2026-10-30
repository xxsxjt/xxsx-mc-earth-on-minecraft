package com.xxsx.earthonminecraft.integration.jei;

import com.xxsx.earthonminecraft.EarthOnMinecraft;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class IronworkingJeiCategory implements IRecipeCategory<IronworkingJeiCategory.IronworkingRecipe> {
    public static final IRecipeType<IronworkingRecipe> RECIPE_TYPE = IRecipeType.create(
            EarthOnMinecraft.MODID, "ironworking", IronworkingRecipe.class);

    private final IDrawableStatic background;
    private final IDrawable icon;

    public IronworkingJeiCategory(IGuiHelper guiHelper) {
        background = guiHelper.createBlankDrawable(150, 62);
        icon = guiHelper.createDrawableItemLike(EarthOnMinecraft.IRONWORKING_ANVIL.get());
    }

    @Override
    public IRecipeType<IronworkingRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.earth_on_minecraft.ironworking.title");
    }

    @Override
    public int getWidth() {
        return background.getWidth();
    }

    @Override
    public int getHeight() {
        return background.getHeight();
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, IronworkingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 4, 13)
                .add(recipe.input().copy())
                .setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 35)
                .add(new ItemStack(EarthOnMinecraft.GEOLOGY_HAMMER.get()))
                .setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.OUTPUT, 119, 13)
                .add(recipe.output().copy())
                .setOutputSlotBackground();
    }

    @Override
    public void draw(IronworkingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor graphics,
                     double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.fill(0, 0, getWidth(), 12, 0xFF292B30);
        graphics.fill(0, 12, getWidth(), getHeight(), 0xFF3A3935);
        graphics.fill(0, 12, 3, getHeight(), 0xFFB9945D);
        graphics.text(font, getTitle(), 5, 2, 0xFFF4F0E8);
        graphics.text(font, Component.translatable("jei.earth_on_minecraft.ironworking.instruction"),
                52, 26, 0xFFE4D7B8);
        graphics.text(font, Component.translatable("jei.earth_on_minecraft.ironworking.hammer_cost"),
                52, 39, 0xFFB9C3C8);
    }

    public record IronworkingRecipe(ItemStack input, ItemStack output) {
        public IronworkingRecipe {
            input = input.copy();
            output = output.copy();
        }
    }
}
