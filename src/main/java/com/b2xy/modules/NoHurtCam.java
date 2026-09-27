package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * NoHurtCam (порт из BepHax): убирает тряску/наклон камеры и красный оверлей при получении урона.
 */
public class NoHurtCam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> disableHurtCam = sgGeneral.add(new BoolSetting.Builder()
        .name("отключить-тряску")
        .description("Отключает тряску/наклон камеры при получении урона.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> disableRedOverlay = sgGeneral.add(new BoolSetting.Builder()
        .name("отключить-красный-оверлей")
        .description("Отключает красный оверлей (экран урона) при получении урона.")
        .defaultValue(false)
        .build());

    public NoHurtCam() {
        super(B2XY.CATEGORY, "no-hurt-cam", "Убирает тряску и наклон камеры при получении урона.");
    }

    public boolean shouldDisableHurtCam() {
        return this.isActive() && this.disableHurtCam.get();
    }

    public boolean shouldDisableRedOverlay() {
        return this.isActive() && this.disableRedOverlay.get();
    }
}