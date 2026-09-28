package com.b2xy.mixin;

import com.b2xy.accessor.InputAccessor;
import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Порт InputMixin из BepHax NEW-SRC: оверрайд движения с нормализацией диагонали.
 *
 * В 1.21.4 у Input нет поля movementVector (оно появилось в 1.21.5+ вместе с
 * переездом на Vec2f). Здесь два отдельных публичных поля:
 * movementSideways (боковое) и movementForward (вперёд) - сверено javap -p
 * net.minecraft.client.input.Input. Поэтому оверрайд пишем прямо в них.
 */
@Mixin(value = Input.class)
public abstract class InputMixin implements InputAccessor {
    @Shadow
    public float movementForward;

    @Shadow
    public float movementSideways;

    @Unique
    private float b2xy$overrideForward = Float.NaN;
    @Unique
    private float b2xy$overrideSideways = Float.NaN;

    @Override
    public float getMovementForward() {
        if (!Float.isNaN(this.b2xy$overrideForward)) {
            return this.b2xy$overrideForward;
        }
        return this.movementForward;
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
        return this.movementSideways;
    }

    @Override
    public void setMovementSideways(float value) {
        this.b2xy$overrideSideways = value;
        this.applyOverrides();
    }

    @Unique
    private void applyOverrides() {
        float sideways = Float.isNaN(this.b2xy$overrideSideways) ? this.movementSideways : this.b2xy$overrideSideways;
        float forward = Float.isNaN(this.b2xy$overrideForward) ? this.movementForward : this.b2xy$overrideForward;
        if (!Float.isNaN(this.b2xy$overrideSideways) && !Float.isNaN(this.b2xy$overrideForward)) {
            float length = (float) Math.sqrt(sideways * sideways + forward * forward);
            if (length > 1.0E-4) {
                sideways /= length;
                forward /= length;
            }
        }

        this.movementSideways = sideways;
        this.movementForward = forward;
        this.b2xy$overrideForward = Float.NaN;
        this.b2xy$overrideSideways = Float.NaN;
    }
}