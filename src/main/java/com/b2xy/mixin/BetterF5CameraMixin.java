package com.b2xy.mixin;

import com.b2xy.modules.BetterF5;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перехват камеры для модуля BetterF5 (порт точках инъекций из arbuz CameraMixin).
 *
 * Точки инъекции в 1.21.11 (owner вызовов — сам Camera, подтверждено javap):
 *  - clipToSpace(F)F вызывается ровно один раз внутри update (ветка thirdPerson),
 *    на offset 379 — здесь модуль обновляет состояние, ставит дистанцию и поворот;
 *  - TAIL update — применение сглаженной позиции и поворота.
 */
@Mixin(Camera.class)
public abstract class BetterF5CameraMixin {
    @Shadow private Vec3d pos;

    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPos(Vec3d pos);

    @Unique
    private BetterF5 getBetterF5() {
        return Modules.get().get(BetterF5.class);
    }

    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"), index = 0)
    private float b2xy$modifyCameraDistance(float desiredDistance) {
        BetterF5 module = getBetterF5();
        if (module == null || !module.isActive()) {
            return desiredDistance;
        }

        module.onCameraUpdate((Camera) (Object) this);

        if (module.isRotationModified()) {
            setRotation(module.getRotationYaw(), module.getRotationPitch());
        }

        return module.getDistance();
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void b2xy$onCameraUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        BetterF5 module = getBetterF5();
        if (module == null || !module.isActive()) {
            return;
        }

        module.onCameraUpdate((Camera) (Object) this);

        double smoothX = module.getSmoothX();
        double smoothY = module.getSmoothY();
        double smoothZ = module.getSmoothZ();
        if (module.isPositionModified() && (smoothX != this.pos.x || smoothY != this.pos.y || smoothZ != this.pos.z)) {
            setPos(new Vec3d(smoothX, smoothY, smoothZ));
        }

        if (module.isRotationModified() && !thirdPerson) {
            setRotation(module.getRotationYaw(), module.getRotationPitch());
        }
    }
}