package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.panel.CheatSheetClient;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A cheat sheet's ghost items in a shulker screen's empty slots: what goes where, and how many. */
@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow protected @Nullable Slot hoveredSlot;

    /** Vanilla's ghost-item look (the recipe book's): the item, then this over it. */
    private static final int GHOST = 0x808B8B8B;

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void shulkerpalette$slotMarks(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        // A cheat sheet's ghost in an empty box slot: what goes there.
        if (menu instanceof ShulkerBoxMenu && !(slot.container instanceof Inventory) && !slot.hasItem()) {
            ItemStack ghost = CheatSheetClient.ghost(menu.containerId, slot.getContainerSlot());
            if (!ghost.isEmpty()) {
                graphics.item(ghost, slot.x, slot.y);
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GHOST);
                // The count over the fade, so it reads clearly: how many go here.
                graphics.itemDecorations(Minecraft.getInstance().font, ghost, slot.x, slot.y);
                // Hovering a ghost says what to put there and how many.
                if (slot == hoveredSlot) {
                    graphics.setTooltipForNextFrame(Minecraft.getInstance().font, List.of(
                            ghost.getHoverName(),
                            Component.translatable("shulkerpalette.ghost.place", ghost.getCount()).withStyle(ChatFormatting.GRAY)),
                            Optional.empty(), mouseX, mouseY);
                }
            }
        }
    }
}
