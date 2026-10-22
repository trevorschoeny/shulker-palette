package com.trevorschoeny.shulkerpalette.library;

import com.trevlar.menukit.api.panel.MKScreen;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The palette library on a screen of its own, for players without Mod Hub (and, until Mod Hub
 * hosts custom pages, for everyone). It holds {@link LibraryPage} and nothing else. Escape
 * returns to the screen it was opened from: the settings, or the game from a box's Load list.
 */
public final class LibraryScreen extends MKScreen {
    public LibraryScreen(@Nullable Screen parent) {
        super(Component.translatable("shulkerpalette.library.title"), ScreenKind.DEFAULT, List.of(
                Panel.builder(ShulkerPalette.MOD_ID + ":library")
                        .style(PanelStyle.NONE)
                        .in(ModeScope.BOTH, Placement.screen(PanelPosition.main()))
                        .elements(LibraryPage.build())
                        .build()));
        setReturnAction(() -> Minecraft.getInstance().gui.setScreen(parent));
    }
}
