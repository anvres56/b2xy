package com.b2xy.util;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;

import java.util.Arrays;

/**
 * Менеджер серверного слота (порт из BepHax NEW-SRC): отслеживает реальный слот
 * на сервере, сед-брос спуф, грайм-детект по ping-транзакциям (id 0,-1,-2,-3).
 */
public class InventoryManager {
    private static InventoryManager INSTANCE;
    private int serverSlot = -1;
    private int lastSentSlot = -1;
    private boolean sendingPacket = false;
    private boolean isEating = false;
    private long lastSetbackTime = -1L;
    private final int[] transactions = new int[4];
    private int transactionIndex = 0;
    private boolean isGrim = false;
    private int currentPriority = 0;

    private InventoryManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
        Arrays.fill(this.transactions, -1);
    }

    public static InventoryManager getInstance() {
        if (INSTANCE == null) INSTANCE = new InventoryManager();
        return INSTANCE;
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!this.sendingPacket) {
            if (!event.isCancelled()) {
                if (event.packet instanceof UpdateSelectedSlotC2SPacket packet) {
                    int packetSlot = packet.getSelectedSlot();
                    if (!PlayerInventory.isValidHotbarIndex(packetSlot) || this.serverSlot == packetSlot) {
                        event.cancel();
                        return;
                    }

                    if (this.lastSentSlot == packetSlot) {
                        event.cancel();
                        this.setSlotForced(packetSlot);
                        return;
                    }

                    this.serverSlot = packetSlot;
                    this.lastSentSlot = packetSlot;
                }
            }
        }
    }

    @EventHandler(priority = 200)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof UpdateSelectedSlotS2CPacket packet) {
            this.serverSlot = packet.slot();
        } else if (event.packet instanceof CommonPingS2CPacket packet) {
            if (this.transactionIndex > 3) {
                return;
            }

            int uid = packet.getParameter();
            this.transactions[this.transactionIndex] = uid;
            this.transactionIndex++;
            if (this.transactionIndex == 4) {
                this.grimCheck();
            }
        } else if (event.packet instanceof PlayerPositionLookS2CPacket) {
            this.lastSetbackTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (MeteorClient.mc.player != null && this.serverSlot == -1) {
            this.serverSlot = MeteorClient.mc.player.getInventory().selectedSlot;
        }

        if (!this.isEating && this.currentPriority > 0) {
            this.currentPriority = 0;
        }
    }

    @EventHandler
    public void onDisconnect(GameLeftEvent event) {
        Arrays.fill(this.transactions, -1);
        this.transactionIndex = 0;
        this.isGrim = false;
        this.lastSetbackTime = -1L;
        this.serverSlot = -1;
        this.lastSentSlot = -1;
        this.currentPriority = 0;
        this.isEating = false;
    }

    private void grimCheck() {
        for (int i = 0; i < 4; i++) {
            if (this.transactions[i] != -i) {
                return;
            }
        }

        this.isGrim = true;
        B2XY.LOG.info("Server is running GrimAC.");
    }

    public boolean isGrim() {
        return this.isGrim;
    }

    public boolean hasPassed(long timeMS) {
        return this.lastSetbackTime != -1L && System.currentTimeMillis() - this.lastSetbackTime >= timeMS;
    }

    public void setSlot(int barSlot) {
        this.setSlot(barSlot, 0);
    }

    public void setSlot(int barSlot, boolean highPriority) {
        this.setSlot(barSlot, highPriority ? 20 : 0);
    }

    public void setSlot(int barSlot, int priority) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.getNetworkHandler() != null) {
            if (priority >= this.currentPriority) {
                if (this.serverSlot == -1) {
                    this.serverSlot = MeteorClient.mc.player.getInventory().selectedSlot;
                }

                if (this.serverSlot != barSlot && PlayerInventory.isValidHotbarIndex(barSlot)) {
                    this.setSlotForced(barSlot);
                    this.currentPriority = priority;
                }
            }
        }
    }

    public void setSlotForced(int barSlot) {
        if (MeteorClient.mc.getNetworkHandler() != null && PlayerInventory.isValidHotbarIndex(barSlot)) {
            if (this.serverSlot != barSlot) {
                this.sendingPacket = true;

                try {
                    if (this.lastSentSlot == barSlot) {
                        int bounce = (barSlot + 1) % 9;
                        MeteorClient.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(bounce));
                        this.lastSentSlot = bounce;
                    }

                    MeteorClient.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(barSlot));
                    this.lastSentSlot = barSlot;
                    this.serverSlot = barSlot;
                } finally {
                    this.sendingPacket = false;
                }
            }
        }
    }

    public void syncToClient() {
        if (MeteorClient.mc.player != null) {
            if (this.isDesynced()) {
                this.setSlotForced(MeteorClient.mc.player.getInventory().selectedSlot);
            }
        }
    }

    public boolean isDesynced() {
        return MeteorClient.mc.player != null
            && MeteorClient.mc.player.getInventory().selectedSlot != this.serverSlot;
    }

    public int getServerSlot() {
        if (MeteorClient.mc.player == null) {
            return -1;
        }
        return this.serverSlot == -1 ? MeteorClient.mc.player.getInventory().selectedSlot : this.serverSlot;
    }

    public int getLastSentSlot() {
        return this.lastSentSlot;
    }

    public void setEating(boolean eating) {
        this.isEating = eating;
        this.currentPriority = eating ? 10 : 0;
    }

    public boolean isEating() {
        return this.isEating;
    }

    public interface IPlayerInteractEntityC2SPacket {
        boolean isAttackPacket();

        int getTargetEntityId();
    }

    public static class Priority {
        public static final int NORMAL = 0;
        public static final int TOTEM = 5;
        public static final int EATING = 10;
        public static final int SURROUND = 20;
        public static final int PEARL_PHASE = 30;
    }

    public enum VelocityMode {
        NORMAL,
        WALLS,
        GRIM,
        GRIM_V3,
        GRIM_SKIP;
    }
}