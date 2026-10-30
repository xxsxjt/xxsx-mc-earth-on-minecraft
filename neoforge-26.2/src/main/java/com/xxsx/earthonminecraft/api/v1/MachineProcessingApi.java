package com.xxsx.earthonminecraft.api.v1;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;

public interface MachineProcessingApi {
    List<MachineSpec> machines();

    Optional<MachineSpec> machine(Identifier machineId);

    List<ProcessingRoute> routes();

    List<ProcessingRoute> routesFor(Identifier machineId);

    record MachineSpec(Identifier id, String displayNameKey, String family, int processTicks,
                       int energyPerTick, String powerMode, String multiblockPattern, int routeCount) {
    }

    record ProcessingRoute(Identifier machineId, Identifier input, List<RouteInput> inputs,
                           List<RouteOutput> outputs, List<RouteFluid> fluidInputs,
                           List<RouteFluid> fluidOutputs, int processTicks, int energyPerTick,
                           String note, String sourceId) {
        public ProcessingRoute {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            fluidInputs = List.copyOf(fluidInputs);
            fluidOutputs = List.copyOf(fluidOutputs);
        }

        /** A route is productive if it yields an item or a fluid; molten-metal casts yield only fluid. */
        public boolean hasOutputs() {
            return !outputs.isEmpty() || !fluidOutputs.isEmpty();
        }
    }

    record RouteInput(Identifier item, int count) {
    }

    record RouteOutput(Identifier item, int count) {
    }

    record RouteFluid(Identifier fluid, int amount) {
    }
}
