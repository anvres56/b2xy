package com.b2xy.mixin;

import com.b2xy.modules.CrystalAuraTurbo;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CrystalAura.class)
public abstract class CrystalAuraTurboMixin {
    @Shadow private Setting<Integer> placeDelay;
    @Shadow private Setting<Integer> breakDelay;
    @Shadow private Setting<Integer> switchDelay;
    @Shadow private Setting<Boolean> smartDelay;
    @Shadow private Setting<Boolean> fastBreak;
    @Shadow private Setting<Integer> ticksExisted;
    @Shadow private Setting<Integer> attackFrequency;
    @Shadow private Setting<Double> yawSteps;

    @Unique
    private CrystalAuraTurbo b2xy$getTurbo() {
        if (Modules.get() == null) return null;
        return (CrystalAuraTurbo) Modules.get().get(CrystalAuraTurbo.class);
    }

    @Inject(method = "onPreTick", at = @At("HEAD"))
    private void b2xy$applyTurboSettings(CallbackInfo ci) {
        CrystalAuraTurbo turbo = b2xy$getTurbo();
        if (turbo == null || !turbo.isActive()) return;

        if (turbo.zeroDelays()) {
            placeDelay.set(0);
            breakDelay.set(0);
            switchDelay.set(0);
        }
        if (turbo.instantTurn()) {
            yawSteps.set(180.0);
        }
        if (turbo.hitImmediately()) {
            smartDelay.set(false);
            fastBreak.set(true);
            ticksExisted.set(0);
        }
        attackFrequency.set(turbo.attacksPerSecond());
    }
}