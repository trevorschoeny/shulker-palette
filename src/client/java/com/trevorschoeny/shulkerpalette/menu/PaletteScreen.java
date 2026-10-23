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
import com.trevorschoeny.shulkerpalette.palette.CheatSheet;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
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
        List<PanelElement> rows = new ArrayList<>();
        rows.add(MenuRow.of(() -> sprite(on() ? "power_on" : "power_off"),
                () -> Component.translatable(on() ? "shulkerpalette.menu.power.on" : "shulkerpalette.menu.power.off"),
                () -> act(PanelActions.POWER),
                b -> b.tooltip(() -> Component.translatable(on() ? "shulkerpalette.panel.power.on" : "shulkerpalette.panel.power.off"))));
        rows.add(MenuRow.of(() -> sprite("mode_" + palette().mode().getSerializedName()),
                () -> Component.translatable("shulkerpalette.menu.mode",
                        Component.translatable("shulkerpalette.menu.mode." + palette().mode().getSerializedName())),
                () -> act(PanelActions.MODE),
                b -> b.tooltip(() -> Component.translatable("shulkerpalette.panel.mode." + palette().mode().getSerializedName(),
                                KeysClient.MODIFIER.getTranslatedKeyMessage()))
                        .disabledWhen(() -> !on())));
        rows.add(MenuRow.of(() -> sprite("repaint"),
                () -> Component.translatable(palette().repaint() ? "shulkerpalette.menu.repaint.on" : "shulkerpalette.menu.repaint.off"),
                () -> act(PanelActions.REPAINT),
                b -> b.tooltip(() -> Component.translatable(palette().repaint()
                                ? "shulkerpalette.panel.repaint.on" : "shulkerpalette.panel.repaint.off"))
                        .disabledWhen(() -> !on())
                        .visibleWhen(PaletteScreen::creative)));
        rows.add(MenuRow.of(() -> sprite("save"),
                () -> Component.translatable("shulkerpalette.menu.save"),
                () -> act(PanelActions.SAVE),
                b -> b.tooltip(() -> answerOr(LibraryClient.Answer.SAVE, "shulkerpalette.panel.save"))));
        rows.add(MenuRow.of(() -> sprite("load"),
                () -> Component.translatable("shulkerpalette.menu.load"),
                LibraryClient::toggleLoad,
                b -> b.tooltip(() -> answerOr(LibraryClient.Answer.LOAD, "shulkerpalette.panel.load"))));
        rows.add(MenuRow.of(() -> sprite("clear"),
                () -> Component.translatable("shulkerpalette.menu.clear"),
                () -> act(PanelActions.CLEAR),
                b -> b.tooltip(Component.translatable("shulkerpalette.panel.clear"))
                        .visibleWhen(() -> held().has(CheatSheet.COMPONENT))));
        rows.add(MenuRow.of(() -> sprite("load"),
                () -> Component.translatable("shulkerpalette.library.manage"),
                () -> Minecraft.getInstance().gui.setScreen(new LibraryScreen(Minecraft.getInstance().gui.screen())),
                b -> b));

        Panel main = Panel.builder(ShulkerPalette.MOD_ID + ":menu")
                .style(PanelStyle.RAISED)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.main()))
                .elements(List.of(Flow.builder().vertical().gap(0, 3).addAll(rows).build()))
                .build();
        List<Panel> panels = new ArrayList<>();
        panels.add(main);
        panels.addAll(LibraryClient.panels());
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

    /** The held shulker's palette, as the server last sent it; a plain shulker reads as off. */
    private static Palette palette() {
        return held().getOrDefault(Palette.COMPONENT, Palette.OFF);
    }

    private static boolean on() {
        return palette().on();
    }

    private static boolean creative() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isCreative();
    }

    private static void act(int action) {
        KeysClient.act(Minecraft.getInstance(), action);
    }

    /** A Save or Load button's tooltip: the answer to the last press while it is fresh, else what it does. */
    private static Component answerOr(LibraryClient.Answer button, String key) {
        Component answer = LibraryClient.answerFor(button);
        return answer != null ? answer : Component.translatable(key);
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, name);
    }
}
