package com.trevorschoeny.shulkerpalette;

import com.trevorschoeny.shulkerpalette.config.Config;
import com.trevorschoeny.shulkerpalette.config.KeysClient;
import com.trevorschoeny.shulkerpalette.ghost.Ghost;
import com.trevorschoeny.shulkerpalette.panel.CheatSheetClient;
import com.trevorschoeny.shulkerpalette.library.LibraryClient;
import com.trevorschoeny.shulkerpalette.panel.ControlPanel;
import com.trevorschoeny.shulkerpalette.panel.RowGrid;
import com.trevorschoeny.shulkerpalette.popup.PalettePopup;
import net.fabricmc.api.ClientModInitializer;

/** Client entry point: the control panel, the held-block render and the HUD strip. */
public final class ShulkerPaletteClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Config.load();
        KeysClient.register();
        ControlPanel.register();
        RowGrid.register();
        LibraryClient.register();
        CheatSheetClient.register();
        Ghost.register();
        PalettePopup.register();
    }
}
