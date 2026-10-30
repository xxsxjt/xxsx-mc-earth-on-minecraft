package com.xxsx.earthonminecraft.integration.jei;

import com.xxsx.earthonminecraft.EarthOnMinecraft;
import com.xxsx.earthonminecraft.ProcessingMachineBlock;
import com.xxsx.earthonminecraft.RouteGuide;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;

public class ProcessingJeiCategory implements IRecipeCategory<ProcessingMachineBlock.Recipe> {
    private final IDrawableStatic background;
    private final IDrawable icon;
    private final ProcessingMachineBlock.Kind kind;
    private final IRecipeType<ProcessingMachineBlock.Recipe> recipeType;

    public ProcessingJeiCategory(IGuiHelper guiHelper, ProcessingMachineBlock.Kind kind,
                                 IRecipeType<ProcessingMachineBlock.Recipe> recipeType, ItemLike iconItem) {
        this.background = guiHelper.createBlankDrawable(168, 104);
        this.icon = guiHelper.createDrawableItemLike(iconItem);
        this.kind = kind;
        this.recipeType = recipeType;
    }

    @Override
    public IRecipeType<ProcessingMachineBlock.Recipe> getRecipeType() {
        return recipeType;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.earth_on_minecraft.processing.machine", Component.translatable(kind.displayNameKey()));
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
    public void setRecipe(IRecipeLayoutBuilder builder, ProcessingMachineBlock.Recipe recipe, IFocusGroup focuses) {
        int slotIndex = 0;
        for (ItemStack input : recipe.inputStacks()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 4 + (slotIndex % 2) * 20, 22 + (slotIndex / 2) * 20)
                    .add(input)
                    .setStandardSlotBackground();
            slotIndex++;
        }
        // Mixed-input routes need both their item ingredients and the fluids consumed by the tank.
        for (ProcessingMachineBlock.FluidInput fluid : recipe.fluidInputs()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 4 + (slotIndex % 2) * 20, 22 + (slotIndex / 2) * 20)
                    .add(NeoForgeTypes.FLUID_STACK, new FluidStack(fluid.fluid(), fluid.amount()))
                    .setStandardSlotBackground();
            slotIndex++;
        }
        if (recipe.requiresCoke()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 4 + (slotIndex % 2) * 20, 22 + (slotIndex / 2) * 20)
                    .add(new net.minecraft.world.item.ItemStack(EarthOnMinecraft.COKE.get()))
                    .setStandardSlotBackground();
        }
        int x = 72;
        int y = 12;
        int index = 0;
        for (var stack : recipe.outputStacks()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, x + (index % 4) * 22, y + (index / 4) * 22)
                    .add(stack)
                    .setOutputSlotBackground();
            index++;
        }
        for (ProcessingMachineBlock.FluidOutput fluid : recipe.fluidOutputs()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, x + (index % 4) * 22, y + (index / 4) * 22)
                    .add(NeoForgeTypes.FLUID_STACK, new FluidStack(fluid.fluid(), fluid.amount()))
                    .setOutputSlotBackground();
            index++;
        }
    }

    @Override
    public void draw(ProcessingMachineBlock.Recipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        int accent = kind.processFamily().accentColor();
        graphics.fill(0, 0, getWidth(), 14, 0xFF25292C);
        graphics.fill(0, 14, getWidth(), getHeight(), 0xFF34383B);
        graphics.fill(0, 14, 3, getHeight(), accent);
        graphics.text(font, Component.translatable(kind.displayNameKey()), 5, 3, 0xFFF4F4F4);
        String processLine = localized(kind.processFamily().labelKey()) + " | " + localized(kind.setpointKey());
        graphics.text(font, fit(processLine, 158), 5, 16, accent);
        graphics.text(font, "->", 44, 31, 0xFFF2E4B6);
        graphics.text(font, fit(localized(kind.powerMode().labelKey()), 158), 5, 51, 0xFFFFD782);
        graphics.text(font, Component.translatable("jei.earth_on_minecraft.processing.spec",
                Math.max(1, (kind.processTicks() + 19) / 20), kind.energyPerTick()), 5, 62, 0xFFD9EEF4);
        String note = recipeNote(recipe);
        graphics.text(font, fit(note, 158), 5, 90, 0xFFFFF2CC);
    }

    private static String recipeNote(ProcessingMachineBlock.Recipe recipe) {
        if (Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(java.util.Locale.ROOT).startsWith("zh")) {
            return recipe.note();
        }
        return Language.getInstance().getOrDefault("screen.earth_on_minecraft.machine.recipe_ready") + ": " + RouteGuide.describeOutputs(recipe);
    }

    private static String localized(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    private static String fit(String text, int width) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }
}
