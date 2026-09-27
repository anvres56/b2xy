package com.b2xy.mixin;

import com.b2xy.modules.ElytraBounce;
import com.b2xy.modules.NoJumpDelay;
import com.b2xy.modules.RocketBoost;
import com.b2xy.util.RotationUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Миксин на LivingEntity: NoJumpDelay + ElytraBounce (сброс jumpingCooldown),
 * RocketBoost (travelGliding), плавный питч в планировании и яр прыжка (RotationUtils).
 */
@Mixin(value = LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    private int jumpingCooldown;

    private Module noJumpDelay;
    private RocketBoost boostModule;
    private ElytraBounce efly;

    @Unique
    private Module getNoJumpDelay() {
        if (this.noJumpDelay == null) this.noJumpDelay = Modules.get().get(NoJumpDelay.class);
        return this.noJumpDelay;
    }

    @Unique
    private RocketBoost getBoost() {
        if (this.boostModule == null) this.boostModule = Modules.get().get(RocketBoost.class);
        return this.boostModule;
    }

    @Unique
    private ElytraBounce getEfly() {
        if (this.efly == null) this.efly = Modules.get().get(ElytraBounce.class);
        return this.efly;
    }

    @Inject(at = @At("HEAD"), method = "tickMovement")
    private void tickMovement(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Module noJumpDelayModule = getNoJumpDelay();
        ElytraBounce eflyModule = getEfly();
        if (MeteorClient.mc.player != null
            && (MeteorClient.mc.player.equals(self) && eflyModule != null && eflyModule.enabled()
                || noJumpDelayModule != null && noJumpDelayModule.isActive())) {
            this.jumpingCooldown = 0;
        }
    }

    @WrapOperation(method = "calcGlidingVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F"))
    private float b2xy$wrapGlidingPitch(LivingEntity entity, Operation<Float> original) {
        Float pitch = RotationUtils.getInstance().getMovementPitch();
        if (entity != MeteorClient.mc.player || pitch == null) {
            return original.call(entity);
        }
        return pitch;
    }

    @WrapOperation(method = "travelGliding", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;calcGlidingVelocity(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d b2xy$rocketBoost(LivingEntity entity, Vec3d oldVelocity, Operation<Vec3d> original) {
        RocketBoost boost = getBoost();
        if (boost == null || !boost.isActive() || entity != MeteorClient.mc.player) {
            return original.call(entity, oldVelocity);
        }
        Vec3d vanilla = original.call(entity, oldVelocity);
        Vec3d boosted = boost.glideVelocity(oldVelocity, vanilla);
        return boosted != null ? boosted : vanilla;
    }

    @WrapOperation(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float b2xy$wrapSprintJumpYaw(LivingEntity entity, Operation<Float> original) {
        RotationUtils rm = RotationUtils.getInstance();
        return entity == MeteorClient.mc.player && rm.isRotating() ? rm.getRotationYaw() : original.call(entity);
    }
}