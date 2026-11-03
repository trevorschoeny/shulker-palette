package com.trevorschoeny.shulkerpalette.menu;

import com.trevorschoeny.shulkerpalette.config.KeysClient;
import com.trevorschoeny.shulkerpalette.mixin.ScreenPosAccessor;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Target;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The palette key over a shulker box's slot, in any container screen (the inventory, a chest,
 * the creative inventory): opens the palette menu for that box, and closing the menu goes back
 * to the screen. Not while typing in a text field, where the key is a letter.
 */
public final class HoverOpen {
    private HoverOpen() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof AbstractContainerScreen<?> container) {
                ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !open(container, event));
            }
        });
    }

    /** Opens the menu if this key press is the palette key over a shulker box; true if it did. */
    private static boolean open(AbstractContainerScreen<?> screen, KeyEvent event) {
        if (!KeysClient.MODIFIER.matches(event) || screen.getFocused() instanceof EditBox) return false;
        Slot slot = ((ScreenPosAccessor) screen).shulkerpalette$hoveredSlot();
        if (slot == null || !Palette.isShulker(slot.getItem())) return false;
        int where = target(screen, slot);
        if (where == Integer.MIN_VALUE) return false;
        Minecraft.getInstance().gui.setScreen(new PaletteScreen(new PaletteControls.For(slot::getItem, () -> where), screen));
        // This same key press opened the screen; its release must not also count as the tap
        // that closes it again.
        KeysClient.swallowRelease();
        return true;
    }

    /**
     * How to name the slot to the server: by inventory index for the player's own slots (the
     * creative screen's slots don't match the server's menu), else by its index on the open
     * menu. A creative tab's items aren't real, so they name nothing ({@code MIN_VALUE}).
     */
    private static int target(Screen screen, Slot slot) {
        if (slot.container instanceof Inventory) return Target.inventorySlot(slot.getContainerSlot());
        if (screen instanceof CreativeModeInventoryScreen) return Integer.MIN_VALUE;
        return Target.menuSlot(slot.index);
    }
}
