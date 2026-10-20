package com.trevorschoeny.shulkerpalette.panel;

import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.slot.SlotElement;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.mixin.ScreenPosAccessor;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PaletteMenu;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.inventory.MenuType;

/**
 * A box with extra rows shows all of them as one grid where vanilla's three rows sit: the
 * same three rows tall, scrolling smoothly through every row the box has, so it reads as one
 * taller shulker. Every cell is a vanilla slot of the menu (the 27 box slots, then the extra
 * rows after the player's inventory) moved into a MenuKit scroll box; a row straddling the
 * edge is cut, a row out of view is parked.
 *
 * <p>Shown whenever the box has more than three rows, palette on or off. Hidden, the 27 slots
 * go back to their vanilla places and the screen is vanilla's.
 */
public final class RowGrid {
    private RowGrid() {}

    private static final int PITCH = 18;
    private static final int WIDTH = Palette.ROW_WIDTH * PITCH;
    private static final int VISIBLE_ROWS = Palette.VANILLA_ROWS;
    /** Vanilla's grid frame, relative to the screen's frame: one pixel up-left of the first slot. */
    private static final int LEFT = 7, TOP = 17;
    /** The container background's grey, to cover the frames vanilla draws at the old spots. */
    private static final int BACKGROUND = 0xFFC6C6C6;
    /** Vanilla's layout: box 0 to 26, player 27 to 62, then the extra rows. */
    private static final int BOX = 27, EXTRA_START = 63;

    public static void register() {
        // A column of rows, each a row of nine cells; a row shows only while the box has it.
        // A vertical Flow measures only the rows shown, so the scroll ends at the box's last row.
        List<PanelElement> rows = new ArrayList<>();
        for (int r = 0; r < Palette.MAX_ROWS; r++) {
            int row = r;
            Flow.Builder cells = Flow.builder().gap(0, 0);
            for (int c = 0; c < Palette.ROW_WIDTH; c++) {
                int i = row * Palette.ROW_WIDTH + c;
                int menuIndex = i < BOX ? i : EXTRA_START + i - BOX;
                cells.add(SlotElement.builder().slot(Address.menuSlot(MenuType.SHULKER_BOX, menuIndex)).build());
            }
            rows.add(cells.visibleWhen(() -> row < rows()).build());
        }
        PanelElement grid = Flow.builder().vertical().gap(0, 0).addAll(rows).build();
        int lane = ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;
        Panel panel = Panel.builder(ShulkerPalette.MOD_ID + ":rows")
                .style(PanelStyle.NONE)
                .position(PanelPosition.pixel(RowGrid::origin))
                .add(new PanelElement() {
                    // Covers the frames vanilla's background draws at the old spots; the slot
                    // elements draw their own frames where they are now.
                    @Override public int getChildX() { return 0; }
                    @Override public int getChildY() { return 0; }
                    @Override public int getWidth() { return WIDTH; }
                    @Override public int getHeight() { return VISIBLE_ROWS * PITCH; }
                    @Override public void render(RenderContext ctx) {
                        ctx.graphics().fill(ctx.originX(), ctx.originY(), ctx.originX() + WIDTH, ctx.originY() + getHeight(), BACKGROUND);
                    }
                })
                .add(ScrollContainer.builder().at(0, 0).size(WIDTH + lane, VISIBLE_ROWS * PITCH).content(List.of(grid)).build())
                .visibleWhen(() -> rows() > Palette.VANILLA_ROWS)
                .build();
        new ScreenPanelAdapter(panel, 0).on(ShulkerBoxScreen.class);
    }

    /** The open shulker screen's rows, palette on or off. */
    private static int rows() {
        return Minecraft.getInstance().gui.screen() instanceof ShulkerBoxScreen screen
                && screen.getMenu() instanceof PaletteMenu menu
                ? PanelActions.unpack(menu.shulkerpalette$packedPalette()).map(Palette::rows).orElse(Palette.VANILLA_ROWS)
                : Palette.VANILLA_ROWS;
    }

    private static ScreenOrigin origin() {
        return Minecraft.getInstance().gui.screen() instanceof ShulkerBoxScreen screen
                ? new ScreenOrigin(((ScreenPosAccessor) screen).shulkerpalette$leftPos() + LEFT,
                        ((ScreenPosAccessor) screen).shulkerpalette$topPos() + TOP)
                : null;
    }
}
