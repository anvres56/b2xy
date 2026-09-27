package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class InvFix2b2t extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> fixGhostItems = sgGeneral.add(new BoolSetting.Builder()
        .name("фикс-призрачных-предметов")
        .description("Убирает призрачные предметы при перетаскивании шалкеров, связок и карт мышью. Кредит: Enderkill98")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> fixBundles = sgGeneral.add(new BoolSetting.Builder()
        .name("фикс-связок")
        .description("Чинит перевёрнутый порядок предметов в связках на 2b2t, позволяя выбрать нужный предмет.")
        .defaultValue(true)
        .build()
    );

    public InvFix2b2t() {
        super(B2XY.CATEGORY, "invfix-2b2t", "Чинит призрачные предметы и сломанные связки на 2b2t. Кредит: Enderkill98");
    }
}
