package com.trevorschoeny.shulkerpalette.popup;

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
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Sneak + scroll with a palette in hand opens a small view of the box right above its hotbar
 * slot: each row drawn as a three-quarter-size hotbar, every item shown, and a title saying
 * what the box does. In pick mode the picked slot wears the hotbar's selection frame, in
 * gradient mode the whole row does, and further scrolls move it. In the random modes the
 * popup only shows the contents. Letting go of sneak closes it.
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
    private static final int TITLE_H = 11;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int HOTBAR_WIDTH = 182;

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
        if (!Scroll.scrolls(palette)) return; // random modes: the popup just shows the box
        expected = Scroll.moved(withSelection(palette, selection(palette)), contents(held), steps);
        expectedAt = System.currentTimeMillis();
    }

    private static boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyShift.isDown()) open = false;
        if (!open || mc.player == null || mc.gui.screen() != null) return false;
        return Palette.activeOn(mc.player.getMainHandItem()) != null;
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
        int height = TITLE_H + ROW_H * rows;

        // Centred over the held hotbar slot (vanilla's slot i is centred 11 + 20i in from the
        // hotbar's left), kept on screen, bottom resting just above the hotbar.
        int slotCentre = originX + 11 + 20 * mc.player.getInventory().getSelectedSlot();
        float left = Math.clamp(slotCentre - ROW_W * SCALE / 2, 0, g.guiWidth() - ROW_W * SCALE);
        float top = originY + 1 - height * SCALE;

        g.pose().pushMatrix();
        g.pose().translate(left, top);
        g.pose().scale(SCALE, SCALE);

        // What the box does, centred above the grid.
        Component title = title(palette);
        g.text(mc.font, title.getVisualOrderText(), (ROW_W - mc.font.width(title)) / 2, 1, TITLE_COLOR, true);

        int picked = palette.mode() == Mode.PICK ? Draw.pickSlot(palette, slots) : -1;
        int lit = palette.mode() == Mode.GRADIENT ? Draw.currentRow(palette) : -1;
        for (int row = 0; row < rows; row++) {
            int y = TITLE_H + ROW_H * row;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, ROW, 0, y, ROW_W, ROW_H);
            for (int col = 0; col < Palette.ROW_WIDTH; col++) {
                int i = row * Palette.ROW_WIDTH + col;
                int x = SLOT_PITCH * col;
                if (i < slots.size() && !slots.get(i).isEmpty()) {
                    g.item(slots.get(i), x + 3, y + 3);
                    g.itemDecorations(mc.font, slots.get(i), x + 3, y + 3);
                }
                // The hotbar's own selection frame: on the picked slot, or on every slot of the gradient row.
                if (i == picked || row == lit) g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION, x - 1, y - 1, 24, 23);
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
