package com.b2xy.mixin;

import com.b2xy.modules.ElytraBounce;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ElytraBounce: показываем нагрудник, пока в слоте элитры.
 *
 * <p>Модель брони рисуется по стекам из состояния отрисовки
 * ({@code equippedChestStack}), а не по тому, что реально лежит в слоте.
 * Поэтому подменяем только то, что уходит в рендер: на экра нагрудник,
 * предмет в слоте остаётся элитрой — и полёт, и всё, что считает сервер,
 * продолжает работать как есть.
 *
 * <p>Цель — именно {@code PlayerEntityRenderer}, а не базовый
 * {@code LivingEntityRenderer}: метод {@code updateRenderState} переопределён
 * в каждом рендеререре и имеет свой intermediary-имя, так что инъекция в
 * базовый класс для игроков просто не выполнялась бы.
 *
 * <p>Первый параметр миксина обязан быть {@code PlayerLikeEntity}, а не
 * {@code PlayerEntity}: рендерер обобщён
 * ({@code PlayerEntityRenderer<AvatarlikeEntity extends PlayerLikeEntity>}),
 * и после стирания типов в байткоде остаётся именно {@code PlayerLikeEntity}.
 * С {@code PlayerEntity} инъекция не находит цель по дескриптору и игра
 * падает на старте.
 *
 * <p>Только свой клиент: состояние отрисовки строится локально, то, что
 * видят остальные, приходит с сервера и нас не касается.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererChestplateMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void b2xy$showChestplateInsteadOfElytra(
        PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!ElytraBounce.showChestplate()) return;
        if (!(player instanceof PlayerEntity self)) return;
        if (self != MinecraftClient.getInstance().player) return;

        ItemStack chest = self.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.isEmpty() || !chest.getItem().getName().getString().contains("elytra")) return;

        state.equippedChestStack = new ItemStack(Items.IRON_CHESTPLATE);
    }
}
