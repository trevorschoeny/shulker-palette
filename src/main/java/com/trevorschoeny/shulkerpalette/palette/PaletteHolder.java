package com.trevorschoeny.shulkerpalette.palette;

/**
 * A container that carries a palette (and its cheat sheet) while the screen is open over it:
 * a placed shulker box (added to vanilla's block entity by a mixin) or one opened from the hand. The shulker screen's control
 * panel edits the palette through this, whatever is behind the menu.
 */
public interface PaletteHolder {
    Palette shulkerpalette$palette();

    void shulkerpalette$setPalette(Palette palette);

    /** The cheat sheet, empty when there is none. */
    java.util.List<SavedPalette.Entry> shulkerpalette$cheatSheet();

    void shulkerpalette$setCheatSheet(java.util.List<SavedPalette.Entry> entries);
}
