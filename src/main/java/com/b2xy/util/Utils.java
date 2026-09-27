package com.b2xy.util;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class Utils {
    public static final boolean XAERO_AVAILABLE = FabricLoader.getInstance().isModLoaded("xaeroworldmap") && FabricLoader.getInstance().isModLoaded("xaerominimap");

    /**
     * Запуск полёта/фейерверка (порт из BepHax). Если elytraRequired и в слоте 38 нет элитр —
     * ставит элитры из хотбара, использует предмет и серверно стартует полёт.
     * Возвращает слот, куда нужно вернуть элитры, или 200 если свапа не было, -1 при неудаче.
     */
    public static int firework(MinecraftClient mc, boolean elytraRequired) {
        if (mc.player != null && mc.interactionManager != null) {
            int elytraSwapSlot = -1;
            if (elytraRequired && !mc.player.getInventory().getStack(38).isOf(Items.ELYTRA)) {
                FindItemResult itemResult = InvUtils.findInHotbar(Items.ELYTRA);
                if (!itemResult.found()) {
                    return -1;
                }

                elytraSwapSlot = itemResult.slot();
                InvUtils.swap(itemResult.slot(), true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                InvUtils.swapBack();
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }

            FindItemResult itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
            if (itemResult.found()) {
                if (itemResult.isOffhand()) {
                    mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                    mc.player.swingHand(Hand.OFF_HAND);
                } else {
                    InvUtils.swap(itemResult.slot(), true);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    InvUtils.swapBack();
                }

                return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
            }

            int movedSlot = -1;
            PlayerInventory inv = mc.player.getInventory();
            for (int n = 9; n < inv.main.size(); n++) {
                Item item = inv.getStack(n).getItem();
                if (item == Items.FIREWORK_ROCKET) {
                    InvUtils.move().from(n).to(inv.selectedSlot);
                    movedSlot = n;
                    break;
                }
            }

            if (movedSlot != -1) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.move().from(inv.getSelectedSlot()).to(movedSlot);
                return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
            }
            return -1;
        }
        return -1;
    }

    public static void setPressed(net.minecraft.client.option.KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
        meteordevelopment.meteorclient.utils.misc.input.Input.setKeyState(key, pressed);
    }

    public static Vec3d yawToDirection(double yaw) {
        yaw = yaw * Math.PI / 180.0;
        double x = -Math.sin(yaw);
        double z = Math.cos(yaw);
        return new Vec3d(x, 0.0, z);
    }

    public static Vec3d positionInDirection(Vec3d pos, double yaw, double distance) {
        Vec3d offset = yawToDirection(yaw).multiply(distance);
        return pos.add(offset);
    }

    public static double angleOnAxis(double yaw) {
        if (yaw < 0.0) {
            yaw += 360.0;
        }
        return Math.round(yaw / 45.0) * 45L;
    }

    public static double angleDifference(double target, double current) {
        double diff = (target - current + 180.0) % 360.0 - 180.0;
        return diff < -180.0 ? diff + 360.0 : diff;
    }

    public static float smoothRotation(double current, double target, double rotationScaling) {
        double difference = angleDifference(target, current);
        return (float) (current + difference * rotationScaling);
    }

    public static double distancePointToDirection(Vec3d point, Vec3d direction, Vec3d start) {
        if (start == null) {
            start = Vec3d.ZERO;
        }
        point = point.multiply(1.0, 0.0, 1.0);
        start = start.multiply(1.0, 0.0, 1.0);
        direction = direction.multiply(1.0, 0.0, 1.0);
        Vec3d directionVec = point.subtract(start);
        double projectionLength = directionVec.dotProduct(direction) / direction.lengthSquared();
        Vec3d projection = direction.multiply(projectionLength);
        Vec3d perp = directionVec.subtract(projection);
        return perp.length();
    }
}