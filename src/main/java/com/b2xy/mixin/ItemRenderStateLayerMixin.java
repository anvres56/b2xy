package com.b2xy.mixin;

import com.b2xy.modules.HandChams;
import net.minecraft.client.render.item.ItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HandChams: подменяет цвет вершин слоя, пока рисуется рука.
 *
 * <p>1.21.11 красит предмет по массиву тинтов на слой: {@code initTints} отдаёт
 * его слой за слоем, и ванильный цвет в нём — {@code 0xFFFFFFFF}. Подменяем
 * массив на цвет модуля — и рука красится через ванильные пайплайны, без
 * чужих шейдеров.
 */
@Mixin(ItemRenderState.LayerRenderState.class)
public abstract class ItemRenderStateLayerMixin {
    @Inject(method = "initTints", at = @At("RETURN"), cancellable = true)
    private void b2xy$applyHandChams(int index, CallbackInfoReturnable<int[]> cir) {
        int[] override = HandChams.overrideTints(cir.getReturnValue());
        if (override != null) cir.setReturnValue(override);
    }
}
