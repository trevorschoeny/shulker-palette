package com.trevorschoeny.shulkerpalette.panel;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.layout.Column;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.palette.Mode;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PaletteMenu;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The palette's control panel: a column of icon buttons on the left of the shulker screen.
 * It holds no state. Every button reads the palette the server last synced into the menu
 * and asks the server to change it; the new state comes back the same way.
 *
 * <p>Palette off shows only the power button, so the screen looks like vanilla's. Rows and
 * save/load join the column in later phases (design.md).
 */
public final class ControlPanel {
    private ControlPanel() {}

    private static final int SIZE = 18;
    private static final int TINT_ON = 0x6040C040;

    public static void register() {
        Panel panel = Panel.builder(ShulkerPalette.MOD_ID + ":controls")
                .position(PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP))
                .elements(Column.at(0, 0).spacing(2)
                        .add(Button.builder()
                                .icon(() -> sprite(palette().map(Palette::on).orElse(false) ? "power_on" : "power_off"))
                                .size(SIZE, SIZE)
                                .onClick(() -> press(PanelActions.POWER))
                                .tooltip(() -> Component.translatable(palette().map(Palette::on).orElse(false)
                                        ? "shulkerpalette.panel.power.on" : "shulkerpalette.panel.power.off"))
                                .build())
                        .add(Button.builder()
                                .icon(() -> sprite("mode_" + mode().getSerializedName()))
                                .size(SIZE, SIZE)
                                .onClick(() -> press(PanelActions.MODE))
                                .tooltip(() -> Component.translatable("shulkerpalette.panel.mode." + mode().getSerializedName()))
                                .visibleWhen(ControlPanel::on)
                                .build())
                        .add(Button.builder()
                                .icon(() -> sprite("repaint"))
                                .size(SIZE, SIZE)
                                .tint(() -> palette().map(Palette::repaint).orElse(false) ? TINT_ON : 0)
                                .onClick(() -> press(PanelActions.REPAINT))
                                .tooltip(() -> Component.translatable(palette().map(Palette::repaint).orElse(false)
                                        ? "shulkerpalette.panel.repaint.on" : "shulkerpalette.panel.repaint.off"))
                                .visibleWhen(() -> on() && creative())
                                .build())
                        .build())
                .build()
                .visibleWhen(() -> palette().isPresent());

        new ScreenPanelAdapter(panel).on(ShulkerBoxScreen.class);
    }

    /** The open shulker screen's palette, as last synced; empty on a container with no palette behind it. */
    private static Optional<Palette> palette() {
        return Minecraft.getInstance().gui.screen() instanceof ShulkerBoxScreen screen
                && screen.getMenu() instanceof PaletteMenu menu
                ? PanelActions.unpack(menu.shulkerpalette$packedPalette())
                : Optional.empty();
    }

    private static boolean on() {
        return palette().map(Palette::on).orElse(false);
    }

    private static Mode mode() {
        return palette().map(Palette::mode).orElse(Mode.RANDOM);
    }

    private static boolean creative() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isCreative();
    }

    /** Sends a panel press through vanilla's menu-button packet; the server decides. */
    private static void press(int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && mc.gui.screen() instanceof ShulkerBoxScreen screen) {
            mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, button);
        }
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, name);
    }
}
