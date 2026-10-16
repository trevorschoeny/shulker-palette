package com.trevorschoeny.shulkerpalette;

import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Upkeep;
import com.trevorschoeny.shulkerpalette.palette.Drops;
import com.trevorschoeny.shulkerpalette.palette.Scroll;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point. Everything a server needs lives under src/main: the palette state
 * kept on each shulker, and taking blocks out of it on placement. Filled in phase by
 * phase; see design.md in the Other Mods drive.
 */
public final class ShulkerPalette implements ModInitializer {
    public static final String MOD_ID = "shulkerpalette";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Palette.register();
        Upkeep.register();
        Scroll.register();
        Drops.register();
    }
}
