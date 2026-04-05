package com.trevorschoeny.shulkerpalette;

/**
 * Duck interface applied to ShulkerBoxBlockEntity via mixin.
 * Provides access to the "is palette" flag from outside the mixin.
 */
public interface ShulkerPaletteAccessor {
    boolean trevorMod$isPalette();
    void trevorMod$setPalette(boolean value);
}
