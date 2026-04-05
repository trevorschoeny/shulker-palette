package com.trevorschoeny.shulkerpalette;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * Renders the representative item overlay on palette shulkers in the GUI.
 *
 * Called from the HUD render callback (hotbar) and inventory screen mixin.
 * Renders the most common block from inside the shulker as a small scaled
 * item icon centered on the slot.
 */
public class ShulkerPaletteOverlayRenderer {

    /**
     * Renders the representative item overlay for a palette shulker at a slot position.
     *
     * @param graphics  The GuiGraphics context.
     * @param stack     The ItemStack in the slot.
     * @param slotX     The slot's X position (top-left of the 16x16 icon area).
     * @param slotY     The slot's Y position.
     */
    public static void renderOverlay(GuiGraphics graphics, ItemStack stack, int slotX, int slotY) {
        if (!ShulkerPalette.isEnabled()) return;
        if (!ShulkerPalette.isShulkerPalette(stack)) return;

        ItemStack representative = ShulkerPaletteRoll.mostCommonItem(stack);
        if (representative.isEmpty()) return;

        float scale = ShulkerPaletteState.INNER_ITEM_SCALE_GUI;

        // Scale and center: the item renders at 16x16 normally.
        // At 0.5 scale it's 8x8. We offset by (16 - 8) / 2 = 4 to center it.
        int offset = (int) (16 * (1.0f - scale) / 2.0f);

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(slotX + offset, slotY + offset);
        pose.scale(scale, scale);
        // Render at (0,0) in the scaled space — the translate handles positioning.
        graphics.renderFakeItem(representative, 0, 0);
        pose.popMatrix();
    }
}
