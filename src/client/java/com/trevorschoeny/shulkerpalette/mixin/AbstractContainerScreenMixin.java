package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.palette.HeldShulker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows that the slot of a shulker open from the hand is inert (the veto in
 * {@link HeldShulker} does the refusing): the slot is greyed out, and a click on it, or a
 * number key that would swap it, plays the dispenser's failure click.
 *
 * <p>Priority below the default so the sound plays even if another mod's hook ends the
 * click first; this only listens, it never cancels.
 */
@Mixin(value = AbstractContainerScreen.class, priority = 500)
abstract class AbstractContainerScreenMixin {
    @Shadow @Final protected AbstractContainerMenu menu;

    /** Grey over the slot and its item, the way a disabled control greys. */
    private static final int GREY = 0xB0606060;

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void shulkerpalette$greyOpenShulker(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (HeldShulker.locks(menu, slot.container, slot.getContainerSlot())) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GREY);
        }
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"))
    private void shulkerpalette$refusedClick(@Nullable Slot slot, int slotId, int buttonNum, ContainerInput input, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean onIt = slot != null && HeldShulker.locks(menu, slot.container, slot.getContainerSlot());
        boolean swapsIt = input == ContainerInput.SWAP && HeldShulker.locks(menu, mc.player.getInventory(), buttonNum);
        if (onIt || swapsIt) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 1.2F));
        }
    }
}
