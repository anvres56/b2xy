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
 * NoFall, режим {@code Vanilla} — порт из CatLean 0.1.2 (класс {@code su.catlean.u7},
 * перечисление режимов {@code su.catlean.lh}, ветка {@code Vanilla} = 4).
 *
 * <h2>Как это работает</h2>
 * Пока игрок падает и дистанция падения набрала порог, каждый тик уходит пакет
 * {@code PlayerMoveC2SPacket.Full} с Y, увеличенным на крошечную величину
 * ({@code 1.0E-9}, то есть на эпсилону), и с {@code onGround = false}. Сервер
 * получает позицию, которая на ничтожную долю выше поверхности, на которую игрок
 * сел, поэтому у него не накапливается дистанция падения и при приземлении не
 * срабатывает урон. Клиент при этом продолжает падать по-настоящему, на своей
 * настоящей высоте — визуально ничего не происходит.
 *
 * <p>Сразу после отправки пакета вызывается {@code player.onLanding()}: он обнуляет
 * <i>клиентскую</i> дистанцию падения, чтобы урон не применился на стороне клиента
 * (в 1.21.11 {@code Entity.fallDistance} — поле типа {@code double}, первая же
 * инструкция {@code onLanding()} — это {@code fallDistance = 0}). Побочные эффекты
 * {@code onLanding()} в этом случае не проявляются: звука в нём нет, а изменение
 * скорости есть только для «липких» блоков, которых в воздухе нет.
 *
 * <p>Дополнительно при приземлении вызывается {@code player.jump()} — в оригинале это
 * сделано во внутреннем автомате состояний (см. ниже).
 *
 * <h2>Что восстановлено из байткода, а что нет</h2>
 * Тело метода {@code M(PlayerUpdateEvent)} помечено обфускацией потока управления
 * (Flow): условия ветвления зашифрованы в длинные константы и через таблицу
 * {@code tableswitch}. Поэтому сами условия прочитать нельзя, но восстановимы
 * следующие вещи, проверенные по байткоду напрямую:
 * <ul>
 *   <li>диспетчер режимов — {@code tableswitch 1..6} по массиву {@code su.catlean.ia.W},
 *       где {@code Rubberband=1, Items=2, MatrixOffGround=3, Vanilla=4, GRIM_NEW=5};</li>
 *   <li>ветка {@code Vanilla} (и {@code MatrixOffGround}, и {@code GRIM_NEW}) сливается
 *       в общий хвост на смещении 1603 — то есть у этих трёх режимов одна и та же механика;</li>
 *   <li>общий хвост: {@code new PlayerMoveC2SPacket.Full(x, y + 1.0E-9, z, yaw, pitch,
 *       false, player.horizontalCollision)}, отправка, затем {@code player.onLanding()};</li>
 *   <li>проверка-ворота {@code a(J)}: {@code player != null && !player.isGliding() &&
 *       !player.getAbilities().flying}, плюс сравнение дистанции падения с настройкой;</li>
 *   <li>хелпер {@code K(J)} — «отпустить клавиши»: {@code player.setSprinting(false)} и
 *       {@code setPressed(false)} для forward/back/left/right/jump (нужно, чтобы во время
 *       падения игрок не «долетел» и чтобы приземление не превратилось в бег);</li>
 *   <li>автомат состояний на статическом поле {@code u}: состояние 2 вызывает
 *       {@code player.jump()}, состояние 3 сбрасывает флаг {@code j = false}. Номера
 *       состояний хранятся в зашифрованных константах, поэтому в какой именно момент
 *       вызывается прыжок — прочитать не удалось; здесь он выполняется в момент
 *       приземления после падения (это настраивается);</li>
 *   <li>отдельный обработчик отправки пакетов {@code L(SendPacket)} — это как раз
 *       хеллер отпускания клавиш, пакетов он не подменяет.</li>
 * </ul>
 * <p>Числовые значения настроек оригинала зашифрованы, поэтому порог дистанции падения
 * и величина сдвига вынесены в настройки со значениями по умолчанию 3.0 (три блока — с
 * них в Minecraft начинается урон от падения) и 1.0E-9 (как в оригинале).
 *
 * <h2>Замечания по API 1.21.11</h2>
 * Сигнатуры сверены по Yarn 1.21.11: {@code PlayerMoveC2SPacket.Full} принимает семь
 * аргументов {@code (double x, double y, double z, float yaw, float pitch, boolean
 * onGround, boolean horizontalCollision)}, {@code KeyBinding.setPressed(boolean)}
 * существует, {@code Entity.onLanding()} публичный и без аргументов. Отправка идёт
 * через 3-аргументный {@code ClientConnection.send}, потому что обычный {@code sendPacket}
 * перехватывается метеором ({@code PacketEvent.Send}) и наш пакет отменился бы сам собой.
 */
public class NofallVanilla extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> fallDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("порог-падения")
        .description("С какой дистанции падения начинать сброс. В Minecraft урон начинается "
            + "с трёх блоков, поэтому и в оригинале сравнение идёт с этим порогом.")
        .defaultValue(3.0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Double> offset = sgGeneral.add(new DoubleSetting.Builder()
        .name("сдвиг")
        .description("На сколько блоков вверх сдвигать Y в пакете. В оригинале ровно 1.0E-9 — "
            + "этого хватает, чтобы сервер не засчитал приземление, но любой заметный сдвиг "
            + "будет выдавать твою настоящую высоту.")
        .defaultValue(1.0E-9)
        .min(0.0)
        .sliderMax(0.01)
        .build()
    );

    private final Setting<Boolean> releaseKeys = sgGeneral.add(new BoolSetting.Builder()
        .name("отпускать-клавиши")
        .description("Хелпер оригинала: снять спринт и отпустить WASD/прыжок на время падения "
            + "(иначе игрок «долетает» и приземление превращается в бег).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> jumpOnLanding = sgGeneral.add(new BoolSetting.Builder()
        .name("прыгать-при-приземлении")
        .description("Вызвать прыжок в момент приземления (в оригинале это состояние "
            + "автомата, который вызывает player.jump()).")
        .defaultValue(true)
        .build()
    );

    /** Падали ли мы в прошлом тике — чтобы поймать сам момент приземления. */
    private boolean wasFalling;
    /** Прыгнули ли уже на этом приземлении, чтобы не прыгать каждый тик. */
    private boolean jumped;

    public NofallVanilla() {
        super(B2XY.CATEGORY, "nofall-vanilla",
            "Сбрасывает серверную дистанцию падения (CatLean NoFall, режим Vanilla).",
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

        // Ворота из оригинала (метод a(J)): игрок есть, не на элитре и не в режиме полёта.
        if (this.mc.player.isOnGround() || this.mc.player.isGliding() || this.mc.player.getAbilities().flying) {
            // Только что приземлились — поймаем момент прыжка.
            if (wasFalling && !jumped && jumpOnLanding.get()) {
                jumped = true;
                this.mc.player.jump();
            }
            wasFalling = false;
            return;
        }

        if (this.mc.player.fallDistance < this.fallDistance.get()) return;

        wasFalling = true;
        jumped = false;

        if (releaseKeys.get()) releaseKeys();

        // 3-аргументный send: обычный sendPacket перехватывается метеором
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

        // Обнуление клиентской дистанции падения — в оригинале так же, вызовом onLanding().
        this.mc.player.onLanding();
    }

    /** Хелпер K(J) из оригинала: снять спринт и отпустить клавиши движения. */
    private void releaseKeys() {
        this.mc.player.setSprinting(false);
        this.mc.options.forwardKey.setPressed(false);
        this.mc.options.backKey.setPressed(false);
        this.mc.options.leftKey.setPressed(false);
        this.mc.options.rightKey.setPressed(false);
        this.mc.options.jumpKey.setPressed(false);
    }
}
