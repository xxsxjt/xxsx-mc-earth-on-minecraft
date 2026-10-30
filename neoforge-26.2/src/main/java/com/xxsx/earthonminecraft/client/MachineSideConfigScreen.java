package com.xxsx.earthonminecraft.client;

import com.xxsx.earthonminecraft.ProcessingMachineBlock;
import com.xxsx.earthonminecraft.ProcessingMachineBlockEntity;
import com.xxsx.earthonminecraft.ProcessingMachineMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

final class MachineSideConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 148;
    private static final int BUTTON_WIDTH = 36;
    private static final int BUTTON_HEIGHT = 18;
    private static final int PAPER = 0xFFC6C6C6;
    private static final int EDGE_DARK = 0xFF555555;
    private static final int EDGE_LIGHT = 0xFFFFFFFF;

    private final ProcessingMachineMenu menu;
    private final Map<Direction, Button> sideButtons = new EnumMap<>(Direction.class);

    MachineSideConfigScreen(ProcessingMachineMenu menu) {
        super(Component.translatable("screen.earth_on_minecraft.side.config.title"));
        this.menu = menu;
    }

    @Override
    protected void init() {
        sideButtons.clear();
        int left = panelLeft();
        int top = panelTop();
        for (Direction side : Direction.values()) {
            final Direction target = side;
            RelativeFace face = relativeFace(side);
            Button button = addRenderableWidget(Button.builder(sideLabel(side), b -> {
                        cycleSideMode(target);
                        syncButton(target);
                    })
                    .bounds(left + face.x, top + face.y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            sideButtons.put(side, button);
            syncButton(side);
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.earth_on_minecraft.side.config.done"), b -> onClose())
                .bounds(left + 118, top + 124, 48, 16)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        for (Direction side : Direction.values()) {
            syncButton(side);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xB0000000);
        int left = panelLeft();
        int top = panelTop();
        g.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, EDGE_DARK);
        g.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, EDGE_LIGHT);
        g.fill(left + 3, top + 3, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, PAPER);
        g.fill(left + 7, top + 34, left + PANEL_WIDTH - 7, top + 35, 0xFF8B8B8B);
        g.centeredText(font, EarthGuiSupport.trim(font, title, PANEL_WIDTH - 20),
                left + PANEL_WIDTH / 2, top + 8, EarthGuiSupport.TEXT);
        g.centeredText(font, EarthGuiSupport.trim(font,
                        Component.translatable("screen.earth_on_minecraft.side.config.subtitle"), PANEL_WIDTH - 20),
                left + PANEL_WIDTH / 2, top + 21, EarthGuiSupport.MUTED);

        drawLegend(g, left + 9, top + 105);
        String hint = EarthGuiSupport.fit(font,
                Component.translatable("screen.earth_on_minecraft.side.config.hint").getString(), 104);
        g.text(font, hint,
                left + 9, top + 126, EarthGuiSupport.MUTED, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawLegend(GuiGraphicsExtractor g, int x, int y) {
        drawLegendEntry(g, x, y, EarthGuiSupport.INPUT, "screen.earth_on_minecraft.side.input");
        drawLegendEntry(g, x + 42, y, EarthGuiSupport.OUTPUT, "screen.earth_on_minecraft.side.output");
        drawLegendEntry(g, x + 84, y, EarthGuiSupport.BOTH, "screen.earth_on_minecraft.side.both");
        drawLegendEntry(g, x + 126, y, 0xFF555555, "screen.earth_on_minecraft.side.off");
    }

    private void drawLegendEntry(GuiGraphicsExtractor g, int x, int y, int color, String key) {
        g.fill(x, y + 2, x + 6, y + 8, color);
        g.text(font, Component.translatable(key), x + 9, y, EarthGuiSupport.TEXT, false);
    }

    private void cycleSideMode(Direction side) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId, ProcessingMachineMenu.BUTTON_SIDE_BASE + side.ordinal());
        }
    }

    private void syncButton(Direction side) {
        Button button = sideButtons.get(side);
        if (button == null) {
            return;
        }
        ProcessingMachineBlockEntity.SideMode mode = menu.sideMode(side);
        button.setMessage(sideLabel(side));
        button.setFGColor(sideModeColor(mode));
        button.setTooltip(Tooltip.create(Component.translatable(mode.tooltipKey())));
    }

    private Component sideLabel(Direction side) {
        ProcessingMachineBlockEntity.SideMode mode = menu.sideMode(side);
        return Component.translatable("screen.earth_on_minecraft.side.config.button",
                Component.translatable(faceShortKey(side)),
                Component.translatable("screen.earth_on_minecraft.side.mode.short." + modeKey(mode)));
    }

    private String faceShortKey(Direction side) {
        return "screen.earth_on_minecraft.side.relative.short." + relativeFace(side).key;
    }

    private int sideModeColor(ProcessingMachineBlockEntity.SideMode mode) {
        return switch (mode) {
            case INPUT -> EarthGuiSupport.INPUT;
            case OUTPUT -> EarthGuiSupport.OUTPUT;
            case BOTH -> EarthGuiSupport.BOTH;
            case OFF -> 0xFFAAAAAA;
        };
    }

    private String modeKey(ProcessingMachineBlockEntity.SideMode mode) {
        return switch (mode) {
            case INPUT -> "input";
            case OUTPUT -> "output";
            case BOTH -> "both";
            case OFF -> "off";
        };
    }

    private Direction machineFront() {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            var state = level.getBlockState(menu.pos());
            if (state.hasProperty(ProcessingMachineBlock.FACING)) {
                return state.getValue(ProcessingMachineBlock.FACING);
            }
        }
        return Direction.NORTH;
    }

    private RelativeFace relativeFace(Direction side) {
        if (side == Direction.UP) {
            return RelativeFace.TOP;
        }
        if (side == Direction.DOWN) {
            return RelativeFace.BOTTOM;
        }
        Direction front = machineFront();
        if (side == front) {
            return RelativeFace.FRONT;
        }
        if (side == front.getOpposite()) {
            return RelativeFace.BACK;
        }
        if (side == front.getClockWise()) {
            return RelativeFace.RIGHT;
        }
        return RelativeFace.LEFT;
    }

    private int panelLeft() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelTop() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private enum RelativeFace {
        TOP("top", 48, 39),
        LEFT("left", 10, 59),
        FRONT("front", 48, 59),
        RIGHT("right", 86, 59),
        BACK("back", 124, 59),
        BOTTOM("bottom", 48, 79);

        private final String key;
        private final int x;
        private final int y;

        RelativeFace(String key, int x, int y) {
            this.key = key;
            this.x = x;
            this.y = y;
        }
    }
}
