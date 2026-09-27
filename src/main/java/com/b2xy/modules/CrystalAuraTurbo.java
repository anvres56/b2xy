package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class CrystalAuraTurbo extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> zeroDelays = sgGeneral.add(new BoolSetting.Builder()
        .name("нулевые-задержки")
        .description("Форсирует настройки Crystal Aura: задержка взрыва, размещения и переключения слота = 0 тиков.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> instantTurn = sgGeneral.add(new BoolSetting.Builder()
        .name("мгновенный-поворот")
        .description("Максимальный угол поворота за тик (yaw-steps = 180): разворот на кристалл занимает один тик.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hitImmediately = sgGeneral.add(new BoolSetting.Builder()
        .name("бить-сразу")
        .description("Игнорировать возраст кристалла (ticks-existed = 0) и включить fast-break.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> attacksPerSecond = sgGeneral.add(new IntSetting.Builder()
        .name("атак-в-секунду")
        .description("Максимум атак кристаллов в секунду.")
        .defaultValue(30)
        .range(1, 30)
        .sliderRange(1, 30)
        .build()
    );

    public CrystalAuraTurbo() {
        super(B2XY.CATEGORY, "crystal-aura-turbo", "Разгон метеорной Crystal Aura: нулевые задержки, мгновенный поворот и мгновенный взрыв кристаллов.");
    }

    public boolean zeroDelays() { return zeroDelays.get(); }
    public boolean instantTurn() { return instantTurn.get(); }
    public boolean hitImmediately() { return hitImmediately.get(); }
    public int attacksPerSecond() { return attacksPerSecond.get(); }
}