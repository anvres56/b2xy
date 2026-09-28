package com.b2xy.modules;

import com.b2xy.B2XY;
import com.b2xy.util.ViaProtocolUtil;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.Vec3d;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class GrimGlide extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgEnvelope;
    private final SettingGroup sgReport;
    private final SettingGroup sgTest;

    private final Setting<Mode> mode;
    private final Setting<Protocol> protocol;
    private final Setting<Double> safety;
    private final Setting<Double> climb;
    private final Setting<Boolean> steer;

    private final Setting<Double> forward;
    private final Setting<Boolean> randomize;
    private final Setting<Double> randomMin;
    private final Setting<Double> randomMax;
    private final Setting<Double> fall;
    private final Setting<Double> lift;
    private final Setting<Double> speedLimit;
    private final Setting<Boolean> teleport;

    private final Setting<Boolean> reportSetbacks;

    private static final double GRAVITY = 0.08;

    private int setbacks;
    private double bps;
    private Vec3d lastPos;

    public GrimGlide() {
        super(B2XY.CATEGORY, "grim-glide", "Зависание на элитрах с использованием неопределённости полёта самого Grim. Нужен протокол раньше 1.18.2, чтобы вообще был какой-то запас.");

        sgGeneral = settings.getDefaultGroup();
        sgEnvelope = settings.createGroup("Огибающая");
        sgReport = settings.createGroup("Отчёт");
        sgTest = settings.createGroup("Тест");

        mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Огибающая летает на запасе, который Grim даёт планеристам. Отчёт — оригинальный код из тикета, оставлен для сравнения.")
            .defaultValue(Mode.Envelope)
            .build()
        );
        protocol = sgEnvelope.add(new EnumSetting.Builder<Protocol>()
            .name("protocol")
            .description("Каким клиентом вас считает Grim — весь запас берётся отсюда. Авто читает цель ViaFabricPlus.")
            .defaultValue(Protocol.Auto)
            .visible(() -> mode.get() == Mode.Envelope)
            .build()
        );
        safety = sgEnvelope.add(new DoubleSetting.Builder()
            .name("safety")
            .description("Доля выданного бюджета, которую реально используем. Точное попадание на границу флагается при любом округлении или спайке лага.")
            .defaultValue(0.85)
            .range(0.1, 1.0)
            .sliderRange(0.5, 1.0)
            .visible(() -> mode.get() == Mode.Envelope)
            .build()
        );
        climb = sgEnvelope.add(new DoubleSetting.Builder()
            .name("climb")
            .description("Блоков за тик вертикального движения. 0 — ровное зависание. Потолок — вертикальный бюджет минус гравитация, модуль зажимается им.")
            .defaultValue(0.0)
            .sliderRange(-0.1, 0.1)
            .visible(() -> mode.get() == Mode.Envelope)
            .build()
        );
        steer = sgEnvelope.add(new BoolSetting.Builder()
            .name("steer")
            .description("Толкать вперёд, только пока зажата клавиша движения. Выключено — постоянно держит направление по yaw.")
            .defaultValue(true)
            .visible(() -> mode.get() == Mode.Envelope)
            .build()
        );
        forward = sgReport.add(new DoubleSetting.Builder()
            .name("forward")
            .description("Горизонтальная скорость, записываемая каждый тик, по yaw. 0.087 в отчёте.")
            .defaultValue(0.087)
            .min(0.0)
            .sliderRange(0.0, 1.0)
            .visible(() -> mode.get() == Mode.Report)
            .build()
        );
        randomize = sgReport.add(new BoolSetting.Builder()
            .name("randomize")
            .description("Масштабирует записанную скорость на случайный множитель при каждой записи, как в отчёте.")
            .defaultValue(true)
            .visible(() -> mode.get() == Mode.Report)
            .build()
        );
        randomMin = sgReport.add(new DoubleSetting.Builder()
            .name("random-min")
            .description("Нижняя граница случайного множителя.")
            .defaultValue(1.1)
            .min(0.0)
            .sliderRange(0.5, 2.0)
            .visible(() -> mode.get() == Mode.Report && randomize.get())
            .build()
        );
        randomMax = sgReport.add(new DoubleSetting.Builder()
            .name("random-max")
            .description("Верхняя граница случайного множителя.")
            .defaultValue(1.21)
            .min(0.0)
            .sliderRange(0.5, 2.0)
            .visible(() -> mode.get() == Mode.Report && randomize.get())
            .build()
        );
        fall = sgReport.add(new DoubleSetting.Builder()
            .name("fall")
            .description("Вычитается из y при первой записи скорости.")
            .defaultValue(0.02)
            .sliderRange(0.0, 0.1)
            .visible(() -> mode.get() == Mode.Report)
            .build()
        );
        lift = sgReport.add(new DoubleSetting.Builder()
            .name("lift")
            .description("Возвращается в y при второй записи скорости. Чистый вертикальный дрейф — lift минус fall, проигрывает гравитации.")
            .defaultValue(0.016)
            .sliderRange(0.0, 0.1)
            .visible(() -> mode.get() == Mode.Report)
            .build()
        );
        speedLimit = sgReport.add(new DoubleSetting.Builder()
            .name("speed-limit")
            .description("Блоков в секунду, при которых толчок вперёд сбрасывается в ноль. 48 (оверворлд) / 52 в отчёте.")
            .defaultValue(48.0)
            .min(0.0)
            .sliderRange(0.0, 120.0)
            .visible(() -> mode.get() == Mode.Report)
            .build()
        );
        teleport = sgReport.add(new BoolSetting.Builder()
            .name("teleport")
            .description("Также сдвигает клиентскую позицию вперёд, поверх записи скорости. Таймер отчёта 50мс — это один тик.")
            .defaultValue(true)
            .visible(() -> mode.get() == Mode.Report)
            .build()
        );
        reportSetbacks = sgTest.add(new BoolSetting.Builder()
            .name("report-setbacks")
            .description("Логирует в чат каждый телепорт сервера, пока активен — откат это то, как флаг Grim выглядит на клиенте.")
            .defaultValue(true)
            .build()
        );
    }

    @Override
    public void onActivate() {
        setbacks = 0;
        bps = 0.0;
        lastPos = mc.player == null ? null : mc.player.getPos();
        if (mode.get() == Mode.Envelope) {
            reportEnvelope();
        }
    }

    private void reportEnvelope() {
        int detected = ViaProtocolUtil.targetProtocol();
        if (protocol.get() == Protocol.Auto) {
            if (!ViaProtocolUtil.isPresent()) {
                warning("ViaFabricPlus не загружен — считается нативный протокол, запаса почти нет.");
            } else if (detected == -1) {
                warning("Не удалось прочитать цель ViaFabricPlus (авто-определение?) — считается нативный. Задайте протокол вручную.");
            } else {
                info("Цель Via %s (протокол %d).", ViaProtocolUtil.targetName(), detected);
            }
        } else if (detected != -1 && isLegacy() != ViaProtocolUtil.isLegacyBand(detected)) {
            warning("Протокол принудительно %s, но Via на %s — бюджет будет неверным.", protocol.get(), ViaProtocolUtil.targetName());
        }
        if (!isLegacy()) {
            warning("Протокол 1.18.2+: порог Grim равен 0.0002, так что бюджет %.4f б/с — ниже его собственной линии флага 0.001. Переключите цель на 1.9-1.18.1.", horizontalBudget() * 20.0);
        } else {
            info("Огибающая активна: %.2f б/с по горизонтали, %.3f блоков/тик по вертикали.", horizontalBudget() * 20.0, verticalBudget());
        }
    }

    private boolean isLegacy() {
        if (protocol.get() == Protocol.Legacy) {
            return true;
        }
        if (protocol.get() == Protocol.Modern) {
            return false;
        }
        return ViaProtocolUtil.isLegacyBand(ViaProtocolUtil.targetProtocol());
    }

    private double threshold() {
        return isLegacy() ? 0.03 : 2.0E-4;
    }

    private double horizontalBudget() {
        double t = threshold();
        return (0.99 * (t * 2.0) + t) * safety.get();
    }

    private double verticalBudget() {
        return threshold() * 2.0 * safety.get();
    }

    private double gravity() {
        double cos = Math.cos(Math.toRadians(mc.player.getPitch()));
        double vertCosRotation = cos * cos;
        return 0.08 * (-1.0 + vertCosRotation * 0.75);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (lastPos != null) {
            Vec3d delta = mc.player.getPos().subtract(lastPos);
            bps = Math.sqrt(delta.x * delta.x + delta.z * delta.z) * 20.0;
        }
        lastPos = mc.player.getPos();
        if (!mc.player.isGliding()) {
            return;
        }
        if (mode.get() == Mode.Report) {
            tickReport();
        } else {
            mc.player.setVelocity(envelope());
        }
    }

    @EventHandler(priority = 100)
    private void onPlayerMove(PlayerMoveEvent event) {
        if (mode.get() != Mode.Envelope || mc.player == null || mc.world == null) {
            return;
        }
        if (event.type != MovementType.SELF || !mc.player.isGliding()) {
            return;
        }
        Vec3d target = envelope();
        ((IVec3d) event.movement).meteor$set(target.x, target.y, target.z);
    }

    private Vec3d envelope() {
        double h = horizontalBudget();
        double v = verticalBudget();
        double gravity = gravity();
        double y = Math.max(gravity - v, Math.min(gravity + v, climb.get()));
        if (steer.get() && !isMoveKeyDown()) {
            return new Vec3d(0.0, y, 0.0);
        }
        double yawRad = Math.toRadians(mc.player.getYaw());
        return new Vec3d(-Math.sin(yawRad) * h, y, Math.cos(yawRad) * h);
    }

    private boolean isMoveKeyDown() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }

    private void tickReport() {
        double step = bps >= speedLimit.get() ? 0.0 : forward.get();
        double yawRad = Math.toRadians(mc.player.getYaw());
        double dx = -Math.sin(yawRad) * step;
        double dz = Math.cos(yawRad) * step;
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(dx * factor(), velocity.y - fall.get(), dz * factor());
        if (teleport.get()) {
            mc.player.setPosition(mc.player.getX() + dx, mc.player.getY(), mc.player.getZ() + dz);
        }
        mc.player.setVelocity(dx * factor(), mc.player.getVelocity().y + lift.get(), dz * factor());
    }

    @EventHandler(priority = -200)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!reportSetbacks.get() || mc.player == null) {
            return;
        }
        Packet<?> packet = event.packet;
        if (!(packet instanceof PlayerPositionLookS2CPacket positionLook)) {
            return;
        }
        PlayerPosition changePacket = positionLook.change();
        Vec3d change = changePacket.position();
        Vec3d current = mc.player.getPos();
        Set<PositionFlag> relatives = positionLook.relatives();
        Vec3d target = new Vec3d(
            relatives.contains(PositionFlag.X) ? current.x + change.x : change.x,
            relatives.contains(PositionFlag.Y) ? current.y + change.y : change.y,
            relatives.contains(PositionFlag.Z) ? current.z + change.z : change.z
        );
        warning("Откат #%d — оттянуто на %.2f блоков.", ++setbacks, target.distanceTo(current));
    }

    private double factor() {
        if (!randomize.get()) {
            return 1.0;
        }
        double min = Math.min(randomMin.get(), randomMax.get());
        double max = Math.max(randomMin.get(), randomMax.get());
        return min == max ? min : ThreadLocalRandom.current().nextDouble(min, max);
    }

    @Override
    public String getInfoString() {
        if (mode.get() == Mode.Report) {
            return String.format("report | %.1f б/с | %d откатов", bps, setbacks);
        }
        return String.format("%.2f б/с предел | %d откатов", horizontalBudget() * 20.0, setbacks);
    }

    public enum Mode {
        Envelope("Огибающая"),
        Report("Отчёт");

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum Protocol {
        Auto("Авто"),
        Legacy("Легаси"),
        Modern("Современный");

        private final String title;

        Protocol(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}