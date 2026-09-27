package com.b2xy.modules;

import com.b2xy.B2XY;
import com.b2xy.managers.SwapManager;
import com.b2xy.util.FadeAnimator;
import com.b2xy.util.GrimUtils;
import com.b2xy.util.RotationUtils;
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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Эндер-сундуки: автоматическая установка и ломание.
 *
 * Цикл: смотришь на эндер-сундук — модуль берёт кирку в руку и непрерывно шлёт
 * START_DESTROY_BLOCK, пока блок не исчезнет; в момент исчезновения подтверждает
 * разрушение пакетом STOP_DESTROY_BLOCK (ребрик). Смотришь в пустое место у грани —
 * модуль достаёт эндер-сундук из инвентаря (в т.ч. не из хотбара) и ставит его.
 *
 * Логика ребрика (обнаружение исчезновения блока, задержка remine-delay, гварды
 * конфликта действий и лимита диг-пакетов) и рендер рамки ломания перенесены из
 * BepMine (fast-mine) этого же аддона.
 */
public class EChestRebreak extends Module {
    private static final int ROTATION_PRIORITY = 45;
    private static final int MAX_DIG_PACKETS_PER_SECOND = 300;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Отображение");

    private final Setting<Boolean> place = sgGeneral.add(new BoolSetting.Builder()
        .name("ставить")
        .description("Ставить эндер-сундук, если под прицелом пустое место у грани блока.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> saveEchests = sgGeneral.add(new IntSetting.Builder()
        .name("оставить-сундуков")
        .description("Сколько эндер-сундуков оставить в инвентаре (ставить, пока есть сверх этого количества).")
        .defaultValue(0)
        .min(0)
        .sliderMax(16)
        .build()
    );

    private final Setting<Boolean> rebreak = sgGeneral.add(new BoolSetting.Builder()
        .name("ребрик")
        .description("После исчезновения блока подтверждать разрушение пакетом STOP_DESTROY_BLOCK.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> remineDelay = sgGeneral.add(new IntSetting.Builder()
        .name("rebreak-задержка")
        .description("Задержка в тиках перед подтверждающим STOP_DESTROY_BLOCK (remine-delay из fast-mine).")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> timeout = sgGeneral.add(new IntSetting.Builder()
        .name("таймаут")
        .description("Сколько тиков ломать сундук, прежде чем сбросить цель. 0 — без ограничения.")
        .defaultValue(60)
        .min(0)
        .sliderMax(120)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("дальность")
        .description("Максимальная дистанция до сундука.")
        .defaultValue(4.5)
        .min(1.0)
        .sliderMax(6.0)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("ротация")
        .description("Поворачиваться на сундук перед действием.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("замах")
        .description("Замахиваться рукой.")
        .defaultValue(true)
        .build()
    );

    // Рендер

    private final Setting<Boolean> renderEnabled = sgRender.add(new BoolSetting.Builder()
        .name("отрисовка")
        .description("Рисовать рамку блока, который ломается.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("режим-фигуры")
        .description("Как отрисовывается фигура.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> colorMining = sgRender.add(new ColorSetting.Builder()
        .name("цвет-майна")
        .description("Цвет рамки во время ломания.")
        .defaultValue(new SettingColor(Color.BLUE.r, Color.BLUE.g, Color.BLUE.b))
        .build()
    );

    private final Setting<SettingColor> colorDone = sgRender.add(new ColorSetting.Builder()
        .name("цвет-готово")
        .description("Цвет рамки, когда сундук сломан.")
        .defaultValue(new SettingColor(Color.CYAN.r, Color.CYAN.g, Color.CYAN.b))
        .build()
    );

    private final Setting<Integer> fadeTime = sgRender.add(new IntSetting.Builder()
        .name("время-затухания")
        .description("Время затухания рамки в миллисекундах.")
        .defaultValue(250)
        .min(0)
        .sliderMax(1000)
        .build()
    );

    // Состояние ломания/ребрика (перенесено из fast-mine).
    private BlockPos rebreakPos;
    private Direction rebreakDir;
    private Block rebreakBlock;
    private boolean rebreakAirSeen;
    private boolean rebreakReplaceSeen;
    private int rebreakSeenTicks;
    private int rebreakStaleTicks;
    private int ticks;

    private final Deque<Long> digPacketTimes = new ArrayDeque<>();
    private boolean digSentThisTick;
    private int lastDigSequence;

    private final FadeAnimator fade = new FadeAnimator();
    private final SettingColor renderBoxColor = new SettingColor();
    private final SettingColor renderLineColor = new SettingColor();
    /** Позиция для отрисовки: хранится отдельно от rebreakPos, чтобы рамка доигрывала затухание. */
    private BlockPos renderPos;

    /** Хотбар-слот, который занимает модуль (кирка/сундук) — для возврата. */
    private int occupiedSlot = -1;

    public EChestRebreak() {
        super(B2XY.CATEGORY, "echest-rebreak", "Ставит эндер-сундук под прицелом и автоматически его ломает (логика ребрика и рендер из fast-mine).");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        this.digSentThisTick = false;

        if (this.mc.player == null || this.mc.world == null) {
            clearRebreak();
            releaseTool();
            return;
        }

        if (this.mc.crosshairTarget instanceof BlockHitResult hit) {
            BlockPos aimed = hit.getBlockPos();
            BlockState state = this.mc.world.getBlockState(aimed);

            if (state.isOf(Blocks.ENDER_CHEST)) {
                mineChest(aimed);
                return;
            }

            // Не сундук под прицелом: если клетка за гранью пустая — ставим.
            tickRebreak();
            if (this.place.get()) tryPlace(hit);
            return;
        }

        // Взгляд в пустоту: только дорабатываем ребрик.
        tickRebreak();
    }

    /** Непрерывное ломание сундука: кирка в руке + START каждый тик до исчезновения. */
    private void mineChest(BlockPos pos) {
        if (this.timeout.get() > 0 && this.ticks > this.timeout.get()) {
            clearRebreak();
            return;
        }

        if (this.rebreakPos == null || !this.rebreakPos.equals(pos)) {
            this.rebreakPos = pos;
            this.rebreakDir = getInteractDirection(pos);
            this.rebreakBlock = this.mc.world.getBlockState(pos).getBlock();
            this.rebreakAirSeen = false;
            this.rebreakReplaceSeen = false;
            this.rebreakSeenTicks = 0;
            this.rebreakStaleTicks = 0;
            this.ticks = 0;
        }
        ticks++;

        if (!inRange(pos)) return;

        // Без кирки эндер-сундук не сломать — держим лучшую кирку в руке.
        if (!ensurePickaxeHeld()) return;

        if (this.rotate.get()) aim(pos);

        // Продолжаем ломание каждый тик.
        sendDig(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, this.rebreakDir);
        if (this.swing.get()) this.mc.player.swingHand(Hand.MAIN_HAND);
    }

    /**
     * Машина состояний ребрика из fast-mine: ждём, пока блок уйдёт в воздух
     * (или заменится), отсчитываем задержку и подтверждаем разрушение пакетом
     * STOP_DESTROY_BLOCK, после чего сбрасываем состояние и освобождаем кирку.
     */
    private void tickRebreak() {
        if (this.rebreakPos == null) return;

        BlockState state = this.mc.world.getBlockState(this.rebreakPos);

        if (state.isAir()) {
            this.rebreakAirSeen = true;
            this.rebreakReplaceSeen = true;
            this.rebreakStaleTicks = 0;
            this.rebreakSeenTicks = 0;
        }
        else if (state.getBlock() != this.rebreakBlock && state.getFluidState().isEmpty()) {
            if (!this.rebreakAirSeen) this.rebreakAirSeen = true;
            this.rebreakReplaceSeen = true;
            this.rebreakBlock = state.getBlock();
        }

        if (!this.rebreakAirSeen) return;

        if (!this.rebreakReplaceSeen && ++this.rebreakStaleTicks < 2) return;

        if (this.rebreak.get()) {
            this.rebreakSeenTicks++;
            int delay = this.remineDelay.get();

            if (this.rebreakSeenTicks <= delay) {
                if (this.rebreakSeenTicks == delay && !actionConflictTick() && ensurePickaxeHeld()) sendSwing();
            }
            else if (!actionConflictTick() && !this.digSentThisTick && digBudgetAvailable()) {
                if (this.rotate.get()) aim(this.rebreakPos);
                sendDig(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, this.rebreakPos, this.rebreakDir);
            }
        }

        // Блок сломан (или ребрит�� завершён) — полный сброс, чтобы не спамить в пустоту.
        clearRebreak();
        releaseTool();
    }

    /** Установка эндер-сундука в пустое место перед игроком. */
    private void tryPlace(BlockHitResult hit) {
        BlockPos target = hit.getBlockPos().offset(hit.getSide());
        BlockState state = this.mc.world.getBlockState(target);
        if (!state.isAir() && !state.isReplaceable()) return;

        int chestSlot = findEchestSlot();
        if (chestSlot < 0) return;
        if (countEchests() <= this.saveEchests.get()) return;

        // Кладём сундук в руку: если он в хотбаре — просто выбираем слот,
        // иначе свопаем инвентарь-слот на текущий хотбар-слот.
        if (chestSlot < 9) {
            this.mc.player.getInventory().setSelectedSlot(chestSlot);
        }
        else if (!swapFromInventory(chestSlot)) {
            return;
        }
        this.occupiedSlot = chestSlot;

        if (this.rotate.get()) aim(target);

        this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hit);
        if (this.swing.get()) this.mc.player.swingHand(Hand.MAIN_HAND);
    }

    // ------------------------------------------------------------------ инвентарь

    /** Индекс слота с эндер-сундуком (хотбар 0-8 или инвентарь 9-35), иначе -1. */
    private int findEchestSlot() {
        for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
            if (this.mc.player.getInventory().getStack(i).isOf(Items.ENDER_CHEST)) return i;
        }
        return -1;
    }

    private int countEchests() {
        int count = 0;
        for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
            if (this.mc.player.getInventory().getStack(i).isOf(Items.ENDER_CHEST)) count++;
        }
        return count;
    }

    /**
     * Кладёт подходящий инструмент в руку. true — подходящий инструмент в руке.
     * Эндер-сундук требует кирку, поэтому проверяем isSuitableFor (как в fast-mine).
     */
    private boolean ensurePickaxeHeld() {
        BlockState state = this.mc.world.getBlockState(this.rebreakPos);
        ItemStack hand = this.mc.player.getMainHandStack();
        if (hand.isSuitableFor(state)) return true;

        int slot = findToolSlot(state);
        if (slot < 0) return false;

        if (slot < 9) {
            this.mc.player.getInventory().setSelectedSlot(slot);
        }
        else if (!swapFromInventory(slot)) {
            return false;
        }
        this.occupiedSlot = slot;
        return true;
    }

    /** Лучший инструмент в инвентаре: приоритет — скорость добычи, сначала хотбар. */
    private int findToolSlot(BlockState state) {
        boolean toolRequired = state.isToolRequired();
        int bestSlot = -1;
        float bestSpeed = 0;

        for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (toolRequired && !stack.isSuitableFor(state)) continue;

            float speed = stack.getMiningSpeedMultiplier(state);
            if (bestSlot < 0 || speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
                if (i < 9) break; // в хотбаре — можно не искать дальше
            }
        }

        return bestSlot;
    }

    /** Своп инвентарь-слота на текущий хотбар-слот (слот >= 9). */
    private boolean swapFromInventory(int slot) {
        if (this.mc.player == null || this.mc.interactionManager == null) return false;
        if (slot < 9 || slot >= this.mc.player.getInventory().size()) return false;

        int hotbar = this.mc.player.getInventory().getSelectedSlot();
        int syncId = this.mc.player.currentScreenHandler.syncId;
        this.mc.interactionManager.clickSlot(syncId, slot, hotbar, SlotActionType.SWAP, this.mc.player);
        return true;
    }

    /** Возвращает инструмент/сундук на место не нужно — достаточно снять занятый слот. */
    private void releaseTool() {
        this.occupiedSlot = -1;
    }

    // ------------------------------------------------------------------ пакеты

    private void sendSwing() {
        if (this.swing.get()) this.mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void sendDig(PlayerActionC2SPacket.Action action, BlockPos pos, Direction direction) {
        this.digPacketTimes.addLast(System.currentTimeMillis());
        this.digSentThisTick = true;
        this.lastDigSequence = GrimUtils.nextSequence();
        this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, pos, direction, this.lastDigSequence));
    }

    private boolean digBudgetAvailable() {
        long now = System.currentTimeMillis();
        while (!this.digPacketTimes.isEmpty() && now - this.digPacketTimes.peekFirst() > 1000L) {
            this.digPacketTimes.pollFirst();
        }
        return this.digPacketTimes.size() < MAX_DIG_PACKETS_PER_SECOND;
    }

    private boolean actionConflictTick() {
        return SwapManager.getInstance().isActionConflictTick();
    }

    private void aim(BlockPos pos) {
        float[] rot = RotationUtils.getRotationsTo(this.mc.player.getEyePos(), Vec3d.ofCenter(pos));
        RotationUtils.getInstance().setRotationFullInstant(this, ROTATION_PRIORITY, rot[0], rot[1]);
    }

    private Direction getInteractDirection(BlockPos pos) {
        Vec3d eyePos = this.mc.player.getEyePos();
        Vec3d posVec = Vec3d.ofCenter(pos);
        Direction bestDir = null;
        double bestDot = -1.0;

        for (Direction dir : Direction.values()) {
            Vec3d dirVec = Vec3d.of(dir.getVector());
            double dot = eyePos.subtract(posVec).normalize().dotProduct(dirVec);
            if (dot > bestDot) {
                bestDot = dot;
                bestDir = dir;
            }
        }
        return bestDir;
    }

    @Override
    public void onDeactivate() {
        clearRebreak();
        releaseTool();
    }

    private void clearRebreak() {
        this.rebreakPos = null;
        this.rebreakDir = null;
        this.rebreakBlock = null;
        this.rebreakAirSeen = false;
        this.rebreakReplaceSeen = false;
        this.rebreakSeenTicks = 0;
        this.rebreakStaleTicks = 0;
        this.ticks = 0;
    }

    private boolean inRange(BlockPos pos) {
        double r = this.range.get();
        return GrimUtils.closestEyeDistanceSqTo(this.mc.player, new Box(pos)) <= r * r;
    }

    // ------------------------------------------------------------------ рендер

    @EventHandler
    private void onRenderWorld(Render3DEvent event) {
        if (this.mc.player == null || this.mc.world == null || !this.renderEnabled.get()) return;

        if (this.rebreakPos != null) this.renderPos = this.rebreakPos;

        BlockPos pos = this.renderPos;
        boolean air = pos == null || this.mc.world.getBlockState(pos).isAir();
        boolean visible = this.rebreakPos != null && !air && inRange(this.rebreakPos);

        double fadeSeconds = this.fadeTime.get() / 1000.0;
        this.fade.update(visible, fadeSeconds, this.fadeTime.get() > 0);
        if (!this.fade.rendering()) {
            this.renderPos = null;
            return;
        }
        if (pos == null) return;

        float factor = this.fade.alpha();
        SettingColor base = air ? this.colorDone.get() : this.colorMining.get();
        this.renderBoxColor.set(base.r, base.g, base.b, (int) (40 * factor));
        this.renderLineColor.set(base.r, base.g, base.b, (int) (100 * factor));

        VoxelShape outline = this.mc.world.getBlockState(pos).getOutlineShape(this.mc.world, pos);
        Box bounds = (outline.isEmpty() ? VoxelShapes.fullCube() : outline).getBoundingBox();

        int total = this.timeout.get() > 0 ? this.timeout.get() : Math.max(this.ticks, 1);
        float scale = MathHelper.clamp((float) this.ticks / total, 0.0F, 1.0F);
        if (air) scale = 1.0F;

        double cx = pos.getX() + (bounds.minX + bounds.maxX) / 2.0;
        double cy = pos.getY() + (bounds.minY + bounds.maxY) / 2.0;
        double cz = pos.getZ() + (bounds.minZ + bounds.maxZ) / 2.0;
        double hx = (bounds.maxX - bounds.minX) / 2.0 * scale;
        double hy = (bounds.maxY - bounds.minY) / 2.0 * scale;
        double hz = (bounds.maxZ - bounds.minZ) / 2.0 * scale;

        event.renderer.box(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz,
            this.renderBoxColor, this.renderLineColor, this.shapeMode.get(), 0);
    }
}
