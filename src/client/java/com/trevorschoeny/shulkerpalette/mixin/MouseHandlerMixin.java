package com.trevorschoeny.shulkerpalette.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Scroll;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holding sneak while scrolling with a pick or gradient palette in hand moves the palette's
 * selection instead of the hotbar. Plain scroll, and sneak + scroll with anything else in
 * hand, stay vanilla. Hooked at the one call where vanilla turns the wheel into a hotbar
 * slot, so screens, spectator mode and flying speed are untouched.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onScroll", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void shulkerpalette$scrollPalette(long handle, double xoffset, double yoffset, CallbackInfo ci,
                                              @Local(ordinal = 0) int wheel) {
        if (minecraft.player == null || !minecraft.options.keyShift.isDown()) return;
        Palette palette = Palette.activeOn(minecraft.player.getMainHandItem());
        if (palette == null || !Scroll.scrolls(palette)) return;
        // Vanilla moves the hotbar against the wheel (up is left); the selection follows suit.
        ClientPlayNetworking.send(new Scroll.Payload(-wheel));
        ci.cancel();
    }
}
