package com.trevorschoeny.shulkerpalette;

import com.trevorschoeny.shulkerpalette.compat.OpenAnywhereClient;
import net.fabricmc.loader.api.FabricLoader;
import com.trevorschoeny.shulkerpalette.config.Config;
import com.trevorschoeny.shulkerpalette.config.KeysClient;
import com.trevorschoeny.shulkerpalette.ghost.Ghost;
import com.trevorschoeny.shulkerpalette.panel.CheatSheetClient;
import com.trevorschoeny.shulkerpalette.library.LibraryClient;
import com.trevorschoeny.shulkerpalette.panel.RowGrid;
import com.trevorschoeny.shulkerpalette.popup.PalettePopup;
import net.fabricmc.api.ClientModInitializer;

/** Client entry point: the control panel, the held-block render and the HUD strip. */
public final class ShulkerPaletteClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Config.load();
        KeysClient.register();
        RowGrid.register();
        LibraryClient.register();
        CheatSheetClient.register();
        Ghost.register();
        PalettePopup.register();
        // A box opened from the inventory through Open Anywhere gets the palette panel there.
        if (FabricLoader.getInstance().isModLoaded("openanywhere")) OpenAnywhereClient.register();
    }
}
