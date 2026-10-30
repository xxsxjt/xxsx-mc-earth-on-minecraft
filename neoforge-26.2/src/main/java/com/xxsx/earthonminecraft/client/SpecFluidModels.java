package com.xxsx.earthonminecraft.client;

import com.xxsx.earthonminecraft.EarthOnMinecraft;
import com.xxsx.earthonminecraft.SpecFluids;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;

/**
 * Binds every spec fluid to its generated still and flowing sprites. Without this the fluids
 * resolve to the missing-texture checkerboard even though the registrations are valid.
 */
final class SpecFluidModels {
    private SpecFluidModels() {
    }

    static void register(RegisterFluidModelsEvent event) {
        for (SpecFluids.Registered fluid : SpecFluids.registered().values()) {
            String id = fluid.source().getId().getPath();
            FluidModel.Unbaked model = new FluidModel.Unbaked(
                    new Material(EarthOnMinecraft.id("block/fluid/" + id + "_still")),
                    new Material(EarthOnMinecraft.id("block/fluid/" + id + "_flow")),
                    null,
                    null);
            event.register(model, fluid.source(), fluid.flowing());
        }
    }
}
