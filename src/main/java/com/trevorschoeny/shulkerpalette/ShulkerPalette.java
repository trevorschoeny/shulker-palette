package com.trevorschoeny.shulkerpalette;

import net.fabricmc.api.ModInitializer;

/**
 * Common entry point. Everything a server needs lives under src/main: the palette state
 * kept on each shulker, and taking blocks out of it on placement. Filled in phase by
 * phase; see design.md in the Other Mods drive.
 */
public final class ShulkerPalette implements ModInitializer {
    public static final String MOD_ID = "shulkerpalette";

    @Override
    public void onInitialize() {
    }
}
