package com.b2xy.modules;

import com.b2xy.B2XY;
import com.b2xy.accessor.InputAccessor;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

import java.util.ArrayList;
import java.util.List;

/**
 * GuiMove — порт {@code antileak.base.client.modules.impl.movement.GuiMove}
 * (Punch, Mojmap) на Meteor/Yarn 1.21.11.
 *
 * Две части:
 * <ul>
 *   <li>пока открыт инвентарь, состояния клавиш движения синхронизируются с
 *       реальными (GLFW) — игрок продолжает идти/прыгать/спринтовать;</li>
 *   <li>режим {@code Задержка} ({@code SpookyTime}): клики по инвентарю и пакет
 *       закрытия окна, пока игрок двигается, не отправляются сразу — они
 *       копятся и уходят через N тиков после закрытия окна. Ввод при этом
 *       блокируется (нулевой {@code PlayerInputC2SPacket}), чтобы сервер не
 *       увидел рассинхрон ввода и позиции.</li>
 * </ul>
 *
 * Отличия от Punch: три режима (SpookyTime/Bypass/Legit) сведены к двум —
 * различались они только длительностью тайм-стадий, в B2XY это настройка
 * {@code задержка-тиков}. Форс-вперёд/сброс спринта (методы {@code stopMovementTemporarily},
 * {@code getSyncSwapDelayMillis}) — это хуки для чужих модулей Punch, в Meteor
 * они не портированы. Клавиши читаются по {@code getDefaultKey()} — геттера
 * текущей привязки в 1.21.11 нет.
 */
public class GuiMove extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("режим")
        .description("Только: синхронизация клавиш. Задержка: плюс отложенная отправка кликов инвентаря после закрытия окна.")
        .defaultValue(Mode.Только)
        .build());

    private final Setting<Integer> delayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("задержка-тиков")
        .description("Сколько тиков ждать после закрытия окна перед отправкой накопленных пакетов.")
        .defaultValue(1)
        .min(1)
        .max(5)
        .sliderMin(1)
        .sliderMax(5)
        .visible(() -> this.mode.get() == Mode.Задержка)
        .build());

    private final Setting<Boolean> anyScreen = sgGeneral.add(new BoolSetting.Builder()
        .name("любые-окна")
        .description("Синхронизировать клавиши во всех окнах, а не только в инвентаре (чат и пауза исключены всегда).")
        .defaultValue(false)
        .build());

    private final List<Packet<?>> packets = new ArrayList<>();
    private boolean movedInGui;
    private boolean processing;
    private int taskTick;
    private boolean wasSprinting;
    private Screen lastScreen;

    public GuiMove() {
        super(B2XY.CATEGORY, "gui-move", "Ходьба и спринт при открытом инвентаре.");
    }

    @Override
    public void onDeactivate() {
        cleanup();
    }

    /** Клики и закрытие окна задерживаем, только если игрок двигается в GUI. */
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (this.mc.player == null || this.mode.get() != Mode.Задержка) return;

        Screen screen = this.mc.currentScreen;
        if (screen == null || screen instanceof ChatScreen) return;

        boolean moving = this.movedInGui || hasMovement();
        this.movedInGui |= moving && !this.packets.isEmpty();

        if (event.packet instanceof ClickSlotC2SPacket && moving && screen instanceof InventoryScreen) {
            this.packets.add(event.packet);
            event.cancel();
        } else if (event.packet instanceof CloseHandledScreenC2SPacket) {
            if (moving && !this.processing) {
                if (this.packets.isEmpty()) event.cancel();
                else {
                    this.packets.add(event.packet);
                    event.cancel();
                    startProcessing();
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.world == null) {
            cleanup();
            return;
        }

        if (this.processing) {
            processPacketTask();
            this.lastScreen = this.mc.currentScreen;
            return;
        }

        Screen screen = this.mc.currentScreen;
        if (screen != null && !(screen instanceof ChatScreen) && isGuiMoveScreen(screen)) syncMovementKeys();
        else this.movedInGui = false;

        // Пакеты копились в GUI, окно закрылось — досылаем.
        if (this.mode.get() == Mode.Задержка && this.packets.isEmpty() && screen == null
            && this.lastScreen instanceof InventoryScreen) {
            this.movedInGui = false;
        }

        this.lastScreen = screen;
    }

    private boolean isGuiMoveScreen(Screen screen) {
        if (this.anyScreen.get()) return true;
        return screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen;
    }

    private void startProcessing() {
        if (this.packets.isEmpty()) return;

        this.processing = true;
        this.taskTick = 0;
        this.wasSprinting = this.mc.player.isSprinting();
        if (this.wasSprinting) this.mc.player.setSprinting(false);

        lockMovement();
    }

    private void processPacketTask() {
        this.taskTick++;

        if (this.taskTick < this.delayTicks.get()) {
            lockMovement();
            return;
        }

        List<Packet<?>> copy = new ArrayList<>(this.packets);
        this.packets.clear();
        for (Packet<?> packet : copy) send(packet);

        // Закрываем окно на сервере отдельным пакетом — иначе сервер ждёт его,
        // пока клиент уже не рисует экран.
        if (this.mc.player != null) send(new CloseHandledScreenC2SPacket(0));

        if (this.wasSprinting && this.mc.player != null) this.mc.player.setSprinting(true);
        this.wasSprinting = false;
        this.processing = false;
        this.movedInGui = false;
        cleanup();
    }

    private void lockMovement() {
        releaseMovementKeys();
        if (this.mc.player == null || this.mc.getNetworkHandler() == null) return;

        ((InputAccessor) this.mc.player.input).setMovementForward(0.0f);
        ((InputAccessor) this.mc.player.input).setMovementSideways(0.0f);
        this.mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(
            new PlayerInput(false, false, false, false, false, false, false)));
    }

    private void syncMovementKeys() {
        for (KeyBinding key : movementKeys()) key.setPressed(isPhysicallyPressed(key));
    }

    private void releaseMovementKeys() {
        for (KeyBinding key : movementKeys()) key.setPressed(false);
        if (this.mc.options != null) this.mc.options.sprintKey.setPressed(false);
    }

    private KeyBinding[] movementKeys() {
        return new KeyBinding[]{
            this.mc.options.forwardKey, this.mc.options.backKey,
            this.mc.options.leftKey, this.mc.options.rightKey,
            this.mc.options.jumpKey
        };
    }

    private boolean isPhysicallyPressed(KeyBinding key) {
        return this.mc.getWindow() != null
            && InputUtil.isKeyPressed(this.mc.getWindow(), key.getDefaultKey().getCode());
    }

    private boolean hasMovement() {
        if (this.mc.player == null) return false;
        Vec2f input = this.mc.player.input.getMovementInput();
        return input.x != 0.0f || input.y != 0.0f;
    }

    private void send(Packet<?> packet) {
        if (packet != null && this.mc.getNetworkHandler() != null) this.mc.getNetworkHandler().sendPacket(packet);
    }

    private void cleanup() {
        this.packets.clear();
        this.movedInGui = false;
        this.processing = false;
        this.taskTick = 0;
        this.wasSprinting = false;
        this.lastScreen = null;
    }

    public enum Mode {
        Только,
        Задержка
    }
}
