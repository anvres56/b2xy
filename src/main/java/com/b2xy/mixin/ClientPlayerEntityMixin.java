package com.b2xy.mixin;

import com.b2xy.modules.BepMine;
import com.b2xy.util.RotationUtils;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Silent-ротации (порт из BepHax NEW-SRC): подмена yaw/pitch в sendMovementPackets
 * (бывший sendPosition) на серверные ротации RotationUtils, пока камера свободна.
 * Owner для this.getYaw()/getPitch() внутри sendMovementPackets — ClientPlayerEntity
 * (javac кладёт в constant pool статический тип ресивера).
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    private float b2xy$modifyGetYaw(float original) {
        RotationUtils rotUtils = RotationUtils.getInstance();
        return !rotUtils.isRotating() && !rotUtils.isWireFresh() ? original : rotUtils.getSentYaw();
    }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    private float b2xy$modifyGetPitch(float original) {
        RotationUtils rotUtils = RotationUtils.getInstance();
        return !rotUtils.isRotating() && !rotUtils.isWireFresh() ? original : rotUtils.getSentPitch();
    }

    /**
     * BepMine (Grim): принудительно шлёт тик-пакет движения, когда нужно точное
     * время старта копания (замена удалённого в 1.21.11 BlockStatePredictionHandler).
     */
    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;squaredMagnitude(DDD)D"))
    private double b2xy$forceMiningTickPacket(double original) {
        BepMine bepMine = Modules.get().get(BepMine.class);
        return bepMine != null && bepMine.needsMiningTickPacket() ? Double.MAX_VALUE : original;
    }
}