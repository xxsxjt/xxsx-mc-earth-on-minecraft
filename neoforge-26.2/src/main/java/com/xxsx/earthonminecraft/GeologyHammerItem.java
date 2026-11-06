package com.xxsx.earthonminecraft;

import com.xxsx.earthlore.api.LoreApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Stone geology hammer: forges at the stone anvil and prospects by striking rock.
 *
 * <p>Striking any block reports up to three live lodes in the 3x3 chunks around it: direction,
 * rounded horizontal distance, rounded depth, block count and remaining reserve. Main ore bodies
 * take the slots first, then limestone, then halo rock, nearest first within each tier. Striking a
 * lode block first assays that block. The anvil handles its own forging before this item is
 * asked.</p>
 */
public class GeologyHammerItem extends Item {
    public static final int DURABILITY = 96;
    public static final int SURVEY_CHUNK_RADIUS = 1;
    public static final int COOLDOWN_TICKS = 20;
    public static final int MAX_REPORTED_LODES = 3;

    public GeologyHammerItem(Properties properties) {
        super(properties.durability(DURABILITY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }

        BlockPos clicked = context.getClickedPos();
        int readout = player instanceof ServerPlayer sp ? LoreApi.readoutLevel(sp, this) : 1;
        List<Component> lines = new ArrayList<>();
        BlockState state = level.getBlockState(clicked);
        if (readout > 0 && level.getBlockEntity(clicked) instanceof DepositBlockEntity deposit) {
            DepositReserve reserve = deposit.reserve();
            lines.add(Component.translatable("message.earth_on_minecraft.geology_hammer.assay",
                    state.getBlock().getName(), reserve.remaining(), reserve.capacity())
                    .withStyle(ChatFormatting.GOLD));
        }
        List<ProspectingSurvey.Group> surveyed = survey(level, clicked);
        lines.addAll(readout <= 0 ? reportCategoryOnly(surveyed) : report(clicked, surveyed));
        lines.forEach(player::sendSystemMessage);

        stack.hurtAndBreak(1, player, context.getHand());
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Live prospectable lodes in the 3x3 chunks around the struck block, in report order. */
    public static List<ProspectingSurvey.Group> survey(Level level, BlockPos struck) {
        return rank(ProspectingSurvey.group(ProspectingSurvey.collect(level, ChunkPos.containing(struck),
                SURVEY_CHUNK_RADIUS, ProspectingCatalog::isProspectable), struck));
    }

    /**
     * Report order: main ore bodies, then limestone, then halo rock; nearest first within a tier.
     * A halo usually sits right underfoot and would otherwise take the slots of the ore it marks.
     */
    public static List<ProspectingSurvey.Group> rank(List<ProspectingSurvey.Group> groups) {
        List<ProspectingSurvey.Group> ranked = new ArrayList<>(groups);
        ranked.sort(Comparator.comparingInt((ProspectingSurvey.Group group) -> tier(group.lodeId()))
                .thenComparingDouble(ProspectingSurvey.Group::distance)
                .thenComparing(ProspectingSurvey.Group::lodeId));
        return ranked;
    }

    private static int tier(String lodeId) {
        if (ProspectingCatalog.isMainLode(lodeId)) {
            return 0;
        }
        return ProspectingCatalog.LIMESTONE.equals(lodeId) ? 1 : 2;
    }

    /** Category-only echo before learning K-GEO-05 (prospecting): only reports presence or absence of lodes. */
    public static List<Component> reportCategoryOnly(List<ProspectingSurvey.Group> groups) {
        if (groups.isEmpty()) {
            return List.of(Component.translatable("message.earth_on_minecraft.geology_hammer.quiet")
                    .withStyle(ChatFormatting.GRAY));
        }
        return List.of(Component.translatable("message.earth_on_minecraft.geology_hammer.category_present")
                .withStyle(ChatFormatting.YELLOW));
    }

    /** Chat lines for a survey: a title and up to three lodes, or the quiet-echo line. */
    public static List<Component> report(BlockPos struck, List<ProspectingSurvey.Group> groups) {
        List<Component> lines = new ArrayList<>();
        if (groups.isEmpty()) {
            lines.add(Component.translatable("message.earth_on_minecraft.geology_hammer.quiet")
                    .withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.translatable("message.earth_on_minecraft.geology_hammer.title")
                .withStyle(ChatFormatting.AQUA));
        for (ProspectingSurvey.Group group : groups.subList(0, Math.min(MAX_REPORTED_LODES, groups.size()))) {
            ProspectingSurvey.Bearing bearing = ProspectingSurvey.bearing(struck, group.nearest());
            Component height = ProspectingSurvey.heightText(ProspectingSurvey.roundedHeight(struck, group.nearest()));
            Component name = ProspectingSurvey.lodeName(group.lodeId());
            Component line = bearing == ProspectingSurvey.Bearing.HERE
                    ? Component.translatable("message.earth_on_minecraft.geology_hammer.entry_here",
                    name, height, group.blocks(), group.remaining())
                    : Component.translatable("message.earth_on_minecraft.geology_hammer.entry",
                    name, Component.translatable(bearing.translationKey()),
                    ProspectingSurvey.roundToFive(ProspectingSurvey.horizontalDistance(struck, group.nearest())),
                    height, group.blocks(), group.remaining());
            lines.add(line.copy().withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.geology_hammer")
                .withStyle(ChatFormatting.GRAY));
    }
}
