package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.ShulkerPaletteRoll;
import com.trevorschoeny.shulkerpalette.ShulkerPaletteState;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side interception for Shulker Palette (Strategy B).
 *
 * When the player right-clicks with a shulker palette, we roll a random block
 * from its contents and set the override fields. Player.getItemInHand() then
 * returns the rolled item for client-side prediction — no hotbar mutation.
 *
 * This mixin runs AFTER the existing MultiPlayerGameModeMixin (palette maker).
 * If the palette maker already handled the placement (its pending fields are set),
 * we skip. Otherwise, we check for shulker palette.
 */
@Mixin(MultiPlayerGameMode.class)
public class ShulkerPaletteClientMixin {

    /** Whether the current useItemOn() call is a shulker palette placement. */
    @Unique
    private boolean trevorMod$spActive = false;

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void trevorMod$shulkerPaletteHead(LocalPlayer player, InteractionHand hand,
                                               BlockHitResult hitResult,
                                               CallbackInfoReturnable<InteractionResult> cir) {
        if (hand != InteractionHand.MAIN_HAND) return;
        if (!ShulkerPalette.isEnabled()) return;

        ItemStack held = player.getItemInHand(hand);
        if (!ShulkerPalette.isShulkerPalette(held)) return;

        // Roll a random placeable block from the shulker's contents.
        ShulkerPaletteRoll.Result roll = ShulkerPaletteRoll.roll(held);
        if (roll == null) {
            // No placeable blocks inside — cancel the placement entirely
            // so the shulker doesn't get placed as a block.
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        // Build the override stack for client-side prediction.
        ItemStack overrideStack = ShulkerPaletteRoll.stackForId(roll.itemId());
        if (overrideStack.isEmpty()) return; // unknown ID — shouldn't happen

        // Set client override: Player.getItemInHand() will return this during
        // the rest of useItemOn(). Client-side prediction renders the correct block.
        ShulkerPaletteState.clientOverride = overrideStack;

        // Store the roll for the server: item to place + shulker location for decrement.
        // Overwrites any previous unconsumed roll — see ShulkerPaletteState docs for why.
        int shulkerSlot = player.getInventory().getSelectedSlot();
        ShulkerPaletteState.pendingRoll.set(
                new ShulkerPaletteState.PendingRoll(roll.itemId(), shulkerSlot, roll.internalSlot()));

        trevorMod$spActive = true;
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void trevorMod$shulkerPaletteReturn(LocalPlayer player, InteractionHand hand,
                                                 BlockHitResult hitResult,
                                                 CallbackInfoReturnable<InteractionResult> cir) {
        if (!trevorMod$spActive) return;
        trevorMod$spActive = false;

        // Clear the client override. The packet has already been sent with the
        // vanilla serialisation — our getItemInHand override served its purpose
        // for client-side prediction.
        ShulkerPaletteState.clientOverride = null;

        // If the useItemOn didn't consume the action (e.g. another mod cancelled
        // it), the packet may not have been sent and the server won't consume our
        // pendingRoll. Clear it here so it doesn't leak into the next placement.
        InteractionResult result = cir.getReturnValue();
        if (result == null || !result.consumesAction()) {
            ShulkerPaletteState.pendingRoll.set(null);
        }
    }

}
