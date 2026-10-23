package com.trevorschoeny.shulkerpalette.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trevorschoeny.shulkerpalette.palette.Repaint;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A repainting right-click skips the block's own use, client side, so the client doesn't open
 * a chest's screen or flip a lever ahead of the server. Twin of ServerPlayerGameModeMixin.
 */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @WrapOperation(method = "performUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isSecondaryUseActive()Z"))
    private boolean shulkerpalette$repaintSkipsUse(LocalPlayer player, Operation<Boolean> original) {
        return original.call(player) || Repaint.claimsClick(player);
    }
}
