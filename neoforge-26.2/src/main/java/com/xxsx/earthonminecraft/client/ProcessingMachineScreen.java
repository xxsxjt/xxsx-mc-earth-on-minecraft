package com.xxsx.earthonminecraft.client;

import com.xxsx.earthonminecraft.MachineMultiblock;
import com.xxsx.earthonminecraft.ProcessingMachineBlock;
import com.xxsx.earthonminecraft.ProcessingMachineBlockEntity;
import com.xxsx.earthonminecraft.ProcessingMachineMenu;
import com.xxsx.earthonminecraft.RouteGuide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ProcessingMachineScreen extends AbstractContainerScreen<ProcessingMachineMenu> {
    private static final Identifier BG_LOCATION = Identifier.fromNamespaceAndPath(
            "earth_on_minecraft", "textures/gui/container/processing_machine.png");
    private static final Identifier PROGRESS_SPRITE = Identifier.withDefaultNamespace("container/furnace/burn_progress");
    private static final Identifier FUEL_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress");

    private static final int REDSTONE_X = 7;
    private static final int SIDE_X = 23;
    private static final int ROUTE_X = 72;
    private static final int CONTROLS_Y = 56;
    private static final int ICON_BUTTON_SIZE = 14;
    private static final int STATUS_X = 100;
    private static final int STATUS_Y = 62;
    private static final int STATUS_W = 69;
    private static final int STATUS_H = 11;
    private static final int TELEMETRY_X = 100;
    private static final int TELEMETRY_Y = 76;
    private static final int TELEMETRY_W = 69;
    private static final int TELEMETRY_H = 12;
    private static final int TANK_X = 38;
    private static final int TANK_Y = 18;
    private static final int TANK_W = 8;
    private static final int TANK_H = 15;
    private static final int TEMP_X = 48;
    private static final int TEMP_Y = 18;
    private static final int TEMP_W = 8;
    private static final int TEMP_H = 15;
    private static final int ENERGY_X = 58;
    private static final int ENERGY_Y = 18;
    private static final int ENERGY_W = 8;
    private static final int ENERGY_H = 15;
    private static final int MANUAL_X = 68;
    private static final int MANUAL_Y = 18;
    private static final int MANUAL_W = 30;
    private static final int MANUAL_H = 16;

    private Button redstoneButton;
    private Button routeButton;
    private Button sideConfigButton;
    private Button manualButton;

    public ProcessingMachineScreen(ProcessingMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 94;
    }

    @Override
    protected void init() {
        super.init();
        redstoneButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleRedstoneMode())
                .bounds(leftPos + REDSTONE_X, topPos + CONTROLS_Y, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE)
                .build());

        if (!isMultiblockMachine()) {
            sideConfigButton = addRenderableWidget(Button.builder(Component.empty(), b ->
                            Minecraft.getInstance().setScreen(new MachineSideConfigScreen(menu)))
                    .bounds(leftPos + SIDE_X, topPos + CONTROLS_Y, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE)
                    .tooltip(Tooltip.create(Component.translatable("screen.earth_on_minecraft.side.config.open.tooltip")))
                    .build());
        }

        routeButton = addRenderableWidget(Button.builder(routeButtonLabel(), b -> cycleRoute())
                .bounds(leftPos + ROUTE_X, topPos + CONTROLS_Y, 26, ICON_BUTTON_SIZE)
                .build());

        if (menu.powerMode().manualFallback()) {
            manualButton = addRenderableWidget(Button.builder(
                            Component.translatable("screen.earth_on_minecraft.machine.manual.button"),
                            b -> triggerManualStep())
                    .bounds(leftPos + MANUAL_X, topPos + MANUAL_Y, MANUAL_W, MANUAL_H)
                    .build());
        }

        addRenderableWidget(Button.builder(Component.empty(), button -> {
                    this.onClose();
                    EarthOnMinecraftClient.openNotebook();
                })
                .bounds(leftPos + 155, topPos + 4, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE)
                .tooltip(Tooltip.create(Component.translatable("screen.earth_on_minecraft.button.notebook.tooltip")))
                .build());

        syncButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncButtons();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        g.blit(RenderPipelines.GUI_TEXTURED, BG_LOCATION, leftPos, topPos,
                0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        drawMachineIdentity(g);
        drawGridOnlyPowerSlot(g);
        drawTank(g);
        drawTemperatureGauge(g);
        drawEnergyGauge(g);
        drawFuel(g);
        drawProgress(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        drawControlGlyphs(g);
        drawStatus(g);
        drawTelemetry(g);
        drawTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, trimmedTitle(), titleLabelX, titleLabelY, EarthGuiSupport.TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, EarthGuiSupport.TEXT, false);
    }

    private void drawMachineIdentity(GuiGraphicsExtractor g) {
        int accent = menu.processFamily().accentColor();
        int x = leftPos + 8;
        int y = topPos + 21;
        g.fill(x - 2, y - 2, x + 20, y, accent);
        Item machineItem = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(
                "earth_on_minecraft", menu.kind().blockId()));
        ItemStack icon = new ItemStack(machineItem);
        if (!icon.isEmpty()) {
            g.item(icon, x, y + 1);
        }
        String family = localized(menu.processFamily().labelKey());
        g.text(font, EarthGuiSupport.fit(font, family, 31), x - 1, y + 20, accent, false);
    }

    private void drawGridOnlyPowerSlot(GuiGraphicsExtractor g) {
        if (menu.acceptsLocalFuel() || menu.powerMode() == ProcessingMachineBlock.PowerMode.KINETIC) {
            return;
        }
        int x = leftPos + 39;
        int y = topPos + 58;
        g.fill(x, y, x + 16, y + 16, 0xFF20282D);
        drawPowerGlyph(g, x + 3, y + 2, menu.processFamily().accentColor());
    }

    /**
     * Draws a vertical gauge next to the input slot. It is only shown for routes that consume or
     * produce a fluid so item-only machines keep their original layout.
     */
    private void drawTank(GuiGraphicsExtractor g) {
        if (!menu.usesFluid() && menu.tankAmount() <= 0 && menu.outputTankAmount() <= 0) {
            return;
        }
        int x = leftPos + TANK_X;
        int y = topPos + TANK_Y;
        EarthGuiSupport.drawInset(g, x, y, TANK_W, TANK_H);
        int capacity = menu.tankCapacity();
        int inFilled = Math.min(TANK_H - 2, (TANK_H - 2) * menu.tankAmount() / capacity);
        if (inFilled > 0) {
            g.fill(x + 1, y + TANK_H - 1 - inFilled, x + 4, y + TANK_H - 1, 0xFF3E7CC4);
        }
        int outFilled = Math.min(TANK_H - 2, (TANK_H - 2) * menu.outputTankAmount() / capacity);
        if (outFilled > 0) {
            g.fill(x + 4, y + TANK_H - 1 - outFilled, x + TANK_W - 1, y + TANK_H - 1, 0xFFD9822B);
        }
    }

    private void drawTemperatureGauge(GuiGraphicsExtractor g) {
        int x = leftPos + TEMP_X;
        int y = topPos + TEMP_Y;
        EarthGuiSupport.drawInset(g, x, y, TEMP_W, TEMP_H);
        int ambient = ProcessingMachineBlockEntity.AMBIENT_TEMP_K;
        int maxTemp = Math.max(ambient + 100, Math.max(menu.maxTemperatureK(), menu.targetTemperatureK()));
        int current = Math.max(ambient, menu.temperatureK());
        int innerH = TEMP_H - 2;
        int filled = Math.min(innerH, Math.max(2, innerH * (current - ambient) / Math.max(1, maxTemp - ambient)));
        if (current <= ambient) {
            filled = 2;
        }
        int barColor = current >= menu.targetTemperatureK() && menu.targetTemperatureK() > ambient
                ? 0xFFE86A24
                : current > ambient + 30 ? 0xFFD99B26 : 0xFF4B779D;
        g.fill(x + 1, y + TEMP_H - 1 - filled, x + TEMP_W - 1, y + TEMP_H - 1, barColor);
        int target = menu.targetTemperatureK();
        if (target > ambient) {
            int targetOffset = Math.min(innerH - 1, Math.max(1, innerH * (target - ambient) / Math.max(1, maxTemp - ambient)));
            int markerY = y + TEMP_H - 1 - targetOffset;
            g.fill(x + 1, markerY, x + TEMP_W - 1, markerY + 1, 0xFFFFE082);
        }
    }

    private void drawEnergyGauge(GuiGraphicsExtractor g) {
        int x = leftPos + ENERGY_X;
        int y = topPos + ENERGY_Y;
        EarthGuiSupport.drawInset(g, x, y, ENERGY_W, ENERGY_H);
        int innerH = ENERGY_H - 2;
        if (menu.powerMode().grid()) {
            int cap = Math.max(1, menu.energyCapacity());
            int filled = Math.min(innerH, innerH * menu.energyStored() / cap);
            if (filled > 0) {
                g.fill(x + 1, y + ENERGY_H - 1 - filled, x + ENERGY_W - 1, y + ENERGY_H - 1, 0xFF31A9C9);
            }
        } else if (menu.powerMode().manualFallback()) {
            int ticks = Math.min(160, menu.manualWorkTicks());
            int filled = menu.driveState() == ProcessingMachineBlockEntity.DriveState.KINETIC_ACTIVE
                    ? innerH
                    : Math.min(innerH, innerH * ticks / 160);
            if (filled > 0) {
                g.fill(x + 1, y + ENERGY_H - 1 - filled, x + ENERGY_W - 1, y + ENERGY_H - 1, 0xFF5FA85B);
            }
        } else if (menu.hasBurningFuel()) {
            int filled = Math.min(innerH, innerH * menu.burnTime() / Math.max(1, menu.burnTimeTotal()));
            if (filled > 0) {
                g.fill(x + 1, y + ENERGY_H - 1 - filled, x + ENERGY_W - 1, y + ENERGY_H - 1, 0xFFD97925);
            }
        }
    }

    private void drawTelemetry(GuiGraphicsExtractor g) {
        int x = leftPos + TELEMETRY_X;
        int y = topPos + TELEMETRY_Y;
        EarthGuiSupport.drawInset(g, x, y, TELEMETRY_W, TELEMETRY_H);
        String line = telemetryLine();
        int color = telemetryColor();
        g.text(font, EarthGuiSupport.fit(font, line, TELEMETRY_W - 6), x + 3, y + 2, color, false);
    }

    private String telemetryLine() {
        if (!menu.structureValid() && menu.faultExpectedRole().isPresent()) {
            return String.format("Δ(%+d,%+d,%+d) %d/%d",
                    menu.faultDx(), menu.faultDy(), menu.faultDz(),
                    menu.faultMatchedParts(), Math.max(1, menu.faultTotalParts()));
        }
        if (menu.kind().usesThermalTemperature() || menu.targetTemperatureK() > ProcessingMachineBlockEntity.AMBIENT_TEMP_K) {
            return menu.temperatureK() + "K / " + menu.targetTemperatureK() + "K";
        }
        if (menu.powerMode().grid()) {
            return EarthGuiSupport.compactEnergy(menu.energyStored(), menu.energyCapacity());
        }
        return localized(menu.driveState().labelKey()) + " " + menu.temperatureK() + "K";
    }

    private int telemetryColor() {
        if (!menu.structureValid()) {
            return EarthGuiSupport.WARNING;
        }
        ProcessingMachineBlockEntity.DriveState ds = menu.driveState();
        if (ds == ProcessingMachineBlockEntity.DriveState.FUEL_TEMP_LOW
                || ds == ProcessingMachineBlockEntity.DriveState.NO_POWER) {
            return EarthGuiSupport.WARNING;
        }
        if (ds == ProcessingMachineBlockEntity.DriveState.HEATING) {
            return 0xFFD99B26;
        }
        if (menu.active()) {
            return 0xFF31A9C9;
        }
        return EarthGuiSupport.TEXT;
    }

    private void drawFuel(GuiGraphicsExtractor g) {        if (!menu.acceptsLocalFuel() || menu.burnTimeTotal() <= 0) {
            return;
        }
        int lit = Math.min(14, 14 * menu.burnTime() / menu.burnTimeTotal());
        if (lit > 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, FUEL_SPRITE, 14, 14, 0, 14 - lit,
                    leftPos + 59, topPos + 59 + 14 - lit, 14, lit);
        }
    }

    private void drawProgress(GuiGraphicsExtractor g) {
        int maxProgress = menu.maxProgress();
        if (maxProgress <= 0) {
            return;
        }
        int progress = Math.min(24, 24 * menu.progress() / maxProgress);
        if (progress > 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS_SPRITE, 24, 16, 0, 0,
                    leftPos + 64, topPos + 38, progress, 16);
        }
    }

    private void drawControlGlyphs(GuiGraphicsExtractor g) {
        EarthGuiSupport.drawRedstoneGlyph(g, leftPos + REDSTONE_X + 2, topPos + CONTROLS_Y + 2,
                menu.redstoneMode());
        if (sideConfigButton != null) {
            EarthGuiSupport.drawIoGlyph(g, leftPos + SIDE_X + 2, topPos + CONTROLS_Y + 2);
        }
        EarthGuiSupport.drawNotebookGlyph(g, leftPos + 157, topPos + 6);
    }

    private void drawStatus(GuiGraphicsExtractor g) {
        int x = leftPos + STATUS_X;
        int y = topPos + STATUS_Y;
        EarthGuiSupport.drawInset(g, x, y, STATUS_W, STATUS_H);
        int color = statusColor();
        g.fill(x + 3, y + 3, x + 8, y + 8, color);
        String line = EarthGuiSupport.fit(font, statusLine(), STATUS_W - 15);
        g.text(font, line, x + 11, y + 1, color, false);
    }

    private String statusLine() {
        if (!menu.structureValid()) {
            if (menu.faultExpectedRole().isPresent()) {
                return Component.translatable("screen.earth_on_minecraft.machine.structure_fault_short",
                        String.format("(%+d,%+d,%+d)", menu.faultDx(), menu.faultDy(), menu.faultDz())).getString();
            }
            return localized("screen.earth_on_minecraft.machine.structure_missing_short");
        }
        ItemStack input = menu.getSlot(ProcessingMachineBlockEntity.SLOT_INPUT).getItem();
        if (input.isEmpty() && menu.selectedRecipe().isEmpty()) {
            return localized("screen.earth_on_minecraft.machine.empty_input_short");
        }
        return menu.selectedRecipe().map(recipe -> {
            if (redstonePaused()) {
                return localized("screen.earth_on_minecraft.machine.redstone_paused_short");
            }
            if (recipe.requiresCoke() && !hasCokeCharge()) {
                return localized("screen.earth_on_minecraft.machine.missing_reductant_short");
            }
            if (menu.catalystMissing()) {
                return localized("screen.earth_on_minecraft.machine.missing_catalyst_short");
            }
            if (menu.secondaryInputsMissing()) {
                return localized("screen.earth_on_minecraft.machine.missing_secondary_short");
            }
            if (menu.fluidInputsMissing()) {
                return localized("screen.earth_on_minecraft.machine.missing_fluid_short");
            }
            if (menu.outputsBlocked() || menu.fluidOutputsBlocked()) {
                return localized("screen.earth_on_minecraft.machine.outputs_full_short");
            }
            ProcessingMachineBlockEntity.DriveState ds = menu.driveState();
            if (ds == ProcessingMachineBlockEntity.DriveState.FUEL_TEMP_LOW) {
                return localized("screen.earth_on_minecraft.machine.fuel_temp_low_short");
            }
            if (ds == ProcessingMachineBlockEntity.DriveState.HEATING) {
                return Component.translatable("screen.earth_on_minecraft.machine.heating_short",
                        menu.temperatureK(), menu.targetTemperatureK()).getString();
            }
            if (ds == ProcessingMachineBlockEntity.DriveState.MANUAL_WAIT) {
                return localized("screen.earth_on_minecraft.machine.manual_wait_short");
            }
            if (ds == ProcessingMachineBlockEntity.DriveState.KINETIC_WAIT) {
                return localized("screen.earth_on_minecraft.machine.kinetic_wait_short");
            }
            if (menu.active()) {
                return localized("screen.earth_on_minecraft.machine.running");
            }
            if (menu.needsExternalPower() && !menu.gridPowered() && menu.energyStored() < Math.max(1, menu.energyPerTick())
                    && !menu.hasBurningFuel() && !fuelSlotHasFuel()
                    && menu.temperatureK() < menu.targetTemperatureK()) {
                return localized(menu.acceptsLocalFuel()
                        ? "screen.earth_on_minecraft.machine.missing_power_short"
                        : "screen.earth_on_minecraft.machine.missing_grid_power_short");
            }
            return localized("screen.earth_on_minecraft.machine.recipe_ready");
        }).orElseGet(() -> localized(input.isEmpty()
                ? "screen.earth_on_minecraft.machine.empty_input_short"
                : "screen.earth_on_minecraft.machine.unsupported_input_short"));
    }

    private String detailedStatusLine() {
        if (!menu.structureValid()) {
            if (menu.faultExpectedRole().isPresent()) {
                BlockPos faultPos = menu.pos().offset(menu.faultDx(), menu.faultDy(), menu.faultDz());
                String coords = faultPos.getX() + ", " + faultPos.getY() + ", " + faultPos.getZ();
                Component roleName = Component.translatable(menu.faultExpectedRole().get().labelKey());
                return Component.translatable("screen.earth_on_minecraft.machine.structure_fault_coord",
                        coords,
                        String.format("(%+d, %+d, %+d)", menu.faultDx(), menu.faultDy(), menu.faultDz()),
                        roleName).getString();
            }
            return localized("screen.earth_on_minecraft.machine.structure_missing");
        }
        ItemStack input = menu.getSlot(ProcessingMachineBlockEntity.SLOT_INPUT).getItem();
        if (input.isEmpty() && menu.selectedRecipe().isEmpty()) {
            return localized(menu.acceptsLocalFuel()
                    ? "screen.earth_on_minecraft.machine.empty_input_fuel"
                    : "screen.earth_on_minecraft.machine.empty_input_grid");
        }
        return menu.selectedRecipe().map(recipe -> {
            if (redstonePaused()) {
                return localized("screen.earth_on_minecraft.machine.redstone_paused");
            }
            if (recipe.requiresCoke() && !hasCokeCharge()) {
                return localized("screen.earth_on_minecraft.machine.missing_reductant");
            }
            if (menu.catalystMissing()) {
                return Component.translatable("screen.earth_on_minecraft.machine.missing_catalyst",
                        recipe.catalystStack().getItemName()).getString();
            }
            if (menu.secondaryInputsMissing()) {
                return localized("screen.earth_on_minecraft.machine.missing_secondary");
            }
            if (menu.fluidInputsMissing()) {
                return localized("screen.earth_on_minecraft.machine.missing_fluid");
            }
            if (menu.outputsBlocked() || menu.fluidOutputsBlocked()) {
                return localized("screen.earth_on_minecraft.machine.outputs_full");
            }
            ProcessingMachineBlockEntity.DriveState ds = menu.driveState();
            if (ds == ProcessingMachineBlockEntity.DriveState.FUEL_TEMP_LOW) {
                return Component.translatable("screen.earth_on_minecraft.machine.fuel_temp_low",
                        menu.maxTemperatureK(), menu.targetTemperatureK()).getString();
            }
            if (ds == ProcessingMachineBlockEntity.DriveState.HEATING) {
                return Component.translatable("screen.earth_on_minecraft.machine.heating",
                        menu.temperatureK(), menu.targetTemperatureK()).getString();
            }
            if (ds == ProcessingMachineBlockEntity.DriveState.MANUAL_WAIT) {
                return localized("screen.earth_on_minecraft.machine.manual_wait");
            }
            if (ds == ProcessingMachineBlockEntity.DriveState.KINETIC_WAIT) {
                return localized("screen.earth_on_minecraft.machine.kinetic_wait");
            }
            if (menu.needsExternalPower() && !menu.gridPowered() && menu.energyStored() < Math.max(1, menu.energyPerTick())
                    && !menu.hasBurningFuel() && !fuelSlotHasFuel()
                    && menu.temperatureK() < menu.targetTemperatureK()) {
                return localized(menu.acceptsLocalFuel()
                        ? "screen.earth_on_minecraft.machine.missing_power"
                        : "screen.earth_on_minecraft.machine.missing_grid_power");
            }
            return recipeSummary(recipe);
        }).orElseGet(() -> localized(input.isEmpty()
                ? "screen.earth_on_minecraft.machine.empty_input"
                : "screen.earth_on_minecraft.machine.unsupported_input"));
    }

    private int statusColor() {
        if (!menu.structureValid()) {
            return EarthGuiSupport.WARNING;
        }
        ItemStack input = menu.getSlot(ProcessingMachineBlockEntity.SLOT_INPUT).getItem();
        if (!input.isEmpty() && !ProcessingMachineBlock.acceptsInput(menu.kind(), input)) {
            return EarthGuiSupport.WARNING;
        }
        if ((!input.isEmpty() || menu.selectedRecipe().isPresent())
                && (redstonePaused() || menu.outputsBlocked() || menu.fluidOutputsBlocked()
                || menu.secondaryInputsMissing() || menu.fluidInputsMissing() || menu.catalystMissing()
                || menu.selectedRecipe().map(recipe -> recipe.requiresCoke() && !hasCokeCharge()).orElse(false))) {
            return EarthGuiSupport.WARNING;
        }
        if (menu.active()) {
            return 0xFF207030;
        }
        ProcessingMachineBlockEntity.DriveState ds = menu.driveState();
        if (ds == ProcessingMachineBlockEntity.DriveState.FUEL_TEMP_LOW
                || ds == ProcessingMachineBlockEntity.DriveState.MANUAL_WAIT
                || ds == ProcessingMachineBlockEntity.DriveState.KINETIC_WAIT) {
            return EarthGuiSupport.WARNING;
        }
        if (ds == ProcessingMachineBlockEntity.DriveState.HEATING) {
            return 0xFFD99B26;
        }
        if ((!input.isEmpty() || menu.selectedRecipe().isPresent()) && menu.needsExternalPower()
                && !menu.gridPowered() && menu.energyStored() < Math.max(1, menu.energyPerTick())
                && !menu.hasBurningFuel() && !fuelSlotHasFuel()) {
            return EarthGuiSupport.WARNING;
        }
        return EarthGuiSupport.MUTED;
    }

    private void drawTooltips(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (isHovering(REDSTONE_X, CONTROLS_Y, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE, mouseX, mouseY)) {
            ProcessingMachineBlockEntity.RedstoneMode redstone = menu.redstoneMode();
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("screen.earth_on_minecraft.redstone.current",
                            Component.translatable(redstone.labelKey())),
                    Component.translatable(redstone.descriptionKey()),
                    Component.translatable("screen.earth_on_minecraft.redstone.tooltip")
            ), mouseX, mouseY);
        }
        if (isHovering(7, 18, 32, 39, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, machineSpecTooltip(), mouseX, mouseY);
        }
        if (isHovering(38, 57, 18, 18, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, powerTooltip(), mouseX, mouseY);
        }
        if (isHovering(TANK_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("screen.earth_on_minecraft.machine.tank",
                            menu.tankAmount(), menu.tankCapacity()),
                    Component.translatable("screen.earth_on_minecraft.machine.output_tank",
                            menu.outputTankAmount(), menu.tankCapacity()),
                    Component.translatable("screen.earth_on_minecraft.machine.tank.tooltip")
            ), mouseX, mouseY);
        }
        if (isHovering(TEMP_X, TEMP_Y, TEMP_W, TEMP_H, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, temperatureTooltip(), mouseX, mouseY);
        }
        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, powerTooltip(), mouseX, mouseY);
        }
        if (menu.powerMode().manualFallback() && isHovering(MANUAL_X, MANUAL_Y, MANUAL_W, MANUAL_H, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("screen.earth_on_minecraft.machine.manual.tooltip"),
                    Component.translatable("screen.earth_on_minecraft.machine.manual.tooltip.0"),
                    Component.translatable("screen.earth_on_minecraft.machine.manual.tooltip.1"),
                    Component.translatable("screen.earth_on_minecraft.machine.manual.tooltip.2")
            ), mouseX, mouseY);
        }
        if (isHovering(80, 76, 18, 18, mouseX, mouseY)) {
            if (menu.kind() == ProcessingMachineBlock.Kind.REDUCTION_FURNACE) {
                g.setComponentTooltipForNextFrame(font,
                        List.of(Component.translatable("screen.earth_on_minecraft.machine.reductant.tooltip")), mouseX, mouseY);
            } else if (menu.selectedRecipe().map(ProcessingMachineBlock.Recipe::hasCatalyst).orElse(false)) {
                ItemStack cat = menu.selectedRecipe().get().catalystStack();
                g.setComponentTooltipForNextFrame(font,
                        List.of(Component.translatable("screen.earth_on_minecraft.machine.catalyst.tooltip", cat.getItemName())),
                        mouseX, mouseY);
            }
        }
        if (isHovering(STATUS_X, STATUS_Y, STATUS_W, STATUS_H, mouseX, mouseY)
                || isHovering(TELEMETRY_X, TELEMETRY_Y, TELEMETRY_W, TELEMETRY_H, mouseX, mouseY)
                || isHovering(64, 38, 24, 16, mouseX, mouseY)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(stateTooltip());
            tooltip.add(Component.literal(detailedStatusLine()));
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.temp.readout",
                    menu.temperatureK(), menu.targetTemperatureK(), menu.maxTemperatureK()));
            if (menu.requiredPressureKpa() > 101) {
                tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.pressure.readout",
                        menu.requiredPressureKpa()));
            }
            menu.selectedRecipe().ifPresent(recipe -> {
                if (recipe.hasEquation()) {
                    tooltip.add(Component.literal(recipe.equation()));
                }
                if (recipe.hasCatalyst()) {
                    tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.catalyst.tooltip",
                            recipe.catalystStack().getItemName()));
                }
            });
            if (menu.powerMode().grid()) {
                tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.eou.buffer",
                        menu.energyStored(), menu.energyCapacity(), menu.energyPerTick()));
            }
            tooltip.add(Component.translatable(menu.driveState().labelKey()));
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.spec",
                    secondsPerOperation(), menu.energyPerTick()));
            tooltip.add(Component.translatable(menu.powerMode().labelKey()));
            if (!menu.structureValid()) {
                MachineMultiblock.Pattern pattern = MachineMultiblock.patternFor(menu.kind());
                tooltip.add(Component.translatable(pattern.screenKey()));
                appendStructureFaultTooltip(tooltip);
            }
            g.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
        if (menu.routeCount() > 1 && isHovering(ROUTE_X, CONTROLS_Y, 26, ICON_BUTTON_SIZE, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("screen.earth_on_minecraft.machine.route.current",
                            menu.selectedRouteIndex() + 1, menu.routeCount()),
                    menu.selectedRecipe().map(recipe -> Component.literal(recipe.note()))
                            .orElse(Component.translatable("screen.earth_on_minecraft.machine.empty_input")),
                    Component.translatable("screen.earth_on_minecraft.machine.route.tooltip")
            ), mouseX, mouseY);
        }
    }

    private void cycleRedstoneMode() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) {
            return;
        }
        int id = switch (menu.redstoneMode()) {
            case ALWAYS -> ProcessingMachineMenu.BUTTON_REDSTONE_REQUIRE_SIGNAL;
            case REQUIRE_SIGNAL -> ProcessingMachineMenu.BUTTON_REDSTONE_REQUIRE_NO_SIGNAL;
            case REQUIRE_NO_SIGNAL -> ProcessingMachineMenu.BUTTON_REDSTONE_ALWAYS;
        };
        mc.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void cycleRoute() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && menu.routeCount() > 1) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId, ProcessingMachineMenu.BUTTON_ROUTE_NEXT);
        }
    }

    private void triggerManualStep() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && menu.powerMode().manualFallback()) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId, ProcessingMachineMenu.BUTTON_MANUAL_STEP);
        }
    }

    private void syncButtons() {
        if (redstoneButton != null) {
            redstoneButton.setMessage(Component.empty());
        }
        if (routeButton != null) {
            routeButton.visible = menu.routeCount() > 1;
            routeButton.active = menu.routeCount() > 1;
            routeButton.setMessage(routeButtonLabel());
        }
        if (manualButton != null) {
            manualButton.visible = menu.powerMode().manualFallback();
            manualButton.active = menu.structureValid();
        }
    }

    private Component routeButtonLabel() {
        if (menu.routeCount() <= 1) {
            return Component.empty();
        }
        return Component.literal((menu.selectedRouteIndex() + 1) + "/" + menu.routeCount());
    }

    private Component trimmedTitle() {
        return EarthGuiSupport.trim(font, title, 140);
    }

    private String recipeSummary(ProcessingMachineBlock.Recipe recipe) {
        if (Minecraft.getInstance().getLanguageManager().getSelected()
                .toLowerCase(java.util.Locale.ROOT).startsWith("zh")) {
            return recipe.note();
        }
        return localized("screen.earth_on_minecraft.machine.recipe_ready") + ": "
                + RouteGuide.describeOutputs(recipe);
    }

    private boolean fuelSlotHasFuel() {
        if (!menu.acceptsLocalFuel()) {
            return false;
        }
        ItemStack fuel = menu.getSlot(ProcessingMachineBlockEntity.SLOT_FUEL).getItem();
        return ProcessingMachineBlockEntity.getFuelTicks(fuel) > 0;
    }

    private boolean hasCokeCharge() {
        ItemStack reagent = menu.getSlot(ProcessingMachineBlockEntity.SLOT_REAGENT).getItem();
        return !reagent.isEmpty() && reagent.getItem() == com.xxsx.earthonminecraft.EarthOnMinecraft.COKE.get();
    }

    private boolean redstonePaused() {
        var level = Minecraft.getInstance().level;
        return level != null && !menu.redstoneMode().allows(level.hasNeighborSignal(menu.pos()));
    }

    private List<Component> powerTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(menu.powerMode().labelKey()));
        tooltip.add(Component.translatable(menu.driveState().labelKey()));
        if (menu.powerMode().grid()) {
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.eou.buffer",
                    menu.energyStored(), menu.energyCapacity(), menu.energyPerTick()));
        }
        tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.spec",
                secondsPerOperation(), menu.energyPerTick()));
        if (menu.powerMode().passive()) {
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.passive.tooltip"));
        } else if (menu.acceptsLocalFuel()) {
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.fuel.tooltip"));
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.fuel.examples"));
            ItemStack fuel = menu.getSlot(ProcessingMachineBlockEntity.SLOT_FUEL).getItem();
            int fuelCap = ProcessingMachineBlockEntity.getFuelMaxTemperatureK(fuel);
            if (fuelCap > 0) {
                tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.temp.fuel_cap", fuelCap));
            }
        } else if (menu.powerMode().manualFallback()) {
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.manual.tooltip"));
        } else {
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.grid_only.tooltip"));
        }
        return tooltip;
    }

    private List<Component> temperatureTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.temp.readout",
                menu.temperatureK(), menu.targetTemperatureK(), menu.maxTemperatureK()));
        tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.temp.tooltip"));
        ItemStack fuel = menu.getSlot(ProcessingMachineBlockEntity.SLOT_FUEL).getItem();
        int fuelCap = ProcessingMachineBlockEntity.getFuelMaxTemperatureK(fuel);
        if (fuelCap > 0) {
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.temp.fuel_cap", fuelCap));
        }
        return tooltip;
    }

    private void appendStructureFaultTooltip(List<Component> tooltip) {
        menu.faultExpectedRole().ifPresent(role -> {
            BlockPos faultPos = menu.pos().offset(menu.faultDx(), menu.faultDy(), menu.faultDz());
            String coords = faultPos.getX() + ", " + faultPos.getY() + ", " + faultPos.getZ();
            String rel = String.format("(%+d, %+d, %+d)", menu.faultDx(), menu.faultDy(), menu.faultDz());
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.structure_progress",
                    menu.faultMatchedParts(), Math.max(1, menu.faultTotalParts())));
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.structure_fault_expected",
                    coords, rel, Component.translatable(role.labelKey())));
            var level = Minecraft.getInstance().level;
            if (level != null) {
                Component actualName = level.getBlockState(faultPos).getBlock().getName();
                tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.structure_fault_actual", actualName));
            }
        });
    }

    private List<Component> machineSpecTooltip() {
        List<Component> result = new ArrayList<>();
        result.add(Component.translatable(menu.processFamily().labelKey()));
        result.add(Component.translatable(menu.kind().setpointKey()));
        result.add(Component.translatable("screen.earth_on_minecraft.machine.spec",
                secondsPerOperation(), menu.energyPerTick()));
        result.add(Component.translatable(menu.powerMode().labelKey()));
        if (isMultiblockMachine()) {
            result.add(Component.translatable(MachineMultiblock.patternFor(menu.kind()).screenKey()));
            result.add(Component.translatable("screen.earth_on_minecraft.machine.interface.tooltip.2"));
        }
        return result;
    }

    private Component stateTooltip() {
        return Component.translatable(menu.active()
                ? "screen.earth_on_minecraft.machine.state.running.tooltip"
                : "screen.earth_on_minecraft.machine.state.idle.tooltip");
    }

    private int secondsPerOperation() {
        return Math.max(1, (menu.maxProgress() + 19) / 20);
    }

    private boolean isMultiblockMachine() {
        return MachineMultiblock.patternFor(menu.kind()) != MachineMultiblock.Pattern.NONE;
    }

    private static String localized(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    private void drawPowerGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 5, y, x + 9, y + 5, color);
        g.fill(x + 2, y + 4, x + 8, y + 8, color);
        g.fill(x + 4, y + 7, x + 7, y + 12, color);
    }
}
