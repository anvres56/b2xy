package com.b2xy.modules;

import com.b2xy.B2XY;
import com.b2xy.util.RotationUtils;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/**
 * HoleSnap — порт {@code me.mioclient.module.movement.HoleSnap} (Mojmap, обфусцированный
 * декомпиляцией) на Meteor/Yarn 1.21.11.
 *
 * Идея оригинала: если игрок давно не падал в дыру (сколько-то секунд подряд бежит
 * мимо), модуль «затягивает» его в ближайшую подходящую дыру — ставит
 * горизонтальное движение в её центр и серверную ротацию на неё.
 *
 * Что восстановлено из хелперов оригинала (все имена там обфусцированы):
 * <ul>
 *   <li>{@code Runnable#do160} + {@code HoleSnapSearchHelper4_5} — поиск дыр
 *       (ячейка воздуха с твёрдым полом, стенки неразрушимые) кубом вокруг игрока;</li>
 *   <li>{@code HoleSnap.getHoleSnapData_2129} — выбор лучшей дыры: ниже игрока,
 *       не закрыта сверху, дистанция в пределах настройки, над дырой пусто,
 *       луч до цели не перекрыт блоком;</li>
 *   <li>{@code HoleSnapSearchHelper4_3.get2511} — базовая скорость 0.2873 (блок/тик)
 *       с поправкой на зелья скорости/медленности;</li>
 *   <li>{@code SearchHelper4_8.getFloatArray2484} — серверная ротация на цель;</li>
 *   <li>{@code HoleSnap.is130} — гейт: красться/наблюдатель/планер/смотрит вверх/
 *       250 мс после серверного телепорта.</li>
 * </ul>
 *
 * Отличия от оригинала (осознанные):
 * <ul>
 *   <li>поиск дыр синхронный раз в тик по небольшому кубу (±радиус по X/Z, -2..+2 по Y)
 *       вместо фонового потока с полным кубом 32³ раз в 50 мс: обращения к миру из
 *       чужого потока в оригинале — источник рассинхрона, а куб ±16 не нужен,
 *       так как отбор идёт по дистанции ≤ 2 блоков;</li>
 *   <li>обход направления в {@code getHoleSnapData2724} сделан по горизонтали
 *       (в декомпиляции остался {@code direction3 != Direction.UP}, из-за чего
 *       проверка проходила и в пол, отсеивая любую дыру с обычным блоком);</li>
 *   <li>ротация идёт через {@link RotationUtils} (owner + GCD-квантование) — сервер
 *       получает корректную ротацию, камера не дёргается;</li>
 *   <li>не портированы настройки {@code shift} и {@code pauseStep}: они завязаны на
 *       модули Mio {@code Warp} и {@code Step}, которых в B2XY нет; форс-вперёд
 *       из тикового обработчика тоже убран — движение всё равно ставится напрямую,
 *       на ввод он не влияет;</li>
 *   <li>взаимоисключающая логика оригинала (движение ставится только когда выключен
 *       его AntiCheat «movementSync», иначе — только запрос ротации) схлопнута:
 *       при активном snap ставятся и движение, и ротация.</li>
 * </ul>
 */
public class HoleSnap extends Module {
    /** Приоритет ротации в RotationUtils (как в ElytraBounce). */
    private static final int ROTATION_PRIORITY = 45;
    /** HoleSnapSearchHelper4_3.val — базовое смещение за тик (0.2873 * 20 = 5.75 б/с). */
    private static final double BASE_SPEED = 0.2873;
    /** Гейт после серверного телепорта, мс (HoleSnap.is130). */
    private static final long TELEPORT_COOLDOWN = 250L;
    /** Сколько тиков назад был замечен в дыре при первом тике модуля (таймаут сразу истёк). */
    private static final long NEVER = -1L;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("авто-выключение")
        .description("Выключать модуль, как только игрок попал в дыру.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> directional = sgGeneral.add(new BoolSetting.Builder()
        .name("только-по-курсу")
        .description("Целиться только в дыры, к которым игрок движется (проверка по вектору скорости). Выключи — будет тянуть в любую ближайшую.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("поворот-к-дыре")
        .description("Серверная (тихая) ротация на цель дыры. GCD-квантование делает RotationUtils, флагов невозможной ротации на Grim не будет.")
        .defaultValue(true)
        .build());

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("высота")
        .description("Верхняя граница цели по Y внутри дыры: центр.y + высота - 0.5, ниже — позиция игрока.")
        .defaultValue(1.0)
        .min(0.0)
        .max(2.0)
        .sliderMin(0.0)
        .sliderMax(2.0)
        .build());

    private final Setting<Double> timeout = sgGeneral.add(new DoubleSetting.Builder()
        .name("таймаут")
        .description("Сколько секунд игрок может бежать мимо дыр, прежде чем модуль дёрнет его в ближайшую. 0 — snap сразу, как только модуль включён.")
        .defaultValue(0.0)
        .min(0.0)
        .max(30.0)
        .sliderMin(0.0)
        .sliderMax(30.0)
        .decimalPlaces(1)
        .build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("дистанция")
        .description("Максимальная дистанция до центра дыры, при которой snap срабатывает.")
        .defaultValue(2.0)
        .min(0.25)
        .max(4.0)
        .sliderMin(0.25)
        .sliderMax(4.0)
        .build());

    private final Setting<Double> speedMult = sgGeneral.add(new DoubleSetting.Builder()
        .name("множитель-скорости")
        .description("Множитель базовой скорости 0.2873 блок/тик (с учётом зелий). 1.0 — примерно 5.75 б/с.")
        .defaultValue(1.0)
        .min(0.1)
        .max(3.0)
        .sliderMin(0.1)
        .sliderMax(3.0)
        .build());

    private final Setting<Double> maxPitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("максимальный-питч")
        .description("0 — выключено. Иначе модуль не работает, если игрок смотрит выше этого угла (в оригинале список значений с переключателем MIN/MAX).")
        .defaultValue(0.0)
        .min(0.0)
        .max(90.0)
        .sliderMin(0.0)
        .sliderMax(90.0)
        .build());

    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("радиус-поиска")
        .description("Радиус сканирования дыр по X/Z вокруг игрока (по Y всегда -2..+2). Больше — точнее, но дороже по блок-статам.")
        .defaultValue(4)
        .min(1)
        .max(12)
        .sliderMax(12)
        .build());

    private final List<Hole> holes = new ArrayList<>();
    private final BlockPos.Mutable searchCursor = new BlockPos.Mutable();

    /** Время, когда игрока последний раз видели в дыре (таймаут). */
    private long lastHoleTime = NEVER;
    /** Время последнего серверного телепорта — гейт в 250 мс. */
    private long lastTeleportTime = NEVER;
    /** Активен ли snap в текущем тике. */
    private boolean active;
    /** Текущая цель (центр дыры). */
    private Vec3d target;

    public HoleSnap() {
        super(B2XY.CATEGORY, "hole-snap", "Затягивает игрока в ближайшую дыру, мимо которой он пробежал.");
    }

    @Override
    public void onActivate() {
        this.lastHoleTime = NEVER;
        this.lastTeleportTime = NEVER;
        this.active = false;
        this.target = null;
    }

    @Override
    public void onDeactivate() {
        this.active = false;
        this.target = null;
        this.holes.clear();
        RotationUtils.getInstance().clearRotations(this);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            this.lastTeleportTime = System.currentTimeMillis();
        }
    }

    /** Решение раз в тик: ищем дыры, обновляем таймаут, целимся в цель. */
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.world == null) {
            stop();
            return;
        }

        searchHoles();

        long now = System.currentTimeMillis();
        if (isInHole()) {
            if (this.autoDisable.get()) {
                toggle();
                return;
            }
            this.lastHoleTime = now;
        }

        if (blocked() || now - this.lastHoleTime < this.timeout.get() * 1000.0) {
            stop();
            return;
        }

        Vec3d hole = findHole();
        if (hole == null) {
            stop();
            return;
        }

        this.target = hole;
        this.active = true;

        if (this.rotate.get()) {
            float[] rotation = rotationTo(hole);
            RotationUtils.getInstance().setRotationSilentInstant(this, ROTATION_PRIORITY, rotation[0], rotation[1]);
        }
    }

    /** Собственно snap: горизонтальное смещение в центр дыры (остальное — как у игрока). */
    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (!this.active || this.target == null || this.mc.player == null) return;

        Vec3d delta = this.target.subtract(playerPos());
        double length = delta.length();
        if (length < 1.0E-4) return;

        // Оригинал: normalize().multiply(min(baseSpeed * speed, дистанция)).
        Vec3d motion = delta.normalize().multiply(Math.min(baseSpeed() * this.speedMult.get(), length));
        ((IVec3d) event.movement).meteor$set(motion.x, event.movement.y, motion.z);
    }

    private Vec3d playerPos() {
        return new Vec3d(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ());
    }

    private void stop() {
        this.active = false;
        this.target = null;
        RotationUtils.getInstance().clearRotations(this);
    }

    /** Гейт из HoleSnap.is130: красться, наблюдатель, планер, взгляд вверх, свежий телепорт. */
    private boolean blocked() {
        if (this.mc.player.isInSneakingPose() || this.mc.player.isSpectator() || this.mc.player.isGliding()) return true;

        double limit = this.maxPitch.get();
        if (limit != 0.0 && this.mc.player.getPitch() < limit) return true;

        return System.currentTimeMillis() - this.lastTeleportTime < TELEPORT_COOLDOWN;
    }

    /** HoleSnapSearchHelper4_3.get2511(true): базовая скорость с зельями. */
    private double baseSpeed() {
        double speed = BASE_SPEED;

        if (this.mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            speed *= 1.0 + 0.2 * (this.mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1);
        }
        if (this.mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
            speed /= 1.0 + 0.2 * (this.mc.player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier() + 1);
        }

        return speed;
    }

    /** Ищем дыры вокруг игрока (Runnable.do160, но синхронно и по кубику поменьше). */
    private void searchHoles() {
        this.holes.clear();

        BlockPos origin = this.mc.player.getBlockPos();
        int radius = this.searchRadius.get();
        BlockPos.Mutable cursor = this.searchCursor;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    Hole hole = getHoleData(cursor, null);
                    if (hole == null) continue;

                    boolean overlapping = false;
                    for (Hole other : this.holes) {
                        if (hole.box().intersects(other.box())) {
                            overlapping = true;
                            break;
                        }
                    }
                    if (!overlapping) this.holes.add(hole);
                }
            }
        }
    }

    /**
     * HoleSnapSearchHelper4_5.getHoleSnapData2724: ячейка воздуха с твёрдым полом и
     * воздухом сверху, стенки — неразрушимые блоки (resistance >= 600). Дыра может
     * быть 1x1 или 1x2 (второй ряд продолжается один раз). covered — над дырой
     * что-то есть (блок через +2), такие дыры в выборку не попадают.
     */
    private Hole getHoleData(BlockPos pos, Direction direction) {
        if (isSolid(pos) || !isSolid(pos.down()) || isSolid(pos.up())) return null;
        if (this.mc.world.getBlockState(pos.down()).isOf(Blocks.END_PORTAL)) return null;

        boolean covered = isSolid(pos.up(2));
        Direction stretch = null;

        for (Direction side : Direction.Type.HORIZONTAL) {
            if (direction != null && side == direction.getOpposite()) continue;

            BlockPos offset = pos.offset(side);
            if (isSolid(offset)) {
                BlockState state = this.mc.world.getBlockState(offset);

                if (state.isOf(Blocks.RESPAWN_ANCHOR)) return null;

                float resistance = state.getBlock().getBlastResistance();
                float hardness = state.getHardness(this.mc.world, offset);
                if (resistance < 600.0f && resistance >= 0.0f || hardness == 0.0f) return null;
            } else {
                // Второй ряд: рекурсия допускает ровно одну клетку (дальше — не дыра).
                if (stretch != null || direction != null || getHoleData(offset, side) == null) return null;
                if (!isSolid(offset.up(2))) covered = false;

                stretch = side;
            }
        }

        Box box = stretch == null ? new Box(pos) : new Box(pos).stretch(stretch.getOffsetX(), 0.0, stretch.getOffsetZ());
        return new Hole(pos.toImmutable(), box, covered);
    }

    /** «Проходимый» блок: воздух, огонь, кнопки, факелы, рельсы, свет. */
    private boolean isSolid(BlockPos pos) {
        BlockState state = this.mc.world.getBlockState(pos);
        return !state.isAir()
            && !state.isOf(Blocks.FIRE)
            && !state.isOf(Blocks.SOUL_FIRE)
            && !(state.getBlock() instanceof ButtonBlock)
            && !(state.getBlock() instanceof TorchBlock)
            && !(state.getBlock() instanceof RailBlock)
            && !state.isOf(Blocks.LIGHT);
    }

    /** HoleSnap.getHoleSnapData_2129: ближайшая подходящая дыра и её центр-цель. */
    private Vec3d findHole() {
        Vec3d pos = playerPos();
        double best = Double.MAX_VALUE;
        Vec3d result = null;

        for (Hole hole : this.holes) {
            if (hole.pos().getY() >= pos.getY() || hole.covered()) continue;

            Vec3d center = hole.box().getCenter();
            Vec3d target = center.withAxis(Direction.Axis.Y, MathHelper.clamp(pos.getY(), center.y, center.y + this.height.get() - 0.5));

            double distance = pos.distanceTo(target);
            if (distance > this.range.get()) continue;
            if (this.directional.get() && !movingTowards(target) && distance > 0.05) continue;

            // Над дырой должно быть пусто до головы, иначе внутрь не упасть.
            if (!this.mc.world.isSpaceEmpty(hole.box().withMaxY(this.mc.player.getBoundingBox().maxY))) continue;

            // Путь к цели не перекрыт: на уровне ног и на уровне головы.
            if (blockedBetween(pos, target)) continue;
            if (blockedBetween(pos.add(0.0, 1.8, 0.0), target.add(0.0, 1.8, 0.0))) continue;

            if (distance < best) {
                best = distance;
                result = target;
            }
        }

        return result;
    }

    /** HoleSnapSearchHelper4_3.is2516: цель впереди по вектору скорости. */
    private boolean movingTowards(Vec3d target) {
        Vec2f input = this.mc.player.input.getMovementInput();
        if (input.x == 0.0f && input.y == 0.0f) return false;

        Vec3d velocity = this.mc.player.getVelocity();
        Vec3d pos = playerPos();
        return pos.add(velocity.x * 10000.0, 0.0, velocity.z * 10000.0).squaredDistanceTo(target)
            < pos.add(velocity.x * -10000.0, 0.0, velocity.z * -10000.0).squaredDistanceTo(target);
    }

    /** HoleSnap.is131: луч игрок -> цель перекрыт блоком. */
    private boolean blockedBetween(Vec3d from, Vec3d to) {
        return this.mc.world.raycast(new RaycastContext(from, to,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.mc.player
        )).getType() != HitResult.Type.MISS;
    }

    /** SearchHelper4_8.getFloatArray2484: yaw/pitch с позиции камеры на цель. */
    private float[] rotationTo(Vec3d target) {
        Vec3d eye = this.mc.player.getCameraPosVec(1.0f);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;

        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz))));
        return new float[]{yaw, pitch};
    }

    /** Игрок стоит в дыре (по блок-позиции с округлённым Y). */
    private boolean isInHole() {
        BlockPos pos = BlockPos.ofFloored(this.mc.player.getX(), Math.round(this.mc.player.getY()), this.mc.player.getZ());
        for (Hole hole : this.holes) {
            if (hole.box().intersects(new Box(pos))) return true;
        }
        return false;
    }

    private record Hole(BlockPos pos, Box box, boolean covered) {}
}
