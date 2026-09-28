package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CobwebBlock;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;

/**
 * NoWeb — порт {@code cc.leonware.client.module.modules.movement.NoWebModule}
 * (Mojmap) на Meteor/Yarn 1.21.11.
 *
 * Три режима:
 * <ul>
 *   <li>{@code Фиксированный} — в паутине каждый тик обнуляется горизонтальная
 *       скорость, вертикальная ровно {@link #VERTICAL_SPEED} (вверх/вниз по
 *       jump/sneak), горизонтальная {@link #HORIZONTAL_SPEED} по направлению
 *       ввода. Скорости заданы константой и не вынесены в настройки;</li>
 *   <li>{@code Кастомный} — оставлен ради совместимости со старыми конфигами,
 *       работает как {@code Фиксированный};</li>
 *   <li>{@code Игнор} — миксин в {@code CobwebBlock#onEntityCollision} вообще
 *       отменяет замедление паутины (режим {@code Grim} дополнительно шлёт
 *       {@code STOP_DESTROY_BLOCK} с инкрементом sequence, чтобы сервер тоже
 *       считал блок снятым).</li>
 * </ul>
 *
 * Отличия от оригинала: {@code SelectModeSetting} -> {@code EnumSetting},
 * {@code BooleanSetting} -> {@code BoolSetting}, {@code SliderSetting} ->
 * {@code DoubleSetting}, {@code EventListener}/{@code TickEvent} ->
 * {@code @EventHandler}/{@code TickEvent.Pre}, {@code Mc.player()} ->
 * {@code mc.player}. {@code ClientWorld#getPendingUpdateManager} в 1.21.11
 * package-private, доступ открыт через access widener
 * {@code b2xy.accesswidener}. Ввод 1.21.11: {@code Input#movementVector}
 * ({@code Vec2f}, x — боковое, y — вперёд) вместо
 * {@code movementForward}/{@code movementSideways}.
 */
public class NoWeb extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /**
     * Скорости подобраны замером и намеренно не вынесены в настройки:
     * старое значение 0.19175/0.995 подхватывалось из modules.nbt и ломало
     * разгон в паутине. Числа заданы константой — поменять можно только кодом.
     */
    private static final double HORIZONTAL_SPEED = 0.64045;
    private static final double VERTICAL_SPEED = 1.27927;

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("режим")
        .description("Фиксированный: скорости заданы константой (горизонталь " + HORIZONTAL_SPEED + ", вертикаль " + VERTICAL_SPEED + "). Кастомный: работает так же, настройки убраны. Игнор: полностью отменить замедление в паутине миксином.")
        .defaultValue(Mode.Фиксированный)
        .build());

    private final Setting<Boolean> grim = sgGeneral.add(new BoolSetting.Builder()
        .name("грим")
        .description("Дополнительно слать STOP_DESTROY_BLOCK по паутине с новым sequence — сервер перестаёт считать блок замедляющим.")
        .defaultValue(false)
        .visible(() -> this.mode.get() == Mode.Игнор)
        .build());

    private final Setting<Boolean> jumpBypass = sgGeneral.add(new BoolSetting.Builder()
        .name("обход-прыжком")
        .description("Рядом с паутиной не спринтовать на прыжке и вернуть спринт после приземления — обход проверок спринта.")
        .defaultValue(false)
        .visible(() -> this.mode.get() == Mode.Игнор)
        .build());

    private static final BlockPos.Mutable MUTABLE_POS = new BlockPos.Mutable();

    private boolean sprintBlocked;

    public NoWeb() {
        super(B2XY.CATEGORY, "no-web", "Убирает замедление в паутине.");
    }

    /** Активен ли режим «Игнор» — гейт для миксина CobwebBlock. */
    public static boolean isIgnoreActive() {
        NoWeb module = Modules.get().get(NoWeb.class);
        return module != null && module.isActive() && module.mode.get() == Mode.Игнор;
    }

    /** Вызывается из миксина, когда игрок задел паутину (режим «Игнор»). */
    public static void onEntityCollideCobweb(BlockPos pos) {
        NoWeb module = Modules.get().get(NoWeb.class);
        if (module == null || !module.isActive() || module.mode.get() != Mode.Игнор) return;
        if (!module.grim.get()) return;
        if (module.mc.player == null || !(module.mc.world instanceof ClientWorld world)) return;

        PendingUpdateManager pending = world.getPendingUpdateManager();
        try (PendingUpdateManager updates = pending.incrementSequence()) {
            module.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                Direction.UP,
                updates.getSequence()
            ));
        }
    }

    @Override
    public void onDeactivate() {
        this.sprintBlocked = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.world == null) return;

        if (this.mode.get() == Mode.Игнор) {
            if (this.jumpBypass.get()) this.handleJumpBypass();
            return;
        }

        if (!isInWeb()) return;

        double targetY = 0.0;
        if (this.mc.options.jumpKey.isPressed()) {
            targetY = VERTICAL_SPEED;
        } else if (this.mc.options.sneakKey.isPressed()) {
            targetY = -VERTICAL_SPEED;
        }

        this.mc.player.setVelocity(0.0, targetY, 0.0);
        setSpeed(HORIZONTAL_SPEED);
    }

    /** Обход-прыжком: не спринтовать, пока в воздухе рядом с паутиной. */
    private void handleJumpBypass() {
        if (isNearCobweb(2.0)) {
            if (this.mc.options.jumpKey.isPressed()) {
                if (this.mc.player.isSprinting()) {
                    this.mc.player.setSprinting(false);
                    this.sprintBlocked = true;
                }
            } else if (this.mc.player.isOnGround() && this.sprintBlocked) {
                this.mc.player.setSprinting(true);
                this.sprintBlocked = false;
            }
        } else if (this.sprintBlocked && this.mc.player.isOnGround()) {
            this.sprintBlocked = false;
        }
    }

    /**
     * Горизонтальная скорость по направлению ввода (аналог MoveUtil#setSpeed
     * из оригинала): диагональ схлопывается в 45°, вертикальная компонента
     * берётся из текущей скорости.
     */
    private void setSpeed(double speed) {
        if (this.mc.player == null) return;

        Vec2f input = this.mc.player.input.getMovementInput();
        float forward = input.y;
        float strafe = input.x;
        double yaw = this.mc.player.getYaw();

        if (forward != 0) {
            if (strafe > 0) {
                yaw += (forward > 0 ? -45 : 45);
            } else if (strafe < 0) {
                yaw += (forward > 0 ? 45 : -45);
            }

            strafe = 0;
            if (forward > 0) {
                forward = 1;
            } else if (forward < 0) {
                forward = -1;
            }
        }

        double radians = Math.toRadians(yaw + 90);
        double velocityY = this.mc.player.getVelocity().y;

        this.mc.player.setVelocity(
            forward * speed * Math.cos(radians) + strafe * speed * Math.sin(radians),
            velocityY,
            forward * speed * Math.sin(radians) - strafe * speed * Math.cos(radians)
        );
    }

    private boolean isNearCobweb(double maxDistance) {
        if (this.mc.player == null || this.mc.world == null) return false;

        double playerX = this.mc.player.getX();
        double playerY = this.mc.player.getY();
        double playerZ = this.mc.player.getZ();
        int blockX = this.mc.player.getBlockX();
        int blockY = this.mc.player.getBlockY();
        int blockZ = this.mc.player.getBlockZ();
        double maxDistanceSq = maxDistance * maxDistance;

        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    int cx = blockX + x;
                    int cy = blockY + y;
                    int cz = blockZ + z;
                    MUTABLE_POS.set(cx, cy, cz);
                    if (this.mc.world.getBlockState(MUTABLE_POS).getBlock() instanceof CobwebBlock) {
                        double dx = playerX - (cx + 0.5);
                        double dy = playerY - (cy + 0.5);
                        double dz = playerZ - (cz + 0.5);
                        if (dx * dx + dy * dy + dz * dz <= maxDistanceSq) return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isInWeb() {
        if (this.mc.player == null || this.mc.world == null) return false;

        Box playerBox = this.mc.player.getBoundingBox();
        BlockPos playerPosition = this.mc.player.getBlockPos();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = playerPosition.add(x, y, z);
                    if (playerBox.intersects(new Box(pos))
                        && this.mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) return true;
                }
            }
        }

        return false;
    }

    /** Константы режима названы по-русски — EnumSetting показывает toString(). */
    public enum Mode {
        Фиксированный,
        Кастомный,
        Игнор
    }
}
