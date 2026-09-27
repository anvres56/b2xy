package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Hand;

public class AutoXp extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> durabilityThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("прочность-меньше")
        .description("Кидать опыт, когда прочность починяемой вещи ниже этого процента.")
        .defaultValue(70)
        .range(1, 99)
        .sliderMax(99)
        .build()
    );

    private final Setting<Integer> keepLevel = sgGeneral.add(new IntSetting.Builder()
        .name("не-тратить-ниже")
        .description("Не кидать опыт, если уровень меньше этого значения.")
        .defaultValue(0)
        .range(0, 100)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("задержка")
        .description("Задержка между бросками в тиках.")
        .defaultValue(8)
        .range(1, 40)
        .sliderMax(20)
        .build()
    );

    private int timer;

    public AutoXp() {
        super(B2XY.CATEGORY, "auto-xp", "Автоматически кидает бутылки опыта для починки брони с Починкой.");
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (timer-- > 0) return;
        if (mc.player.experienceLevel < keepLevel.get()) return;

        FindItemResult bottle = InvUtils.findInHotbar(Items.EXPERIENCE_BOTTLE);
        if (!bottle.found()) return;

        if (!needsMending()) return;

        InvUtils.swap(bottle.slot(), true);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        InvUtils.swapBack();
        timer = cooldown.get();
    }

    private boolean needsMending() {
        var reg = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        for (net.minecraft.entity.EquipmentSlot slot : net.minecraft.entity.EquipmentSlot.values()) {
            if (!slot.isArmorSlot()) continue;
            var stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty() || !stack.isDamageable()) continue;
            int max = stack.getMaxDamage();
            int dmg = stack.getDamage();
            if (max <= 0 || (max - dmg) * 100 / max >= durabilityThreshold.get()) continue;
            if (EnchantmentHelper.getLevel(reg.getEntry(reg.getValueOrThrow(Enchantments.MENDING)), stack) > 0) return true;
        }
        return false;
    }
}
