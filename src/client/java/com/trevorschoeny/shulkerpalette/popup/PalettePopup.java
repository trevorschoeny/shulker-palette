package com.trevorschoeny.shulkerpalette.popup;

import com.trevorschoeny.shulkerpalette.config.Config;
import com.trevorschoeny.shulkerpalette.config.KeysClient;
import com.trevlar.menukit.api.hud.HudPanel;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.PanelStyle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.palette.Draw;
import com.trevorschoeny.shulkerpalette.palette.Mode;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Scroll;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Palette modifier + scroll with a palette in hand opens a small view of the box right above its hotbar
 * slot: rows drawn as three-quarter-size hotbars, one stack per block type with its total,
 * and a title saying what the box does. The hotbar's selection frame marks what a click can
 * place: the picked type, the gradient row, or in the random modes every type. Letting go
 * of the modifier closes it. The settings can turn it off.
 *
 * <p>A MenuKit HUD panel above the hotbar; the grid itself is drawn by hand, from vanilla's
 * hotbar sprites, because it has to sit over one particular hotbar slot.
 *
 * <p>The server owns the selection. Between a scroll and the server's answer the popup shows
 * the selection it expects, so the light moves at once.
 */
public final class PalettePopup {
    private PalettePopup() {}

    // Each row of the box is drawn as vanilla's hotbar sprite (translucent, as the hotbar is),
    // at the hotbar's own size, and the whole grid is then scaled down.
    private static final float SCALE = 0.75f;
    private static final Identifier ROW = Identifier.withDefaultNamespace("hud/hotbar");
    private static final Identifier SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");
    private static final int ROW_W = 182;
    private static final int ROW_H = 22;
    private static final int SLOT_PITCH = 20;
    /** Rows overlap by one pixel, so neighbouring rows share a border line instead of doubling it. */
    private static final int ROW_PITCH = 21;
    private static final int TITLE_H = 11;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int HOTBAR_WIDTH = 182;

    /** How long to trust our own guess before going back to what the server says. */
    private static final long EXPECT_MILLIS = 1000;
    private static int expected = -1;
    private static long expectedAt;

    /** Opened by a modifier + scroll, closed by letting go of the modifier. */
    private static boolean open;

    public static void register() {
        // A one-pixel strip along the top of the hotbar, as wide as it: the anchor the grid is
        // drawn up from. HUD panels don't clip, so the grid's height (it grows with extra
        // rows) never has to be reserved.
        HudPanel.builder(ShulkerPalette.MOD_ID + ":popup")
                .region(OutsideRegion.TOP_CENTER)
                .style(PanelStyle.NONE)
                .size(HOTBAR_WIDTH, 1)
                .visibleWhen(PalettePopup::shown)
                .custom(0, 0, HOTBAR_WIDTH, 1, ctx -> draw(ctx.graphics(), ctx.originX(), ctx.originY()))
                .build();
    }

    /** Called right after a modifier + scroll is sent: open, guess the new selection. */
    public static void scrolled(int steps) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        Palette palette = Palette.activeOn(held);
        if (palette == null) return;
        open = true;
        if (!Scroll.scrolls(palette)) return; // random modes: the popup just shows the box
        expected = Scroll.moved(withSelection(palette, selection(palette)), contents(held), steps);
        expectedAt = System.currentTimeMillis();
    }

    private static boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        if (!KeysClient.modifierDown()) open = false;
        if (!open || !Config.popup() || mc.player == null || mc.gui.screen() != null) return false;
        return Palette.activeOn(mc.player.getMainHandItem()) != null;
    }

    /** The server's selection, unless we scrolled a moment ago and it hasn't caught up yet. */
    private static int selection(Palette palette) {
        boolean waiting = expected >= 0 && System.currentTimeMillis() - expectedAt < EXPECT_MILLIS;
        if (waiting && palette.selection() != expected) return expected;
        expected = -1;
        return palette.selection();
    }

    /** One cell of the popup: the stack drawn, and whether it wears the selection frame. */
    private record Cell(ItemStack stack, boolean lit) {}

    /**
     * What the popup shows, row by row: one stack per block type holding its total (equal
     * random: one item per type), never empty slots or non-blocks. The selection frame marks
     * what a click can place.
     * <ul>
     *   <li>Random and equal random: the box's types, nine to a row, all framed.</li>
     *   <li>Pick: the box's types, nine to a row, the picked one framed.</li>
     *   <li>Gradient: one row per box row (rows with no blocks left out), its types with the
     *       row's totals, the current row framed.</li>
     * </ul>
     */
    private static List<List<Cell>> rows(Palette palette, List<ItemStack> slots) {
        List<List<Cell>> rows = new ArrayList<>();
        if (palette.mode() == Mode.GRADIENT) {
            int lit = Draw.currentRow(palette);
            for (int row = 0; row < palette.rows(); row++) {
                int from = Math.min(row * Palette.ROW_WIDTH, slots.size());
                boolean current = row == lit;
                List<Cell> cells = cells(Draw.types(slots.subList(from, Math.min(from + Palette.ROW_WIDTH, slots.size()))), false, i -> current);
                if (!cells.isEmpty()) rows.add(cells);
            }
            return rows;
        }
        List<Draw.Entry> types = Draw.types(slots);
        int picked = palette.mode() == Mode.PICK ? Draw.currentPick(palette, types) : -1;
        List<Cell> cells = cells(types, palette.mode() == Mode.EQUAL, i -> picked < 0 || i == picked);
        for (int from = 0; from < cells.size(); from += Palette.ROW_WIDTH) {
            rows.add(cells.subList(from, Math.min(from + Palette.ROW_WIDTH, cells.size())));
        }
        return rows;
    }

    private static List<Cell> cells(List<Draw.Entry> types, boolean oneEach, IntPredicate lit) {
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            cells.add(new Cell(types.get(i).type().copyWithCount(oneEach ? 1 : types.get(i).total()), lit.test(i)));
        }
        return cells;
    }

    private static void draw(GuiGraphicsExtractor g, int originX, int originY) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = mc.player.getMainHandItem();
        Palette palette = Palette.activeOn(held);
        if (palette == null) return;
        palette = withSelection(palette, selection(palette));
        List<List<Cell>> rows = rows(palette, Draw.slots(contents(held)));
        int height = TITLE_H + ROW_PITCH * rows.size() + (ROW_H - ROW_PITCH);

        // Centred over the held hotbar slot (vanilla's slot i is centred 11 + 20i in from the
        // hotbar's left), kept on screen, bottom resting just above the hotbar.
        int widest = rows.stream().mapToInt(List::size).max().orElse(0) * SLOT_PITCH + 2;
        Component title = title(palette);
        int width = Math.max(widest, mc.font.width(title));
        int slotCentre = originX + 11 + 20 * mc.player.getInventory().getSelectedSlot();
        float left = Math.clamp(slotCentre - width * SCALE / 2, 0, g.guiWidth() - width * SCALE);
        float top = originY + 1 - height * SCALE;

        g.pose().pushMatrix();
        g.pose().translate(left, top);
        g.pose().scale(SCALE, SCALE);

        // What the box does, centred above the grid.
        g.text(mc.font, title.getVisualOrderText(), (width - mc.font.width(title)) / 2, 1, TITLE_COLOR, true);

        for (int r = 0; r < rows.size(); r++) {
            List<Cell> cells = rows.get(r);
            int rowWidth = cells.size() * SLOT_PITCH + 2;
            int x0 = (width - rowWidth) / 2;
            int y = TITLE_H + ROW_PITCH * r;
            // The hotbar sprite cut to this many slots: its left end and slots, then its right edge.
            g.blitSprite(RenderPipelines.GUI_TEXTURED, ROW, ROW_W, ROW_H, 0, 0, x0, y, rowWidth - 1, ROW_H);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, ROW, ROW_W, ROW_H, ROW_W - 1, 0, x0 + rowWidth - 1, y, 1, ROW_H);
            for (int c = 0; c < cells.size(); c++) {
                int x = x0 + SLOT_PITCH * c;
                ItemStack stack = cells.get(c).stack();
                g.item(stack, x + 3, y + 3);
                g.itemDecorations(mc.font, stack, x + 3, y + 3);
            }
        }
        // Frames last, so a frame is never covered by the next row's sprite.
        for (int r = 0; r < rows.size(); r++) {
            List<Cell> cells = rows.get(r);
            int x0 = (width - (cells.size() * SLOT_PITCH + 2)) / 2;
            for (int c = 0; c < cells.size(); c++) {
                if (cells.get(c).lit()) {
                    g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION, x0 + SLOT_PITCH * c - 1, TITLE_H + ROW_PITCH * r - 1, 24, 23);
                }
            }
        }
        g.pose().popMatrix();
    }

    private static Component title(Palette palette) {
        return switch (palette.mode()) {
            case RANDOM -> Component.translatable("shulkerpalette.popup.random");
            case EQUAL -> Component.translatable("shulkerpalette.popup.equal");
            case PICK -> Component.translatable("shulkerpalette.popup.pick");
            case GRADIENT -> Component.translatable("shulkerpalette.popup.gradient", Draw.currentRow(palette) + 1);
        };
    }

    private static Palette withSelection(Palette p, int selection) {
        return new Palette(p.on(), p.mode(), p.repaint(), p.rows(), selection, p.next(), p.fingerprint());
    }

    private static ItemContainerContents contents(ItemStack shulker) {
        return shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
    }
}
