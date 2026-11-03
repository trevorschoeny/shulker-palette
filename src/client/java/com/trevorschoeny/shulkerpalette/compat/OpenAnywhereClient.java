package com.trevorschoeny.shulkerpalette.compat;

import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.panel.AnchorPanelAdapter;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevorschoeny.openanywhere.api.OpenItems;
import com.trevorschoeny.openanywhere.api.ShownItem;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.config.Config;
import com.trevorschoeny.shulkerpalette.library.LibraryClient;
import com.trevorschoeny.shulkerpalette.menu.PaletteControls;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Target;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Shulker Palette on a box opened through Open Anywhere: the palette's icon column beside the
 * box's screen and its library dialogs on it. SlotKit draws the reservation ghosts in the box's
 * empty slots itself, on any menu. Loaded only when Open Anywhere is installed.
 *
 * <p>The box is the one whose screen is in front ({@link ShownItem#get()}), on vanilla's shulker
 * box screen; the controls name it to the server as {@link Target#SHOWN}, and the server checks
 * it is still in front.
 */
public final class OpenAnywhereClient {
    private OpenAnywhereClient() {}

    /** The shulker the panel is for: the shown opened item, while it's a shulker box. */
    private static final PaletteControls.For SHOWN = new PaletteControls.For(
            () -> {
                OpenItems.OpenItem item = ShownItem.get();
                return item != null && Palette.isShulker(item.stack()) ? item.stack() : ItemStack.EMPTY;
            },
            () -> Target.SHOWN);

    private static @Nullable Screen lastScreen;

    public static void register() {
        // The icon column, left of the box's screen and moving with it; hidden with no shulker shown.
        Panel panel = Panel.builder(ShulkerPalette.MOD_ID + ":open_anywhere")
                .in(ModeScope.BOTH, Placement.around(ShownItem.SECTION, PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)))
                // A vertical Flow: what doesn't apply is hidden and the rest close up.
                .elements(List.of(Flow.builder().vertical().gap(0, 2).addAll(PaletteControls.icons(SHOWN)).build()))
                .build()
                // A setting, off by default: the palette is handled in hand unless the player turns it on.
                .visibleWhen(() -> Config.openAnywherePanel() && !SHOWN.stack().get().isEmpty());
        new AnchorPanelAdapter(panel);

        for (Panel dialog : LibraryClient.panels("open_anywhere_", SHOWN)) new ScreenPanelAdapter(dialog).on(ShulkerBoxScreen.class);
        // A newly opened screen starts with no dialog up; a resize (same screen) keeps it.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen != lastScreen && screen instanceof ShulkerBoxScreen) LibraryClient.reset();
            lastScreen = screen;
        });
    }
}
