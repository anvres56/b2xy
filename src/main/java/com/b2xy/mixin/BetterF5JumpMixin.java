package com.b2xy.mixin;

import com.b2xy.modules.BetterF5;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Эмиттер прыжка для BetterF5 (аналог LivingEntityJumpMixin из arbuzhack).
 * Записывает метку времени прыжка в модуль, чтобы тот поднимал камеру на дуге.
 */
@Mixin(LivingEntity.class)
public abstract class BetterF5JumpMixin {
    @Unique
    private BetterF5 getBetterF5() {
        return Modules.get().get(BetterF5.class);
    }

    @Inject(method = "jump", at = @At("HEAD"))
    private void b2xy$onJump(CallbackInfo ci) {
        BetterF5 module = getBetterF5();
        if (module != null && module.isActive()) {
            module.onJump();
        }
    }
}