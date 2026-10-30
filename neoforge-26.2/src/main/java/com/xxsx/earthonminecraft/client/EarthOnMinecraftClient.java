package com.xxsx.earthonminecraft.client;

import com.xxsx.earthonminecraft.EarthOnMinecraft;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class EarthOnMinecraftClient {
    private EarthOnMinecraftClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EarthOnMinecraftClient::registerScreens);
        modBus.addListener(EarthOnMinecraftClient::registerRenderers);
        modBus.addListener(MachineFeedbackClient::registerParticleProviders);
        modBus.addListener(ConnectedOreModels::registerStandaloneModels);
        modBus.addListener(ConnectedOreModels::replaceBakedModels);
        modBus.addListener(SpecFluidModels::register);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EarthOnMinecraft.PROCESSING_MACHINE_MENU.get(), ProcessingMachineScreen::new);
        event.register(EarthOnMinecraft.ENERGY_GENERATOR_MENU.get(), EnergyGeneratorScreen::new);
        event.register(EarthOnMinecraft.BATTERY_BOX_MENU.get(), BatteryBoxScreen::new);
        event.register(EarthOnMinecraft.SETTLEMENT_BOARD_MENU.get(), SettlementBoardScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EarthOnMinecraft.CONVEYOR_BELT_BLOCK_ENTITY.get(), ConveyorBeltRenderer::new);
    }

    public static void openNotebook() {
        Minecraft.getInstance().setScreen(new FieldGeologyNotebookScreen());
    }
}
