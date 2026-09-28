package com.b2xy.mixin;

import com.b2xy.modules.HandChams;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HandChams: обрамляет проход рендера руки.
 *
 * <p>Рука рисуется отложенно, и тинты вершин читаются уже во время отрисовки
 * слоёв, а не при вызове рендера. Поэтому нужен явный гейт: открываем его на
 * входе в рендер руки и закрываем на выходе, иначе цвет уехал бы на все
 * предметы в мире.
 *
 * <p>У {@code HeldItemRenderer} две перегрузки с именем {@code renderItem}, и
 * просто по имени миксин попадает не туда: ремап берёт ту, что на 6
 * аргументов, и сигнатура инъекции не совпадает — с {@code defaultRequire: 1}
 * это падение на загрузке класса. Поэтому перегрузка указана явно, с
 * дескриптором.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    /** {@code renderItem(float, MatrixStack, OrderedRenderCommandQueue, ClientPlayerEntity, int)}. */
    private static final String HAND_RENDER =
        "renderItem(FLnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
            + "Lnet/minecraft/client/network/ClientPlayerEntity;I)V";

    @Inject(method = HAND_RENDER, at = @At("HEAD"))
    private void b2xy$beginHandChams(float tickProgress, MatrixStack matrices,
        OrderedRenderCommandQueue queue, ClientPlayerEntity player, int light, CallbackInfo ci) {
        HandChams.beginHandPass();
    }

    @Inject(method = HAND_RENDER, at = @At("RETURN"))
    private void b2xy$endHandChams(float tickProgress, MatrixStack matrices,
        OrderedRenderCommandQueue queue, ClientPlayerEntity player, int light, CallbackInfo ci) {
        HandChams.endHandPass();
    }
}
