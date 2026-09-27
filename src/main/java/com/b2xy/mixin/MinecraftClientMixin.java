package com.b2xy.mixin;

import com.b2xy.managers.SwapManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SwapManager: сброс удержания слота при ручном действии игрока
 * (клик правой кнопкой или атака сущности).
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Shadow
    public ClientPlayerEntity player;

    @Shadow
    public HitResult crosshairTarget;

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void b2xy$onDoItemUse(CallbackInfo ci) {
        if (this.player != null) {
            SwapManager.getInstance().onUserAction();
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void b2xy$onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        if (this.player != null && this.crosshairTarget != null && this.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            SwapManager.getInstance().onUserAction();
        }
    }
}