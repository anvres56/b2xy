package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * NoFall — не получает урон от падения.
 *
 * <h2>Идея</h2>
 * Урон считается по <i>серверной</i> дистанции падения: игрок накапливает её, пока
 * падает, и при касании земли из неё выводится количество урона. Значит, задача —
 * не дать серверу накопить дистанцию, а не «отменить урон» после приземления.
 *
 * <p>Приём: пока игрок падает и дистанция уже перевалила порог, каждый тик
 * отправляется пакет позиции, в котором Y увеличен на крошечную величину
 * (по умолчанию 1.0E-9) и стоит {@code onGround = false}. Сервер получает
 * координату, которая на ничтожную долю выше той поверхности, на которую игрок
 * садится, и не считает, что приземление состоялось: дистанция падения у него
 * не растёт, и при касании земли урона нет. Клиент при этом продолжает падать по
 * настоящей высоте — визуально ничего не происходит.
 *
 * <p>Свою дистанцию падения клиент обнуляет сам, иначе урон применился бы на
 * стороне клиента. Это делается прямой записью в {@code fallDistance}, а не
 * вызовом {@code onLanding()}: у того есть побочные эффекты (частицы и правка
 * скорости на «липких» блоках), которые в воздухе не нужны.
 *
 * <h2>Почему не «прыжок при приземлении»</h2>
 * Прыжок в момент касания земли тоже обнуляет серверную дистанцию, но только
 * один раз и только в момент приземления: при падении с большой высоты сервер
 * успевает накопить дистанцию и урон всё равно начисляет. Приём с пакетом
 * работает начиная с любой высоты, поэтому прыжок тут только дописка.
 *
 * <h2>Замечания по API 1.21.11</h2>
 * <ul>
 *   <li>у {@code Entity.fallDistance} публичное поле типа {@code double};</li>
 *   <li>{@code PlayerMoveC2SPacket.Full} принимает семь аргументов:
 *       {@code (double x, double y, double z, float yaw, float pitch, boolean
 *       onGround, boolean horizontalCollision)};</li>
 *   <li>пакет отправляется трёхаргументным {@code ClientConnection.send} — обычный
 *       {@code sendPacket} перехватывается метеором ({@code PacketEvent.Send}) и
 *       наш пакет отменился бы сам собой.</li>
 * </ul>
 */
public class NofallVanilla extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> fallDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("порог-падения")
        .description("С какой дистанции падения начинать сброс. В Minecraft урон начинается "
            + "с трёх блоков, поэтому и дефолт — три.")
        .defaultValue(3.0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Double> offset = sgGeneral.add(new DoubleSetting.Builder()
        .name("сдвиг")
        .description("На сколько блоков вверх сдвигать Y в пакете. Нужен ровно настолько, "
            + "чтобы сервер не засчитал приземление: слишком большой сдвиг выдаст "
            + "настоящую высоту игрока.")
        .defaultValue(1.0E-9)
        .min(0.0)
        .sliderMax(0.01)
        .build()
    );

    private final Setting<Boolean> onlyFalling = sgGeneral.add(new BoolSetting.Builder()
        .name("только-при-падении")
        .description("Сбрасывать дистанцию только когда игрок реально падает (vy < 0). "
            + "Выключи — сбрасывать всегда, пока игрок в воздухе.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> resetLocal = sgGeneral.add(new BoolSetting.Builder()
        .name("сбрасывать-локально")
        .description("Обнулять собственную дистанцию падения клиента, чтобы урон не "
            + "применился на стороне клиента. Выключи, если нужен честный урон на "
            + "высоте меньше порога — тогда сброс будет виден как «зависание» урона.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> jumpOnLanding = sgGeneral.add(new BoolSetting.Builder()
        .name("прыгать-при-приземлении")
        .description("Дополнительно прыгать в момент касания земли: прыжок тоже обнуляет "
            + "серверную дистанцию. Не нужно для основного приёма, но иногда помогает "
            + "догнать сервер, если он успел что-то засчитать.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> releaseKeys = sgGeneral.add(new BoolSetting.Builder()
        .name("отпускать-клавиши")
        .description("На время падения снимать спринт и отпускать клавиши движения, чтобы "
            + "игрок не «долетел» на высокой скорости. Учти: это отнимает контроль "
            + "над воздушным движением, в том числе на элитре.")
        .defaultValue(false)
        .build()
    );

    /** Падали ли мы в прошлом тике — чтобы поймать сам момент приземления. */
    private boolean wasFalling;
    /** Прыгнули ли уже на этом приземлении, чтобы не прыгать каждый тик. */
    private boolean jumped;

    public NofallVanilla() {
        super(B2XY.CATEGORY, "nofall-vanilla",
            "Сбрасывает серверную дистанцию падения: урона от падения не будет.",
            "nofall", "vanilla-nofall");
    }

    @Override
    public void onActivate() {
        wasFalling = false;
        jumped = false;
    }

    @Override
    public void onDeactivate() {
        wasFalling = false;
        jumped = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.world == null) {
            wasFalling = false;
            jumped = false;
            return;
        }

        if (this.mc.player.isOnGround()) {
            if (wasFalling && !jumped && jumpOnLanding.get()) {
                jumped = true;
                this.mc.player.jump();
            }
            wasFalling = false;
            return;
        }

        // На элитре и в режиме полёта сбрасывать нечего: там урон не считается.
        if (this.mc.player.isGliding() || this.mc.player.getAbilities().flying) {
            wasFalling = false;
            jumped = false;
            return;
        }

        if (onlyFalling.get() && this.mc.player.getVelocity().y >= 0) return;
        if (this.mc.player.fallDistance < this.fallDistance.get()) return;

        wasFalling = true;
        jumped = false;

        if (releaseKeys.get()) releaseKeys();

        // Трёхаргументный send: обычный sendPacket перехватывается метеором
        // (PacketEvent.Send), и наш пакет отменился бы сам собой.
        this.mc.player.networkHandler.getConnection().send(new PlayerMoveC2SPacket.Full(
            this.mc.player.getX(),
            this.mc.player.getY() + this.offset.get(),
            this.mc.player.getZ(),
            this.mc.player.getYaw(),
            this.mc.player.getPitch(),
            false,
            this.mc.player.horizontalCollision
        ), null, true);

        if (resetLocal.get()) this.mc.player.fallDistance = 0.0;
    }

    private void releaseKeys() {
        this.mc.player.setSprinting(false);
        this.mc.options.forwardKey.setPressed(false);
        this.mc.options.backKey.setPressed(false);
        this.mc.options.leftKey.setPressed(false);
        this.mc.options.rightKey.setPressed(false);
        this.mc.options.jumpKey.setPressed(false);
    }
}
