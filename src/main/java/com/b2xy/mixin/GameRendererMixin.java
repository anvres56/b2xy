package com.b2xy.mixin;

import com.b2xy.modules.NoHurtCam;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoHurtCam: блокирует наклон камеры при уроне (1.21.11 — GameRenderer.tiltViewWhenHurt(MatrixStack, float)).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void b2xy$onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        NoHurtCam module = Modules.get().get(NoHurtCam.class);
        if (module != null && module.shouldDisableHurtCam()) {
            ci.cancel();
        }
    }
}