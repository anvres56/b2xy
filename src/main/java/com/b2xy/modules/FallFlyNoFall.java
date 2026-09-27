package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

/**
 * NoFall через «фейковый нырок» — порт Kotlin-модуля NoFall (fall-flying вариант).
 *
 * Идея оригинала: в момент приземления после падения отправить либо прыжок, либо
 * START_FALL_FLYING, чтобы сервер не засчитал урон. Состояние держится на счётчике
 * тиков падения (fallCounter) и флаге «нужно прыгнуть на следующем вводе».
 *
 * Отличие от оригинала (важно): в протоколе 1.21.11 пакета ServerboundPlayerCommandPacket
 * (START_FALL_FLYING) больше нет — он удалён из ванильного протокола целиком, доступен
 * только PlayerInputC2SPacket. Поэтому обе ветки оригинала тут сведены к отправке
 * прыжка (jump), который сервер так же засчитывает и сбрасывает дистанцию падения.
 *
 * Название модуля: Meteor уже занимает ID «no-fall», поэтому используется
 * уникальный «fall-fly-nofall» (иначе Modules.add() удалил бы чужой модуль).
 */
public class FallFlyNoFall extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> minFallTicks = sgGeneral.add(new IntSetting.Builder()
        .name("мин-тиков-падения")
        .description("Сколько тиков нужно падать, чтобы сработать (fallCounter > N в оригинале). "
            + "0 — сработать при первом же тике в воздухе.")
        .defaultValue(0)
        .min(0)
        .max(20)
        .build()
    );

    private final Setting<Boolean> onlyWhenLanding = sgGeneral.add(new BoolSetting.Builder()
        .name("только-при-приземлении")
        .description("Сбрасывать дистанцию только при касании земли (verticalCollision). "
            + "Выключи — сбрасывать сразу, как только набралось нужное число тиков падения.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableAfterUse = sgGeneral.add(new BoolSetting.Builder()
        .name("выключать-после-работы")
        .description("Выключать модуль после срабатывания (как в оригинале: setEnabled(false)).")
        .defaultValue(true)
        .build()
    );

    /** Счётчик тиков падения (fallCounter в оригинале). */
    private int fallCounter;
    /** Взвести прыжок на следующий ввод (shouldJump в оригинале). */
    private boolean shouldJump;
    private boolean jumpForced;

    public FallFlyNoFall() {
        super(B2XY.CATEGORY, "fall-fly-nofall", "Сбрасывает серверную дистанцию падения прыжком в момент приземления (порт NoFall).");
    }

    @Override
    public void onActivate() {
        fallCounter = 0;
        shouldJump = false;
        releaseJump();
    }

    @Override
    public void onDeactivate() {
        fallCounter = 0;
        shouldJump = false;
        releaseJump();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.world == null) {
            fallCounter = 0;
            shouldJump = false;
            return;
        }

        // Прыжок живёт ровно один тик: взводим перед отправкой пакетов, снимаем после.
        releaseJump();

        if (shouldJump) {
            shouldJump = false;
            fallCounter = 0;
            forceJump();
            if (disableAfterUse.get()) toggle();
            return;
        }

        Vec3d motion = this.mc.player.getVelocity();

        boolean onGround = this.mc.player.isOnGround();
        boolean verticalCollision = this.mc.player.verticalCollision;
        boolean moving = motion.x != 0.0 || motion.z != 0.0;

        if (!onGround) {
            // Падаем: копим тики падения (fallCounter).
            if (motion.y < 0) fallCounter++;
            return;
        }

        if (fallCounter <= minFallTicks.get()) {
            fallCounter = 0;
            return;
        }

        // Приземлились.
        if (onlyWhenLanding.get() && !verticalCollision) return;

        // Обе ветки оригинала (прыжок / START_FALL_FLYING) сведены к прыжку:
        // START_FALL_FLYING в 1.21.11 отсутствует, а прыжок сервер засчитывает так же.
        // Разница с оригиналом по скорости: при наличии горизонтального движения
        // оригинал слал elytra-start, здесь всё равно шлём прыжок, но не гасим импульс.
        shouldJump = true;
    }

    private void forceJump() {
        this.mc.options.jumpKey.setPressed(true);
        jumpForced = true;
    }

    private void releaseJump() {
        if (jumpForced) {
            jumpForced = false;
            this.mc.options.jumpKey.setPressed(false);
        }
    }
}
