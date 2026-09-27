package com.b2xy.mixin;

import com.b2xy.modules.InvFix2b2t;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCursorItemS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class Fix2b2tBundlesMixin {
    @Unique
    private void b2xy$fixBundle(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        InvFix2b2t module = (InvFix2b2t) Modules.get().get(InvFix2b2t.class);
        if (module == null || !module.isActive() || !module.fixBundles.get()) return;

        if (stack.contains(DataComponentTypes.BUNDLE_CONTENTS)) {
            stack.get(DataComponentTypes.BUNDLE_CONTENTS).iterate().forEach(this::b2xy$fixBundle);
        } else if (stack.contains(DataComponentTypes.CONTAINER)) {
            stack.get(DataComponentTypes.CONTAINER).stream().forEach(this::b2xy$fixBundle);
        }

        if (!stack.contains(DataComponentTypes.BUNDLE_CONTENTS)) return;
        BundleContentsComponent contents = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        List<ItemStack> reversed = contents.stream().toList().reversed();
        stack.set(DataComponentTypes.BUNDLE_CONTENTS, new BundleContentsComponent(reversed));
    }

    private static boolean b2xy$onMainThread() {
        return MinecraftClient.getInstance().isOnThread();
    }

    @Inject(method = "onInventory", at = @At("HEAD"))
    public void onInventory(InventoryS2CPacket packet, CallbackInfo info) {
        if (b2xy$onMainThread()) {
            packet.contents().forEach(this::b2xy$fixBundle);
            this.b2xy$fixBundle(packet.cursorStack());
        }
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"))
    public void onScreenHandlerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo info) {
        if (b2xy$onMainThread()) {
            this.b2xy$fixBundle(packet.getStack());
        }
    }

    @Inject(method = "onSetPlayerInventory", at = @At("HEAD"))
    public void onSetPlayerInventory(SetPlayerInventoryS2CPacket packet, CallbackInfo info) {
        if (b2xy$onMainThread()) {
            this.b2xy$fixBundle(packet.contents());
        }
    }

    @Inject(method = "onSetCursorItem", at = @At("HEAD"))
    public void onSetCursorItem(SetCursorItemS2CPacket packet, CallbackInfo info) {
        if (b2xy$onMainThread()) {
            this.b2xy$fixBundle(packet.contents());
        }
    }
}
