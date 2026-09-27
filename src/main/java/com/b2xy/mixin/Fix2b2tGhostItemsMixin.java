package com.b2xy.mixin;

import com.b2xy.modules.InvFix2b2t;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public class Fix2b2tGhostItemsMixin<T extends ScreenHandler> {
    @Shadow
    @Final
    protected T handler;

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    public void onMouseDragged(CallbackInfoReturnable<Boolean> cir) {
        InvFix2b2t module = (InvFix2b2t) Modules.get().get(InvFix2b2t.class);
        if (module == null || !module.isActive() || !module.fixGhostItems.get()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.isCreative()) return;

        ItemStack cursorStack = this.handler.getCursorStack();
        if (cursorStack == null || cursorStack.isEmpty()) return;

        if (!cursorStack.isStackable() || cursorStack.getItem() instanceof BundleItem || cursorStack.getItem() instanceof FilledMapItem) {
            cir.setReturnValue(true);
        }
    }
}
