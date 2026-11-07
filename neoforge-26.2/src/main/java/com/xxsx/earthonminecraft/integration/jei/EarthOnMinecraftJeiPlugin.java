package com.xxsx.earthonminecraft.integration.jei;

import com.xxsx.earthonminecraft.EarthOnMinecraft;
import com.xxsx.earthonminecraft.MachineMultiblock;
import com.xxsx.earthonminecraft.ProcessingMachineBlock;
import com.xxsx.earthonminecraft.RouteGuide;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

@JeiPlugin
public class EarthOnMinecraftJeiPlugin implements IModPlugin {
    private static final Map<ProcessingMachineBlock.Kind, IRecipeType<ProcessingMachineBlock.Recipe>> PROCESSING_TYPES =
            createProcessingTypes();
    private static final ChatFormatting JEI_BODY = ChatFormatting.WHITE;
    private static final ChatFormatting JEI_HINT = ChatFormatting.YELLOW;

    @Override
    public Identifier getPluginUid() {
        return EarthOnMinecraft.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        for (ProcessingMachineBlock.Kind kind : ProcessingMachineBlock.Kind.values()) {
            registration.addRecipeCategories(new ProcessingJeiCategory(guiHelper, kind, recipeTypeFor(kind), machineFor(kind)));
        }
        registration.addRecipeCategories(new IronworkingJeiCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (ProcessingMachineBlock.Kind kind : ProcessingMachineBlock.Kind.values()) {
            registration.addRecipes(recipeTypeFor(kind), ProcessingMachineBlock.recipesFor(kind));
        }
        registerIronworkingRecipes(registration);
        registerHandbookInfo(registration);
        registerLivingWorldInfo(registration);
        registerMachineInfo(registration);
        registerEnergyInfo(registration);
        registerRouteInfo(registration);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        Item ae2Crank = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getOptional(Identifier.fromNamespaceAndPath("ae2", "crank"))
                .orElse(null);
        for (ProcessingMachineBlock.Kind kind : ProcessingMachineBlock.Kind.values()) {
            registration.addCraftingStation(recipeTypeFor(kind), machineFor(kind));
            if (kind.powerMode().manualFallback()) {
                registration.addCraftingStation(recipeTypeFor(kind), EarthOnMinecraft.HAND_CRANK.get());
                if (ae2Crank != null) {
                    registration.addCraftingStation(recipeTypeFor(kind), ae2Crank);
                }
            }
        }
        registration.addCraftingStation(IronworkingJeiCategory.RECIPE_TYPE, EarthOnMinecraft.IRONWORKING_ANVIL.get());
    }

    public static IRecipeType<ProcessingMachineBlock.Recipe> recipeTypeFor(ProcessingMachineBlock.Kind kind) {
        return PROCESSING_TYPES.get(kind);
    }

    private static Map<ProcessingMachineBlock.Kind, IRecipeType<ProcessingMachineBlock.Recipe>> createProcessingTypes() {
        EnumMap<ProcessingMachineBlock.Kind, IRecipeType<ProcessingMachineBlock.Recipe>> types =
                new EnumMap<>(ProcessingMachineBlock.Kind.class);
        for (ProcessingMachineBlock.Kind kind : ProcessingMachineBlock.Kind.values()) {
            types.put(kind, IRecipeType.create(EarthOnMinecraft.MODID, "processing_" + kind.blockId(), ProcessingMachineBlock.Recipe.class));
        }
        return Collections.unmodifiableMap(types);
    }

    private static ItemLike machineFor(ProcessingMachineBlock.Kind kind) {
        return switch (kind) {
            case CRUSHER -> EarthOnMinecraft.JAW_CRUSHER.get();
            case BALL_MILL -> EarthOnMinecraft.BALL_MILL.get();
            case SIEVE -> EarthOnMinecraft.SIEVE.get();
            case MAGNETIC_SEPARATOR -> EarthOnMinecraft.MAGNETIC_SEPARATOR.get();
            case FLOTATION_CELL -> EarthOnMinecraft.FLOTATION_CELL.get();
            case ROASTER -> EarthOnMinecraft.ORE_ROASTER.get();
            case REDUCTION_FURNACE -> EarthOnMinecraft.REDUCTION_FURNACE.get();
            case LEACHING_TANK -> EarthOnMinecraft.LEACHING_TANK.get();
            case ELECTROLYTIC_CELL -> EarthOnMinecraft.ELECTROLYTIC_CELL.get();
            case POWDER_PRESS -> EarthOnMinecraft.POWDER_PRESS.get();
            case CHEMICAL_REACTOR -> EarthOnMinecraft.CHEMICAL_REACTOR.get();
            case DISTILLATION_COLUMN -> EarthOnMinecraft.DISTILLATION_COLUMN.get();
            case MIXER -> EarthOnMinecraft.MIXER.get();
            case CRYSTALLIZER -> EarthOnMinecraft.CRYSTALLIZER.get();
            case INDUSTRIAL_KILN -> EarthOnMinecraft.INDUSTRIAL_KILN.get();
            case GAS_SEPARATOR -> EarthOnMinecraft.GAS_SEPARATOR.get();
            case FERTILIZER_GRANULATOR -> EarthOnMinecraft.FERTILIZER_GRANULATOR.get();
            case POLYMERIZER -> EarthOnMinecraft.POLYMERIZER.get();
            case STEAM_CRACKER -> EarthOnMinecraft.STEAM_CRACKER.get();
            case SYNTHESIS_LOOP -> EarthOnMinecraft.SYNTHESIS_LOOP.get();
            case ABSORPTION_TOWER -> EarthOnMinecraft.ABSORPTION_TOWER.get();
            case ANVIL -> EarthOnMinecraft.ANVIL.get();
            case BAR_COUNTER -> EarthOnMinecraft.BAR_COUNTER.get();
            case CAMPFIRE -> EarthOnMinecraft.CAMPFIRE.get();
            case CARPENTRY_BENCH -> EarthOnMinecraft.CARPENTRY_BENCH.get();
            case CASTING_TABLE -> EarthOnMinecraft.CASTING_TABLE.get();
            case COFFEE_ROASTER -> EarthOnMinecraft.COFFEE_ROASTER.get();
            case CONDENSER -> EarthOnMinecraft.CONDENSER.get();
            case CONVERTER -> EarthOnMinecraft.CONVERTER.get();
            case CURING_BARN -> EarthOnMinecraft.CURING_BARN.get();
            case FERMENTING_CROCK -> EarthOnMinecraft.FERMENTING_CROCK.get();
            case FILTER_PRESS -> EarthOnMinecraft.FILTER_PRESS.get();
            case GEM_CUTTER -> EarthOnMinecraft.GEM_CUTTER.get();
            case HERB_PROCESSING_TABLE -> EarthOnMinecraft.HERB_PROCESSING_TABLE.get();
            case LOOM -> EarthOnMinecraft.LOOM.get();
            case MILL -> EarthOnMinecraft.MILL.get();
            case NOODLE_PRESS -> EarthOnMinecraft.NOODLE_PRESS.get();
            case OIL_PRESS -> EarthOnMinecraft.OIL_PRESS.get();
            case RICE_HULLER -> EarthOnMinecraft.RICE_HULLER.get();
            case SMELTER -> EarthOnMinecraft.SMELTER.get();
            case SMOKEHOUSE -> EarthOnMinecraft.SMOKEHOUSE.get();
            case STEAMER -> EarthOnMinecraft.STEAMER.get();
            case STEAMER_BASKET -> EarthOnMinecraft.STEAMER_BASKET.get();
            case STILL -> EarthOnMinecraft.STILL.get();
            case STONE_MILL -> EarthOnMinecraft.STONE_MILL.get();
            case STOVE -> EarthOnMinecraft.STOVE.get();
            case TABLET_PRESS -> EarthOnMinecraft.TABLET_PRESS.get();
            case TANNERY -> EarthOnMinecraft.TANNERY.get();
            case TEA_KILN -> EarthOnMinecraft.TEA_KILN.get();
            case THICKENER -> EarthOnMinecraft.THICKENER.get();
            case WATER_TREATMENT_UNIT -> EarthOnMinecraft.WATER_TREATMENT_UNIT.get();
            case BLOOMERY -> EarthOnMinecraft.BLOOMERY.get();
            case BREAD_OVEN -> EarthOnMinecraft.BREAD_OVEN.get();
            case GLASS_KILN -> EarthOnMinecraft.GLASS_KILN.get();
            case LIME_KILN -> EarthOnMinecraft.LIME_KILN.get();
            case PIT_KILN -> EarthOnMinecraft.PIT_KILN.get();
            case SHAFT_FURNACE -> EarthOnMinecraft.SHAFT_FURNACE.get();
        };
    }

    private static void registerHandbookInfo(IRecipeRegistration registration) {
        registration.addItemStackInfo(new ItemStack(EarthOnMinecraft.FIELD_GEOLOGY_NOTEBOOK.get()),
                lineKey("jei.earth_on_minecraft.notebook.0", ChatFormatting.GOLD),
                lineKey("jei.earth_on_minecraft.notebook.1", JEI_BODY),
                lineKey("jei.earth_on_minecraft.notebook.2", ChatFormatting.AQUA),
                lineKey("jei.earth_on_minecraft.notebook.3", ChatFormatting.GREEN),
                lineKey("jei.earth_on_minecraft.notebook.4", ChatFormatting.GOLD),
                lineKey("jei.earth_on_minecraft.notebook.5", ChatFormatting.AQUA),
                lineKey("jei.earth_on_minecraft.notebook.6", JEI_HINT));
    }

    private static void registerLivingWorldInfo(IRecipeRegistration registration) {
        registration.addItemStackInfo(new ItemStack(EarthOnMinecraft.SETTLEMENT_BOARD.get()),
                lineKey("jei.earth_on_minecraft.settlement_board.0", ChatFormatting.GOLD),
                lineKey("jei.earth_on_minecraft.settlement_board.1", JEI_BODY),
                lineKey("jei.earth_on_minecraft.settlement_board.2", ChatFormatting.AQUA),
                lineKey("jei.earth_on_minecraft.settlement_board.3", ChatFormatting.GREEN),
                lineKey("jei.earth_on_minecraft.settlement_board.4", JEI_HINT),
                lineKey("jei.earth_on_minecraft.settlement_board.5", JEI_BODY));
    }

    private static void registerIronworkingRecipes(IRecipeRegistration registration) {
        registration.addRecipes(IronworkingJeiCategory.RECIPE_TYPE, java.util.List.of(
                new IronworkingJeiCategory.IronworkingRecipe(
                        new ItemStack(EarthOnMinecraft.SPONGE_IRON.get()),
                        new ItemStack(EarthOnMinecraft.WROUGHT_IRON.get())),
                new IronworkingJeiCategory.IronworkingRecipe(
                        new ItemStack(EarthOnMinecraft.WROUGHT_IRON.get()),
                        new ItemStack(net.minecraft.world.item.Items.IRON_INGOT))));
        registration.addItemStackInfo(new ItemStack(EarthOnMinecraft.GEOLOGY_HAMMER.get()),
                lineKey("jei.earth_on_minecraft.ironworking.hammer.0", ChatFormatting.GOLD),
                lineKey("jei.earth_on_minecraft.ironworking.hammer.1", JEI_BODY),
                lineKey("jei.earth_on_minecraft.ironworking.hammer.2", JEI_HINT),
                lineKey("jei.earth_on_minecraft.ironworking.hammer.3", JEI_BODY));
        registration.addItemStackInfo(new ItemStack(EarthOnMinecraft.IRONWORKING_ANVIL.get()),
                lineKey("jei.earth_on_minecraft.ironworking.anvil.0", ChatFormatting.GOLD),
                lineKey("jei.earth_on_minecraft.ironworking.anvil.1", JEI_BODY),
                lineKey("jei.earth_on_minecraft.ironworking.anvil.2", JEI_HINT));
        registration.addItemStackInfo(new ItemStack(EarthOnMinecraft.SPONGE_IRON.get()),
                lineKey("jei.earth_on_minecraft.ironworking.sponge.0", ChatFormatting.GOLD),
                lineKey("jei.earth_on_minecraft.ironworking.sponge.1", JEI_BODY));
        registration.addItemStackInfo(new ItemStack(EarthOnMinecraft.WROUGHT_IRON.get()),
                lineKey("jei.earth_on_minecraft.ironworking.wrought.0", ChatFormatting.GOLD),
                lineKey("jei.earth_on_minecraft.ironworking.wrought.1", JEI_BODY));
    }

    private static void registerMachineInfo(IRecipeRegistration registration) {
        registerMachine(registration, EarthOnMinecraft.JAW_CRUSHER.get(), ProcessingMachineBlock.Kind.CRUSHER);
        registerMachine(registration, EarthOnMinecraft.BALL_MILL.get(), ProcessingMachineBlock.Kind.BALL_MILL);
        registerMachine(registration, EarthOnMinecraft.SIEVE.get(), ProcessingMachineBlock.Kind.SIEVE);
        registerMachine(registration, EarthOnMinecraft.MAGNETIC_SEPARATOR.get(), ProcessingMachineBlock.Kind.MAGNETIC_SEPARATOR);
        registerMachine(registration, EarthOnMinecraft.FLOTATION_CELL.get(), ProcessingMachineBlock.Kind.FLOTATION_CELL);
        registerMachine(registration, EarthOnMinecraft.ORE_ROASTER.get(), ProcessingMachineBlock.Kind.ROASTER);
        registerMachine(registration, EarthOnMinecraft.REDUCTION_FURNACE.get(), ProcessingMachineBlock.Kind.REDUCTION_FURNACE);
        registerMachine(registration, EarthOnMinecraft.LEACHING_TANK.get(), ProcessingMachineBlock.Kind.LEACHING_TANK);
        registerMachine(registration, EarthOnMinecraft.ELECTROLYTIC_CELL.get(), ProcessingMachineBlock.Kind.ELECTROLYTIC_CELL);
        registerMachine(registration, EarthOnMinecraft.POWDER_PRESS.get(), ProcessingMachineBlock.Kind.POWDER_PRESS);
        registerMachine(registration, EarthOnMinecraft.CHEMICAL_REACTOR.get(), ProcessingMachineBlock.Kind.CHEMICAL_REACTOR);
        registerMachine(registration, EarthOnMinecraft.DISTILLATION_COLUMN.get(), ProcessingMachineBlock.Kind.DISTILLATION_COLUMN);
        registerMachine(registration, EarthOnMinecraft.MIXER.get(), ProcessingMachineBlock.Kind.MIXER);
        registerMachine(registration, EarthOnMinecraft.CRYSTALLIZER.get(), ProcessingMachineBlock.Kind.CRYSTALLIZER);
        registerMachine(registration, EarthOnMinecraft.INDUSTRIAL_KILN.get(), ProcessingMachineBlock.Kind.INDUSTRIAL_KILN);
        registerMachine(registration, EarthOnMinecraft.GAS_SEPARATOR.get(), ProcessingMachineBlock.Kind.GAS_SEPARATOR);
        registerMachine(registration, EarthOnMinecraft.FERTILIZER_GRANULATOR.get(), ProcessingMachineBlock.Kind.FERTILIZER_GRANULATOR);
        registerMachine(registration, EarthOnMinecraft.POLYMERIZER.get(), ProcessingMachineBlock.Kind.POLYMERIZER);
        registerMachine(registration, EarthOnMinecraft.STEAM_CRACKER.get(), ProcessingMachineBlock.Kind.STEAM_CRACKER);
        registerMachine(registration, EarthOnMinecraft.SYNTHESIS_LOOP.get(), ProcessingMachineBlock.Kind.SYNTHESIS_LOOP);
        registerMachine(registration, EarthOnMinecraft.ABSORPTION_TOWER.get(), ProcessingMachineBlock.Kind.ABSORPTION_TOWER);
        registerMachine(registration, EarthOnMinecraft.ANVIL.get(), ProcessingMachineBlock.Kind.ANVIL);
        registerMachine(registration, EarthOnMinecraft.BAR_COUNTER.get(), ProcessingMachineBlock.Kind.BAR_COUNTER);
        registerMachine(registration, EarthOnMinecraft.CAMPFIRE.get(), ProcessingMachineBlock.Kind.CAMPFIRE);
        registerMachine(registration, EarthOnMinecraft.CARPENTRY_BENCH.get(), ProcessingMachineBlock.Kind.CARPENTRY_BENCH);
        registerMachine(registration, EarthOnMinecraft.CASTING_TABLE.get(), ProcessingMachineBlock.Kind.CASTING_TABLE);
        registerMachine(registration, EarthOnMinecraft.COFFEE_ROASTER.get(), ProcessingMachineBlock.Kind.COFFEE_ROASTER);
        registerMachine(registration, EarthOnMinecraft.CONDENSER.get(), ProcessingMachineBlock.Kind.CONDENSER);
        registerMachine(registration, EarthOnMinecraft.CONVERTER.get(), ProcessingMachineBlock.Kind.CONVERTER);
        registerMachine(registration, EarthOnMinecraft.CURING_BARN.get(), ProcessingMachineBlock.Kind.CURING_BARN);
        registerMachine(registration, EarthOnMinecraft.FERMENTING_CROCK.get(), ProcessingMachineBlock.Kind.FERMENTING_CROCK);
        registerMachine(registration, EarthOnMinecraft.FILTER_PRESS.get(), ProcessingMachineBlock.Kind.FILTER_PRESS);
        registerMachine(registration, EarthOnMinecraft.GEM_CUTTER.get(), ProcessingMachineBlock.Kind.GEM_CUTTER);
        registerMachine(registration, EarthOnMinecraft.HERB_PROCESSING_TABLE.get(), ProcessingMachineBlock.Kind.HERB_PROCESSING_TABLE);
        registerMachine(registration, EarthOnMinecraft.LOOM.get(), ProcessingMachineBlock.Kind.LOOM);
        registerMachine(registration, EarthOnMinecraft.MILL.get(), ProcessingMachineBlock.Kind.MILL);
        registerMachine(registration, EarthOnMinecraft.NOODLE_PRESS.get(), ProcessingMachineBlock.Kind.NOODLE_PRESS);
        registerMachine(registration, EarthOnMinecraft.OIL_PRESS.get(), ProcessingMachineBlock.Kind.OIL_PRESS);
        registerMachine(registration, EarthOnMinecraft.RICE_HULLER.get(), ProcessingMachineBlock.Kind.RICE_HULLER);
        registerMachine(registration, EarthOnMinecraft.SMELTER.get(), ProcessingMachineBlock.Kind.SMELTER);
        registerMachine(registration, EarthOnMinecraft.SMOKEHOUSE.get(), ProcessingMachineBlock.Kind.SMOKEHOUSE);
        registerMachine(registration, EarthOnMinecraft.STEAMER.get(), ProcessingMachineBlock.Kind.STEAMER);
        registerMachine(registration, EarthOnMinecraft.STEAMER_BASKET.get(), ProcessingMachineBlock.Kind.STEAMER_BASKET);
        registerMachine(registration, EarthOnMinecraft.STILL.get(), ProcessingMachineBlock.Kind.STILL);
        registerMachine(registration, EarthOnMinecraft.STONE_MILL.get(), ProcessingMachineBlock.Kind.STONE_MILL);
        registerMachine(registration, EarthOnMinecraft.STOVE.get(), ProcessingMachineBlock.Kind.STOVE);
        registerMachine(registration, EarthOnMinecraft.TABLET_PRESS.get(), ProcessingMachineBlock.Kind.TABLET_PRESS);
        registerMachine(registration, EarthOnMinecraft.TANNERY.get(), ProcessingMachineBlock.Kind.TANNERY);
        registerMachine(registration, EarthOnMinecraft.TEA_KILN.get(), ProcessingMachineBlock.Kind.TEA_KILN);
        registerMachine(registration, EarthOnMinecraft.THICKENER.get(), ProcessingMachineBlock.Kind.THICKENER);
        registerMachine(registration, EarthOnMinecraft.WATER_TREATMENT_UNIT.get(), ProcessingMachineBlock.Kind.WATER_TREATMENT_UNIT);
    }

    private static void registerMachine(IRecipeRegistration registration, ItemLike item, ProcessingMachineBlock.Kind kind) {
        registration.addItemStackInfo(new ItemStack(item.asItem()),
                Component.translatable(kind.displayNameKey()).withStyle(ChatFormatting.GOLD),
                Component.translatable(kind.descriptionKey()).withStyle(JEI_BODY),
                lineKey("tooltip.earth_on_minecraft.machine.use", ChatFormatting.AQUA),
                Component.translatable(kind.powerMode().labelKey()).withStyle(ChatFormatting.YELLOW),
                kind.powerMode().manualFallback()
                        ? lineKey("tooltip.earth_on_minecraft.machine.crank_hint", ChatFormatting.GOLD)
                        : lineKey("tooltip.earth_on_minecraft.machine.fuel", ChatFormatting.YELLOW),
                Component.translatable(MachineMultiblock.patternFor(kind).descriptionKey()).withStyle(ChatFormatting.GREEN),
                lineKey("tooltip.earth_on_minecraft.machine.redstone", ChatFormatting.GREEN),
                lineKey("tooltip.earth_on_minecraft.machine.routes", JEI_HINT, ProcessingMachineBlock.recipesFor(kind).size()));
    }

    private static void registerEnergyInfo(IRecipeRegistration registration) {
        registration.addItemStackInfo(new ItemStack(EarthOnMinecraft.COMBUSTION_GENERATOR.get()),
                lineKey("tooltip.earth_on_minecraft.energy.generator", ChatFormatting.GOLD),
                lineKey("jei.earth_on_minecraft.energy.generator.0", JEI_BODY),
                lineKey("jei.earth_on_minecraft.energy.generator.1", ChatFormatting.AQUA));
        registration.addItemStackInfo(new ItemStack(EarthOnMinecraft.THIN_COPPER_POWER_CABLE.get()),
                lineKey("tooltip.earth_on_minecraft.energy.cable", ChatFormatting.GOLD),
                lineKey("jei.earth_on_minecraft.energy.cable.0", JEI_BODY),
                lineKey("jei.earth_on_minecraft.energy.cable.1", ChatFormatting.AQUA));
        registration.addItemStackInfo(new ItemStack(EarthOnMinecraft.COPPER_POWER_CABLE.get()),
                lineKey("tooltip.earth_on_minecraft.energy.cable", ChatFormatting.GOLD),
                lineKey("jei.earth_on_minecraft.energy.cable.0", JEI_BODY),
                lineKey("jei.earth_on_minecraft.energy.cable.1", ChatFormatting.AQUA));
        registration.addItemStackInfo(new ItemStack(EarthOnMinecraft.HEAVY_COPPER_POWER_CABLE.get()),
                lineKey("tooltip.earth_on_minecraft.energy.cable", ChatFormatting.GOLD),
                lineKey("jei.earth_on_minecraft.energy.cable.0", JEI_BODY),
                lineKey("jei.earth_on_minecraft.energy.cable.1", ChatFormatting.AQUA));
        registration.addItemStackInfo(new ItemStack(EarthOnMinecraft.BATTERY_BOX.get()),
                lineKey("tooltip.earth_on_minecraft.energy.battery", ChatFormatting.GOLD),
                lineKey("jei.earth_on_minecraft.energy.battery.0", JEI_BODY),
                lineKey("jei.earth_on_minecraft.energy.battery.1", ChatFormatting.AQUA));
    }

    private static void registerRouteInfo(IRecipeRegistration registration) {
        for (Map.Entry<Item, RouteGuide.RouteInfo> entry : RouteGuide.buildRoutesByItem().entrySet()) {
            Item item = entry.getKey();
            RouteGuide.RouteInfo info = entry.getValue();
            java.util.List<Component> lines = new java.util.ArrayList<>();
            lines.add(lineKey("jei.earth_on_minecraft.route.header", ChatFormatting.GOLD));
            if (!info.next().isEmpty()) {
                ProcessingMachineBlock.Recipe example = info.next().get(0);
                lines.add(lineKey("tooltip.earth_on_minecraft.route.next", ChatFormatting.AQUA, RouteGuide.joinMachines(info.next(), 4)));
                lines.add(lineKey("tooltip.earth_on_minecraft.route.outputs", JEI_BODY, RouteGuide.describeOutputs(example)));
            }
            RouteGuide.addBeginnerShortcutTips(item, lines::add);
            if (!info.sources().isEmpty()) {
                lines.add(lineKey("tooltip.earth_on_minecraft.route.sources", ChatFormatting.DARK_GREEN, RouteGuide.joinSources(info.sources(), 3)));
            }
            lines.add(lineKey("jei.earth_on_minecraft.route.footer", JEI_HINT));
            registration.addItemStackInfo(new ItemStack(item), lines.toArray(Component[]::new));
        }
    }

    private static Component line(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    private static Component lineKey(String key, ChatFormatting color, Object... args) {
        return Component.translatable(key, args).withStyle(color);
    }
}
