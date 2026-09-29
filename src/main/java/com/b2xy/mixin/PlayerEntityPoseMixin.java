package com.b2xy.mixin;

import com.b2xy.modules.ElytraBounce;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ElytraBounce: локально выдаём падение за эфли-полёт.
 *
 * <p>Пока игрок скользит на элитрах, {@code PlayerEntity.updatePose} ставит
 * позу {@code SWIMMING} — это и есть поза планирования. Если вместо неё
 * оставить {@code STANDING}, модель рисуется в анимации падения: тело
 * вертикально, руки в стороны, вместо лежащего на спине планера.
 *
 * <p>Движение при этом не трогаем: пакеты позиции уходят ровно те же, что и
 * без этого миксина, поэтому сервер по-прежнему считает нас летящими.
 * Правка действует только на свой клиент — чужих игроков она не касается,
 * поза для них приходит с сервера.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityPoseMixin {
    @Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
    private void b2xy$lookLikeFalling(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!ElytraBounce.lookLikeFalling()) return;
        if (!self.isGliding()) return;

        self.setPose(EntityPose.STANDING);
        ci.cancel();
    }
}
