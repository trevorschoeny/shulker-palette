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
import net.minecraft.world.item.ItemStack;

/**
 * The palette menu: everything about the palette in hand, without its slots. Opened by tapping
 * the palette key (V) with a shulker in hand, palette on or off, so a plain shulker can be
 * switched on here too.
 *
 * <p>Power (the menu stays open), mode, repaint (creative), Save and Load (the library), Clear
 * cheat sheet when there is one, and Manage library. Every button reads the held shulker every
 * frame and asks the server to change it ({@code HeldActions}), so what it shows is always the
 * item's real state. It closes itself if the shulker leaves the hand.
 */
public final class PaletteScreen extends MKScreen {
    public PaletteScreen() {
        super(Component.translatable("shulkerpalette.menu.title"), panels());
    }

    private static List<Panel> panels() {
        List<PanelElement> rows = new ArrayList<>(PaletteControls.rows(PaletteControls.For.HAND));
        rows.add(MenuRow.of(() -> sprite("load"),
                () -> Component.translatable("shulkerpalette.library.manage"),
                () -> Minecraft.getInstance().gui.setScreen(new LibraryScreen(Minecraft.getInstance().gui.screen())),
                b -> b.disabledWhen(() -> !PaletteControls.For.HAND.palette().on())));

        Panel main = Panel.builder(ShulkerPalette.MOD_ID + ":menu")
                .style(PanelStyle.RAISED)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.main()))
                .elements(List.of(Flow.builder().vertical().gap(0, 3).addAll(rows).build()))
                .build();
        List<Panel> panels = new ArrayList<>();
        panels.add(main);
        panels.addAll(LibraryClient.panels("", PaletteControls.For.HAND));
        return panels;
    }

    @Override
    public void tick() {
        super.tick();
        // The menu is about the shulker in hand; with none there, there's nothing to show.
        if (!Palette.isShulker(held())) onClose();
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

    private static ItemStack held() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, name);
    }
}
