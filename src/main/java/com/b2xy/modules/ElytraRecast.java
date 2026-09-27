package com.b2xy.modules;

import com.b2xy.B2XY;
import com.b2xy.util.Utils;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.DamageTiltS2CPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * ElytraRecast (порт из BepHax NEW-SRC): следит за полётом на элитрах и
 * автоматически поднимает игрока, если полёт прервался или высота упала ниже границ.
 * Работает в паре с Pitch40 (умеет брать границы высоты из него).
 */
public class ElytraRecast extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWallSafety = settings.createGroup("Безопасность стен");
    private final SettingGroup sgNether = settings.createGroup("Ад");
    private final SettingGroup sgOverworld = settings.createGroup("Верхний мир/Энд");

    private final Setting<Boolean> disableIfNoRockets = sgGeneral.add(new BoolSetting.Builder()
        .name("отключение-без-ракет")
        .description("Автоматически отключает ElytraRecast, когда нет фейерверков.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> debugMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("отладочные-сообщения")
        .description("Показывать отладочные сообщения в чате о смене состояний и событиях восстановления.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> activationDelay = sgGeneral.add(new IntSetting.Builder()
        .name("задержка-активации")
        .description("Тиков ожидания между прыжком и попытками активации элитр.")
        .defaultValue(3)
        .range(1, 20)
        .sliderRange(1, 20)
        .build());

    private final Setting<Integer> rocketDelay = sgGeneral.add(new IntSetting.Builder()
        .name("задержка-ракеты")
        .description("Минимальное число тиков между запусками ракет при подъёме.")
        .defaultValue(15)
        .range(5, 60)
        .sliderRange(5, 60)
        .build());

    private final Setting<Boolean> smartRockets = sgGeneral.add(new BoolSetting.Builder()
        .name("умные-ракеты")
        .description("Запускать ракету только когда предыдущее ускорение закончилось и скорость набора упала, а не по фиксированному таймеру.")
        .defaultValue(true)
        .build());

    private final Setting<Double> minClimbSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("мин-скорость-набора")
        .description("Запускать следующую ракету, только когда вертикальная скорость упадёт ниже этого значения при подъёме.")
        .defaultValue(0.5)
        .range(0.0, 2.0)
        .sliderRange(0.0, 2.0)
        .visible(this.smartRockets::get)
        .build());

    private final Setting<Integer> wallClearance = sgWallSafety.add(new IntSetting.Builder()
        .name("свободный-просвет")
        .description("Блоков свободного пространства нужно по направлению взгляда перед запуском ракеты. Предотвращает буст в стены.")
        .defaultValue(20)
        .range(5, 50)
        .sliderRange(5, 50)
        .build());

    private final Setting<WallHitAction> wallHitAction = sgWallSafety.add(new EnumSetting.Builder<WallHitAction>()
        .name("при-ударе-о-стену")
        .description("Что делать при получении урона от столкновения со стеной.")
        .defaultValue(WallHitAction.Pause)
        .build());

    private final Setting<Integer> pauseDuration = sgWallSafety.add(new IntSetting.Builder()
        .name("длительность-паузы")
        .description("Тиков паузы восстановления после удара о стену.")
        .defaultValue(20)
        .range(0, 200)
        .sliderRange(0, 200)
        .visible(() -> this.wallHitAction.get() == WallHitAction.Pause)
        .build());

    private final Setting<Integer> maxWallHits = sgWallSafety.add(new IntSetting.Builder()
        .name("макс-ударов-о-стену")
        .description("Отключается после такого числа ударов о стену без 30 секунд безопасного полёта.")
        .defaultValue(3)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(() -> this.wallHitAction.get() == WallHitAction.Pause)
        .build());

    private final Setting<Double> rotationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("скорость-ротации")
        .description("Градусов за тик для подъёма питча при восстановлении. Меньше = плавнее и реалистичнее.")
        .defaultValue(3.0)
        .range(0.5, 15.0)
        .sliderRange(0.5, 15.0)
        .build());

    private final Setting<Double> targetPitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("целевой-питч")
        .description("Целевой угол питча при подъёме. Отрицательный = смотреть вверх.")
        .defaultValue(-70.0)
        .range(-89.0, 0.0)
        .sliderRange(-89.0, 0.0)
        .build());

    private final Setting<Integer> netherFallDelay = sgNether.add(new IntSetting.Builder()
        .name("задержка-падения")
        .description("Тиков ожидания после остановки полёта перед запуском восстановления в Аду.")
        .defaultValue(5)
        .range(1, 40)
        .sliderRange(1, 40)
        .build());

    private final Setting<Boolean> usePitch40Bounds = sgOverworld.add(new BoolSetting.Builder()
        .name("границы-pitch40")
        .description("Брать высоты из границ Pitch40: восстанавливаться ниже его нижней границы, подниматься выше верхней. Если Pitch40 неактивен — используется нижележащие настройки.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> overworldTargetAltitude = sgOverworld.add(new IntSetting.Builder()
        .name("целевая-высота")
        .description("Целевая высота Y для подъёма в Верхнем мире/Энде. Игнорируется, если включены границы-pitch40.")
        .defaultValue(360)
        .sliderRange(50, 400)
        .visible(() -> !this.usePitch40Bounds.get())
        .build());

    private final Setting<Integer> overworldMinAltitude = sgOverworld.add(new IntSetting.Builder()
        .name("мин-высота")
        .description("Запустить восстановление, если Y упадёт ниже этого в Верхнем мире/Энде. Игнорируется, если включены границы-pitch40.")
        .defaultValue(310)
        .sliderRange(10, 400)
        .visible(() -> !this.usePitch40Bounds.get())
        .build());

    private static final int WALL_HIT_RESET_TICKS = 600;
    private State state;
    private int tickCounter;
    private int rocketTickCounter;
    private boolean usedActivationRocket;
    private int notGlidingTicks;
    private boolean netherRocketUsed;
    private Pitch40 pitch40Util;
    private int pauseTicks;
    private int wallHits;
    private int ticksSinceWallHit;
    private volatile boolean pendingWallHit;

    public ElytraRecast() {
        super(B2XY.CATEGORY, "elytra-recast", "Восстановление полёта: следит за полётом на элитрах и поднимает при падении или остановке.");
    }

    public boolean isRecovering() {
        return this.state == State.JUMPING || this.state == State.ACTIVATING || this.state == State.ASCENDING;
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null) {
            if (!this.hasElytraEquipped()) {
                this.error("Элитры не надеты!");
                this.toggle();
            } else {
                this.tickCounter = 0;
                this.rocketTickCounter = 0;
                this.usedActivationRocket = false;
                this.notGlidingTicks = 0;
                this.netherRocketUsed = false;
                this.pitch40Util = Modules.get().get(Pitch40.class);
                this.pauseTicks = 0;
                this.wallHits = 0;
                this.ticksSinceWallHit = 0;
                this.pendingWallHit = false;
                this.state = State.MONITORING;
                if (this.debugMessages.get()) {
                    this.info("Слежу за полётом...");
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        this.state = null;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.world != null) {
            if (this.state != null) {
                if (this.hasElytraEquipped()) {
                    if (this.pendingWallHit) {
                        this.pendingWallHit = false;
                        this.handleWallHit();
                        if (!this.isActive()) {
                            return;
                        }
                    }

                    this.ticksSinceWallHit++;
                    if (this.wallHits > 0 && this.ticksSinceWallHit > WALL_HIT_RESET_TICKS) {
                        this.wallHits = 0;
                    }

                    this.tickCounter++;
                    this.rocketTickCounter++;
                    switch (this.state) {
                        case MONITORING -> this.handleMonitoring();
                        case JUMPING -> this.handleJumping();
                        case ACTIVATING -> this.handleActivating();
                        case ASCENDING -> this.handleAscending();
                    }
                }
            }
        }
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (this.mc.player != null && this.mc.world != null) {
            // 1.21.11: ClientboundDamageEventPacket удалён, остался только DamageTiltS2CPacket
            // (id сущности + yaw) — источника урона в пакете нет. Поэтому детект удара о стену —
            // эвристика: урон получен в полёте и впереди по вектору движения стена вплотную.
            if (event.packet instanceof DamageTiltS2CPacket packet) {
                if (packet.id() == this.mc.player.getId() && this.looksLikeWallHit()) {
                    this.pendingWallHit = true;
                }
            }
        }
    }

    /**
     * Эвристика удара о стену (замена удалённого DamageTypes.FLY_INTO_WALL):
     * игрок планирует и у него есть горизонтальная скорость, а прямо по курсу
     * в течение 1.5 блоков — твёрдый блок.
     */
    private boolean looksLikeWallHit() {
        if (!this.mc.player.isGliding()) {
            return false;
        }
        Vec3d vel = this.mc.player.getVelocity();
        if (Math.abs(vel.getX()) + Math.abs(vel.getZ()) < 0.1) {
            return false;
        }
        Vec3d eye = this.mc.player.getEyePos();
        Vec3d dir = new Vec3d(vel.getX(), 0.0, vel.getZ()).normalize();
        HitResult hit = this.mc.world.raycast(
            new RaycastContext(eye, eye.add(dir.multiply(1.5)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.mc.player)
        );
        return hit.getType() == HitResult.Type.BLOCK;
    }

    private void handleWallHit() {
        this.wallHits++;
        this.ticksSinceWallHit = 0;
        if (this.wallHitAction.get() == WallHitAction.Disable) {
            this.error("Удар о стену! Отключаюсь.");
            this.toggle();
        } else if (this.wallHits >= this.maxWallHits.get()) {
            this.error("Удар о стену " + this.wallHits + " раз! Отключаюсь.");
            this.toggle();
        } else {
            this.warning(
                "Удар о стену (" + this.wallHits + "/" + this.maxWallHits.get() + ")! Пауза восстановления на " + this.pauseDuration.get() + " тиков."
            );
            this.state = State.MONITORING;
            this.pauseTicks = this.pauseDuration.get();
            this.tickCounter = 0;
            this.notGlidingTicks = 0;
            this.usedActivationRocket = false;
            this.netherRocketUsed = false;
        }
    }

    private void handleMonitoring() {
        if (this.pauseTicks > 0) {
            this.pauseTicks--;
            this.notGlidingTicks = 0;
        } else {
            boolean currentlyGliding = this.mc.player.isGliding();
            double currentY = this.mc.player.getY();
            if (this.isInNether()) {
                if (!currentlyGliding) {
                    this.notGlidingTicks++;
                    if (this.notGlidingTicks >= this.netherFallDelay.get()) {
                        if (this.debugMessages.get()) {
                            this.info("Ад: полёт остановлен " + this.notGlidingTicks + " тиков! Восстанавливаюсь...");
                        }
                        this.triggerRecovery();
                        this.notGlidingTicks = 0;
                        return;
                    }
                } else {
                    this.notGlidingTicks = 0;
                }
            } else {
                double minAlt = this.getEffectiveMinAltitude();
                if (currentY < minAlt) {
                    if (!currentlyGliding) {
                        if (this.debugMessages.get()) {
                            this.info("Ниже мин. высоты (Y=" + (int) currentY + " < " + (int) minAlt + ") и не в полёте! Восстанавливаюсь...");
                        }
                        this.triggerRecovery();
                        return;
                    }
                    if (this.debugMessages.get()) {
                        this.info("Высота слишком мала (Y=" + (int) currentY + " < " + (int) minAlt + ")! Набираю высоту...");
                    }
                    this.triggerRecovery();
                }
            }
        }
    }

    private double getEffectiveMinAltitude() {
        return this.usePitch40Bounds.get() && this.pitch40Util != null && this.pitch40Util.isActive()
            ? this.pitch40Util.pitch40LowerBounds.get() - 10.0
            : this.overworldMinAltitude.get().intValue();
    }

    private double getEffectiveTargetAltitude() {
        return this.usePitch40Bounds.get() && this.pitch40Util != null && this.pitch40Util.isActive()
            ? Math.max(this.pitch40Util.pitch40UpperBounds.get(), this.pitch40Util.pitch40LowerBounds.get() + 40.0) + 2.0
            : this.overworldTargetAltitude.get().intValue();
    }

    private void triggerRecovery() {
        if (!this.hasRockets()) {
            if (this.disableIfNoRockets.get()) {
                this.error("Нет фейерверков! Отключаю ElytraRecast.");
                this.toggle();
                return;
            }
            this.warning("Нет фейерверков! Восстановление может не сработать.");
        }

        this.tickCounter = 0;
        this.rocketTickCounter = this.rocketDelay.get();
        this.usedActivationRocket = false;
        this.netherRocketUsed = false;
        if (this.mc.player.isGliding()) {
            this.state = State.ASCENDING;
        } else if (this.mc.player.isOnGround()) {
            this.state = State.JUMPING;
        } else {
            this.state = State.ACTIVATING;
        }
    }

    private void handleJumping() {
        if (this.mc.player.isOnGround()) {
            this.mc.player.jump();
            this.tickCounter = 0;
        } else if (this.tickCounter >= this.activationDelay.get()) {
            this.state = State.ACTIVATING;
            this.tickCounter = 0;
        }
    }

    private void handleActivating() {
        if (this.mc.player.isGliding()) {
            this.state = State.ASCENDING;
            this.rocketTickCounter = this.rocketDelay.get();
            this.tickCounter = 0;
        } else if (this.mc.player.isOnGround()) {
            this.state = State.JUMPING;
            this.tickCounter = 0;
            this.usedActivationRocket = false;
        } else {
            if (this.tickCounter % 2 == 0) {
                this.sendElytraPacket();
            }
            if (this.tickCounter > 15 && !this.usedActivationRocket) {
                if (!this.hasRocketClearance()) {
                    this.adjustPitchUp();
                } else if (this.useRocket()) {
                    this.usedActivationRocket = true;
                } else if (this.disableIfNoRockets.get()) {
                    this.error("Нет ракет для активации! Отключаюсь.");
                    this.toggle();
                    return;
                }
            }
            if (this.tickCounter > 40) {
                if (!this.hasRockets() && this.disableIfNoRockets.get()) {
                    this.error("Нет ракет и активация не удалась! Отключаюсь.");
                    this.toggle();
                    return;
                }
                this.state = State.JUMPING;
                this.tickCounter = 0;
                this.usedActivationRocket = false;
            }
        }
    }

    private void handleAscending() {
        if (!this.mc.player.isGliding()) {
            if (!this.hasRockets() && this.disableIfNoRockets.get()) {
                this.error("Полёт потерян и ракет нет! Отключаюсь.");
                this.toggle();
            } else {
                this.state = State.ACTIVATING;
                this.tickCounter = 0;
                this.usedActivationRocket = false;
            }
        } else {
            if (this.isInNether()) {
                this.adjustPitchUp();
                if (!this.netherRocketUsed && this.hasRocketClearance()) {
                    if (this.useRocket()) {
                        this.netherRocketUsed = true;
                    } else if (this.disableIfNoRockets.get()) {
                        this.error("Нет ракет для восстановления в Аду! Отключаюсь.");
                        this.toggle();
                        return;
                    }
                }
                if (this.netherRocketUsed ? this.tickCounter > 10 : this.tickCounter > 40) {
                    if (this.debugMessages.get()) {
                        this.info(
                            this.netherRocketUsed
                                ? "Восстановление в Аду завершено — дальше барitone."
                                : "Ад: нет свободного пути для ракеты, возвращаюсь к мониторингу."
                        );
                    }
                    this.state = State.MONITORING;
                    this.tickCounter = 0;
                    this.netherRocketUsed = false;
                    return;
                }
            } else {
                double currentY = this.mc.player.getY();
                double target = this.getEffectiveTargetAltitude();
                if (currentY >= target) {
                    if (this.debugMessages.get()) {
                        this.info("Целевая высота Y=" + (int) target + " достигнута! Возвращаюсь к мониторингу.");
                    }
                    this.state = State.MONITORING;
                    this.tickCounter = 0;
                    return;
                }
                this.adjustPitchUp();
                if (this.rocketTickCounter >= this.rocketDelay.get() && this.shouldBoost()) {
                    if (this.useRocket()) {
                        this.rocketTickCounter = 0;
                    } else if (this.disableIfNoRockets.get()) {
                        this.error("Ракеты кончились во время подъёма! Отключаюсь.");
                        this.toggle();
                        return;
                    }
                }
            }
        }
    }

    private boolean shouldBoost() {
        if (!this.hasRocketClearance()) {
            return false;
        }
        return !this.smartRockets.get() || !this.isBoosted() && this.mc.player.getVelocity().y < this.minClimbSpeed.get();
    }

    private boolean hasRocketClearance() {
        Vec3d eye = this.mc.player.getEyePos();
        Vec3d end = eye.add(this.mc.player.getRotationVec(1.0f).multiply(this.wallClearance.get().intValue()));
        return this.mc.world.raycast(
            new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.mc.player)
        ).getType() == HitResult.Type.MISS;
    }

    private boolean isBoosted() {
        return !this.mc.world.getOtherEntities(
            this.mc.player,
            this.mc.player.getBoundingBox().expand(2.0),
            e -> e instanceof FireworkRocketEntity rocket && !rocket.wasShotAtAngle()
        ).isEmpty();
    }

    private void adjustPitchUp() {
        float currentPitch = this.mc.player.getPitch();
        float target = this.targetPitch.get().floatValue();
        float speed = this.rotationSpeed.get().floatValue();
        float diff = target - currentPitch;
        if (!(Math.abs(diff) < 0.5F)) {
            float adjustment;
            if (Math.abs(diff) <= speed) {
                adjustment = diff;
            } else {
                adjustment = diff > 0.0F ? speed : -speed;
            }
            float newPitch = currentPitch + adjustment;
            newPitch = Math.max(-89.0F, Math.min(89.0F, newPitch));
            this.mc.player.setPitch(newPitch);
        }
    }

    private void sendElytraPacket() {
        if (this.mc.player != null && this.mc.getNetworkHandler() != null) {
            this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
    }

    private boolean hasRockets() {
        FindItemResult hotbar = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (hotbar.found()) {
            return true;
        }
        return InvUtils.find(Items.FIREWORK_ROCKET).found();
    }

    private boolean useRocket() {
        FindItemResult hotbar = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!hotbar.found()) {
            FindItemResult inv = InvUtils.find(Items.FIREWORK_ROCKET);
            if (!inv.found()) {
                return false;
            }
            int hotbarSlot = this.findEmptyHotbarSlot();
            if (hotbarSlot != -1) {
                InvUtils.move().from(inv.slot()).to(hotbarSlot);
            }
        }
        Utils.firework(this.mc, false);
        return true;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasElytraEquipped() {
        return this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
    }

    private boolean isInNether() {
        return this.mc.world == null ? false : this.mc.world.getRegistryKey() == World.NETHER;
    }

    @Override
    public String getInfoString() {
        if (this.state == null) {
            return null;
        }
        if (this.mc.player == null) {
            return this.state.name();
        }

        double minAlt = this.isInNether() ? 0.0 : this.getEffectiveMinAltitude();

        return switch (this.state) {
            case MONITORING -> this.pauseTicks > 0
                ? String.format("ПАУЗА %d", this.pauseTicks)
                : (this.isInNether()
                    ? String.format("Y=%.0f", this.mc.player.getY())
                    : String.format("Y=%.0f (мин:%.0f)", this.mc.player.getY(), minAlt));
            case JUMPING -> "ПРЫЖОК";
            case ACTIVATING -> "АКТИВАЦИЯ";
            case ASCENDING -> this.isInNether()
                ? String.format("↑%.0f", this.mc.player.getY())
                : String.format("↑%.0f/%.0f", this.mc.player.getY(), this.getEffectiveTargetAltitude());
        };
    }

    private enum State {
        MONITORING,
        JUMPING,
        ACTIVATING,
        ASCENDING
    }

    public enum WallHitAction {
        Pause,
        Disable
    }
}