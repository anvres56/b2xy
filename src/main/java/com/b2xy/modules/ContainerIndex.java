package com.b2xy.modules;

import com.b2xy.B2XY;
import com.b2xy.util.tracker.ChestTrackerDataManager;
import com.b2xy.util.tracker.ContainerIndexRecorder;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/**
 * Индекс контейнеров (порт из BepHax, там — ChestTracker).
 *
 * <p>Помнит, что и где лежит: при каждом открытии контейнера его снимок
 * записывается в json рядом с модулями Meteor, отдельно на каждый сервер.
 * На этом индексе работают тултипы содержимого и автосортировщик стеша.
 *
 * <p>Включать постоянно: пока модуль выключен, индекс не пишется и
 * содержимое в подсказках будет устаревшим.
 */
public class ContainerIndex extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> trackShulkerBoxes = sgGeneral.add(new BoolSetting.Builder()
        .name("сундуки-шалкеры")
        .description("Записывать содержимое шалкеров, поставленных как блок (не только шалкеров-итемов).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> trackEnderChests = sgGeneral.add(new BoolSetting.Builder()
        .name("эндер-сундуки")
        .description("Записывать эндер-сундуки. Их содержимое общее для всего аккаунта, но записей будет много.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> autosave = sgGeneral.add(new BoolSetting.Builder()
        .name("автосохранение")
        .description("Сохранять индекс раз в минуту, чтобы не потерять его при краше.")
        .defaultValue(true)
        .build());

    public ContainerIndex() {
        super(B2XY.CATEGORY, "chest-tracker", "Помнит, что и где лежит: пишет содержимое контейнеров при открытии.");
    }

    @Override
    public void onActivate() {
        ChestTrackerDataManager.onModuleActivate();
        applyOptions();
    }

    @Override
    public void onDeactivate() {
        ChestTrackerDataManager.saveData();
        ChestTrackerDataManager.onModuleDeactivate();
    }

    private void applyOptions() {
        ContainerIndexRecorder.setOptions(trackShulkerBoxes.get(), trackEnderChests.get(), autosave.get());
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        ChestTrackerDataManager.onWorldJoin();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        ChestTrackerDataManager.saveData();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Хука «настройки изменились» в Meteor 1.21.11 нет — сверяем каждый тик
        applyOptions();
        ContainerIndexRecorder.onTick(event);
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        ContainerIndexRecorder.onInventory(event);
    }
}
