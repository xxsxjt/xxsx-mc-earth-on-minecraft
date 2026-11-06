package com.xxsx.earthonminecraft;

import com.xxsx.earthlore.api.GateResult;
import com.xxsx.earthlore.api.GradeAccess;
import com.xxsx.earthlore.api.LoreApi;
import com.xxsx.earthlore.api.LoreMachines;
import com.xxsx.earthlore.gate.GateService;
import com.xxsx.earthlore.knowledge.KnowledgeService;
import com.xxsx.earthonminecraft.component.EarthDataComponents;
import com.xxsx.earthonminecraft.component.FoodQuality;
import com.xxsx.earthonminecraft.component.GemComponent;
import com.xxsx.earthonminecraft.component.Grade;
import com.xxsx.earthonminecraft.component.HerbComponent;
import com.xxsx.earthonminecraft.component.MaterialBatch;
import com.xxsx.earthonminecraft.component.PharmaceuticalComponent;
import com.xxsx.earthonminecraft.component.QualityComponents;
import com.xxsx.earthonminecraft.component.ToolQualityComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bridges {@code earth_on_minecraft} quality components, machines, and field instruments with {@code earth_lore}.
 */
public final class LoreIntegration {
    public static final Identifier NODE_MINERAL_ID =
            Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, "geo/mineral_identification");
    public static final Identifier NODE_TOPOGRAPHY =
            Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, "geo/field_survey");
    public static final Identifier NODE_STRATIGRAPHY =
            Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, "geo/stratigraphy");
    public static final Identifier NODE_PROSPECTING =
            Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, "geo/economic_geology");
    public static final Identifier NODE_EXPLORATION_DRILLING =
            Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, "geo/geophysics");
    public static final Identifier NODE_ANALYTICAL_CHEMISTRY =
            Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, "che/analytical_chemistry");

    private static boolean initialized;

    private LoreIntegration() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        LoreApi.registerGradeAccess(new GradeAccess() {
            @Override
            public Optional<com.xxsx.earthlore.api.Grade> read(ItemStack stack) {
                return readGrade(stack).map(LoreIntegration::toLoreGrade);
            }

            @Override
            public ItemStack write(ItemStack stack, com.xxsx.earthlore.api.Grade loreGrade) {
                return writeGrade(stack, fromLoreGrade(loreGrade));
            }
        });
    }

    public static com.xxsx.earthlore.api.Grade toLoreGrade(Grade grade) {
        return switch (grade) {
            case CRUDE -> com.xxsx.earthlore.api.Grade.CRUDE;
            case COMMON -> com.xxsx.earthlore.api.Grade.COMMON;
            case FINE -> com.xxsx.earthlore.api.Grade.FINE;
            case MASTERWORK -> com.xxsx.earthlore.api.Grade.MASTERWORK;
            case HEIRLOOM -> com.xxsx.earthlore.api.Grade.HEIRLOOM;
        };
    }

    public static Grade fromLoreGrade(com.xxsx.earthlore.api.Grade grade) {
        return switch (grade) {
            case CRUDE -> Grade.CRUDE;
            case COMMON -> Grade.COMMON;
            case FINE -> Grade.FINE;
            case MASTERWORK -> Grade.MASTERWORK;
            case HEIRLOOM -> Grade.HEIRLOOM;
        };
    }

    public static Optional<Grade> readGrade(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        ToolQualityComponent tool = stack.get(EarthDataComponents.TOOL_QUALITY.get());
        if (tool != null) {
            return Optional.of(tool.grade());
        }
        FoodQuality food = stack.get(EarthDataComponents.FOOD_QUALITY.get());
        if (food != null) {
            return Optional.of(food.grade());
        }
        HerbComponent herb = stack.get(EarthDataComponents.HERB.get());
        if (herb != null) {
            return Optional.of(herb.grade());
        }
        PharmaceuticalComponent pharma = stack.get(EarthDataComponents.PHARMACEUTICAL.get());
        if (pharma != null) {
            return Optional.of(pharma.grade());
        }
        GemComponent gem = stack.get(EarthDataComponents.GEM.get());
        if (gem != null) {
            return Optional.of(gem.grade());
        }
        MaterialBatch batch = stack.get(EarthDataComponents.MATERIAL_BATCH.get());
        if (batch != null) {
            return Optional.of(batch.grade());
        }
        return QualityComponents.resolveMaterialBatch(stack).map(MaterialBatch::grade);
    }

    public static ItemStack writeGrade(ItemStack stack, Grade newGrade) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }
        ToolQualityComponent tool = stack.get(EarthDataComponents.TOOL_QUALITY.get());
        if (tool != null) {
            stack.set(EarthDataComponents.TOOL_QUALITY.get(),
                    new ToolQualityComponent(tool.schemaVersion(), newGrade, tool.smith(), tool.rolls()));
            return stack;
        }
        FoodQuality food = stack.get(EarthDataComponents.FOOD_QUALITY.get());
        if (food != null) {
            stack.set(EarthDataComponents.FOOD_QUALITY.get(), food.withGrade(newGrade));
            return stack;
        }
        HerbComponent herb = stack.get(EarthDataComponents.HERB.get());
        if (herb != null) {
            stack.set(EarthDataComponents.HERB.get(), new HerbComponent(
                    herb.schemaVersion(), herb.species(), herb.part(), herb.nature(), herb.flavor(),
                    herb.toxicity(), herb.prepared(), herb.potency(), newGrade, herb.batchId()));
            return stack;
        }
        PharmaceuticalComponent pharma = stack.get(EarthDataComponents.PHARMACEUTICAL.get());
        if (pharma != null) {
            stack.set(EarthDataComponents.PHARMACEUTICAL.get(), new PharmaceuticalComponent(
                    pharma.schemaVersion(), pharma.drug(), newGrade, pharma.purity(), pharma.doseMg(),
                    pharma.form(), pharma.batchId(), pharma.shelfNote()));
            return stack;
        }
        GemComponent gem = stack.get(EarthDataComponents.GEM.get());
        if (gem != null) {
            stack.set(EarthDataComponents.GEM.get(), new GemComponent(
                    gem.schemaVersion(), gem.species(), gem.variety(), newGrade, gem.clarity(),
                    gem.colorGrade(), gem.carat(), gem.treatment(), gem.cut(), gem.matrix(), gem.pattern()));
            return stack;
        }
        MaterialBatch batch = QualityComponents.resolveMaterialBatch(stack).orElse(null);
        if (batch != null) {
            stack.set(EarthDataComponents.MATERIAL_BATCH.get(), batch.withPurityAndGrade(batch.purity(), newGrade));
        }
        return stack;
    }

    public static GateResult checkMachine(BlockEntity be, Object... contents) {
        if (be == null || !KnowledgeService.running()) {
            return GateResult.FREE;
        }
        List<Object> list = new ArrayList<>();
        for (Object content : contents) {
            if (content instanceof Collection<?> many) {
                list.addAll(many);
            } else if (content != null) {
                list.add(content);
            }
        }
        return LoreMachines.check(be, list);
    }

    public static ItemStack capMachineOutput(BlockEntity be, ItemStack stack, Object... extraGates) {
        if (stack == null || stack.isEmpty() || be == null || !KnowledgeService.running()) {
            return stack;
        }
        List<Object> targets = new ArrayList<>();
        targets.add(stack);
        for (Object extra : extraGates) {
            if (extra != null) {
                targets.add(extra);
            }
        }
        GateResult gate = LoreMachines.check(be, targets);
        return GateService.apply(stack, gate);
    }

    public static void awardMachineProduct(BlockEntity be, ItemStack stack) {
        if (be == null || stack == null || stack.isEmpty() || !KnowledgeService.running()) {
            return;
        }
        Optional<UUID> owner = LoreMachines.owner(be);
        if (owner.isEmpty()) {
            return;
        }
        MinecraftServer server = KnowledgeService.server();
        if (server == null) {
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(owner.get());
        if (online != null) {
            LoreApi.recordMade(online, stack.getItem(), stack.getCount());
        }
    }

    /**
     * Checks whether the local client player knows {@code node}. On a dedicated server or before the client
     * knowledge graph is synced, returns {@code true} when the graph is empty so unit tests are unaffected.
     */
    public static boolean knowsClient(Identifier node) {
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            return true;
        }
        try {
            if (!com.xxsx.earthlore.client.ClientKnowledge.synced()
                    && com.xxsx.earthlore.client.ClientKnowledge.graph().nodes().isEmpty()) {
                return true;
            }
            return com.xxsx.earthlore.client.ClientKnowledge.knows(node);
        } catch (Throwable ignored) {
            return true;
        }
    }
}
