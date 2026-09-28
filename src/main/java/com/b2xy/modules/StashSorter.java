package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.List;

/**
 * StashSorter — сам открывает сундук/бочку под прицелом и перекладывает предметы
 * через обычные клики по слотам.
 *
 * <h2>Как работает</h2>
 * Пока экран не открыт, модуль смотрит на блок под прицелом. Если этот блок входит в
 * список {@code блоки}, отправляется обычное взаимодействие правой кнопкой
 * ({@code interactBlock}) — сервер сам присылает окно контейнера, никаких
 * нестандартных пакетов для этого не нужно.
 *
 * <p>Когда окно открыто, граница между слотами контейнера и слотами игрока ищется
 * по владельцу слота: в 1.21.11 у {@code Slot} поля {@code inventory} и {@code id}
 * публичные, поэтому первый слот, чей инвентарь — это инвентарь игрока, и есть
 * граница. Это работает одинаково для сундука, бочки и шалкера, без знания о числе
 * рядов у каждого типа.
 *
 * <p>Перекладывание — shift-клик ({@code QUICK_MOVE}): сервер сам кладёт предмет
 * в противоположную сторону (из контейнера в инвентарь и наоборот). Не нужно
 * водить курсор, и при переполнении сервер просто откажет — модуль это ловит по
 * отсутствию пустых слотов и останавливается.
 *
 * <h2>Замечания по API 1.21.11</h2>
 * <ul>
 *   <li>{@code MinecraftClient.crosshairTarget} — поле с результатом наведения
 *       (в старых версиях было {@code hitResult});</li>
 *   <li>{@code ClientPlayerInteractionManager.interactBlock} возвращает
 *       {@code ActionResult}, у которого есть {@code isAccepted()};</li>
 *   <li>{@code clickSlot} принимает пять аргументов и не требует объекта
 *       {@code Click};</li>
 *   <li>{@code PlayerEntity.closeHandledScreen()} в 1.21.11 protected, поэтому
 *       окно закрывается через публичный {@code Screen.close()};</li>
 *   <li>{@code Slot.getSlot(int)} у {@code HandledScreen} больше нет, слоты берутся
 *       из публичного поля {@code ScreenHandler.slots};</li>
 *   <li>для списка блоков используется {@code BlockListSetting} из Meteor, чтобы
 *       можно было выбирать сундук/бочку прямо в настройках.</li>
 * </ul>
 */
public class StashSorter extends Module {
    /** Что делать с содержимым. */
    public enum Mode {
        /** Только доставать из контейнера. */
        Брать,
        /** Только складывать в контейнер. */
        Класть,
        /** Сначала достать, потом доложить. */
        Оба
    }

    private final SettingGroup sgBlocks = settings.getDefaultGroup();

    private final Setting<List<Block>> blocks = sgBlocks.add(new BlockListSetting.Builder()
        .name("блоки")
        .description("Какие блоки модуль открывает сам, когда на них наведён прицел.")
        .defaultValue(Blocks.CHEST, Blocks.BARREL, Blocks.SHULKER_BOX)
        .build()
    );

    private final SettingGroup sgActions = settings.createGroup("Действия");

    private final Setting<Mode> mode = sgActions.add(new EnumSetting.Builder<Mode>()
        .name("режим")
        .description("Что делать с содержимым контейнера.")
        .defaultValue(Mode.Оба)
        .build()
    );

    private final Setting<List<Item>> takeItems = sgActions.add(new ItemListSetting.Builder()
        .name("что-брать")
        .description("Эти предметы забираются из контейнера. Пустой список означает, что не "
            + "забирается ничего: модуль не будет брать всё подряд без правила.")
        .build()
    );

    private final Setting<List<Item>> putItems = sgActions.add(new ItemListSetting.Builder()
        .name("что-класть")
        .description("Эти предметы складываются в контейнер. Пустой список означает, что не "
            + "кладётся ничего.")
        .build()
    );

    private final SettingGroup sgTiming = settings.createGroup("Тайминги");

    private final Setting<Integer> openDelay = sgTiming.add(new IntSetting.Builder()
        .name("пауза-после-открытия")
        .description("Сколько миллисекунд ждать после открытия окна, прежде чем начать клики. "
            + "Нужно, чтобы сервер успел дослать содержимое.")
        .defaultValue(250)
        .min(0)
        .max(2000)
        .build()
    );

    private final Setting<Integer> clickDelay = sgTiming.add(new IntSetting.Builder()
        .name("задержка-между-кликами")
        .description("Пауза между соседними shift-кликами по слотам.")
        .defaultValue(60)
        .min(0)
        .max(1000)
        .build()
    );

    private final Setting<Integer> clicksPerTick = sgTiming.add(new IntSetting.Builder()
        .name("кликов-на-тик")
        .description("Сколько кликов разрешено за один тик. Больше одного на 2b2t заметно.")
        .defaultValue(1)
        .min(1)
        .max(5)
        .build()
    );

    private final Setting<Boolean> waitStill = sgTiming.add(new BoolSetting.Builder()
        .name("ждать-покоя")
        .description("Открывать и работать только когда игрок стоит: нет горизонтальной "
            + "скорости и не нажаты клавиши движения. На 2b2т это заметно безопаснее.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> closeAfter = sgTiming.add(new BoolSetting.Builder()
        .name("закрывать-после")
        .description("Закрывать окно, когда все нужные предметы обработаны.")
        .defaultValue(true)
        .build()
    );

    /** Открыли ли мы текущее окно сами: чужое окно модуль не трогает. */
    private boolean openedByUs;
    /** Момент открытия окна. */
    private long openedAt;
    /** Момент последнего клика. */
    private long lastClickAt;
    /** Сколько кликов уже сделано в этом тике. */
    private int clicksThisTick;

    public StashSorter() {
        super(B2XY.CATEGORY, "stash-sorter",
            "Сам открывает сундук/бочку и перекладывает предметы shift-кликами.",
            "stashsorter", "autostash");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        openedByUs = false;
        openedAt = 0;
        lastClickAt = 0;
        clicksThisTick = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.world == null) {
            reset();
            return;
        }

        if (this.mc.currentScreen == null) {
            // Экран закрыли — начинаем с чистого листа.
            openedByUs = false;
            clicksThisTick = 0;
            tryOpen();
            return;
        }

        clicksThisTick = 0;
        if (!openedByUs) return;

        if (!(this.mc.currentScreen instanceof HandledScreen<?> screen)) {
            reset();
            return;
        }

        ScreenHandler handler = screen.getScreenHandler();
        if (!(handler instanceof GenericContainerScreenHandler) && !(handler instanceof ShulkerBoxScreenHandler)) {
            // Открылось не то окно (например, сундук игрока): сворачиваем работу.
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - openedAt < this.openDelay.get()) return;

        boolean done = sort(handler, now);
        if (done && this.closeAfter.get() && now - lastClickAt >= this.clickDelay.get()) {
            this.mc.currentScreen.close();
            reset();
        }
    }

    /** Открывает контейнер под прицелом, если он есть в списке. */
    private void tryOpen() {
        if (this.waitStill.get() && isMoving()) return;
        if (!(this.mc.crosshairTarget instanceof BlockHitResult hit)) return;

        Block block = this.mc.world.getBlockState(hit.getBlockPos()).getBlock();
        if (!this.blocks.get().contains(block)) return;

        ActionResult result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hit);
        if (!result.isAccepted()) return;

        openedByUs = true;
        openedAt = System.currentTimeMillis();
        lastClickAt = openedAt;
    }

    /**
     * Перекладывает предметы. Возвращает true, когда делать больше нечего.
     */
    private boolean sort(ScreenHandler handler, long now) {
        int boundary = playerSlotBoundary(handler);
        if (boundary < 0) return true;

        List<Slot> slots = handler.slots;
        boolean taking = this.mode.get() == Mode.Брать || this.mode.get() == Mode.Оба;
        boolean putting = this.mode.get() == Mode.Класть || this.mode.get() == Mode.Оба;

        // Сначала забираем: если инвентарь полон, класть уже бессмысленно.
        if (taking && !this.takeItems.get().isEmpty()) {
            if (hasFreePlayerSlot(slots, boundary)) {
                for (int i = 0; i < boundary && clicksThisTick < this.clicksPerTick.get(); i++) {
                    ItemStack stack = slots.get(i).getStack();
                    if (stack.isEmpty() || !this.takeItems.get().contains(stack.getItem())) continue;
                    if (click(handler, slots.get(i), now)) clicksThisTick++;
                }
            }
        }

        if (putting && !this.putItems.get().isEmpty() && clicksThisTick < this.clicksPerTick.get()) {
            for (int i = boundary; i < slots.size() && clicksThisTick < this.clicksPerTick.get(); i++) {
                ItemStack stack = slots.get(i).getStack();
                if (stack.isEmpty() || !this.putItems.get().contains(stack.getItem())) continue;
                if (click(handler, slots.get(i), now)) clicksThisTick++;
            }
        }

        return !hasMoreWork(slots, boundary, taking, putting);
    }

    /** Индекс первого слота, принадлежащего инвентарю игрока, или -1. */
    private int playerSlotBoundary(ScreenHandler handler) {
        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.slots.get(i).inventory == this.mc.player.getInventory()) return i;
        }
        return -1;
    }

    private boolean hasFreePlayerSlot(List<Slot> slots, int boundary) {
        for (int i = boundary; i < slots.size(); i++) {
            if (slots.get(i).getStack().isEmpty()) return true;
        }
        return false;
    }

    /** Осталось ли что переложить. */
    private boolean hasMoreWork(List<Slot> slots, int boundary, boolean taking, boolean putting) {
        if (taking && !this.takeItems.get().isEmpty()) {
            for (int i = 0; i < boundary; i++) {
                ItemStack stack = slots.get(i).getStack();
                if (!stack.isEmpty() && this.takeItems.get().contains(stack.getItem())) return true;
            }
        }
        if (putting && !this.putItems.get().isEmpty()) {
            for (int i = boundary; i < slots.size(); i++) {
                ItemStack stack = slots.get(i).getStack();
                if (!stack.isEmpty() && this.putItems.get().contains(stack.getItem())) return true;
            }
        }
        return false;
    }

    /** Shift-клик по слоту. Возвращает false, если не прошло время задержки. */
    private boolean click(ScreenHandler handler, Slot slot, long now) {
        if (now - lastClickAt < this.clickDelay.get()) return false;
        lastClickAt = now;
        this.mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, this.mc.player);
        return true;
    }

    private boolean isMoving() {
        if (this.mc.player.getVelocity().lengthSquared() > 0.0025) return true;
        return this.mc.options.forwardKey.isPressed()
            || this.mc.options.backKey.isPressed()
            || this.mc.options.leftKey.isPressed()
            || this.mc.options.rightKey.isPressed()
            || this.mc.options.jumpKey.isPressed();
    }
}
