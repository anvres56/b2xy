package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * GrimVelocity (порт фрагмента «New Grim» на Yarn 1.21.11, полного исходника нет):
 * обход GrimAC для полёта/скорости. Отменяет обычные пакеты движения и вместо них
 * периодически шлёт чистую пару PosRot + STOP_DESTROY_BLOCK (по позиции игрока,
 * грань DOWN) — по фрагменту оригинала.
 *
 * Отправка идёт через 3-аргументный ClientConnection.send (слушатель сетей,
 * flush=true): метеор перехватывает только send(Packet, listener), иначе модуль
 * отменял бы собственные пакеты в onPacketSend.
 */
public class GrimVelocity extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> packetInterval = sgGeneral.add(new IntSetting.Builder()
        .name("интервал-пакетов")
        .description("Через сколько тиков отправлять пару PosRot + STOP_DESTROY_BLOCK (cooldown ccCooldown).")
        .defaultValue(2)
        .min(1)
        .sliderMax(20)
        .build());

    private int ccCooldown;
    private boolean flag;

    public GrimVelocity() {
        super(B2XY.CATEGORY, "grim-velocity", "Обход GrimAC: отменяет обычные пакеты движения и шлёт пару PosRot + STOP_DESTROY_BLOCK.");
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket)) return;

        event.cancel();
        this.flag = true;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (this.ccCooldown > 0) {
            this.ccCooldown--;
        }

        if (this.flag) {
            this.handleNewGrimTick();
        }
    }

    private void handleNewGrimTick() {
        if (this.ccCooldown <= 0) {
            // 3-арг send: (пакет, слушатель, flush) — обходит перехват PacketEvent.Send,
            // иначе наш собственный PosRot был бы отменён в onPacketSend.
            mc.player.networkHandler.getConnection().send(new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                mc.player.getYaw(),
                mc.player.getPitch(),
                mc.player.isOnGround(),
                false
            ), null, true);

            mc.player.networkHandler.getConnection().send(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                Direction.DOWN
            ), null, true);

            this.ccCooldown = this.packetInterval.get();
        }

        this.flag = false;
    }
}