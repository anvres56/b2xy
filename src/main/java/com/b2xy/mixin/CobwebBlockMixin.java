package com.b2xy.mixin;

import com.b2xy.modules.NoWeb;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Порт CobwebBlockMixin из leonware-клиента: в режиме «Игнор» модуля
 * {@link NoWeb} замедление в паутине отменяется полностью (HEAD + cancel).
 *
 * Сигнатура 1.21.4 сверена с рантайм-джаром
 * ({@code javap -p net.minecraft.block.CobwebBlock}):
 * {@code protected void onEntityCollision(BlockState, World, BlockPos, Entity)}.
 * В 1.21.4 у метода ровно четыре аргумента — ни EntityCollisionHandler,
 * ни дополнительного boolean здесь ещё нет (они появились в 1.21.5+).
 */
@Mixin(CobwebBlock.class)
public class CobwebBlockMixin {
    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void b2xy$onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity,
                                       CallbackInfo ci) {
        if (NoWeb.isIgnoreActive() && entity == MinecraftClient.getInstance().player) {
            ci.cancel();
            NoWeb.onEntityCollideCobweb(pos);
        }
    }
}
