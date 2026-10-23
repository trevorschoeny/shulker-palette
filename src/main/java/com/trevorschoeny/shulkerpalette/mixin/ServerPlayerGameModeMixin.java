package com.trevorschoeny.shulkerpalette.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trevorschoeny.shulkerpalette.palette.Repaint;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A repainting right-click skips the block's own use, server side. Vanilla skips it when the
 * player sneaks with something in hand; a repaint click counts as that, so a chest, a lever or
 * a crafting table is repainted instead of used. The client's twin is MultiPlayerGameModeMixin.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isSecondaryUseActive()Z"))
    private boolean shulkerpalette$repaintSkipsUse(ServerPlayer player, Operation<Boolean> original) {
        return original.call(player) || Repaint.claimsClick(player);
    }
}
