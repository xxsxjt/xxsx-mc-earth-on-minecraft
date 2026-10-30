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
    private static final int TANK_X = 58;
    private static final int TANK_Y = 18;
    private static final int TANK_W = 8;
    private static final int TANK_H = 16;

    private Button redstoneButton;
    private Button routeButton;
    private Button sideConfigButton;

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
        drawFuel(g);
        drawProgress(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        drawControlGlyphs(g);
        drawStatus(g);
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
        if (menu.acceptsLocalFuel()) {
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
        if (!menu.usesFluid() && menu.tankAmount() <= 0) {
            return;
        }
        int x = leftPos + TANK_X;
        int y = topPos + TANK_Y;
        EarthGuiSupport.drawInset(g, x, y, TANK_W, TANK_H);
        int capacity = menu.tankCapacity();
        int filled = Math.min(TANK_H, TANK_H * menu.tankAmount() / capacity);
        if (filled > 0) {
            g.fill(x + 1, y + TANK_H - filled, x + TANK_W - 1, y + TANK_H - 1, 0xFF3E7CC4);
        }
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
            return localized("screen.earth_on_minecraft.machine.structure_missing_short");
        }
        ItemStack input = menu.getSlot(ProcessingMachineBlockEntity.SLOT_INPUT).getItem();
        if (input.isEmpty()) {
            return localized("screen.earth_on_minecraft.machine.empty_input_short");
        }
        return menu.selectedRecipe().map(recipe -> {
            if (redstonePaused()) {
                return localized("screen.earth_on_minecraft.machine.redstone_paused_short");
            }
            if (recipe.requiresCoke() && !hasCokeCharge()) {
                return localized("screen.earth_on_minecraft.machine.missing_reductant_short");
            }
            if (menu.secondaryInputsMissing()) {
                return localized("screen.earth_on_minecraft.machine.missing_secondary_short");
            }
            if (menu.fluidInputsMissing()) {
                return localized("screen.earth_on_minecraft.machine.missing_fluid_short");
            }
            if (menu.outputsBlocked()) {
                return localized("screen.earth_on_minecraft.machine.outputs_full_short");
            }
            if (menu.active()) {
                return localized("screen.earth_on_minecraft.machine.running");
            }
            if (menu.needsExternalPower() && !menu.gridPowered() && !menu.hasBurningFuel() && !fuelSlotHasFuel()) {
                return localized(menu.acceptsLocalFuel()
                        ? "screen.earth_on_minecraft.machine.missing_power_short"
                        : "screen.earth_on_minecraft.machine.missing_grid_power_short");
            }
            return localized("screen.earth_on_minecraft.machine.recipe_ready");
        }).orElseGet(() -> localized("screen.earth_on_minecraft.machine.unsupported_input_short"));
    }

    private String detailedStatusLine() {
        if (!menu.structureValid()) {
            return localized("screen.earth_on_minecraft.machine.structure_missing");
        }
        ItemStack input = menu.getSlot(ProcessingMachineBlockEntity.SLOT_INPUT).getItem();
        if (input.isEmpty()) {
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
            if (menu.secondaryInputsMissing()) {
                return localized("screen.earth_on_minecraft.machine.missing_secondary");
            }
            if (menu.fluidInputsMissing()) {
                return localized("screen.earth_on_minecraft.machine.missing_fluid");
            }
            if (menu.outputsBlocked()) {
                return localized("screen.earth_on_minecraft.machine.outputs_full");
            }
            if (menu.needsExternalPower() && !menu.gridPowered() && !menu.hasBurningFuel() && !fuelSlotHasFuel()) {
                return localized(menu.acceptsLocalFuel()
                        ? "screen.earth_on_minecraft.machine.missing_power"
                        : "screen.earth_on_minecraft.machine.missing_grid_power");
            }
            return recipeSummary(recipe);
        }).orElseGet(() -> localized("screen.earth_on_minecraft.machine.unsupported_input"));
    }

    private int statusColor() {
        if (!menu.structureValid()) {
            return EarthGuiSupport.WARNING;
        }
        ItemStack input = menu.getSlot(ProcessingMachineBlockEntity.SLOT_INPUT).getItem();
        if (!input.isEmpty() && !ProcessingMachineBlock.acceptsInput(menu.kind(), input)) {
            return EarthGuiSupport.WARNING;
        }
        if (!input.isEmpty() && (redstonePaused() || menu.outputsBlocked() || menu.secondaryInputsMissing()
                || menu.fluidInputsMissing()
                || menu.selectedRecipe().map(recipe -> recipe.requiresCoke() && !hasCokeCharge()).orElse(false))) {
            return EarthGuiSupport.WARNING;
        }
        if (menu.active()) {
            return 0xFF207030;
        }
        if (!input.isEmpty() && menu.needsExternalPower() && !menu.gridPowered()
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
                    Component.translatable("screen.earth_on_minecraft.machine.tank.tooltip")
            ), mouseX, mouseY);
        }
        if (isHovering(80, 76, 18, 18, mouseX, mouseY)
                && menu.kind() == ProcessingMachineBlock.Kind.REDUCTION_FURNACE) {
            g.setComponentTooltipForNextFrame(font,
                    List.of(Component.translatable("screen.earth_on_minecraft.machine.reductant.tooltip")), mouseX, mouseY);
        }
        if (isHovering(STATUS_X, STATUS_Y, STATUS_W, STATUS_H, mouseX, mouseY)
                || isHovering(64, 38, 24, 16, mouseX, mouseY)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(stateTooltip());
            tooltip.add(Component.literal(detailedStatusLine()));
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.spec",
                    secondsPerOperation(), menu.energyPerTick()));
            tooltip.add(Component.translatable(menu.powerMode().labelKey()));
            if (!menu.structureValid()) {
                MachineMultiblock.Pattern pattern = MachineMultiblock.patternFor(menu.kind());
                tooltip.add(Component.translatable(pattern.screenKey()));
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

    private void syncButtons() {
        if (redstoneButton != null) {
            redstoneButton.setMessage(Component.empty());
        }
        if (routeButton != null) {
            routeButton.visible = menu.routeCount() > 1;
            routeButton.active = menu.routeCount() > 1;
            routeButton.setMessage(routeButtonLabel());
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
        tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.spec",
                secondsPerOperation(), menu.energyPerTick()));
        if (menu.powerMode().passive()) {
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.passive.tooltip"));
        } else if (menu.acceptsLocalFuel()) {
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.fuel.tooltip"));
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.fuel.examples"));
        } else {
            tooltip.add(Component.translatable("screen.earth_on_minecraft.machine.grid_only.tooltip"));
        }
        return tooltip;
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
