package com.b2xy.modules;

import com.b2xy.B2XY;
import com.b2xy.managers.SwapManager;
import com.b2xy.util.GrimUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * GrimAirPlace — порт {@code bep.hax.modules.GrimAirPlace} на 1.21.11.
 *
 * Ставит блок туда, куда смотрит прицел, без реальной грани: пакетная связка
 * START_DESTROY_BLOCK(0,0,0, DOWN) → PlayerInteractBlock → HandSwing →
 * START_DESTROY_BLOCK(0,0,0, DOWN) плюс сэмпл-блокстат, который Grim
 * воспринимает как «воздух» в точке установки. Позиция луча берётся из
 * {@code Entity#raycast} по дистанции взаимодействия (или своей, если включено).
 *
 * Отличия от оригинала: {@code Action.SWAP_ITEM_WITH_OFFHAND} из
 * декомпиляции заменён на {@code START_DESTROY_BLOCK} — поле
 * {@code field_12969} в 1.21.11 указывает на другой action, а по смыслу
 * (обход блок-чека на air place) нужен именно START_DESTROY_BLOCK; проверка
 * «можно ли ставить» в оригинале — {@code state.isReplaceable()}, она и оставлена.
 */
public class GrimAirPlace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRange = settings.createGroup("Дистанция");

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("задержка")
        .description("Задержка в тиках между установками блока.")
        .defaultValue(1)
        .min(0)
        .sliderMax(5)
        .build());

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("отображение")
        .description("Рисовать рамку блока в точке установки.")
        .defaultValue(true)
        .build());

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("режим-отображения")
        .description("Как рисуется рамка.")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("цвет-граней")
        .description("Цвет заливки рамки.")
        .defaultValue(new SettingColor(204, 0, 0, 10))
        .build());

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("цвет-линий")
        .description("Цвет обводки рамки.")
        .defaultValue(new SettingColor(12, 0, 204, 255))
        .build());

    private final Setting<Boolean> customRange = sgRange.add(new BoolSetting.Builder()
        .name("своя-дистанция")
        .description("Использовать свою дистанцию луча вместо дистанции взаимодействия.")
        .defaultValue(false)
        .build());

    private final Setting<Double> range = sgRange.add(new DoubleSetting.Builder()
        .name("дистанция")
        .description("Своя дистанция установки.")
        .defaultValue(5.0)
        .min(0.0)
        .max(5.5)
        .sliderMin(0.0)
        .sliderMax(5.5)
        .visible(() -> this.customRange.get())
        .build());

    private HitResult hitResult;
    private int delay;
    private boolean wasPressed;

    public GrimAirPlace() {
        super(B2XY.CATEGORY, "grim-air-place", "Ставит блок туда, куда смотрит прицел (air place для Grim).");
    }

    @Override
    public void onActivate() {
        this.delay = 0;
        this.wasPressed = false;
    }

    @Override
    public void onDeactivate() {
        this.hitResult = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player == null) return;

        if (this.delay < this.placeDelay.get()) this.delay++;

        double distance = this.customRange.get() ? this.range.get() : this.mc.player.getBlockInteractionRange();
        this.hitResult = this.mc.player.raycast(distance, 0.0f, false);

        if (!(this.hitResult instanceof BlockHitResult blockHit) || !placeable()) {
            this.wasPressed = false;
            return;
        }

        boolean isPressed = this.mc.options.useKey.isPressed();
        if (this.mc.currentScreen != null) {
            this.wasPressed = isPressed;
            return;
        }

        if (isPressed && !this.wasPressed && this.delay >= this.placeDelay.get()) {
            BlockPos targetPos = blockHit.getBlockPos();
            if (!this.mc.world.getBlockState(targetPos).isReplaceable()) {
                this.wasPressed = isPressed;
                return;
            }

            if (!SwapManager.getInstance().hold(this, this.mc.player.getInventory().selectedSlot, SwapManager.Priority.PLACE, 2)) return;

            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, new BlockPos(0, 0, 0), Direction.DOWN));
            this.mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
                Hand.MAIN_HAND, blockHit, GrimUtils.nextSequence()));
            this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, new BlockPos(0, 0, 0), Direction.DOWN));

            this.delay = 0;
        }

        this.wasPressed = isPressed;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!(this.hitResult instanceof BlockHitResult blockHit)) return;
        if (!this.render.get() || !placeable()) return;
        if (!this.mc.world.getBlockState(blockHit.getBlockPos()).isReplaceable()) return;

        event.renderer.box(blockHit.getBlockPos(), this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
    }

    /** В руке должен быть блок (или спавн-яйцо — как в оригинале). */
    private boolean placeable() {
        if (this.mc.player == null) return false;
        Item item = this.mc.player.getMainHandStack().getItem();
        return item instanceof BlockItem || item instanceof SpawnEggItem;
    }
}
