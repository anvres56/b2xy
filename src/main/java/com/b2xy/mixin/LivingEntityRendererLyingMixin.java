package com.b2xy.mixin;

import com.b2xy.modules.ElytraBounce;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ElytraBounce: не кладём своё тело при планировании.
 *
 * <p>Позы мало: {@code LivingEntityRenderer} кладёт модель на 90 градусов
 * отдельным поворотом, и он не зависит от позы сущности — метод
 * {@code getLyingPositionRotationDegrees} в базовом рендеререре всегда
 * возвращает 90. Возвращаем 0, и тело остаётся вертикальным, как стоящий
 * или падающий игрок.
 *
 * <p>Правка локальная: свой клиент рисует себя стоящим, сервер по-прежнему
 * получает те же пакеты и считает нас летящими.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererLyingMixin {
    @Inject(method = "getLyingPositionRotationDegrees", at = @At("RETURN"), cancellable = true)
    private void b2xy$standUprightWhileFlying(CallbackInfoReturnable<Float> cir) {
        if (ElytraBounce.lookLikeFalling()) cir.setReturnValue(0.0F);
    }
}
