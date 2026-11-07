package com.xxsx.earthonminecraft;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/**
 * Soft compatibility bridge for Applied Energistics 2's Wooden Crank ({@code ae2:crank}).
 * When AE2 is present in the instance, attaching {@code ae2:crank} to the top or side of any
 * crankable processing machine (Ball Mill, Jaw Crusher, Sieve, Magnetic Separator, Mill, Stone Mill,
 * Filter Press, Tablet Press, Anvil) or Battery Box lets the AE2 crank turn and supply kinetic/electric work.
 */
public final class Ae2CrankCompat {
    private static final Identifier AE2_CRANK_ID = Identifier.fromNamespaceAndPath("ae2", "crank");

    private Ae2CrankCompat() {
    }

    public static boolean isAe2CrankItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return AE2_CRANK_ID.equals(key);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(RegisterCapabilitiesEvent event) {
        try {
            Class<?> aeCapsClass = Class.forName("appeng.api.AECapabilities");
            Class<?> crankableInterface = Class.forName("appeng.api.implementations.blockentities.ICrankable");
            Field crankableField = aeCapsClass.getField("CRANKABLE");
            Object rawCap = crankableField.get(null);
            if (!(rawCap instanceof BlockCapability blockCapability)) {
                return;
            }

            ICapabilityProvider<ProcessingMachineBlockEntity, Direction, Object> machineProvider =
                    (machine, side) -> {
                        if (!machine.supportsCrankAttachment()) {
                            return null;
                        }
                        return Proxy.newProxyInstance(
                                crankableInterface.getClassLoader(),
                                new Class<?>[]{crankableInterface},
                                (proxy, method, args) -> switch (method.getName()) {
                                    case "canTurn" -> machine.canCrankTurn();
                                    case "applyTurn" -> {
                                        machine.applyCrankTurn(45);
                                        yield null;
                                    }
                                    case "toString" -> "EarthMachineCrankable[" + machine.getBlockPos() + "]";
                                    case "hashCode" -> System.identityHashCode(machine);
                                    case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
                                    default -> null;
                                });
                    };
            event.registerBlockEntity(
                    (BlockCapability<Object, Direction>) blockCapability,
                    (BlockEntityType<ProcessingMachineBlockEntity>) EarthOnMinecraft.PROCESSING_MACHINE_BLOCK_ENTITY.get(),
                    machineProvider);

            ICapabilityProvider<BatteryBoxBlockEntity, Direction, Object> batteryProvider =
                    (battery, side) -> Proxy.newProxyInstance(
                            crankableInterface.getClassLoader(),
                            new Class<?>[]{crankableInterface},
                            (proxy, method, args) -> switch (method.getName()) {
                                case "canTurn" -> battery.getEnergyStored() < battery.getEnergyCapacity();
                                case "applyTurn" -> {
                                    battery.receiveEnergy(180, false);
                                    yield null;
                                }
                                case "toString" -> "EarthBatteryCrankable[" + battery.getBlockPos() + "]";
                                case "hashCode" -> System.identityHashCode(battery);
                                case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
                                default -> null;
                            });
            event.registerBlockEntity(
                    (BlockCapability<Object, Direction>) blockCapability,
                    (BlockEntityType<BatteryBoxBlockEntity>) EarthOnMinecraft.BATTERY_BOX_BLOCK_ENTITY.get(),
                    batteryProvider);
            EarthOnMinecraft.LOGGER.info("[Earth on Minecraft] Registered AE2 ICrankable compatibility for machines & battery boxes");
        } catch (ClassNotFoundException ignored) {
            // AE2 not installed; skip quietly.
        } catch (Throwable t) {
            EarthOnMinecraft.LOGGER.warn("[Earth on Minecraft] Failed to bind AE2 ICrankable capability: {}", t.toString());
        }
    }
}
