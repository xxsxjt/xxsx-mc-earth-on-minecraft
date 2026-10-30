package com.xxsx.earthonminecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration for every spec fluid: one fluid type plus a still/flowing pair.
 *
 * <p>The still variant is registered first and the flowing variant points back at it. Both are
 * held in {@link #REGISTERED} so callers can resolve a fluid id to its source holder.</p>
 */
public final class SpecFluids {
    public record FluidEntry(String id, String stateOfMatter, int density, int viscosity,
                             int temperatureK, boolean luminous) {
    }

    public record Registered(DeferredHolder<FluidType, FluidType> type,
                             DeferredHolder<Fluid, Fluid> source,
                             DeferredHolder<Fluid, Fluid> flowing) {
    }

    private static final List<FluidEntry> ENTRIES = List.of(
            new FluidEntry("carbon_dioxide", "gas", 1000, 1000, 293, false),
            new FluidEntry("carbon_monoxide", "gas", 1000, 1000, 293, false),
            new FluidEntry("chlorine", "gas", 1000, 1000, 293, false),
            new FluidEntry("crude_oil", "liquid", 1000, 1000, 293, false),
            new FluidEntry("fluorine", "gas", 1000, 1000, 293, false),
            new FluidEntry("hydrogen", "gas", 1000, 1000, 293, false),
            new FluidEntry("molten_aluminium", "liquid", 2, 2, 934, true),
            new FluidEntry("molten_blister_copper", "liquid", 8, 2, 1358, true),
            new FluidEntry("molten_calcium", "liquid", 1, 2, 1115, true),
            new FluidEntry("molten_calcium_carbide", "liquid", 2, 2, 2160, true),
            new FluidEntry("molten_chromium", "liquid", 6, 2, 2180, true),
            new FluidEntry("molten_copper", "liquid", 1000, 1000, 293, true),
            new FluidEntry("molten_ferrosilicon", "liquid", 6, 2, 1473, true),
            new FluidEntry("molten_iron", "liquid", 1000, 1000, 293, true),
            new FluidEntry("molten_magnesium", "liquid", 1, 2, 923, true),
            new FluidEntry("molten_manganese", "liquid", 5, 2, 1519, true),
            new FluidEntry("molten_silicon", "liquid", 2, 2, 1687, true),
            new FluidEntry("molten_sodium", "liquid", 0, 2, 371, true),
            new FluidEntry("molten_sodium_cyanide", "liquid", 1, 2, 835, true),
            new FluidEntry("molten_tin", "liquid", 6, 2, 505, true),
            new FluidEntry("molten_titanium_sponge", "liquid", 4, 2, 1941, true),
            new FluidEntry("nitrogen", "gas", 1000, 1000, 293, false),
            new FluidEntry("oxygen", "gas", 1000, 1000, 293, false),
            new FluidEntry("phosphorus_white_vapour", "gas", 0, 0, 553, false),
            new FluidEntry("slurry", "slurry", 1000, 1000, 293, false),
            new FluidEntry("spent_electrolyte", "liquid", 1000, 1000, 293, false),
            new FluidEntry("steam", "gas", 1000, 1000, 293, false),
            new FluidEntry("sulfur_dioxide", "gas", 1000, 1000, 293, false),
            new FluidEntry("uranium_hexafluoride", "gas", 1000, 1000, 293, false),
            new FluidEntry("uranium_leachate", "liquid", 1000, 1000, 293, false)
    );

    private static final Map<String, FluidEntry> BY_ID = new LinkedHashMap<>();
    private static final Map<String, Registered> REGISTERED = new LinkedHashMap<>();

    private SpecFluids() {
    }

    public static void register(DeferredRegister<FluidType> fluidTypes, DeferredRegister<Fluid> fluids) {
        for (FluidEntry entry : ENTRIES) {
            BY_ID.put(entry.id(), entry);
            DeferredHolder<FluidType, FluidType> type = fluidTypes.register(entry.id(),
                    () -> new FluidType(FluidType.Properties.create()
                            .descriptionId("fluid.earth_on_minecraft." + entry.id())
                            .density(entry.density())
                            .viscosity(entry.viscosity())
                            .temperature(entry.temperatureK())
                            .lightLevel(entry.luminous() ? 12 : 0)
                            .canDrown(false)
                            .canSwim(false)
                            .supportsBoating(false)));
            // The still fluid is created first; the flowing variant resolves it from the map.
            DeferredHolder<Fluid, Fluid> source = fluids.register(entry.id(),
                    () -> new BaseFlowingFluid.Source(properties(entry.id(), type, false)));
            DeferredHolder<Fluid, Fluid> flowing = fluids.register("flowing_" + entry.id(),
                    () -> new BaseFlowingFluid.Flowing(properties(entry.id(), type, true)));
            REGISTERED.put(entry.id(), new Registered(type, source, flowing));
        }
    }

    private static BaseFlowingFluid.Properties properties(String id, DeferredHolder<FluidType, FluidType> type,
                                                          boolean flowingVariant) {
        return new BaseFlowingFluid.Properties(
                type,
                () -> resolve(id, false),
                () -> resolve(id, true))
                .slopeFindDistance(entry(id).stateOfMatter().equals("gas") ? 1 : 3)
                .levelDecreasePerBlock(entry(id).stateOfMatter().equals("gas") ? 2 : 1)
                .tickRate(entry(id).stateOfMatter().equals("gas") ? 2 : 5)
                .explosionResistance(100.0F);
    }

    private static Fluid resolve(String id, boolean flowingVariant) {
        Registered registered = REGISTERED.get(id);
        if (registered == null) {
            return null;
        }
        return (flowingVariant ? registered.flowing() : registered.source()).get();
    }

    public static List<FluidEntry> entries() {
        return ENTRIES;
    }

    public static Map<String, Registered> registered() {
        return Map.copyOf(REGISTERED);
    }

    public static FluidEntry entry(String id) {
        return BY_ID.get(id);
    }

    public static DeferredHolder<Fluid, Fluid> source(String id) {
        Registered registered = REGISTERED.get(id);
        return registered == null ? null : registered.source();
    }
}
