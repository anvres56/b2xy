package com.b2xy.modules;

import com.b2xy.B2XY;
import com.b2xy.util.BaritoneHelper;
import com.b2xy.util.BounceSolver;
import com.b2xy.util.RotationUtils;
import com.b2xy.util.Utils;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;

import java.util.List;

/**
 * ElytraBounce (порт из BepHax NEW-SRC): эфли-полёт с автопрыжковым бумом,
 * обходом препятствий через Baritone и фейк-флаем (chestplate fakefly).
 */
public class ElytraBounce extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgObstaclePasser = settings.createGroup("Обход препятствий");

    private final Setting<Boolean> bounce = sgGeneral.add(new BoolSetting.Builder()
        .name("прыжок")
        .description("Автоматически делает bounce-эфли (прыжок-планирование-прыжок).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> lockPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("фикс-питч")
        .description("Фиксирует питч при включённом bounce.")
        .defaultValue(true)
        .visible(this.bounce::get)
        .build());

    private final Setting<Boolean> autoPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("авто-питч")
        .description("Подбирает питч, при котором bounce получается самым быстрым, вместо фиксированного. Скорость зависит от того, как часто ты приземляешься (каждый спринт-прыжок добавляет 0.2, а элитры сохраняют 0.99 трения), поэтому лучший питч зависит от высоты потолка над головой — даёт до +25% под низким потолком.")
        .defaultValue(true)
        .visible(() -> this.bounce.get() && this.lockPitch.get())
        .build());

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("питч")
        .description("Питч, который ставится при включённом bounce.")
        .defaultValue(90.0)
        .sliderRange(-90.0, 90.0)
        .visible(() -> this.bounce.get() && this.lockPitch.get() && !this.autoPitch.get())
        .build());

    private final Setting<Integer> setbackPause = sgGeneral.add(new IntSetting.Builder()
        .name("пауза-после-сетбека")
        .description("Тиков паузы bounce после телепорта-сетбека от сервера, чтобы модуль не дрался с сетбек-лупом. 0 — отключено.")
        .defaultValue(5)
        .min(0)
        .sliderMax(40)
        .visible(this.bounce::get)
        .build());

    private final Setting<Boolean> lockYaw = sgGeneral.add(new BoolSetting.Builder()
        .name("фикс-яр")
        .description("Фиксирует яр при включённом bounce.")
        .defaultValue(false)
        .visible(this.bounce::get)
        .build());

    private final Setting<Boolean> useCustomYaw = sgGeneral.add(new BoolSetting.Builder()
        .name("свой-яр")
        .description("Включай, если нужен яр, не кратный 45. ВНИМАНИЕ: влияет на цель Baritone в обходе препятствий; используй стандартный модуль Rotations, если нужен только другой лок яр.")
        .defaultValue(false)
        .visible(this.bounce::get)
        .build());

    private final Setting<Double> yaw = sgGeneral.add(new DoubleSetting.Builder()
        .name("яр")
        .description("Яр при включённом bounce. Автоматически ставится ближайшим к тебе углом, кратным 45, если не включён «Свой яр». ВНИМАНИЕ: влияет на цель Baritone в обходе препятствий.")
        .defaultValue(0.0)
        .sliderRange(0.0, 359.0)
        .visible(() -> this.bounce.get() && this.useCustomYaw.get())
        .build());

    private final Setting<Boolean> highwayObstaclePasser = sgObstaclePasser.add(new BoolSetting.Builder()
        .name("обход-препятствий")
        .description("Использует Baritone для обхода препятствий.")
        .defaultValue(true)
        .visible(this.bounce::get)
        .build());

    private final Setting<Boolean> awayFromStartPos = sgObstaclePasser.add(new BoolSetting.Builder()
        .name("от-стартовой-позиции")
        .description("Если true — будет идти от стартовой позиции вместо того, чтобы идти к ней. Стартовая позиция автоматически ставится при активации модуля.")
        .defaultValue(true)
        .visible(() -> this.bounce.get() && this.highwayObstaclePasser.get())
        .build());

    private final Setting<Double> distance = sgObstaclePasser.add(new DoubleSetting.Builder()
        .name("дистанция")
        .description("Дистанция до цели Baritone для перестроения маршрута.")
        .defaultValue(10.0)
        .visible(() -> this.bounce.get() && this.highwayObstaclePasser.get())
        .build());

    private final Setting<Boolean> avoidPortalTraps = sgObstaclePasser.add(new BoolSetting.Builder()
        .name("обход-портал-ловушек")
        .description("Пытается обнаружить портал-ловушки при загрузке чанков и обойти их.")
        .defaultValue(false)
        .visible(() -> this.bounce.get() && this.highwayObstaclePasser.get())
        .build());

    private final Setting<Double> portalAvoidDistance = sgObstaclePasser.add(new DoubleSetting.Builder()
        .name("дистанция-обхода-портала")
        .description("Дистанция до портал-ловушки, на которой обход препятствий перехватывает управление и обходит её стороной.")
        .defaultValue(20.0)
        .min(0.0)
        .sliderMax(50.0)
        .visible(() -> this.bounce.get() && this.highwayObstaclePasser.get() && this.avoidPortalTraps.get())
        .build());

    private final Setting<Integer> portalScanWidth = sgObstaclePasser.add(new IntSetting.Builder()
        .name("ширина-скана-порталов")
        .description("Ширина по оси хайвея, на которой будет проводиться скан портал-ловушек.")
        .defaultValue(5)
        .min(3)
        .sliderMax(10)
        .visible(() -> this.bounce.get() && this.highwayObstaclePasser.get() && this.avoidPortalTraps.get())
        .build());

    private final Setting<Boolean> killAuraWhileWalking = sgObstaclePasser.add(new BoolSetting.Builder()
        .name("киллаура-при-ходьбе")
        .description("Включает KillAura только пока bounce остановлен и Baritone идёт (обход препятствий), затем выключает, чтобы её ротации не мешали. Возвращает прежнее состояние KillAura при выключении.")
        .defaultValue(false)
        .visible(() -> this.bounce.get() && this.highwayObstaclePasser.get())
        .build());

    private final Setting<Boolean> fakeFly = sgGeneral.add(new BoolSetting.Builder()
        .name("фейк-флай")
        .description("Позволяет летать с нагрудником, тратя почти 0 прочности элитр. Элитры должны быть в хотбаре.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> toggleElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("переодевать-элитры")
        .description("Надевает элитры при активации и нагрудник при деактивации.")
        .defaultValue(true)
        .visible(() -> !this.fakeFly.get())
        .build());

    private final Setting<Boolean> lookLikeFalling = sgGeneral.add(new BoolSetting.Builder()
        .name("выглядеть-стоящим")
        .description("На своём клиенте модель стоит вертикально, а не лежит планируя: "
            + "тело не кладётся на 90 градусов, поза обычная, крылья не рисуются. "
            + "Пакеты позиции не меняются, поэтому сервер как считает нас летящими, "
            + "так и продолжает считать. На чужих игроков не влияет.")
        .defaultValue(false)
        .build());

    private boolean startSprinting;
    private boolean jumpKeyDown;
    private BlockPos portalTrap = null;
    private boolean paused = false;
    private boolean elytraToggled = false;
    private Vec3d lastUnstuckPos;
    private int stuckTimer = 0;
    private int targetY = 120;
    private BlockPos startPos = new BlockPos(0, 0, 0);
    private KillAura killAura;
    private Boolean killAuraUserState = null;
    private float solvedPitch = 90.0F;
    private double solvedHeadroom = -1.0;
    private double predictedSpeed = 0.0;
    private int solveCooldown = 0;
    private int setbackTicks = 0;
    private final double maxDistance = 80.0;
    private BlockPos tempPath = null;
    private boolean waitingForChunksToLoad;

    /** Приоритет ротации для bounce-бума: выше BepMine (10) и LitematicaPrinter (SwapManager.Priority.PLACE = 35). */
    private static final int ROTATION_PRIORITY = 45;

    public ElytraBounce() {
        super(B2XY.CATEGORY, "elytra-bounce", "Эфли-полёт: bounce, фиксы ротаций, обход препятствий, фейк-флай.");
    }

    /** Рисовать ли на своём клиенте падение вместо планирования. Гейт для миксинов. */
    public static boolean lookLikeFalling() {
        ElytraBounce module = meteordevelopment.meteorclient.systems.modules.Modules.get().get(ElytraBounce.class);
        return module != null && module.isActive() && module.lookLikeFalling.get();
    }

    @Override
    public void onActivate() {
        if (mc.player != null && !mc.player.getAbilities().allowFlying) {
            this.startSprinting = mc.player.isSprinting();
            this.jumpKeyDown = false;
            this.tempPath = null;
            this.portalTrap = null;
            this.paused = false;
            this.waitingForChunksToLoad = false;
            this.elytraToggled = false;
            this.lastUnstuckPos = playerPos();
            this.stuckTimer = 0;
            this.solvedPitch = this.pitch.get().floatValue();
            this.solvedHeadroom = -1.0;
            this.predictedSpeed = 0.0;
            this.solveCooldown = 0;
            this.setbackTicks = 0;
            if (this.bounce.get() && playerPos().multiply(1.0, 0.0, 1.0).length() >= 100.0) {
                if (!BaritoneHelper.hasElytraProcess()
                    || BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
                }

                if (this.highwayObstaclePasser.get()) {
                    this.startPos = mc.player.getBlockPos();
                    this.targetY = mc.player.getBlockY();
                } else {
                    this.startPos = new BlockPos(0, 0, 0);
                }

                if (!this.useCustomYaw.get()) {
                    if (!(mc.player.getBlockPos().getSquaredDistance(this.startPos) < 10000.0) && this.highwayObstaclePasser.get()) {
                        BlockPos directionVec = mc.player.getBlockPos().subtract(this.startPos);
                        double angle = Math.toDegrees(Math.atan2(-directionVec.getX(), directionVec.getZ()));
                        double angleNormalized = Utils.angleOnAxis(angle);
                        if (!this.awayFromStartPos.get()) {
                            angleNormalized += 180.0;
                        }

                        this.yaw.set(angleNormalized);
                    } else {
                        double playerAngleNormalized = Utils.angleOnAxis(mc.player.getYaw());
                        this.yaw.set(playerAngleNormalized);
                    }
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        this.restoreKillAura();
        RotationUtils.getInstance().clearRotations(this);
        if (mc.player != null) {
            if (this.bounce.get()
                && (!BaritoneHelper.hasElytraProcess()
                    || BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null)) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
            }

            mc.player.setSprinting(this.startSprinting);
            if (this.toggleElytra.get()
                && !this.fakeFly.get()
                && !mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().toString().contains("chestplate")) {
                Modules.get().get(ChestSwap.class).swap();
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (mc.player != null && !mc.player.getAbilities().allowFlying) {
            if (this.setbackTicks > 0) {
                this.setbackTicks--;
            }

            this.updateAutoPitch();
            this.updateKillAura();
            this.updateJumpKey();
            if (this.toggleElytra.get() && !this.fakeFly.get() && !this.elytraToggled) {
                if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
                    Modules.get().get(ChestSwap.class).swap();
                } else {
                    this.elytraToggled = true;
                }
            }

            if (this.enabled() && hasEnoughFood()) {
                mc.player.setSprinting(true);
            }

            if (this.bounce.get()) {
                if (this.tempPath != null && mc.player.getBlockPos().getSquaredDistance(this.tempPath) < 500.0) {
                    this.tempPath = null;
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
                } else if (this.tempPath != null) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.tempPath));
                    RotationUtils.getInstance().clearRotations(this);
                    return;
                }

                if (this.highwayObstaclePasser.get()
                    && BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal() != null) {
                    RotationUtils.getInstance().clearRotations(this);
                    return;
                }

                if (mc.player.squaredDistanceTo(this.lastUnstuckPos) < 25.0) {
                    this.stuckTimer++;
                } else {
                    this.stuckTimer = 0;
                    this.lastUnstuckPos = playerPos();
                }

                if (this.highwayObstaclePasser.get()
                    && playerPos().length() > 100.0
                    && (
                        mc.player.getY() < this.targetY
                            || mc.player.getY() > this.targetY + 2
                            || mc.player.horizontalCollision
                            || this.portalTrap != null
                                && this.portalTrap.getSquaredDistance(mc.player.getBlockPos())
                                    < this.portalAvoidDistance.get() * this.portalAvoidDistance.get()
                            || this.waitingForChunksToLoad
                            || this.stuckTimer > 50
                    )) {
                    this.waitingForChunksToLoad = false;
                    this.paused = true;
                    RotationUtils.getInstance().clearRotations(this);
                    BlockPos goal = mc.player.getBlockPos();
                    double currDistance = this.distance.get();
                    if (this.portalTrap != null) {
                        currDistance += playerPos().distanceTo(this.portalTrap.toCenterPos());
                        this.portalTrap = null;
                        this.info("Путь в обход портала.");
                    }

                    do {
                        if (currDistance > 80.0) {
                            this.tempPath = goal;
                            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                            return;
                        }

                        Vec3d unitYawVec = Utils.yawToDirection(this.yaw.get());
                        Vec3d travelVec = playerPos().subtract(this.startPos.toCenterPos());
                        double parallelCurrPosDot = travelVec.multiply(new Vec3d(1.0, 0.0, 1.0)).dotProduct(unitYawVec);
                        Vec3d parallelCurrPosComponent = unitYawVec.multiply(parallelCurrPosDot);
                        Vec3d pos = this.startPos.toCenterPos().add(parallelCurrPosComponent);
                        pos = Utils.positionInDirection(pos, this.yaw.get(), currDistance);
                        goal = new BlockPos((int) Math.floor(pos.getX()), this.targetY, (int) Math.floor(pos.getZ()));
                        currDistance++;
                        if (mc.world.getBlockState(goal).getBlock() == Blocks.VOID_AIR) {
                            this.waitingForChunksToLoad = true;
                            return;
                        }
                    } while (
                        !mc.world.getBlockState(goal.down()).isSolidBlock(mc.world, goal.down())
                            || mc.world.getBlockState(goal).getBlock() == Blocks.NETHER_PORTAL
                            || !mc.world.getBlockState(goal).isAir()
                    );

                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                } else {
                    this.paused = false;
                    if (!this.enabled()) {
                        RotationUtils.getInstance().clearRotations(this);
                        return;
                    }

                    if (this.lockYaw.get()) {
                        mc.player.setYaw(this.yaw.get().floatValue());
                    }

                    RotationUtils rotation = RotationUtils.getInstance();
                    if (this.lockPitch.get()) {
                        // Питч меняется только на стороне сервера, мгновенно и без плавного разгона
                        // (setRotationFullInstant): камера свободна, а отправляемый питч и физика
                        // глайдинга (calcGlidingVelocity) равны целевым точно — иначе Grim ловит
                        // рассинхрон симуляции положения из-за рампа ротации.
                        rotation.setRotationFullInstant(
                            this,
                            ROTATION_PRIORITY,
                            this.lockYaw.get() ? this.yaw.get().floatValue() : mc.player.getYaw(),
                            this.effectivePitch()
                        );
                    } else {
                        rotation.clearRotations(this);
                    }
                }
            }

            if (this.enabled() && this.fakeFly.get()) {
                this.doGrimEflyStuff();
            }
        }
    }

    private void updateKillAura() {
        if (this.killAura == null) {
            this.killAura = Modules.get().get(KillAura.class);
        }

        if (this.killAura != null) {
            if (this.isActive() && this.killAuraWhileWalking.get() && this.bounce.get() && this.highwayObstaclePasser.get()) {
                if (this.killAuraUserState == null) {
                    this.killAuraUserState = this.killAura.isActive();
                }

                boolean walking = this.paused;
                if (walking != this.killAura.isActive()) {
                    this.killAura.toggle();
                }
            } else {
                this.restoreKillAura();
            }
        }
    }

    private void updateAutoPitch() {
        if (this.autoPitch.get() && this.bounce.get() && this.lockPitch.get() && mc.world != null) {
            if (this.solveCooldown > 0) {
                this.solveCooldown--;
            } else if (mc.player.isOnGround()) {
                this.solveCooldown = 10;
                double headroom = BounceSolver.measureHeadroom(mc.player, mc.world);
                if (!(this.solvedHeadroom >= 0.0) || Math.abs(headroom - this.solvedHeadroom) >= 0.05) {
                    this.solvedHeadroom = headroom;
                    this.solvedPitch = BounceSolver.solvePitch(headroom);
                    this.predictedSpeed = BounceSolver.simulateSpeed(this.solvedPitch, headroom);
                }
            }
        }
    }

    private float effectivePitch() {
        return this.autoPitch.get() ? this.solvedPitch : this.pitch.get().floatValue();
    }

    @Override
    public String getInfoString() {
        return this.bounce.get() && this.lockPitch.get() && this.autoPitch.get() && !(this.predictedSpeed <= 0.0)
            ? String.format("%.0f б/с @ %.0f°", this.predictedSpeed, this.solvedPitch)
            : null;
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (this.bounce.get() && this.setbackPause.get() > 0) {
            if (event.packet instanceof PlayerPositionLookS2CPacket) {
                this.setbackTicks = this.setbackPause.get();
            }
        }
    }

    private void restoreKillAura() {
        if (this.killAuraUserState != null) {
            if (this.killAura != null && (this.killAura.isActive() != this.killAuraUserState)) {
                this.killAura.toggle();
            }

            this.killAuraUserState = null;
        }
    }

    /**
     * Активен ли bounce-режим по факту (элитры надеты или включён фейк-флай).
     */
    public boolean enabled() {
        return this.isActive()
            && !this.paused
            && this.setbackTicks == 0
            && mc.player != null
            && (this.fakeFly.get() || mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA));
    }

    public boolean isBounceRenderStabilized() {
        return this.enabled() && this.bounce.get() && this.lockPitch.get();
    }

    public boolean isFakeFlyEnabled() {
        return this.fakeFly.get();
    }

    public boolean shouldAutoJump() {
        return this.bounce.get() && !this.fakeFly.get();
    }

    private void updateJumpKey() {
        boolean prev = this.jumpKeyDown;
        if (!this.enabled() || !this.shouldAutoJump()) {
            this.jumpKeyDown = false;
        } else if (mc.player.isOnGround()) {
            this.jumpKeyDown = true;
        } else if (mc.player.isGliding()) {
            this.jumpKeyDown = false;
        } else {
            this.jumpKeyDown = !prev;
        }
    }

    public boolean isJumpKeyForcedDown() {
        return this.jumpKeyDown;
    }

    private void doGrimEflyStuff() {
        if (this.bounce.get() && mc.player.isOnGround()) {
            mc.player.jump();
        }
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (this.fakeFly.get()) {
            for (Identifier identifier : List.of(
                Identifier.of("minecraft:item.armor.equip_generic"),
                Identifier.of("minecraft:item.armor.equip_netherite"),
                Identifier.of("minecraft:item.armor.equip_elytra"),
                Identifier.of("minecraft:item.armor.equip_diamond"),
                Identifier.of("minecraft:item.armor.equip_gold"),
                Identifier.of("minecraft:item.armor.equip_iron"),
                Identifier.of("minecraft:item.armor.equip_chain"),
                Identifier.of("minecraft:item.armor.equip_leather"),
                Identifier.of("minecraft:item.elytra.flying")
            )) {
                if (identifier.equals(event.sound.getId())) {
                    event.cancel();
                    break;
                }
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (this.avoidPortalTraps.get() && this.highwayObstaclePasser.get()) {
            ChunkPos pos = event.chunk().getPos();
            BlockPos centerPos = pos.getCenterAtY(this.targetY);
            Vec3d moveDir = Utils.yawToDirection(this.yaw.get());
            double distanceToHighway = Utils.distancePointToDirection(Vec3d.of(centerPos), moveDir, playerPos());
            if (distanceToHighway <= 21.0) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = this.targetY; y < this.targetY + 3; y++) {
                            BlockPos position = new BlockPos(pos.getStartX() + x, y, pos.getStartZ() + z);
                            if ((Utils.distancePointToDirection(Vec3d.of(position), moveDir, playerPos())
                                    <= this.portalScanWidth.get().intValue())
                                && mc.world.getBlockState(position).getBlock().equals(Blocks.NETHER_PORTAL)) {
                                BlockPos posBehind = new BlockPos(
                                    (int) Math.floor(position.getX() + moveDir.getX()),
                                    position.getY(),
                                    (int) Math.floor(position.getZ() + moveDir.getZ())
                                );
                                if ((
                                        mc.world.getBlockState(posBehind).isSolidBlock(mc.world, posBehind)
                                            || mc.world.getBlockState(posBehind).getBlock() == Blocks.NETHER_PORTAL
                                    )
                                    && (
                                        this.portalTrap == null
                                            || this.portalTrap.getSquaredDistance(posBehind) > 100.0
                                                && mc.player.getBlockPos().getSquaredDistance(posBehind)
                                                    < mc.player.getBlockPos().getSquaredDistance(this.portalTrap)
                                    )) {
                                    this.portalTrap = posBehind;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private Vec3d playerPos() {
        return new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private boolean hasEnoughFood() {
        return mc.player.getHungerManager().getFoodLevel() >= 20;
    }
}