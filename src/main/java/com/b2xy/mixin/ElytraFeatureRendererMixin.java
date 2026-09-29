package com.b2xy.mixin;

import com.b2xy.modules.ElytraBounce;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ElytraBounce: прячем крылья элитр у своего игрока.
 *
 * <p>Позы мало — {@code ElytraFeatureRenderer} рисует крылья по своему
 * состоянию (у {@code PlayerEntityRenderState} есть поле {@code glidingTicks})
 * и от позы сущности не зависит. Поэтому фича отключается отдельно, и тоже
 * только для своего игрока: чужие модели продолжают рисоваться как обычно.
 *
 * <p>Цель указана явно, с дескриптором: у фичи есть одноимённый
 * {@code render} с базовым {@code EntityRenderState}, но он объявлен не в
 * ней, а в суперклассе {@code FeatureRenderer}. Ремап по одному имени взял
 * бы именно его, инъекция не нашла бы цели в классе — и с
 * {@code defaultRequire: 1} это падение при загрузке. Поэтому целимся в
 * перегрузку, которая действительно объявлена в {@code ElytraFeatureRenderer}.
 */
@Mixin(ElytraFeatureRenderer.class)
public abstract class ElytraFeatureRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;"
        + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I"
        + "Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
        at = @At("HEAD"), cancellable = true)
    private void b2xy$hideOwnWings(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
        BipedEntityRenderState state, float tickDelta, float tickProgress, CallbackInfo ci) {
        if (ElytraBounce.lookLikeFalling() && MinecraftClient.getInstance().player != null) ci.cancel();
    }
}
