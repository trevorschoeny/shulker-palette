package com.trevorschoeny.shulkerpalette.palette;

/**
 * A container that carries a palette while its shulker is not an item: today a placed
 * shulker box (added to vanilla's block entity by a mixin). The shulker screen's control
 * panel edits the palette through this, whatever is behind the menu.
 */
public interface PaletteHolder {
    Palette shulkerpalette$palette();

    void shulkerpalette$setPalette(Palette palette);
}
