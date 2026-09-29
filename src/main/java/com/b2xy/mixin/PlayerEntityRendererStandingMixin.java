package com.b2xy.mixin;

import com.b2xy.modules.ElytraBounce;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ElytraBounce: в F5 игрок выглядит стоящим, а не планирующим.
 *
 * <p>Позы и разворота тела мало: в состоянии отрисовки остаётся флаг
 * {@code isGliding}, и модель по нему ставит руки назад и ведёт взмах
 * крыльев. В первом лице это не видно — своя модель там не рисуется вовсе,
 * а в F5 видно сразу, поэтому гасим и его: {@code isGliding} снимается,
 * счётчик взмаха обнуляется.
 *
 * <p>Живой игрок, его полёт и всё, что считает сервер, не задеты — правится
 * только то, что уходит в отрисовку, и только у своего клиента.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererStandingMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void b2xy$stopGlidingLookInF5(
        PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!ElytraBounce.lookLikeFalling()) return;
        if (!(player instanceof PlayerEntity self)) return;
        if (self != MinecraftClient.getInstance().player) return;

        state.isGliding = false;
        state.glidingTicks = 0.0F;
    }
}
