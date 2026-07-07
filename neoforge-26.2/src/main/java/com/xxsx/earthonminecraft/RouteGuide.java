package com.xxsx.earthonminecraft;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class RouteGuide {
    private static final Map<Item, RouteInfo> ROUTES_BY_ITEM = createRoutesByItem();

    private RouteGuide() {
    }

    public static Map<Item, RouteInfo> buildRoutesByItem() {
        return ROUTES_BY_ITEM;
    }

    private static Map<Item, RouteInfo> createRoutesByItem() {
        Map<Item, RouteInfo> routes = new LinkedHashMap<>();
        for (ProcessingMachineBlock.Recipe recipe : ProcessingMachineBlock.recipes()) {
            routes.computeIfAbsent(recipe.input().get().asItem(), ignored -> new RouteInfo()).next.add(recipe);
            for (ProcessingMachineBlock.Output output : recipe.outputs()) {
                routes.computeIfAbsent(output.item().get().asItem(), ignored -> new RouteInfo()).sources.add(recipe);
            }
        }
        return routes;
    }

    public static RouteInfo routeFor(Item item) {
        return ROUTES_BY_ITEM.getOrDefault(item, RouteInfo.EMPTY);
    }

    public static void addRouteTips(ItemStack stack, Consumer<Component> lines) {
        if (stack.isEmpty()) {
            return;
        }
        RouteInfo route = routeFor(stack.getItem());

        addBeginnerShortcutTips(stack.getItem(), lines);
        addDirectUseTips(stack.getItem(), lines);

        if (!route.next().isEmpty()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.route.next", joinMachines(route.next(), 4)).withStyle(ChatFormatting.AQUA));
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.route.outputs", describeOutputs(route.next().get(0))).withStyle(ChatFormatting.GRAY));
            if (uniqueMachineCount(route.next()) > 1) {
                lines.accept(Component.translatable("tooltip.earth_on_minecraft.route.multiple").withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        if (!route.sources().isEmpty()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.route.sources", joinSources(route.sources(), 3)).withStyle(ChatFormatting.DARK_GREEN));
        }

        if (route.isEmpty()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.route.compat").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void addBeginnerShortcutTips(Item item, Consumer<Component> lines) {
        if (item == EarthOnMinecraft.MAGNETITE_CHUNK.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.beginner.first_iron_chunk").withStyle(ChatFormatting.GOLD));
        } else if (item == EarthOnMinecraft.MAGNETITE_DUST.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.beginner.first_iron_dust").withStyle(ChatFormatting.GOLD));
        } else if (item == EarthOnMinecraft.CHALCOPYRITE_CHUNK.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.beginner.first_copper_chunk").withStyle(ChatFormatting.GOLD));
        } else if (item == EarthOnMinecraft.CHALCOPYRITE_DUST.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.beginner.first_copper_dust").withStyle(ChatFormatting.GOLD));
        } else if (item == EarthOnMinecraft.JAW_CRUSHER.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.beginner.jaw_crusher").withStyle(ChatFormatting.GOLD));
        } else if (item == EarthOnMinecraft.BALL_MILL.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.beginner.ball_mill").withStyle(ChatFormatting.GOLD));
        } else if (item == EarthOnMinecraft.BITUMINOUS_COAL_SEAM.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.deposit.bituminous").withStyle(ChatFormatting.YELLOW));
        } else if (item == EarthOnMinecraft.ANTHRACITE_COAL_SEAM.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.deposit.anthracite").withStyle(ChatFormatting.YELLOW));
        } else if (item == EarthOnMinecraft.BAUXITE_LATERITE_DEPOSIT.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.deposit.bauxite").withStyle(ChatFormatting.YELLOW));
        } else if (item == EarthOnMinecraft.PHOSPHORITE_BED.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.deposit.phosphorite").withStyle(ChatFormatting.YELLOW));
        } else if (item == EarthOnMinecraft.EVAPORITE_SALT_BED.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.deposit.evaporite").withStyle(ChatFormatting.YELLOW));
        } else if (item == EarthOnMinecraft.CASSITERITE_PLACER_DEPOSIT.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.deposit.cassiterite").withStyle(ChatFormatting.YELLOW));
        } else if (item == EarthOnMinecraft.RARE_EARTH_CARBONATITE.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.deposit.rare_earth").withStyle(ChatFormatting.YELLOW));
        } else if (item == EarthOnMinecraft.COAL_DUST.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.fuel.coal_dust").withStyle(ChatFormatting.YELLOW));
        } else if (item == EarthOnMinecraft.COKE.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.fuel.coke").withStyle(ChatFormatting.YELLOW));
        } else if (item == EarthOnMinecraft.PETROLEUM_COKE.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.fuel.petroleum_coke").withStyle(ChatFormatting.YELLOW));
        } else if (item == EarthOnMinecraft.COAL_GAS_CELL.get().asItem() || item == EarthOnMinecraft.NATURAL_GAS_CELL.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.fuel.gas_cell").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void addDirectUseTips(Item item, Consumer<Component> lines) {
        if (item == EarthOnMinecraft.STEEL_BLOOM.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.steel_bloom").withStyle(ChatFormatting.GREEN));
        } else if (item == EarthOnMinecraft.PRINTED_CIRCUIT_BOARD.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.printed_circuit_board").withStyle(ChatFormatting.GREEN));
        } else if (item == EarthOnMinecraft.SIMPLE_BATTERY_CELL.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.simple_battery_cell").withStyle(ChatFormatting.GREEN));
        } else if (item == EarthOnMinecraft.VULCANIZED_RUBBER.get().asItem() || item == EarthOnMinecraft.RUBBER_GASKET.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.rubber").withStyle(ChatFormatting.GREEN));
        } else if (item == EarthOnMinecraft.CEMENT_POWDER.get().asItem() || item == EarthOnMinecraft.ASPHALT.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.construction").withStyle(ChatFormatting.GREEN));
        } else if (item == EarthOnMinecraft.FERTILIZER_BLEND.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.fertilizer").withStyle(ChatFormatting.GREEN));
        } else if (item == EarthOnMinecraft.HUMUS_SAMPLE.get().asItem() || item == EarthOnMinecraft.SANDY_LOAM_SAMPLE.get().asItem()
                || item == EarthOnMinecraft.ALLUVIAL_LOAM_SAMPLE.get().asItem() || item == EarthOnMinecraft.SALINE_SOIL_SAMPLE.get().asItem()
                || item == EarthOnMinecraft.SOIL_MINERAL_MIX.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.soil").withStyle(ChatFormatting.GREEN));
        } else if (item == EarthOnMinecraft.IRRIGATION_MINERAL_DEPOSIT.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.irrigation_deposit").withStyle(ChatFormatting.GREEN));
        } else if (item == EarthOnMinecraft.CELLULOSE_PULP.get().asItem() || item == EarthOnMinecraft.BLEACHED_PULP.get().asItem()
                || item == EarthOnMinecraft.CELLULOSE_FIBER.get().asItem() || item == EarthOnMinecraft.NYLON_FIBER.get().asItem()
                || item == EarthOnMinecraft.MINERAL_WOOL.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.fiber").withStyle(ChatFormatting.GREEN));
        } else if (item == EarthOnMinecraft.CARBON_BLACK.get().asItem() || item == EarthOnMinecraft.TITANIUM_DIOXIDE.get().asItem()
                || item == EarthOnMinecraft.IRON_OXIDE_PIGMENT.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.pigment").withStyle(ChatFormatting.GREEN));
        } else if (item == EarthOnMinecraft.YELLOWCAKE.get().asItem() || item == EarthOnMinecraft.LOW_ENRICHED_URANIUM.get().asItem()
                || item == EarthOnMinecraft.NUCLEAR_FUEL_ASSEMBLY.get().asItem() || item == EarthOnMinecraft.NUCLEAR_HEAT_MODULE.get().asItem()
                || item == EarthOnMinecraft.DRY_STORAGE_CASK.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.nuclear").withStyle(ChatFormatting.GREEN));
        } else if (item == EarthOnMinecraft.INDUSTRIAL_SENSOR.get().asItem() || item == EarthOnMinecraft.PLC_CONTROLLER.get().asItem()
                || item == EarthOnMinecraft.ROBOTIC_ARM.get().asItem() || item == EarthOnMinecraft.QUALITY_INSPECTION_MODULE.get().asItem()
                || item == EarthOnMinecraft.AUTOMATION_BUS.get().asItem() || item == EarthOnMinecraft.REDSTONE_IO_GATEWAY.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.automation").withStyle(ChatFormatting.GREEN));
        } else if (item == EarthOnMinecraft.TRANSFORMER_CORE.get().asItem() || item == EarthOnMinecraft.GRID_SWITCHGEAR.get().asItem()
                || item == EarthOnMinecraft.GENERATOR_STATOR.get().asItem() || item == EarthOnMinecraft.STEAM_TURBINE_ASSEMBLY.get().asItem()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.use.grid").withStyle(ChatFormatting.GREEN));
        }
    }

    public static String joinMachines(List<ProcessingMachineBlock.Recipe> recipes, int limit) {
        List<String> names = new ArrayList<>();
        for (ProcessingMachineBlock.Recipe recipe : recipes) {
            String name = recipe.kind().localizedDisplayName();
            if (!names.contains(name)) {
                names.add(name);
            }
            if (names.size() >= limit) {
                break;
            }
        }
        return String.join(" / ", names);
    }

    public static String joinSources(List<ProcessingMachineBlock.Recipe> recipes, int limit) {
        List<String> names = new ArrayList<>();
        for (ProcessingMachineBlock.Recipe recipe : recipes) {
            String input = recipe.inputStack().getItemName().getString();
            String name = recipe.kind().localizedDisplayName() + ": " + input;
            if (!names.contains(name)) {
                names.add(name);
            }
            if (names.size() >= limit) {
                break;
            }
        }
        return String.join("；", names);
    }

    public static String describeOutputs(ProcessingMachineBlock.Recipe recipe) {
        List<String> outputs = new ArrayList<>();
        for (ProcessingMachineBlock.Output output : recipe.outputs()) {
            ItemStack out = output.stack();
            outputs.add(out.getCount() + "x " + out.getItemName().getString());
        }
        return String.join(" + ", outputs);
    }

    public static int uniqueMachineCount(List<ProcessingMachineBlock.Recipe> recipes) {
        List<ProcessingMachineBlock.Kind> kinds = new ArrayList<>();
        for (ProcessingMachineBlock.Recipe recipe : recipes) {
            if (!kinds.contains(recipe.kind())) {
                kinds.add(recipe.kind());
            }
        }
        return kinds.size();
    }

    public static final class RouteInfo {
        private static final RouteInfo EMPTY = new RouteInfo();

        private final List<ProcessingMachineBlock.Recipe> next = new ArrayList<>();
        private final List<ProcessingMachineBlock.Recipe> sources = new ArrayList<>();

        public List<ProcessingMachineBlock.Recipe> next() {
            return next;
        }

        public List<ProcessingMachineBlock.Recipe> sources() {
            return sources;
        }

        public boolean isEmpty() {
            return next.isEmpty() && sources.isEmpty();
        }
    }
}
