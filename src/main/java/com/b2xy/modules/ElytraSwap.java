package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * ElytraSwap (порт из BepHax NEW-SRC): автоматически меняет элитры при низкой
 * прочности (поэтапно, до 4 стадий — из инвентаря через хотбар и обратно),
 * плюс боевая защита: при ударе ставит нагрудник, а после — элитры обратно.
 */
public class ElytraSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> durabilityThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("порог-прочности")
        .description("Менять элитры, когда прочность опустится ниже этого значения.")
        .defaultValue(10)
        .min(1)
        .max(100)
        .sliderRange(1, 50)
        .build());
    private final Setting<Boolean> onlyWhileFlying = sgGeneral.add(new BoolSetting.Builder()
        .name("только-в-полёте")
        .description("Менять элитры только во время активного полёта.")
        .defaultValue(false)
        .build());
    private final Setting<Boolean> pauseInInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("пауза-в-инвентаре")
        .description("Не менять, пока открыт инвентарь — чтобы избежать рассинхрона.")
        .defaultValue(true)
        .build());
    private final Setting<Integer> swapCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("кулдаун-замены")
        .description("Тиков ожидания после замены перед следующей проверкой.")
        .defaultValue(100)
        .min(20)
        .max(200)
        .sliderRange(20, 200)
        .build());
    private final Setting<Integer> stageDelay = sgGeneral.add(new IntSetting.Builder()
        .name("задержка-этапа")
        .description("Тиков между этапами замены. Больше — безопаснее для античита.")
        .defaultValue(8)
        .min(3)
        .max(20)
        .sliderRange(3, 20)
        .build());
    private final Setting<Boolean> notifySwap = sgGeneral.add(new BoolSetting.Builder()
        .name("уведомлять-о-замене")
        .description("Сообщать в чат при замене элитр.")
        .defaultValue(true)
        .build());

    private final SettingGroup sgCombat = settings.createGroup("Боевая защита");
    private final Setting<Boolean> swapOnHit = sgCombat.add(new BoolSetting.Builder()
        .name("менять-при-ударе")
        .description("При ударе сущности менять элитры на нагрудник.")
        .defaultValue(false)
        .build());
    private final Setting<Integer> hitProtectionDuration = sgCombat.add(new IntSetting.Builder()
        .name("длительность-защиты")
        .description("Тиков держать нагрудник после удара.")
        .defaultValue(60)
        .min(20)
        .max(200)
        .sliderRange(20, 200)
        .visible(() -> this.swapOnHit.get())
        .build());
    private final Setting<Boolean> autoSwapBack = sgCombat.add(new BoolSetting.Builder()
        .name("авто-возврат")
        .description("Автоматически вернуть элитры после окончания защиты.")
        .defaultValue(true)
        .visible(() -> this.swapOnHit.get())
        .build());
    private final Setting<Boolean> prioritizeNetherite = sgCombat.add(new BoolSetting.Builder()
        .name("приоритет-незерита")
        .description("Приоритет незеритовых нагрудников над алмазными.")
        .defaultValue(true)
        .visible(() -> this.swapOnHit.get())
        .build());

    private int cooldownTimer;
    private boolean needsSwap;
    private int swapStage;
    private int stageTimer;
    private int targetSlot;
    private int newElytraOriginalSlot;
    private int hotbarSlotUsed;
    private ItemStack hotbarOriginalItem;
    private boolean protectionActive;
    private int protectionTimer;
    private int lastHurtTime;
    private boolean needsChestplateSwap;
    private int chestplateSwapStage;
    private int chestplateSlot;
    private ItemStack storedElytra;

    public ElytraSwap() {
        super(B2XY.CATEGORY, "elytra-swap", "Автоматически меняет элитры при низкой прочности и надевает нагрудник при ударе.");
    }

    @Override
    public void onActivate() {
        this.resetSwapState();
    }

    @Override
    public void onDeactivate() {
        this.resetSwapState();
    }

    private void resetSwapState() {
        this.cooldownTimer = 0;
        this.needsSwap = false;
        this.swapStage = 0;
        this.stageTimer = 0;
        this.targetSlot = -1;
        this.newElytraOriginalSlot = -1;
        this.hotbarSlotUsed = -1;
        this.hotbarOriginalItem = ItemStack.EMPTY;
        this.protectionActive = false;
        this.protectionTimer = 0;
        this.lastHurtTime = 0;
        this.needsChestplateSwap = false;
        this.chestplateSwapStage = 0;
        this.chestplateSlot = -1;
        this.storedElytra = ItemStack.EMPTY;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }
        if (this.swapOnHit.get()) {
            this.handleCombatProtection();
        }
        if (this.cooldownTimer > 0) {
            this.cooldownTimer--;
            return;
        }
        if (this.pauseInInventory.get() && this.mc.player.currentScreenHandler != this.mc.player.playerScreenHandler) {
            this.resetSwapState();
            return;
        }
        ItemStack chestItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (this.protectionActive) {
            return;
        }
        if (!chestItem.getItem().equals(Items.ELYTRA)) {
            return;
        }
        if (this.onlyWhileFlying.get() && !this.mc.player.isGliding()) {
            return;
        }
        if (this.needsSwap) {
            this.processSwapStages();
            return;
        }
        int currentDurability = chestItem.getMaxDamage() - chestItem.getDamage();
        if (currentDurability <= this.durabilityThreshold.get()) {
            this.initiateSwap();
        }
    }

    private void initiateSwap() {
        int bestSlot = -1;
        int bestDurability = this.durabilityThreshold.get();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (!stack.getItem().equals(Items.ELYTRA)) {
                continue;
            }
            int durability = stack.getMaxDamage() - stack.getDamage();
            if (durability <= bestDurability) {
                continue;
            }
            bestDurability = durability;
            bestSlot = i;
        }
        if (bestSlot == -1) {
            return;
        }
        this.targetSlot = bestSlot;
        this.needsSwap = true;
        this.swapStage = 1;
        this.stageTimer = 0;
    }

    private void processSwapStages() {
        this.stageTimer++;
        if (this.stageTimer < this.stageDelay.get()) {
            return;
        }
        switch (this.swapStage) {
            case 1 -> {
                this.newElytraOriginalSlot = this.targetSlot;
                if (this.targetSlot >= 9) {
                    int hotbarSlot = -1;
                    if (this.hotbarSlotUsed != -1 && this.hotbarSlotUsed < 9) {
                        hotbarSlot = this.hotbarSlotUsed;
                    } else {
                        for (int i = 0; i < 9; i++) {
                            ItemStack stack = this.mc.player.getInventory().getStack(i);
                            if (!stack.isEmpty() && this.isEssentialItem(stack)) {
                                continue;
                            }
                            hotbarSlot = i;
                            break;
                        }
                        if (hotbarSlot == -1) {
                            hotbarSlot = 0;
                        }
                    }
                    this.hotbarOriginalItem = this.mc.player.getInventory().getStack(hotbarSlot).copy();
                    this.hotbarSlotUsed = hotbarSlot;
                    InvUtils.move().from(this.targetSlot).toHotbar(hotbarSlot);
                    this.targetSlot = hotbarSlot;
                    this.swapStage = 2;
                    this.stageTimer = 0;
                } else {
                    this.hotbarSlotUsed = this.targetSlot;
                    this.hotbarOriginalItem = ItemStack.EMPTY;
                    this.swapStage = 2;
                    this.stageTimer = 0;
                }
            }
            case 2 -> {
                ItemStack toEquip = this.mc.player.getInventory().getStack(this.targetSlot);
                if (!toEquip.getItem().equals(Items.ELYTRA)) {
                    this.resetSwapState();
                    return;
                }
                if (toEquip.getItem().equals(Items.LEATHER_LEGGINGS)
                    || toEquip.getItem().equals(Items.CHAINMAIL_LEGGINGS)
                    || toEquip.getItem().equals(Items.IRON_LEGGINGS)
                    || toEquip.getItem().equals(Items.GOLDEN_LEGGINGS)
                    || toEquip.getItem().equals(Items.DIAMOND_LEGGINGS)
                    || toEquip.getItem().equals(Items.NETHERITE_LEGGINGS)) {
                    this.resetSwapState();
                    return;
                }
                InvUtils.swap(this.targetSlot, false);
                this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
                this.mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.swapBack();
                this.swapStage = 3;
                this.stageTimer = 0;
            }
            case 3 -> {
                if (this.newElytraOriginalSlot >= 9) {
                    InvUtils.move().fromHotbar(this.targetSlot).to(this.newElytraOriginalSlot);
                    if (!this.hotbarOriginalItem.isEmpty()) {
                        this.swapStage = 4;
                        this.stageTimer = 0;
                        return;
                    }
                }
                if (this.notifySwap.get()) {
                    ItemStack newChest = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
                    if (newChest.getItem().equals(Items.ELYTRA)) {
                        int newDurability = newChest.getMaxDamage() - newChest.getDamage();
                        this.info("Элитры заменены, прочность " + newDurability);
                    }
                }
                this.needsSwap = false;
                this.swapStage = 0;
                this.stageTimer = 0;
                this.targetSlot = -1;
                this.cooldownTimer = this.swapCooldown.get();
            }
            case 4 -> {
                if (this.stageTimer < 3) {
                    this.stageTimer++;
                    return;
                }
                for (int i = 9; i < 36; i++) {
                    ItemStack stack = this.mc.player.getInventory().getStack(i);
                    if (!ItemStack.areItemsEqual(stack, this.hotbarOriginalItem)) {
                        continue;
                    }
                    InvUtils.move().from(i).toHotbar(this.hotbarSlotUsed);
                    break;
                }
                this.needsSwap = false;
                this.swapStage = 0;
                this.stageTimer = 0;
                this.targetSlot = -1;
                this.cooldownTimer = this.swapCooldown.get();
            }
        }
    }

    /**
     * Предметы, которые нельзя трогать в хотбаре при временном переносе элитр.
     */
    private boolean isEssentialItem(ItemStack stack) {
        Item item = stack.getItem();
        return item.equals(Items.TOTEM_OF_UNDYING)
            || item.equals(Items.GOLDEN_APPLE)
            || item.equals(Items.ENCHANTED_GOLDEN_APPLE)
            || item.equals(Items.ENDER_PEARL)
            || item.equals(Items.CHORUS_FRUIT);
    }

    private void handleCombatProtection() {
        if (this.mc.player == null) {
            return;
        }
        ItemStack chestItem;
        if (this.mc.player.hurtTime > 0 && this.mc.player.hurtTime > this.lastHurtTime) {
            this.lastHurtTime = this.mc.player.hurtTime;
            chestItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestItem.getItem().equals(Items.ELYTRA) && !this.protectionActive) {
                int bestChestplate = this.findBestChestplate();
                if (bestChestplate != -1) {
                    this.storedElytra = chestItem.copy();
                    this.chestplateSlot = bestChestplate;
                    this.needsChestplateSwap = true;
                    this.chestplateSwapStage = 1;
                    this.stageTimer = 0;
                    this.protectionActive = true;
                    this.protectionTimer = this.hitProtectionDuration.get();
                    if (this.notifySwap.get()) {
                        this.info("Удар! Надеваю нагрудник для защиты.");
                    }
                }
            } else if (this.protectionActive) {
                this.protectionTimer = this.hitProtectionDuration.get();
            }
        }
        if (this.mc.player.hurtTime < this.lastHurtTime) {
            this.lastHurtTime = this.mc.player.hurtTime;
        }
        if (this.needsChestplateSwap) {
            this.processChestplateSwap();
            return;
        }
        if (this.protectionActive && !this.needsChestplateSwap) {
            this.protectionTimer--;
            if (this.protectionTimer <= 0 && this.autoSwapBack.get()) {
                chestItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
                if (!chestItem.getItem().equals(Items.ELYTRA) && !this.storedElytra.isEmpty()) {
                    int elytraSlot = this.findStoredElytra();
                    if (elytraSlot != -1) {
                        this.chestplateSlot = elytraSlot;
                        this.needsChestplateSwap = true;
                        this.chestplateSwapStage = 1;
                        this.stageTimer = 0;
                        if (this.notifySwap.get()) {
                            this.info("Защита окончена, возвращаю элитры.");
                        }
                    } else {
                        this.protectionActive = false;
                        this.storedElytra = ItemStack.EMPTY;
                    }
                } else {
                    this.protectionActive = false;
                    this.storedElytra = ItemStack.EMPTY;
                }
            }
        }
    }

    private void processChestplateSwap() {
        this.stageTimer++;
        if (this.stageTimer < this.stageDelay.get()) {
            return;
        }
        switch (this.chestplateSwapStage) {
            case 1 -> {
                if (this.chestplateSlot >= 9) {
                    int hotbarSlot = 0;
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = this.mc.player.getInventory().getStack(i);
                        if (!stack.isEmpty() && this.isEssentialItem(stack)) {
                            continue;
                        }
                        hotbarSlot = i;
                        break;
                    }
                    InvUtils.move().from(this.chestplateSlot).toHotbar(hotbarSlot);
                    this.chestplateSlot = hotbarSlot;
                }
                this.chestplateSwapStage = 2;
                this.stageTimer = 0;
            }
            case 2 -> {
                ItemStack toEquip = this.mc.player.getInventory().getStack(this.chestplateSlot);
                if (!this.isChestplateItem(toEquip)) {
                    this.needsChestplateSwap = false;
                    this.chestplateSwapStage = 0;
                    return;
                }
                if (toEquip.getItem().equals(Items.LEATHER_LEGGINGS)
                    || toEquip.getItem().equals(Items.CHAINMAIL_LEGGINGS)
                    || toEquip.getItem().equals(Items.IRON_LEGGINGS)
                    || toEquip.getItem().equals(Items.GOLDEN_LEGGINGS)
                    || toEquip.getItem().equals(Items.DIAMOND_LEGGINGS)
                    || toEquip.getItem().equals(Items.NETHERITE_LEGGINGS)) {
                    this.needsChestplateSwap = false;
                    this.chestplateSwapStage = 0;
                    return;
                }
                InvUtils.swap(this.chestplateSlot, false);
                this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
                this.mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.swapBack();
                this.chestplateSwapStage = 3;
                this.stageTimer = 0;
            }
            case 3 -> {
                this.needsChestplateSwap = false;
                this.chestplateSwapStage = 0;
                this.stageTimer = 0;
                this.chestplateSlot = -1;
                ItemStack chestItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
                if (chestItem.getItem().equals(Items.ELYTRA)) {
                    this.protectionActive = false;
                    this.storedElytra = ItemStack.EMPTY;
                }
            }
        }
    }

    private int findBestChestplate() {
        int bestSlot = -1;
        int bestValue = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            int value = this.getChestplateValue(stack);
            if (value <= bestValue) {
                continue;
            }
            bestValue = value;
            bestSlot = i;
        }
        return bestSlot;
    }

    private int getChestplateValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.getItem().equals(Items.NETHERITE_CHESTPLATE)) {
            return (this.prioritizeNetherite.get() ? 1000 : 400) + (stack.getMaxDamage() - stack.getDamage());
        }
        if (stack.getItem().equals(Items.DIAMOND_CHESTPLATE)) {
            return 300 + (stack.getMaxDamage() - stack.getDamage());
        }
        if (stack.getItem().equals(Items.IRON_CHESTPLATE)) {
            return 200 + (stack.getMaxDamage() - stack.getDamage());
        }
        if (stack.getItem().equals(Items.CHAINMAIL_CHESTPLATE)) {
            return 150 + (stack.getMaxDamage() - stack.getDamage());
        }
        if (stack.getItem().equals(Items.GOLDEN_CHESTPLATE)) {
            return 100 + (stack.getMaxDamage() - stack.getDamage());
        }
        if (stack.getItem().equals(Items.LEATHER_CHESTPLATE)) {
            return 50 + (stack.getMaxDamage() - stack.getDamage());
        }
        return 0;
    }

    private boolean isChestplateItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item.equals(Items.ELYTRA)
            || item.equals(Items.NETHERITE_CHESTPLATE)
            || item.equals(Items.DIAMOND_CHESTPLATE)
            || item.equals(Items.IRON_CHESTPLATE)
            || item.equals(Items.GOLDEN_CHESTPLATE)
            || item.equals(Items.CHAINMAIL_CHESTPLATE)
            || item.equals(Items.LEATHER_CHESTPLATE);
    }

    private int findStoredElytra() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (!stack.getItem().equals(Items.ELYTRA)) {
                continue;
            }
            if (Math.abs(stack.getDamage() - this.storedElytra.getDamage()) > 5) {
                continue;
            }
            return i;
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (stack.getItem().equals(Items.ELYTRA)) {
                return i;
            }
        }
        return -1;
    }
}