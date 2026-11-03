package com.trevorschoeny.shulkerpalette.menu;

import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.MKScreen;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.config.KeysClient;
import com.trevorschoeny.shulkerpalette.library.LibraryClient;
import com.trevorschoeny.shulkerpalette.library.LibraryScreen;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The palette menu: everything about one shulker's palette, without its slots. Opened by tapping
 * the palette key (V) with a shulker in hand, or by pressing it over a shulker box's slot in any
 * screen ({@code HoverOpen}); palette on or off, so a plain shulker can be switched on here too.
 * Opened from a screen, closing it goes back to that screen.
 *
 * <p>Power (the menu stays open), mode, repaint (creative), Save and Load (the library), Clear
 * cheat sheet when there is one, and Manage library. Every button reads the held shulker every
 * frame and asks the server to change it ({@code HeldActions}), so what it shows is always the
 * item's real state. It closes itself if the shulker leaves its place.
 */
public final class PaletteScreen extends MKScreen {
    private final PaletteControls.For box;
    private final @Nullable Screen parent;

    /** The menu for the shulker in hand, opened in the world. */
    public PaletteScreen() {
        this(PaletteControls.For.HAND, null);
    }

    /** The menu for {@code box}; closing it returns to {@code parent} (null: back to the world). */
    public PaletteScreen(PaletteControls.For box, @Nullable Screen parent) {
        super(Component.translatable("shulkerpalette.menu.title"), panels(box));
        this.box = box;
        this.parent = parent;
    }

    private static List<Panel> panels(PaletteControls.For box) {
        List<PanelElement> rows = new ArrayList<>(PaletteControls.rows(box));
        rows.add(MenuRow.of(() -> sprite("load"),
                () -> Component.translatable("shulkerpalette.library.manage"),
                () -> Minecraft.getInstance().gui.setScreen(new LibraryScreen(Minecraft.getInstance().gui.screen())),
                b -> b.disabledWhen(() -> !box.palette().on())));

        Panel main = Panel.builder(ShulkerPalette.MOD_ID + ":menu")
                .style(PanelStyle.RAISED)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.main()))
                .elements(List.of(Flow.builder().vertical().gap(0, 3).addAll(rows).build()))
                .build();
        List<Panel> panels = new ArrayList<>();
        panels.add(main);
        panels.addAll(LibraryClient.panels("", box));
        return panels;
    }

    @Override
    public void tick() {
        super.tick();
        // The menu is about one shulker; with it gone from its place, there's nothing to show.
        if (!Palette.isShulker(box.stack().get())) onClose();
    }

    @Override
    public void onClose() {
        if (parent != null) Minecraft.getInstance().gui.setScreen(parent);
        else super.onClose();
    }

    /**
     * The palette key (V) or the inventory key (E) closes the menu, as E closes an inventory.
     * Not while a library dialog is up: a V typed into a palette's name is a letter.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!LibraryClient.dialogUp()) {
            Minecraft mc = Minecraft.getInstance();
            if (KeysClient.MODIFIER.matches(event)) {
                // V is still down as the menu closes; its release must not count as a tap.
                KeysClient.swallowTap();
                onClose();
                return true;
            }
            if (mc.options.keyInventory.matches(event)) {
                onClose();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, name);
    }
}
