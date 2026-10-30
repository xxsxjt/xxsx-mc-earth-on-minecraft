package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MachineMultiblock {
    private static final Direction[] HORIZONTALS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private MachineMultiblock() {
    }

    public static Pattern patternFor(ProcessingMachineBlock.Kind kind) {
        return switch (kind) {
            case GAS_SEPARATOR -> Pattern.PRESSURE_SKID;
            case FLOTATION_CELL, LEACHING_TANK -> Pattern.WET_VESSEL;
            case ELECTROLYTIC_CELL -> Pattern.ELECTROLYSIS_BANK;
            case CHEMICAL_REACTOR, POLYMERIZER -> Pattern.REACTION_VESSEL;
            case ROASTER, INDUSTRIAL_KILN -> Pattern.HEATED_LINE;
            case STEAM_CRACKER -> Pattern.CRACKING_FURNACE;
            case DISTILLATION_COLUMN, ABSORPTION_TOWER -> Pattern.TALL_COLUMN;
            case SYNTHESIS_LOOP -> Pattern.PRESSURE_LOOP;
            case BLOOMERY -> Pattern.BLOOMERY;
            case BREAD_OVEN -> Pattern.BREAD_OVEN;
            case GLASS_KILN -> Pattern.GLASS_KILN;
            case LIME_KILN -> Pattern.LIME_KILN;
            case PIT_KILN -> Pattern.PIT_KILN;
            case SHAFT_FURNACE -> Pattern.SHAFT_FURNACE;
            default -> Pattern.NONE;
        };
    }

    public static boolean isComplete(Level level, BlockPos controller, ProcessingMachineBlock.Kind kind) {
        return findMatch(level, controller, kind).isPresent();
    }

    public static Optional<Match> findMatch(Level level, BlockPos controller, ProcessingMachineBlock.Kind kind) {
        return patternFor(kind).findMatch(level, controller);
    }

    public static Optional<BlockPos> findControllerForPart(Level level, BlockPos part) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    cursor.set(part.getX() + dx, part.getY() + dy, part.getZ() + dz);
                    if (level.getBlockEntity(cursor) instanceof ProcessingMachineBlockEntity machine) {
                        Optional<Match> match = findMatch(level, cursor, machine.kind());
                        if (match.isPresent() && match.get().parts().contains(part)) {
                            return Optional.of(cursor.immutable());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<BlockPos> findControllerForPanel(Level level, BlockPos panel) {
        return findControllerForPart(level, panel);
    }

    public static void refreshProjectionForPanel(Level level, BlockPos panel) {
        if (level.isClientSide()) {
            return;
        }
        for (Direction front : HORIZONTALS) {
            BlockPos controller = panel.relative(front.getOpposite());
            if (level.getBlockEntity(controller) instanceof ProcessingMachineBlockEntity machine) {
                refreshProjection(level, controller, machine.kind());
            }
        }
    }

    public static void refreshProjection(Level level, BlockPos controller, ProcessingMachineBlock.Kind kind) {
        if (level.isClientSide()) {
            return;
        }
        Pattern pattern = patternFor(kind);
        if (pattern == Pattern.NONE || isComplete(level, controller, kind)) {
            clearProjectionForPattern(level, controller, pattern);
            return;
        }
        Optional<Direction> panelFront = findPanelFront(level, controller);
        if (panelFront.isEmpty()) {
            clearProjectionForPattern(level, controller, pattern);
            return;
        }
        Direction front = panelFront.get();
        clearStaleProjections(level, controller, pattern, front);
        for (Requirement requirement : requirementsFor(pattern, front)) {
            if (requirement.role() == PartRole.CONTROL_PANEL) {
                continue;
            }
            BlockPos pos = requirement.resolve(controller);
            if (!hasRole(level, pos, requirement.role())) {
                placeProjection(level, pos, requirement.role());
            }
        }
    }

    public static void clearProjectionForController(Level level, BlockPos controller, ProcessingMachineBlock.Kind kind) {
        if (!level.isClientSide()) {
            clearProjectionForPattern(level, controller, patternFor(kind));
        }
    }

    public static void syncAssembly(Level level, BlockPos controller, ProcessingMachineBlock.Kind kind, boolean assembled) {
        Pattern pattern = patternFor(kind);
        clearAssemblyForPattern(level, controller, pattern);
        if (!assembled) {
            return;
        }
        StyledSupportPartBlock.AssemblyStyle style = assemblyStyleFor(pattern);
        findMatch(level, controller, kind).ifPresent(match -> {
            for (BlockPos part : match.parts()) {
                setAssembled(level, part, true, style);
            }
        });
    }

    public static void syncPanelActive(Level level, BlockPos controller, ProcessingMachineBlock.Kind kind, boolean active) {
        if (level.isClientSide()) {
            return;
        }
        findMatch(level, controller, kind).ifPresent(match -> {
            for (BlockPos part : match.parts()) {
                BlockState state = level.getBlockState(part);
                if (state.getBlock() != EarthOnMinecraft.CONTROL_PANEL.get()
                        || !state.hasProperty(ControlPanelBlock.ACTIVE)) {
                    continue;
                }
                boolean nextActive = active && state.getValue(SupportPartBlock.ASSEMBLED);
                if (state.getValue(ControlPanelBlock.ACTIVE) != nextActive) {
                    level.setBlock(part, state.setValue(ControlPanelBlock.ACTIVE, nextActive), Block.UPDATE_CLIENTS);
                }
            }
        });
    }

    private static void clearAssemblyForPattern(Level level, BlockPos controller, Pattern pattern) {
        if (pattern == Pattern.NONE) {
            return;
        }
        for (Direction front : HORIZONTALS) {
            for (Requirement requirement : requirementsFor(pattern, front)) {
                setAssembled(level, requirement.resolve(controller), false,
                        StyledSupportPartBlock.AssemblyStyle.NONE);
            }
        }
    }

    private static void setAssembled(Level level, BlockPos pos, boolean assembled,
                                     StyledSupportPartBlock.AssemblyStyle style) {
        var state = level.getBlockState(pos);
        if (!state.hasProperty(SupportPartBlock.ASSEMBLED)) {
            return;
        }
        BlockState next = state.setValue(SupportPartBlock.ASSEMBLED, assembled);
        if (next.hasProperty(ControlPanelBlock.ACTIVE) && !assembled) {
            next = next.setValue(ControlPanelBlock.ACTIVE, false);
        }
        if (next.hasProperty(StyledSupportPartBlock.ASSEMBLY_STYLE)) {
            next = next.setValue(StyledSupportPartBlock.ASSEMBLY_STYLE,
                    assembled ? style : StyledSupportPartBlock.AssemblyStyle.NONE);
        }
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        }
    }

    private static StyledSupportPartBlock.AssemblyStyle assemblyStyleFor(Pattern pattern) {
        return switch (pattern) {
            case NONE -> StyledSupportPartBlock.AssemblyStyle.NONE;
            case PRESSURE_SKID -> StyledSupportPartBlock.AssemblyStyle.HEAVY;
            case WET_VESSEL, ELECTROLYSIS_BANK, REACTION_VESSEL -> StyledSupportPartBlock.AssemblyStyle.WET;
            case HEATED_LINE, CRACKING_FURNACE -> StyledSupportPartBlock.AssemblyStyle.HEATED;
            case TALL_COLUMN, PRESSURE_LOOP -> StyledSupportPartBlock.AssemblyStyle.TOWER;
            case BLOOMERY, BREAD_OVEN, GLASS_KILN, LIME_KILN, PIT_KILN, SHAFT_FURNACE ->
                    StyledSupportPartBlock.AssemblyStyle.HEATED;
        };
    }

    private static Optional<Direction> findPanelFront(Level level, BlockPos controller) {
        for (Direction front : HORIZONTALS) {
            if (hasRole(level, controller.relative(front), PartRole.CONTROL_PANEL)) {
                return Optional.of(front);
            }
        }
        return Optional.empty();
    }

    private static void clearProjectionForPattern(Level level, BlockPos controller, Pattern pattern) {
        if (pattern == Pattern.NONE) {
            return;
        }
        for (Direction front : HORIZONTALS) {
            for (Requirement requirement : requirementsFor(pattern, front)) {
                if (requirement.role() == PartRole.CONTROL_PANEL) {
                    continue;
                }
                BlockPos pos = requirement.resolve(controller);
                if (level.getBlockState(pos).getBlock() == EarthOnMinecraft.STRUCTURE_PROJECTION.get()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void clearStaleProjections(Level level, BlockPos controller, Pattern pattern, Direction currentFront) {
        Map<BlockPos, StructureProjectionBlock.Role> keep = new HashMap<>();
        for (Requirement requirement : requirementsFor(pattern, currentFront)) {
            if (requirement.role() != PartRole.CONTROL_PANEL) {
                keep.put(requirement.resolve(controller), projectionRoleFor(requirement.role()));
            }
        }
        for (Direction front : HORIZONTALS) {
            for (Requirement requirement : requirementsFor(pattern, front)) {
                if (requirement.role() == PartRole.CONTROL_PANEL) {
                    continue;
                }
                BlockPos pos = requirement.resolve(controller);
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() != EarthOnMinecraft.STRUCTURE_PROJECTION.get()) {
                    continue;
                }
                StructureProjectionBlock.Role expected = keep.get(pos);
                if (expected == null || state.getValue(StructureProjectionBlock.ROLE) != expected) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void placeProjection(Level level, BlockPos pos, PartRole role) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && state.getBlock() != EarthOnMinecraft.STRUCTURE_PROJECTION.get()) {
            return;
        }
        StructureProjectionBlock.Role projectionRole = projectionRoleFor(role);
        BlockState projection = EarthOnMinecraft.STRUCTURE_PROJECTION.get().defaultBlockState()
                .setValue(StructureProjectionBlock.ROLE, projectionRole);
        if (state.getBlock() != EarthOnMinecraft.STRUCTURE_PROJECTION.get()
                || state.getValue(StructureProjectionBlock.ROLE) != projectionRole) {
            level.setBlock(pos, projection, Block.UPDATE_CLIENTS);
        }
    }

    private static StructureProjectionBlock.Role projectionRoleFor(PartRole role) {
        return role == PartRole.PIPE ? StructureProjectionBlock.Role.PIPE : StructureProjectionBlock.Role.CASING;
    }

    public enum Pattern {
        NONE("tooltip.earth_on_minecraft.multiblock.none", "screen.earth_on_minecraft.machine.structure.none") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return Optional.of(new Match(Direction.NORTH, List.of()));
            }
        },
        PRESSURE_SKID("tooltip.earth_on_minecraft.multiblock.pressure_skid", "screen.earth_on_minecraft.machine.structure.pressure_skid") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findBox(level, controller, 1, 2, 2, PartRole.CASING, PartRole.CASING);
            }
        },
        WET_VESSEL("tooltip.earth_on_minecraft.multiblock.wet_vessel", "screen.earth_on_minecraft.machine.structure.wet_vessel") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findBox(level, controller, 1, 2, 2, PartRole.CASING, PartRole.PIPE);
            }
        },
        ELECTROLYSIS_BANK("tooltip.earth_on_minecraft.multiblock.electrolysis_bank", "screen.earth_on_minecraft.machine.structure.electrolysis_bank") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findBox(level, controller, 1, 3, 2, PartRole.CASING, PartRole.PIPE);
            }
        },
        REACTION_VESSEL("tooltip.earth_on_minecraft.multiblock.reaction_vessel", "screen.earth_on_minecraft.machine.structure.reaction_vessel") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findBox(level, controller, 1, 3, 3, PartRole.CASING, PartRole.PIPE);
            }
        },
        HEATED_LINE("tooltip.earth_on_minecraft.multiblock.heated_line", "screen.earth_on_minecraft.machine.structure.heated_line") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findBox(level, controller, 1, 3, 2, PartRole.CASING, PartRole.PIPE);
            }
        },
        CRACKING_FURNACE("tooltip.earth_on_minecraft.multiblock.cracking_furnace", "screen.earth_on_minecraft.machine.structure.cracking_furnace") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findBox(level, controller, 1, 3, 3, PartRole.CASING, PartRole.PIPE);
            }
        },
        TALL_COLUMN("tooltip.earth_on_minecraft.multiblock.tall_column", "screen.earth_on_minecraft.machine.structure.tall_column") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findBox(level, controller, 1, 3, 4, PartRole.CASING, PartRole.PIPE);
            }
        },
        PRESSURE_LOOP("tooltip.earth_on_minecraft.multiblock.pressure_loop", "screen.earth_on_minecraft.machine.structure.pressure_loop") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findBox(level, controller, 1, 3, 3, PartRole.CASING, PartRole.PIPE);
            }
        },
        BLOOMERY("tooltip.earth_on_minecraft.multiblock.bloomery", "screen.earth_on_minecraft.machine.structure.bloomery") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findLayered(level, controller, KILN_LAYERS.get(BLOOMERY));
            }
        },
        BREAD_OVEN("tooltip.earth_on_minecraft.multiblock.bread_oven", "screen.earth_on_minecraft.machine.structure.bread_oven") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findLayered(level, controller, KILN_LAYERS.get(BREAD_OVEN));
            }
        },
        GLASS_KILN("tooltip.earth_on_minecraft.multiblock.glass_kiln", "screen.earth_on_minecraft.machine.structure.glass_kiln") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findLayered(level, controller, KILN_LAYERS.get(GLASS_KILN));
            }
        },
        LIME_KILN("tooltip.earth_on_minecraft.multiblock.lime_kiln", "screen.earth_on_minecraft.machine.structure.lime_kiln") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findLayered(level, controller, KILN_LAYERS.get(LIME_KILN));
            }
        },
        PIT_KILN("tooltip.earth_on_minecraft.multiblock.pit_kiln", "screen.earth_on_minecraft.machine.structure.pit_kiln") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findLayered(level, controller, KILN_LAYERS.get(PIT_KILN));
            }
        },
        SHAFT_FURNACE("tooltip.earth_on_minecraft.multiblock.shaft_furnace", "screen.earth_on_minecraft.machine.structure.shaft_furnace") {
            @Override
            Optional<Match> findMatch(Level level, BlockPos controller) {
                return findLayered(level, controller, KILN_LAYERS.get(SHAFT_FURNACE));
            }
        };

        private final String descriptionKey;
        private final String screenKey;

        Pattern(String descriptionKey, String screenKey) {
            this.descriptionKey = descriptionKey;
            this.screenKey = screenKey;
        }

        abstract Optional<Match> findMatch(Level level, BlockPos controller);

        public String descriptionKey() {
            return descriptionKey;
        }

        public String screenKey() {
            return screenKey;
        }
    }

    private static List<Requirement> requirementsFor(Pattern pattern, Direction front) {
        return switch (pattern) {
            case NONE -> List.of();
            case PRESSURE_SKID -> buildBoxRequirements(front, 1, 2, 2, PartRole.CASING, PartRole.CASING);
            case WET_VESSEL -> buildBoxRequirements(front, 1, 2, 2, PartRole.CASING, PartRole.PIPE);
            case ELECTROLYSIS_BANK -> buildBoxRequirements(front, 1, 3, 2, PartRole.CASING, PartRole.PIPE);
            case REACTION_VESSEL -> buildBoxRequirements(front, 1, 3, 3, PartRole.CASING, PartRole.PIPE);
            case HEATED_LINE -> buildBoxRequirements(front, 1, 3, 2, PartRole.CASING, PartRole.PIPE);
            case CRACKING_FURNACE -> buildBoxRequirements(front, 1, 3, 3, PartRole.CASING, PartRole.PIPE);
            case TALL_COLUMN -> buildBoxRequirements(front, 1, 3, 4, PartRole.CASING, PartRole.PIPE);
            case PRESSURE_LOOP -> buildBoxRequirements(front, 1, 3, 3, PartRole.CASING, PartRole.PIPE);
            case BLOOMERY, BREAD_OVEN, GLASS_KILN, LIME_KILN, PIT_KILN, SHAFT_FURNACE ->
                    buildLayeredRequirements(front, KILN_LAYERS.get(pattern));
        };
    }

    /**
     * The six spec kilns are described as horizontal layer patterns in {@code multiblocks.jsonl}.
     * Each layer is a grid of characters: {@code B} shell, {@code #} interior air, {@code C}
     * controller, {@code T} tuyere, {@code A} air inlet, {@code O} oven mouth, {@code H} chimney,
     * and {@code .} meaning "no requirement at this height".
     */
    private static final Map<Pattern, List<String[]>> KILN_LAYERS = Map.of(
            Pattern.BLOOMERY, List.of(
                    new String[]{"BBB", "B#B", "BBB"},
                    new String[]{"BCB", "B#B", "BTB"},
                    new String[]{"BBB", "A#B", "BBB"},
                    new String[]{"...", ".H.", "..."}),
            Pattern.BREAD_OVEN, List.of(
                    new String[]{"BBB", "BBB", "BBB"},
                    new String[]{"BCB", "B#B", "BOB"},
                    new String[]{"BBB", ".H.", "BBB"}),
            Pattern.GLASS_KILN, List.of(
                    new String[]{"BBB", "BBB", "BBB"},
                    new String[]{"BCB", "B#B", "BTB"},
                    new String[]{"BBB", "B#B", "BOB"},
                    new String[]{"...", ".H.", "..."}),
            Pattern.LIME_KILN, List.of(
                    new String[]{"BB", "BB"},
                    new String[]{"BC", "B#"},
                    new String[]{"BB", "B#"},
                    new String[]{"..", "H."}),
            Pattern.PIT_KILN, List.of(
                    new String[]{"BBB", "B#B", "BBB"},
                    new String[]{"BBB", "B#B", "BBB"},
                    new String[]{"...", ".A.", "..."}),
            Pattern.SHAFT_FURNACE, List.of(
                    new String[]{"BBB", "B#B", "BBB"},
                    new String[]{"BCB", "B#B", "BTB"},
                    new String[]{"BBB", "B#B", "BTB"},
                    new String[]{"BBB", "A#B", "BBB"},
                    new String[]{"...", ".H.", "..."}));

    private static List<Requirement> buildLayeredRequirements(Direction front, List<String[]> layers) {
        List<Requirement> requirements = new ArrayList<>();

        // Kiln patterns mark their own controller with `C`, so unlike the industrial boxes they do
        // not need a separate control panel next to them.

        // The `C` cell marks where the controller block actually sits inside the pattern. Anchoring
        // on the pattern centre instead shifted every requirement by the controller's own offset,
        // so no kiln could ever match: the 2x2 lime kiln has its `C` in the right-hand column, and
        // centring put the whole shell one block off in both directions.
        int[] anchor = controllerCell(layers);
        int anchorUp = anchor[0];
        int anchorRow = anchor[1];
        int anchorColumn = anchor[2];

        for (int up = 0; up < layers.size(); up++) {
            String[] rows = layers.get(up);
            for (int row = 0; row < rows.length; row++) {
                for (int column = 0; column < rows[row].length(); column++) {
                    char symbol = rows[row].charAt(column);
                    PartRole role = roleFor(symbol);
                    if (role == null) {
                        continue;
                    }
                    int right = column - anchorColumn;
                    int back = row - anchorRow;
                    int height = up - anchorUp;
                    requirements.add(Requirement.relative(front, right, back, height, role));
                }
            }
        }
        return requirements;
    }

    /** Returns {@code {layer, row, column}} of the `C` cell, or the pattern centre when absent. */
    private static int[] controllerCell(List<String[]> layers) {
        for (int up = 0; up < layers.size(); up++) {
            String[] rows = layers.get(up);
            for (int row = 0; row < rows.length; row++) {
                int column = rows[row].indexOf('C');
                if (column >= 0) {
                    return new int[]{up, row, column};
                }
            }
        }
        return new int[]{0, layers.getFirst().length / 2, layers.getFirst()[0].length() / 2};
    }

    private static PartRole roleFor(char symbol) {
        return switch (symbol) {
            case 'B' -> PartRole.CASING;
            case 'T', 'A', 'H' -> PartRole.PIPE;
            case 'O' -> PartRole.MOUTH;
            case 'C', '#', '.' -> null;
            default -> null;
        };
    }

    private static Optional<Match> findLayered(Level level, BlockPos controller, List<String[]> layers) {
        if (layers == null) {
            return Optional.empty();
        }
        for (Direction front : HORIZONTALS) {
            List<Requirement> requirements = buildLayeredRequirements(front, layers);
            Optional<Match> match = matchFootprint(level, controller, front, requirements);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private static Optional<Match> findBox(Level level, BlockPos controller, int halfWidth, int depth, int height,
                                           PartRole lowerRole, PartRole upperRole) {
        for (Direction front : HORIZONTALS) {
            List<Requirement> requirements = buildBoxRequirements(front, halfWidth, depth, height, lowerRole, upperRole);
            Optional<Match> match = matchFootprint(level, controller, front, requirements);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private static List<Requirement> buildBoxRequirements(Direction front, int halfWidth, int depth, int height,
                                                          PartRole lowerRole, PartRole upperRole) {
        List<Requirement> requirements = new ArrayList<>();
        requirements.add(Requirement.relative(front, 0, -1, 0, PartRole.CONTROL_PANEL));
        for (int right = -halfWidth; right <= halfWidth; right++) {
            for (int back = -1; back < depth - 1; back++) {
                for (int up = 0; up < height; up++) {
                    if (right == 0 && up == 0 && (back == -1 || back == 0)) {
                        continue;
                    }
                    PartRole role = up == 0 ? lowerRole : upperRole;
                    requirements.add(Requirement.relative(front, right, back, up, role));
                }
            }
        }
        return requirements;
    }

    private static Optional<Match> matchFootprint(Level level, BlockPos controller, Direction front, List<Requirement> requirements) {
        List<BlockPos> parts = new ArrayList<>();
        for (Requirement requirement : requirements) {
            BlockPos pos = requirement.resolve(controller);
            if (!hasRole(level, pos, requirement.role())) {
                return Optional.empty();
            }
            parts.add(pos);
        }
        return Optional.of(new Match(front, List.copyOf(parts)));
    }

    private static boolean hasRole(Level level, BlockPos pos, PartRole role) {
        Block block = level.getBlockState(pos).getBlock();
        return switch (role) {
            case CONTROL_PANEL -> block == EarthOnMinecraft.CONTROL_PANEL.get();
            case CASING -> block == EarthOnMinecraft.INDUSTRIAL_MACHINE_CASING.get()
                    || block == EarthOnMinecraft.MACHINE_INPUT_INTERFACE.get()
                    || block == EarthOnMinecraft.MACHINE_OUTPUT_INTERFACE.get()
                    || isKilnShell(block);
            case PIPE -> block == EarthOnMinecraft.STEEL_PROCESS_PIPE.get()
                    || isKilnPipe(block);
            case MOUTH -> isKilnMouth(block);
        };
    }

    /**
     * The spec kilns are built from their own masonry and fittings rather than the industrial
     * casing set, so the shell role accepts the kiln shell blocks named in {@code multiblocks.jsonl}.
     */
    private static boolean isKilnShell(Block block) {
        return block == specBlock("firebrick_block")
                || block == specBlock("clay_brick_block")
                || block == specBlock("packed_earth");
    }

    private static boolean isKilnPipe(Block block) {
        return block == specBlock("tuyere")
                || block == specBlock("air_inlet")
                || block == specBlock("chimney");
    }

    private static boolean isKilnMouth(Block block) {
        return block == specBlock("oven_mouth");
    }

    /** Resolves a generated spec block, or {@code null} when it is not registered. */
    private static Block specBlock(String id) {
        var holder = SpecBlocks.BY_ID.get(id);
        return holder == null ? null : holder.get();
    }

    public record Match(Direction front, List<BlockPos> parts) {
    }

    /** One block a structure needs, expressed as an offset from the controller. */
    public record PlacedRequirement(int x, int y, int z, String role) {
    }

    /**
     * The blocks a layered kiln needs, relative to its controller, for one facing.
     *
     * <p>Exposed so the guide screen and the GameTest can build or describe a kiln from the same
     * pattern the matcher uses, instead of hand-coding coordinates that drift out of sync.</p>
     */
    public static List<PlacedRequirement> requirementsFor(ProcessingMachineBlock.Kind kind, Direction front) {
        List<String[]> layers = KILN_LAYERS.get(patternFor(kind));
        if (layers == null) {
            return List.of();
        }
        // `Requirement` stores (x, z, up); `PlacedRequirement` exposes (x, y, z). Passing them
        // positionally swapped height and depth, so every kiln requirement pointed at the wrong
        // block.
        return buildLayeredRequirements(front, layers).stream()
                .map(requirement -> new PlacedRequirement(
                        requirement.x(), requirement.up(), requirement.z(), requirement.role().name()))
                .toList();
    }

    private enum PartRole {
        CONTROL_PANEL,
        CASING,
        PIPE,
        MOUTH
    }

    private record Requirement(int x, int z, int up, PartRole role) {
        static Requirement relative(Direction front, int right, int back, int up, PartRole role) {
            Direction rightDir = front.getClockWise();
            Direction backDir = front.getOpposite();
            int x = rightDir.getStepX() * right + backDir.getStepX() * back;
            int z = rightDir.getStepZ() * right + backDir.getStepZ() * back;
            return new Requirement(x, z, up, role);
        }

        BlockPos resolve(BlockPos controller) {
            return controller.offset(x, up, z);
        }
    }
}
