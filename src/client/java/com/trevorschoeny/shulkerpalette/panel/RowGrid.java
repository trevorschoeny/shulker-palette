package com.trevorschoeny.shulkerpalette.panel;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.SlotElement;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PaletteMenu;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;

/**
 * The box's own section in MenuKit's container region, shown when the box needs more than
 * vanilla's screen: extra rows, or the rows bar in creative. Without either the screen stays
 * vanilla's.
 *
 * <ul>
 *   <li><b>The grid</b>: every row the box has while the window fits them, scrolling only when it doesn't, so it
 *       reads as one taller shulker. Every cell is a vanilla slot of the box's "Shulker" group
 *       (its 27 slots, then the extra rows after the player's inventory).</li>
 *   <li><b>The rows bar</b> (creative): two short buttons under the grid, as wide as it, that
 *       add or take away the box's last row. A row is only taken away empty.</li>
 * </ul>
 * The region lays the player's inventory out in its own panel below the section, so the bar
 * pushes the inventory down instead of covering its label. It is the same region Open Anywhere
 * stacks containers in, so the box section is one block wherever it is shown.
 */
public final class RowGrid {
    private RowGrid() {}

    private static final int PITCH = 18;
    private static final int WIDTH = Palette.ROW_WIDTH * PITCH;
    private static final int VISIBLE_ROWS = Palette.VANILLA_ROWS;
    private static final int ROW_GAP = 2;
    private static final int BAR_HEIGHT = 12;
    /** The box's slot group: the 27 vanilla slots and every extra row, in row order. */
    private static final SlotGroupId SHULKER = SlotGroupId.category(SlotGroupCategory.SHULKER_STORAGE);

    public static void register() {
        // A column of rows, each a row of nine cells; a row shows only while the box has it.
        // A vertical Flow measures only the rows shown, so the scroll ends at the box's last row.
        // Not one SlotGroupElement: it shows a vanilla slot whether or not the slot is active,
        // so it would lay out all 16 rows of every box.
        List<PanelElement> rows = new ArrayList<>();
        for (int r = 0; r < Palette.MAX_ROWS; r++) {
            int row = r;
            Flow.Builder cells = Flow.builder().gap(0, 0);
            for (int c = 0; c < Palette.ROW_WIDTH; c++) {
                cells.add(SlotElement.builder().slot(SHULKER, row * Palette.ROW_WIDTH + c).build());
            }
            rows.add(cells.visibleWhen(() -> row < rows()).build());
        }
        PanelElement grid = Flow.builder().vertical().gap(0, 0).addAll(rows).build();
        // Every row the box has while the window has the height; shorter by whole rows, and
        // scrolling with its lane beside it, only once it doesn't. Never under three rows.
        PanelElement scroll = ScrollContainer.builder().size(WIDTH, VISIBLE_ROWS * PITCH)
                .laneOutside().fitRoom(PITCH).content(List.of(grid)).build();

        int half = (WIDTH - ROW_GAP) / 2;
        PanelElement bar = Flow.builder().gap(ROW_GAP)
                .add(Button.builder()
                        .label(Component.translatable("shulkerpalette.panel.rows.less.label"))
                        .size(half, BAR_HEIGHT)
                        .onClick(() -> press(PanelActions.ROWS_LESS))
                        .tooltip(() -> Component.translatable("shulkerpalette.panel.rows.less", rows(), Palette.MAX_ROWS))
                        .disabledWhen(() -> rows() <= Palette.VANILLA_ROWS)
                        .build())
                .add(Button.builder()
                        .label(Component.translatable("shulkerpalette.panel.rows.more.label"))
                        .size(half, BAR_HEIGHT)
                        .onClick(() -> press(PanelActions.ROWS_MORE))
                        .tooltip(() -> Component.translatable("shulkerpalette.panel.rows.more", rows(), Palette.MAX_ROWS))
                        .disabledWhen(() -> rows() >= Palette.MAX_ROWS)
                        .build())
                .visibleWhen(RowGrid::creative)
                .build();

        ContainerRegion.section(ShulkerPalette.MOD_ID + ":box", ModeScope.BOTH)
                // The screen's own title: vanilla's key, or the name the player gave the box.
                .title(() -> Minecraft.getInstance().gui.screen() instanceof ShulkerBoxScreen screen
                        ? screen.getTitle() : Component.translatable("container.shulkerBox"))
                .texture(ContainerRegion.SHULKER_BOX)
                .content(Flow.builder().vertical().gap(0, ROW_GAP + 1).add(scroll).add(bar).build())
                .on(ShulkerBoxScreen.class)
                .visibleWhen(() -> palette().isPresent()
                        && (rows() > Palette.VANILLA_ROWS || creative()))
                .declare();
    }

    /** The open box screen's palette, as last synced; empty on a container with no palette behind it. */
    private static Optional<Palette> palette() {
        return Minecraft.getInstance().gui.screen() instanceof ShulkerBoxScreen screen
                && screen.getMenu() instanceof PaletteMenu menu
                ? PanelActions.unpack(menu.shulkerpalette$packedPalette())
                : Optional.empty();
    }

    private static int rows() {
        return palette().map(Palette::rows).orElse(Palette.VANILLA_ROWS);
    }

    /** Sends a rows press through vanilla's menu-button packet; the server decides. */
    private static void press(int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && mc.gui.screen() instanceof ShulkerBoxScreen screen) {
            mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, button);
        }
    }

    private static boolean creative() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isCreative();
    }
}
