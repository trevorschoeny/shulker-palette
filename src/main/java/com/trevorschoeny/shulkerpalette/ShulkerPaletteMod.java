package com.trevorschoeny.shulkerpalette;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShulkerPaletteMod implements ModInitializer {
    public static final String MOD_ID = "shulker-palette";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ShulkerPalette.init();
        LOGGER.info("[ShulkerPalette] Loaded successfully!");
    }
}
