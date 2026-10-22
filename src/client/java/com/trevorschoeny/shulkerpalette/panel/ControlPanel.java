package com.trevorschoeny.shulkerpalette.panel;

import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevorschoeny.shulkerpalette.config.KeysClient;
import com.trevorschoeny.shulkerpalette.library.LibraryClient;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.palette.Mode;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PaletteMenu;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import java.util.List;
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
 * <p>Palette off shows only the power button. In creative a bar under the box adds or takes
 * away its last row, palette on or off, since extra rows are storage too; a row is only taken
 * away empty.
 */
public final class ControlPanel {
    private ControlPanel() {}

    private static final int SIZE = 14; // the icons are 10 px, drawn 1:1 inside a 2 px inset
    private static final int TINT_ON = 0x6040C040;
    private static final int FRAME_WIDTH = 176; // vanilla's shulker screen
    private static final int ROW_GAP = 2;
    private static final int ROW_BUTTON_HEIGHT = 12;

    public static void register() {
        Panel panel = Panel.builder(ShulkerPalette.MOD_ID + ":controls")
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)))
                // A vertical Flow, not a Column: a hidden button takes no room, so the ones
                // left close up (palette off in creative: power, then the rows buttons).
                .elements(List.of(Flow.builder().vertical().gap(0, 2)
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
                                .tooltip(() -> Component.translatable("shulkerpalette.panel.mode." + mode().getSerializedName(),
                                        KeysClient.MODIFIER.getTranslatedKeyMessage()))
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
                        .add(Button.builder()
                                .icon(() -> sprite("save"))
                                .size(SIZE, SIZE)
                                .onClick(() -> press(PanelActions.SAVE))
                                .tooltip(() -> answerOr(LibraryClient.Answer.SAVE, "shulkerpalette.panel.save"))
                                .visibleWhen(ControlPanel::on)
                                .build())
                        .add(Button.builder()
                                .icon(() -> sprite("load"))
                                .size(SIZE, SIZE)
                                .onClick(LibraryClient::toggleLoad)
                                .tooltip(() -> answerOr(LibraryClient.Answer.LOAD, "shulkerpalette.panel.load"))
                                .visibleWhen(ControlPanel::on)
                                .build())
                        .add(Button.builder()
                                .icon(() -> sprite("clear"))
                                .size(SIZE, SIZE)
                                .onClick(() -> press(PanelActions.CLEAR))
                                .tooltip(Component.translatable("shulkerpalette.panel.clear"))
                                .visibleWhen(() -> on() && openMenuId() >= 0 && CheatSheetClient.has(openMenuId()))
                                .build())
                        .build()))
                .build()
                .visibleWhen(() -> palette().isPresent());

        // Rows, in creative: a short bar of two buttons under the box's frame, as wide as it,
        // since rows belong to the box's grid rather than to the palette.
        int half = (FRAME_WIDTH - ROW_GAP) / 2;
        Panel rows = Panel.builder(ShulkerPalette.MOD_ID + ":rows_bar")
                .style(PanelStyle.NONE)
                .in(ModeScope.CREATIVE, Placement.screen(PanelPosition.region(OutsideRegion.BOTTOM_CENTER)))
                .elements(List.of(Flow.builder().gap(ROW_GAP)
                        .add(Button.builder()
                                .label(Component.translatable("shulkerpalette.panel.rows.less.label"))
                                .size(half, ROW_BUTTON_HEIGHT)
                                .onClick(() -> press(PanelActions.ROWS_LESS))
                                .tooltip(() -> Component.translatable("shulkerpalette.panel.rows.less", rows(), Palette.MAX_ROWS))
                                .disabledWhen(() -> rows() <= Palette.VANILLA_ROWS)
                                .build())
                        .add(Button.builder()
                                .label(Component.translatable("shulkerpalette.panel.rows.more.label"))
                                .size(half, ROW_BUTTON_HEIGHT)
                                .onClick(() -> press(PanelActions.ROWS_MORE))
                                .tooltip(() -> Component.translatable("shulkerpalette.panel.rows.more", rows(), Palette.MAX_ROWS))
                                .disabledWhen(() -> rows() >= Palette.MAX_ROWS)
                                .build())
                        .build()))
                .build()
                .visibleWhen(() -> palette().isPresent());

        new ScreenPanelAdapter(panel).on(ShulkerBoxScreen.class);
        new ScreenPanelAdapter(rows).on(ShulkerBoxScreen.class);
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

    private static int rows() {
        return palette().map(Palette::rows).orElse(Palette.VANILLA_ROWS);
    }

    private static Mode mode() {
        return palette().map(Palette::mode).orElse(Mode.RANDOM);
    }

    private static int openMenuId() {
        return Minecraft.getInstance().gui.screen() instanceof ShulkerBoxScreen screen ? screen.getMenu().containerId : -1;
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

    /** A Save or Load button's tooltip: the answer to the last press while it is fresh, else what it does. */
    private static Component answerOr(LibraryClient.Answer button, String key) {
        Component answer = LibraryClient.answerFor(button);
        return answer != null ? answer : Component.translatable(key);
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(ShulkerPalette.MOD_ID, name);
    }
}
