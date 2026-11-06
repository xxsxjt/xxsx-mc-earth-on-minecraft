package com.xxsx.earthonminecraft;

import com.xxsx.earthlore.api.GateResult;
import com.xxsx.earthlore.api.LoreApi;
import com.xxsx.earthlore.gate.RecipeGates;
import com.xxsx.earthlore.knowledge.KnowledgeService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * A field instrument that reports live lodes in the 5x5 chunks around the player over the full
 * world height, without changing terrain. Sneak-use cycles the target lode family.
 */
public final class OreScannerItem extends Item {
    public static final int SURVEY_CHUNK_RADIUS = 2;
    public static final int COOLDOWN_TICKS = 40;
    public static final String FILTER_KEY = "EomScanFilter";
    private static final int MAX_RESULTS = 5;

    public OreScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && KnowledgeService.running()) {
            GateResult gate = LoreApi.check(serverPlayer, stack);
            if (gate.locked()) {
                serverPlayer.sendOverlayMessage(RecipeGates.lockedMessage(KnowledgeService.graph(), gate));
                return InteractionResult.FAIL;
            }
        }
        if (player.isSecondaryUseActive()) {
            ProspectingCatalog.Filter filter = cycleFilter(stack);
            player.sendSystemMessage(Component.translatable("message.earth_on_minecraft.ore_scanner.filter_set",
                    Component.translatable(filter.translationKey())).withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS_SERVER;
        }

        ProspectingCatalog.Filter filter = filterOf(stack);
        BlockPos origin = player.blockPosition();
        List<ProspectingSurvey.Group> groups = survey(level, origin, filter);
        Component filterName = Component.translatable(filter.translationKey());
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        if (groups.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "message.earth_on_minecraft.ore_scanner.none", filterName).withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }

        ProspectingSurvey.Group nearest = groups.get(0);
        BlockPos target = nearest.nearest();
        int blocks = groups.stream().mapToInt(ProspectingSurvey.Group::blocks).sum();
        int reserve = groups.stream().mapToInt(ProspectingSurvey.Group::remaining).sum();
        player.sendSystemMessage(Component.translatable("message.earth_on_minecraft.ore_scanner.found",
                filterName, ProspectingSurvey.lodeName(nearest.lodeId()),
                offset(target.getX() - origin.getX(), "east", "west", "ew_zero"),
                offset(target.getY() - origin.getY(), "up", "down", "ud_zero"),
                offset(target.getZ() - origin.getZ(), "south", "north", "sn_zero"),
                Math.round(nearest.distance()), blocks, groups.size(), reserve).withStyle(ChatFormatting.AQUA));
        groups.stream()
                .sorted(Comparator.comparingInt(ProspectingSurvey.Group::remaining).reversed()
                        .thenComparing(ProspectingSurvey.Group::lodeId))
                .limit(MAX_RESULTS)
                .forEach(group -> player.sendSystemMessage(Component.translatable(
                        "message.earth_on_minecraft.ore_scanner.entry",
                        ProspectingSurvey.lodeName(group.lodeId()), group.blocks(), group.remaining())
                        .withStyle(ChatFormatting.GRAY)));
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Live lodes accepted by {@code filter} in the 5x5 chunks around {@code origin}, nearest first. */
    public static List<ProspectingSurvey.Group> survey(Level level, BlockPos origin, ProspectingCatalog.Filter filter) {
        return ProspectingSurvey.group(ProspectingSurvey.collect(level, ChunkPos.containing(origin),
                SURVEY_CHUNK_RADIUS, filter::accepts), origin);
    }

    public static ProspectingCatalog.Filter filterOf(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return ProspectingCatalog.Filter.byKey(data.copyTag().getStringOr(FILTER_KEY, ""));
    }

    /** Advances the stored target to the next family and returns it. */
    public static ProspectingCatalog.Filter cycleFilter(ItemStack stack) {
        ProspectingCatalog.Filter next = filterOf(stack).next();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(FILTER_KEY, next.key()));
        return next;
    }

    private static Component offset(int delta, String positive, String negative, String zero) {
        String prefix = "message.earth_on_minecraft.ore_scanner.";
        if (delta > 0) {
            return Component.translatable(prefix + positive, delta);
        }
        if (delta < 0) {
            return Component.translatable(prefix + negative, -delta);
        }
        return Component.translatable(prefix + zero);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.ore_scanner"));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.ore_scanner.filter",
                Component.translatable(filterOf(stack).translationKey())).withStyle(ChatFormatting.YELLOW));
    }
}
