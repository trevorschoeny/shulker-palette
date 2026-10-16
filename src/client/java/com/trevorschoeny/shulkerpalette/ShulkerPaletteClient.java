package com.trevorschoeny.shulkerpalette;

import com.trevorschoeny.shulkerpalette.ghost.RepaintGhost;
import com.trevorschoeny.shulkerpalette.panel.ControlPanel;
import com.trevorschoeny.shulkerpalette.strip.PaletteStrip;
import net.fabricmc.api.ClientModInitializer;

/** Client entry point: the control panel, the held-block render and the HUD strip. */
public final class ShulkerPaletteClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ControlPanel.register();
        RepaintGhost.register();
        PaletteStrip.register();
    }
}
