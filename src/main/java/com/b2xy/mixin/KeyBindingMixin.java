package com.b2xy.mixin;

import com.b2xy.modules.ElytraBounce;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ElytraBounce: подмена isPressed для клавиш вперёд/прыжка, пока bounce активен
 * (порт из BepHax NEW-SRC; 1.21.11 — KeyBinding.isPressed()/getId()).
 */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    @Shadow
    @Final
    private String id;

    @Unique
    private ElytraBounce efly = null;

    @Inject(method = "isPressed", at = @At("RETURN"), cancellable = true)
    private void b2xy$isPressed(CallbackInfoReturnable<Boolean> cir) {
        if (Modules.get() != null) {
            if (this.efly == null) {
                this.efly = Modules.get().get(ElytraBounce.class);
            }
            if (this.efly != null && this.efly.isActive() && this.efly.enabled()) {
                if (this.id.equals("key.forward")) {
                    cir.setReturnValue(true);
                } else if (this.id.equals("key.jump") && this.efly.shouldAutoJump()) {
                    cir.setReturnValue(this.efly.isJumpKeyForcedDown());
                }
            }
        }
    }
}