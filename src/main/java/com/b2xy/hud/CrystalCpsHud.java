package com.b2xy.hud;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;

public class CrystalCpsHud extends HudElement {
    public static final HudElementInfo<CrystalCpsHud> INFO = new HudElementInfo<>(
        B2XY.HUD_GROUP, "crystal-cps",
        "Скорость размещения кристаллов в секунду.",
        CrystalCpsHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("радиус")
        .description("Дистанция от игрока, на которой спавн кристалла учитывается.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> ownOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("только-свои")
        .description("Считать только кристаллы, размещённые вами.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> background = sgGeneral.add(new BoolSetting.Builder()
        .name("показывать-фон")
        .description("Рисовать подложку под текстом.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> bgColor = sgGeneral.add(new ColorSetting.Builder()
        .name("цвет-фона")
        .description("Цвет подложки.")
        .defaultValue(new SettingColor(0, 0, 0, 110))
        .visible(() -> background.get())
        .build()
    );

    private static final Deque<Long> SPAWN_TIMES = new ArrayDeque<>();
    private static final Listener LISTENER = new Listener();
    private static boolean subscribed;
    private static CrystalCpsHud active;
    private static BlockPos lastOwnPlace;
    private static long lastOwnPlaceTime;

    public CrystalCpsHud() {
        super(INFO);
        active = this;
        if (!subscribed) {
            subscribed = true;
            MeteorClient.EVENT_BUS.subscribe(LISTENER);
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        long now = System.currentTimeMillis();
        while (!SPAWN_TIMES.isEmpty() && now - SPAWN_TIMES.peekFirst() > 1000) SPAWN_TIMES.removeFirst();

        String text = "Кристалы: " + SPAWN_TIMES.size() + "/с";
        double w = renderer.textWidth(text, true);
        double h = renderer.textHeight(true);

        setSize(w, h);

        if (background.get()) {
            renderer.quad(x, y, w, h, bgColor.get());
        }
        renderer.text(text, x, y, Color.WHITE, true);
    }

    private static class Listener {
        @EventHandler
        private void onPacketReceive(PacketEvent.Receive event) {
            if (active == null || MeteorClient.mc.player == null || MeteorClient.mc.world == null) return;
            if (!(event.packet instanceof EntitySpawnS2CPacket packet)) return;
            if (packet.getEntityType() != EntityType.END_CRYSTAL) return;

            double dx = packet.getX() - MeteorClient.mc.player.getX();
            double dy = packet.getY() - MeteorClient.mc.player.getY();
            double dz = packet.getZ() - MeteorClient.mc.player.getZ();
            if (dx * dx + dy * dy + dz * dz > active.radius.get() * active.radius.get()) return;

            if (active.ownOnly.get()) {
                long now = System.currentTimeMillis();
                if (lastOwnPlace == null || now - lastOwnPlaceTime > 1000) return;

                double cx = lastOwnPlace.getX() + 0.5;
                double cy = lastOwnPlace.getY() + 1.0;
                double cz = lastOwnPlace.getZ() + 0.5;

                if (Math.hypot(packet.getX() - cx, packet.getZ() - cz) > 0.75) return;
                if (Math.abs(packet.getY() - cy) > 0.5) return;
            }

            SPAWN_TIMES.addLast(System.currentTimeMillis());
        }

        @EventHandler
        private void onInteractBlock(InteractBlockEvent event) {
            if (MeteorClient.mc.player == null) return;
            if (event.result instanceof BlockHitResult blockHitResult) {
                lastOwnPlace = blockHitResult.getBlockPos();
                lastOwnPlaceTime = System.currentTimeMillis();
            }
        }
    }
}