package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.ShulkerPaletteState;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side cleanup for Shulker Palette placements.
 *
 * After vanilla's placement completes (using our overridden getItemInHand),
 * this RETURN inject:
 *   1. Clears the server override so getItemInHand returns to normal.
 *   2. Decrements the actual item inside the shulker box's contents.
 *
 * Vanilla's shrink(1) hits the copy returned by getItemInHand(), not the real
 * shulker — so we must explicitly decrement the source here.
 */
@Mixin(ServerPlayerGameMode.class)
public class ShulkerPaletteServerMixin {

    // Capture state from HEAD for use in RETURN (same call context).
    @Unique private int trevorMod$spShulkerSlot  = -1;
    @Unique private int trevorMod$spInternalSlot = -1;
    @Unique private boolean trevorMod$spActive   = false;

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void trevorMod$captureShulkerPaletteState(ServerPlayer player, Level level,
                                                       ItemStack stack, InteractionHand hand,
                                                       BlockHitResult hitResult,
                                                       CallbackInfoReturnable<InteractionResult> cir) {
        // Only act if a shulker palette override is active.
        if (ShulkerPaletteState.serverOverride == null) return;

        // Capture the decrement coordinates before anyone clears them.
        trevorMod$spShulkerSlot  = ShulkerPaletteState.serverShulkerInvSlot;
        trevorMod$spInternalSlot = ShulkerPaletteState.serverInternalSlot;
        trevorMod$spActive = true;
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void trevorMod$cleanupShulkerPalette(ServerPlayer player, Level level,
                                                  ItemStack stack, InteractionHand hand,
                                                  BlockHitResult hitResult,
                                                  CallbackInfoReturnable<InteractionResult> cir) {
        if (!trevorMod$spActive) return;
        trevorMod$spActive = false;

        // Clear the server override — getItemInHand returns to normal.
        ShulkerPaletteState.serverOverride       = null;
        ShulkerPaletteState.serverShulkerInvSlot = -1;
        ShulkerPaletteState.serverInternalSlot   = -1;

        // Only decrement if the placement actually consumed an action.
        InteractionResult result = cir.getReturnValue();
        if (result == null || !result.consumesAction()) {
            trevorMod$spShulkerSlot  = -1;
            trevorMod$spInternalSlot = -1;
            return;
        }

        int shulkerSlot  = trevorMod$spShulkerSlot;
        int internalSlot = trevorMod$spInternalSlot;
        trevorMod$spShulkerSlot  = -1;
        trevorMod$spInternalSlot = -1;

        if (shulkerSlot < 0 || internalSlot < 0) return;

        // Creative mode — don't consume items.
        if (player.getAbilities().instabuild) return;

        // Decrement the actual item inside the shulker box.
        // The shulker itself is in the player's hotbar at shulkerSlot.
        ItemStack shulkerStack = player.getInventory().getItem(shulkerSlot);
        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents == null) return;

        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.copyInto(items);
        if (internalSlot >= items.size()) return;

        items.get(internalSlot).shrink(1);
        shulkerStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }
}
