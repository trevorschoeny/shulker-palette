package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.ShulkerPaletteState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The core of Strategy B: intercepts LivingEntity.getItemInHand() to return the
 * shulker palette override item during placement.
 *
 * Targets LivingEntity (where getItemInHand is defined) rather than Player.
 * The check for client vs server uses the level's isClientSide() method.
 *
 * The override is scoped precisely:
 *   - Client: set in ShulkerPaletteClientMixin HEAD, cleared in RETURN.
 *   - Server: set in ShulkerPalettePacketMixin HEAD, cleared in ShulkerPaletteServerMixin RETURN.
 *
 * Only MAIN_HAND is overridden — off-hand is never affected.
 */
@Mixin(LivingEntity.class)
public class ShulkerPalettePlayerMixin {

    @Inject(method = "getItemInHand", at = @At("RETURN"), cancellable = true)
    private void trevorMod$overrideForShulkerPalette(InteractionHand hand,
                                                      CallbackInfoReturnable<ItemStack> cir) {
        if (hand != InteractionHand.MAIN_HAND) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) {
            // Client thread — return the client override if active.
            ItemStack override = ShulkerPaletteState.clientOverride;
            if (override != null) {
                cir.setReturnValue(override.copy());
            }
        } else {
            // Server thread — return the server override if active.
            ItemStack override = ShulkerPaletteState.serverOverride;
            if (override != null) {
                cir.setReturnValue(override.copy());
            }
        }
    }
}
