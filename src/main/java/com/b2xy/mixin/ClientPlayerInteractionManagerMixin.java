package com.b2xy.mixin;

import com.b2xy.modules.BepMine;
import com.b2xy.util.RotationUtils;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Silent-ротации для использования предмета (method_41929 — сборщик пакета
 * PlayerInteractItemC2SPacket с ротацией игрока) и DAMAGE-режим BepMine
 * (порог destroyProgress в 1.21.11 — currentBreakingProgress).
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Shadow
    private float currentBreakingProgress;

    @ModifyExpressionValue(method = "method_41929", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"))
    private float b2xy$useItemYaw(float original) {
        RotationUtils rotations = RotationUtils.getInstance();
        return !rotations.isRotating() && !rotations.isWireFresh() ? original : rotations.getSentYaw();
    }

    @ModifyExpressionValue(method = "method_41929", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getPitch()F"))
    private float b2xy$useItemPitch(float original) {
        RotationUtils rotations = RotationUtils.getInstance();
        return !rotations.isRotating() && !rotations.isWireFresh() ? original : rotations.getSentPitch();
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void b2xy$onUpdateBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        if (bepMine != null
            && bepMine.isActive()
            && bepMine.getModeConfig().get() == BepMine.SpeedmineMode.DAMAGE
            && this.currentBreakingProgress >= bepMine.getEffectiveThreshold()) {
            this.currentBreakingProgress = 1.0F;
            cir.setReturnValue(true);
        }
    }
}