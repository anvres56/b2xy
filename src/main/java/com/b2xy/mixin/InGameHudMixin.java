package com.b2xy.mixin;

import com.b2xy.modules.NoHurtCam;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoHurtCam: блокирует красный оверлей урона (1.21.11 — InGameHud.renderOverlay(DrawContext, Identifier, float)).
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void b2xy$onRenderOverlay(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        NoHurtCam module = Modules.get().get(NoHurtCam.class);
        if (module != null && module.shouldDisableRedOverlay()) {
            if (MeteorClient.mc.player != null && MeteorClient.mc.player.hurtTime > 0) {
                ci.cancel();
            }
        }
    }
}