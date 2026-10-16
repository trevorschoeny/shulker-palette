package com.trevorschoeny.shulkerpalette;

import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Upkeep;
import com.trevorschoeny.shulkerpalette.palette.CheatSheet;
import com.trevorschoeny.shulkerpalette.palette.Clipboard;
import com.trevorschoeny.shulkerpalette.palette.Drops;
import com.trevorschoeny.shulkerpalette.palette.HeldShulker;
import com.trevorschoeny.shulkerpalette.palette.Keys;
import com.trevorschoeny.shulkerpalette.palette.Scroll;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point. Everything a server needs lives under src/main: the palette state
 * kept on each shulker, and taking blocks out of it on placement.
 */
public final class ShulkerPalette implements ModInitializer {
    public static final String MOD_ID = "shulkerpalette";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Palette.register();
        CheatSheet.register();
        Upkeep.register();
        Scroll.register();
        Drops.register();
        HeldShulker.register();
        Clipboard.register();
        Keys.register();
    }
}
