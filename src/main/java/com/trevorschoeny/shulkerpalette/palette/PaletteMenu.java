package com.trevorschoeny.shulkerpalette.palette;

import org.jspecify.annotations.Nullable;

/** What a mixin adds to the shulker menu. */
public interface PaletteMenu {
    /** The packed palette, as its data slot last synced it. */
    int shulkerpalette$packedPalette();

    /** The palette behind the menu, on the server; null on the client or over another mod's container. */
    @Nullable PaletteHolder shulkerpalette$holder();
}
