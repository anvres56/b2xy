package com.b2xy.mixin;

import com.b2xy.accessor.InputAccessor;
import net.minecraft.client.input.Input;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Порт InputMixin из BepHax NEW-SRC: оверрайд движения с нормализацией диагонали.
 * 1.21.5: Input.movementVector (Vec2f, x=боковое, y=вперёд).
 */
@Mixin(value = Input.class)
public abstract class InputMixin implements InputAccessor {
    @Shadow
    protected Vec2f movementVector;

    @Unique
    private float b2xy$overrideForward = Float.NaN;
    @Unique
    private float b2xy$overrideSideways = Float.NaN;

    @Override
    public float getMovementForward() {
        if (!Float.isNaN(this.b2xy$overrideForward)) {
            return this.b2xy$overrideForward;
        }
        return this.movementVector != null ? this.movementVector.y : 0.0f;
    }

    @Override
    public void setMovementForward(float value) {
        this.b2xy$overrideForward = value;
        this.applyOverrides();
    }

    @Override
    public float getMovementSideways() {
        if (!Float.isNaN(this.b2xy$overrideSideways)) {
            return this.b2xy$overrideSideways;
        }
        return this.movementVector != null ? this.movementVector.x : 0.0f;
    }

    @Override
    public void setMovementSideways(float value) {
        this.b2xy$overrideSideways = value;
        this.applyOverrides();
    }

    @Unique
    private void applyOverrides() {
        if (this.movementVector != null) {
            float sideways = Float.isNaN(this.b2xy$overrideSideways) ? this.movementVector.x : this.b2xy$overrideSideways;
            float forward = Float.isNaN(this.b2xy$overrideForward) ? this.movementVector.y : this.b2xy$overrideForward;
            if (!Float.isNaN(this.b2xy$overrideSideways) && !Float.isNaN(this.b2xy$overrideForward)) {
                float length = (float) Math.sqrt(sideways * sideways + forward * forward);
                if (length > 1.0E-4) {
                    sideways /= length;
                    forward /= length;
                }
            }

            this.movementVector = new Vec2f(sideways, forward);
            this.b2xy$overrideForward = Float.NaN;
            this.b2xy$overrideSideways = Float.NaN;
        }
    }
}