package com.b2xy.modules;

import com.b2xy.B2XY;
import com.b2xy.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Pitch40 (порт из BepHax NEW-SRC): утилита для mode Pitch40 модуля ElytraFly —
 * синхронизация границ высоты, автозапуск фейерверков, автовключение ElytraRecast
 * и повторное включение ElytraFly после падения.
 */
public class Pitch40 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFirework = settings.createGroup("Авто-фейерверк");
    private final SettingGroup sgRecast = settings.createGroup("Авто-рекаст");

    public final Setting<Double> pitch40LowerBounds = sgGeneral.add(new DoubleSetting.Builder()
        .name("верхняя-граница")
        .description("Нижняя граница высоты для pitch40. При снижении ниже неё начнёт задирать нос. Синхронизируется с ElytraFly.")
        .defaultValue(360.0)
        .min(-128.0)
        .sliderRange(-64.0, 500.0)
        .onChanged(this::syncLowerBoundsToElytraFly)
        .build());

    public final Setting<Double> pitch40UpperBounds = sgGeneral.add(new DoubleSetting.Builder()
        .name("нижняя-граница")
        .description("Верхняя граница высоты для pitch40. При подъёме выше неё начнёт опускать нос. Синхронизируется с ElytraFly.")
        .defaultValue(420.0)
        .min(-128.0)
        .sliderRange(-64.0, 500.0)
        .onChanged(this::syncUpperBoundsToElytraFly)
        .build());

    public final Setting<Boolean> autoFirework = sgFirework.add(new BoolSetting.Builder()
        .name("авто-фейерверк")
        .description("Автоматически использует фейерверк, когда скорость слишком мала или высота падает ниже границ.")
        .defaultValue(true)
        .build());

    public final Setting<Double> minSpeed = sgFirework.add(new DoubleSetting.Builder()
        .name("мин-скорость")
        .description("Запускать ракету, когда скорость падает ниже этого значения (блоков/сек). Обычный Pitch40 держит ~30-40 б/с.")
        .defaultValue(5.0)
        .sliderRange(10.0, 50.0)
        .visible(this.autoFirework::get)
        .build());

    public final Setting<Integer> fireworkCooldownTicks = sgFirework.add(new IntSetting.Builder()
        .name("кулдаун-тиков")
        .description("Минимальное число тиков между запусками фейерверков.")
        .defaultValue(100)
        .sliderRange(10, 100)
        .visible(this.autoFirework::get)
        .build());

    public final Setting<Boolean> autoRecast = sgRecast.add(new BoolSetting.Builder()
        .name("авто-рекаст")
        .description("Автоматически включает ElytraRecast при активации Pitch40. ElytraRecast будет следить за полётом и поднимать при падении.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> useBoost = sgGeneral.add(new BoolSetting.Builder()
        .name("ускорение")
        .description("Запускать RocketBoost во время полёта Pitch40. Он работает только пока горит ракета — сам полёт Pitch40 держит между ними, а без ракеты сервер не создаёт окно для ускорения.")
        .defaultValue(false)
        .onChanged(val -> {
            if (this.isActive()) {
                if (val) {
                    this.enableBoost();
                } else {
                    this.disableBoost();
                }
            }
        })
        .build());

    private boolean boostToggledByUs = false;
    private Module elytraFly;
    private ElytraFlightModes oldValue;
    private Setting<ElytraFlightModes> elytraFlyMode;
    private int fireworkCooldown = 0;
    private boolean goingUp = false;
    private int elytraSwapSlot = -1;
    private double peakY = 0.0;
    private double troughY = 0.0;
    private ElytraRecast elytraRecast = null;
    private boolean oldElytraAutoFirework = false;
    private RegistryKey<World> lastDimension = null;
    private boolean userDisabledElytraFly = false;
    private boolean wasElytraFlyActive = false;
    private boolean waitingNoticeShown = false;
    private int elytraFlyRetryDelay = 0;
    private static final int ELYTRA_FLY_RETRY_DELAY_TICKS = 20;

    public Pitch40() {
        super(B2XY.CATEGORY, "pitch-40", "Утилита для Pitch40-полёта: синхронизация границ с ElytraFly, авто-фейерверк и авто-рекаст.");
    }

    private void enableBoost() {
        RocketBoost boost = Modules.get().get(RocketBoost.class);
        if (boost != null && !boost.isActive()) {
            boost.toggle();
            this.boostToggledByUs = true;
        }
    }

    private void disableBoost() {
        if (this.boostToggledByUs) {
            this.boostToggledByUs = false;
            RocketBoost boost = Modules.get().get(RocketBoost.class);
            if (boost != null && boost.isActive()) {
                boost.toggle();
            }
        }
    }

    private void driveBoost() {
        if (this.useBoost.get() && this.mc.player != null && this.mc.player.isGliding()) {
            RocketBoost boost = Modules.get().get(RocketBoost.class);
            if (boost != null && boost.isActive()) {
                boost.declareTravelling();
            }
        }
    }

    private Module getElytraFly() {
        if (this.elytraFly == null) {
            this.elytraFly = Modules.get().get(ElytraFly.class);
        }
        return this.elytraFly;
    }

    @SuppressWarnings("unchecked")
    private Setting<ElytraFlightModes> getElytraFlyMode() {
        if (this.elytraFlyMode == null) {
            this.elytraFlyMode = (Setting<ElytraFlightModes>) this.getElytraFly().settings.get("mode");
        }
        return this.elytraFlyMode;
    }

    private void syncLowerBoundsToElytraFly(Double value) {
        if (value != null && this.getElytraFly() != null) {
            @SuppressWarnings("unchecked")
            Setting<Double> elytraLower = (Setting<Double>) this.getElytraFly().settings.get("pitch40-lower-bounds");
            if (elytraLower != null && !elytraLower.get().equals(value)) {
                elytraLower.set(value);
            }
        }
    }

    private void syncUpperBoundsToElytraFly(Double value) {
        if (value != null && this.getElytraFly() != null) {
            @SuppressWarnings("unchecked")
            Setting<Double> elytraUpper = (Setting<Double>) this.getElytraFly().settings.get("pitch40-upper-bounds");
            if (elytraUpper != null && !elytraUpper.get().equals(value)) {
                elytraUpper.set(value);
            }
        }
    }

    @Override
    public void onActivate() {
        this.oldValue = this.getElytraFlyMode().get();
        this.getElytraFlyMode().set(ElytraFlightModes.Pitch40);
        this.fireworkCooldown = 0;
        this.goingUp = false;
        this.elytraFlyRetryDelay = 0;
        this.peakY = this.mc.player != null ? this.mc.player.getY() : 0.0;
        this.troughY = this.peakY;
        this.elytraRecast = Modules.get().get(ElytraRecast.class);
        this.lastDimension = this.mc.world != null ? this.mc.world.getRegistryKey() : null;
        this.userDisabledElytraFly = false;
        this.waitingNoticeShown = false;
        this.wasElytraFlyActive = this.getElytraFly().isActive();
        this.syncLowerBoundsToElytraFly(this.pitch40LowerBounds.get());
        this.syncUpperBoundsToElytraFly(this.pitch40UpperBounds.get());
        if (this.useBoost.get()) {
            this.enableBoost();
        }
        if (this.autoRecast.get() && this.elytraRecast != null && !this.elytraRecast.isActive()) {
            this.elytraRecast.toggle();
        }
        if (this.autoFirework.get() && this.getElytraFly().settings.get("auto-firework") instanceof BoolSetting boolSetting) {
            this.oldElytraAutoFirework = boolSetting.get();
            boolSetting.set(false);
        }
    }

    @Override
    public void onDeactivate() {
        if (this.getElytraFly().isActive()) {
            this.getElytraFly().toggle();
        }
        this.getElytraFlyMode().set(this.oldValue);
        if (this.autoFirework.get() && this.getElytraFly().settings.get("auto-firework") instanceof BoolSetting boolSetting) {
            boolSetting.set(this.oldElytraAutoFirework);
        }
        if (this.autoRecast.get() && this.elytraRecast != null && this.elytraRecast.isActive()) {
            this.elytraRecast.toggle();
        }
        this.disableBoost();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.world != null) {
            this.driveBoost();
            RegistryKey<World> currentDimension = this.mc.world.getRegistryKey();
            if (this.lastDimension != null && !this.lastDimension.equals(currentDimension)) {
                this.info("Измерение сменилось! Синхронизирую границы с ElytraFly.");
                this.lastDimension = currentDimension;
                this.userDisabledElytraFly = false;
                this.waitingNoticeShown = false;
                this.elytraFlyRetryDelay = 0;
                this.syncLowerBoundsToElytraFly(this.pitch40LowerBounds.get());
                this.syncUpperBoundsToElytraFly(this.pitch40UpperBounds.get());
                this.peakY = this.mc.player.getY();
                this.troughY = this.peakY;
                this.goingUp = false;
            } else {
                this.lastDimension = currentDimension;
                boolean elytraFlyActive = this.getElytraFly().isActive();
                boolean elytraRecastRecovering = this.elytraRecast != null && this.elytraRecast.isActive() && this.elytraRecast.isRecovering();
                if (this.wasElytraFlyActive && !elytraFlyActive && !elytraRecastRecovering) {
                    this.userDisabledElytraFly = true;
                }
                this.wasElytraFlyActive = elytraFlyActive;
                if (!elytraFlyActive) {
                    boolean isValidDimension = World.OVERWORLD.equals(currentDimension) || World.END.equals(currentDimension);
                    if (this.elytraFlyRetryDelay > 0) {
                        this.elytraFlyRetryDelay--;
                        this.goingUp = false;
                    } else {
                        if (!this.userDisabledElytraFly && isValidDimension) {
                            if (elytraRecastRecovering || !this.meetsPitch40ModeBounds()) {
                                if (!this.waitingNoticeShown) {
                                    this.info("Жду, пока высота вернётся выше границ Pitch40, чтобы включить ElytraFly обратно.");
                                    this.waitingNoticeShown = true;
                                }
                                this.goingUp = false;
                                return;
                            }
                            this.syncLowerBoundsToElytraFly(this.pitch40LowerBounds.get());
                            this.syncUpperBoundsToElytraFly(this.pitch40UpperBounds.get());
                            this.getElytraFly().toggle();
                            if (this.getElytraFly().isActive()) {
                                this.wasElytraFlyActive = true;
                                this.userDisabledElytraFly = false;
                                this.elytraFlyRetryDelay = 0;
                                this.waitingNoticeShown = false;
                            } else {
                                this.elytraFlyRetryDelay = ELYTRA_FLY_RETRY_DELAY_TICKS;
                            }
                        }
                        this.goingUp = false;
                    }
                } else {
                    if (this.fireworkCooldown > 0) {
                        this.fireworkCooldown--;
                    }
                    if (this.elytraSwapSlot != -1) {
                        InvUtils.swap(this.elytraSwapSlot, true);
                        this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
                        InvUtils.swapBack();
                        this.elytraSwapSlot = -1;
                    }

                    double playerY = this.mc.player.getY();
                    double velocityY = this.mc.player.getVelocity().y;
                    boolean wasGoingUp = this.goingUp;
                    this.goingUp = velocityY > 0.0;
                    if (wasGoingUp && !this.goingUp) {
                        this.peakY = playerY;
                    } else if (!wasGoingUp && this.goingUp) {
                        this.troughY = playerY;
                    }

                    if (this.goingUp && playerY > this.peakY) {
                        this.peakY = playerY;
                    } else if (!this.goingUp && playerY < this.troughY) {
                        this.troughY = playerY;
                    }

                    this.checkAndUseFirework();
                }
            }
        }
    }

    private boolean meetsPitch40ModeBounds() {
        double y = this.mc.player.getY();
        return y >= this.pitch40UpperBounds.get() && y - 40.0 >= this.pitch40LowerBounds.get();
    }

    private double getSpeedBPS() {
        Vec3d velocity = this.mc.player.getVelocity();
        double speedPerTick = Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z);
        return speedPerTick * 20.0;
    }

    private void checkAndUseFirework() {
        if (this.autoFirework.get() && this.fireworkCooldown <= 0) {
            if (this.mc.player != null && this.mc.player.isGliding()) {
                if (this.elytraRecast == null || !this.elytraRecast.isActive() || !this.elytraRecast.isRecovering()) {
                    double currentSpeed = this.getSpeedBPS();
                    double playerY = this.mc.player.getY();
                    double lowerBounds = this.pitch40LowerBounds.get();
                    boolean needsBoost = currentSpeed < this.minSpeed.get();
                    boolean emergency = playerY < lowerBounds - 10.0;
                    if (needsBoost || emergency) {
                        int launchStatus = Utils.firework(this.mc, false);
                        if (launchStatus >= 0) {
                            this.fireworkCooldown = this.fireworkCooldownTicks.get();
                            if (launchStatus != 200) {
                                this.elytraSwapSlot = launchStatus;
                            }
                            this.peakY = playerY;
                            this.troughY = playerY;
                        }
                    }
                }
            }
        }
    }
}