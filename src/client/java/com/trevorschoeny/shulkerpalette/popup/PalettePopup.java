package com.trevorschoeny.shulkerpalette.popup;

import com.trevlar.menukit.api.hud.HudPanel;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.palette.Draw;
import com.trevorschoeny.shulkerpalette.palette.Mode;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Scroll;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Sneak + scroll with a pick or gradient palette in hand opens a small copy of the box's
 * screen right above its hotbar slot: every slot and item, at three quarters size, with the
 * selection lit (one slot in pick mode, a whole row in gradient mode). Further scrolls move
 * the light. Letting go of sneak closes it.
 *
 * <p>A MenuKit HUD panel above the hotbar; the grid itself is drawn by hand, in vanilla's
 * container colours, because it has to sit over one particular hotbar slot.
 *
 * <p>The server owns the selection. Between a scroll and the server's answer the popup shows
 * the selection it expects, so the light moves at once.
 */
public final class PalettePopup {
    private PalettePopup() {}

    // The grid is drawn at vanilla's own sizes, then scaled down as a whole.
    private static final float SCALE = 0.75f;
    private static final int SLOT = 18;
    private static final int PAD = 7;
    private static final int WIDTH = PAD * 2 + SLOT * Palette.ROW_WIDTH;
    private static final int HOTBAR_WIDTH = 182;

    // Vanilla's container colours: the light grey panel, its bevel, the dark slot well.
    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int WELL = 0xFF8B8B8B;
    private static final int LIGHT = 0x80FFFFFF;
    private static final int LIGHT_EDGE = 0xFFFFFFFF;

    /** How long to trust our own guess before going back to what the server says. */
    private static final long EXPECT_MILLIS = 1000;
    private static int expected = -1;
    private static long expectedAt;

    /** Opened by a sneak + scroll, closed by letting go of sneak. */
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

    /** Called right after a sneak + scroll is sent: open, guess the new selection. */
    public static void scrolled(int steps) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        Palette palette = Palette.activeOn(held);
        if (palette == null) return;
        open = true;
        expected = Scroll.moved(withSelection(palette, selection(palette)), contents(held), steps);
        expectedAt = System.currentTimeMillis();
    }

    private static boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyShift.isDown()) open = false;
        if (!open || mc.player == null || mc.gui.screen() != null) return false;
        Palette palette = Palette.activeOn(mc.player.getMainHandItem());
        return palette != null && Scroll.scrolls(palette);
    }

    /** The server's selection, unless we scrolled a moment ago and it hasn't caught up yet. */
    private static int selection(Palette palette) {
        boolean waiting = expected >= 0 && System.currentTimeMillis() - expectedAt < EXPECT_MILLIS;
        if (waiting && palette.selection() != expected) return expected;
        expected = -1;
        return palette.selection();
    }

    private static void draw(GuiGraphicsExtractor g, int originX, int originY) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = mc.player.getMainHandItem();
        Palette palette = Palette.activeOn(held);
        if (palette == null) return;
        palette = withSelection(palette, selection(palette));
        List<ItemStack> slots = Draw.slots(contents(held));
        int rows = palette.rows();
        int height = PAD * 2 + SLOT * rows;

        // Centred over the held hotbar slot (vanilla's slot i is centred 11 + 20i in from the
        // hotbar's left), kept on screen, bottom resting just above the hotbar.
        int slotCentre = originX + 11 + 20 * mc.player.getInventory().getSelectedSlot();
        float left = slotCentre - WIDTH * SCALE / 2;
        left = Math.clamp(left, 0, g.guiWidth() - WIDTH * SCALE);
        float top = originY + 1 - height * SCALE;

        g.pose().pushMatrix();
        g.pose().translate(left, top);
        g.pose().scale(SCALE, SCALE);

        // The panel, with vanilla's bevel.
        g.fill(0, 0, WIDTH, height, PANEL);
        g.fill(0, 0, WIDTH - 1, 1, BEVEL_LIGHT);
        g.fill(0, 0, 1, height - 1, BEVEL_LIGHT);
        g.fill(1, height - 1, WIDTH, height, BEVEL_DARK);
        g.fill(WIDTH - 1, 1, WIDTH, height, BEVEL_DARK);

        int picked = palette.mode() == Mode.PICK ? Draw.pickSlot(palette, slots) : -1;
        int row = palette.mode() == Mode.GRADIENT ? Draw.currentRow(palette) : -1;
        for (int i = 0; i < rows * Palette.ROW_WIDTH; i++) {
            int x = PAD + SLOT * (i % Palette.ROW_WIDTH);
            int y = PAD + SLOT * (i / Palette.ROW_WIDTH);
            g.fill(x, y, x + SLOT - 1, y + SLOT - 1, WELL);
            if (i < slots.size() && !slots.get(i).isEmpty()) {
                g.item(slots.get(i), x + 1, y + 1);
                g.itemDecorations(mc.font, slots.get(i), x + 1, y + 1);
            }
            if (i == picked || i / Palette.ROW_WIDTH == row) g.fill(x, y, x + SLOT - 1, y + SLOT - 1, LIGHT);
        }
        // An edge round what is lit, so it reads at a glance.
        if (picked >= 0) {
            g.outline(PAD + SLOT * (picked % Palette.ROW_WIDTH) - 1, PAD + SLOT * (picked / Palette.ROW_WIDTH) - 1, SLOT + 1, SLOT + 1, LIGHT_EDGE);
        } else if (row >= 0) {
            g.outline(PAD - 1, PAD + SLOT * row - 1, SLOT * Palette.ROW_WIDTH + 1, SLOT + 1, LIGHT_EDGE);
        }
        g.pose().popMatrix();
    }

    private static Palette withSelection(Palette p, int selection) {
        return new Palette(p.on(), p.mode(), p.repaint(), p.rows(), selection, p.next(), p.fingerprint());
    }

    private static ItemContainerContents contents(ItemStack shulker) {
        return shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
    }
}
