package com.trevorschoeny.shulkerpalette;

import net.minecraft.world.inventory.DataSlot;

/**
 * Duck interface applied to ShulkerBoxMenu via mixin.
 * Provides access to the synced palette data slot.
 */
public interface ShulkerPaletteMenuAccessor {
    DataSlot trevorMod$getPaletteSlot();
}
