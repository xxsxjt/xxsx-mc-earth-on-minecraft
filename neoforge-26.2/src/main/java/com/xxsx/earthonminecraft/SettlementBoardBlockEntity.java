package com.xxsx.earthonminecraft;

import com.xxsx.earthonminecraft.living.SettlementSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class SettlementBoardBlockEntity extends BlockEntity implements MenuProvider {
    private String settlementId = "";

    public SettlementBoardBlockEntity(BlockPos pos, BlockState state) {
        super(EarthOnMinecraft.SETTLEMENT_BOARD_BLOCK_ENTITY.get(), pos, state);
    }

    public void initialize() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean firstIndex = settlementId.isBlank();
        SettlementSavedData savedData = SettlementSavedData.get(serverLevel);
        SettlementSavedData.SettlementSnapshot snapshot = savedData.byId(settlementId).orElse(null);
        if (snapshot == null) {
            var addonAnchor = SettlementFacilities.nearbyAddonSettlement(serverLevel, worldPosition, 24);
            if (addonAnchor.isPresent()) {
                var anchor = addonAnchor.get();
                long seed = com.xxsx.earthonminecraft.living.SettlementDataManager.mix(
                        serverLevel.getSeed() ^ anchor.pos().asLong() ^ anchor.profileId().hashCode());
                String nameKey = com.xxsx.earthonminecraft.living.SettlementDataManager
                        .chooseSettlementNameKey(anchor.profileId(), seed);
                snapshot = savedData.getOrCreate(serverLevel, anchor.pos(), anchor.profileId(), nameKey);
            } else {
                snapshot = savedData.getOrCreate(serverLevel, worldPosition);
            }
        }
        if (!snapshot.id().equals(settlementId)) {
            settlementId = snapshot.id();
            setChanged();
        }
        SettlementFacilities.registerBoard(serverLevel, worldPosition, snapshot.id());
        if (firstIndex) {
            SettlementFacilities.indexNearby(serverLevel, worldPosition, 24, 10, snapshot.id());
        }
    }

    public SettlementSavedData.SettlementSnapshot snapshot() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return SettlementBoardMenu.emptySnapshot(worldPosition);
        }
        return SettlementSavedData.get(serverLevel).byId(settlementId)
                .orElseGet(() -> SettlementSavedData.get(serverLevel).getOrCreate(serverLevel, worldPosition));
    }

    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        SettlementBoardMenu.writePayload(buf, worldPosition, snapshot());
    }

    @Override
    public Component getDisplayName() {
        SettlementSavedData.SettlementSnapshot snapshot = snapshot();
        return Component.translatable("screen.earth_on_minecraft.settlement_board.title",
                Component.translatable(snapshot.nameKey()));
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SettlementBoardMenu(containerId, inventory, worldPosition, snapshot());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        settlementId = input.getStringOr("SettlementId", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("SettlementId", settlementId);
    }
}
